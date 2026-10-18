package com.example.ClaimShieldX.history;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.example.ClaimShieldX.history.repository.GarageHistoryRepository;
import com.example.ClaimShieldX.history.repository.HistoricalClaimRepository;
import com.example.ClaimShieldX.support.TestActors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.example.ClaimShieldX.history.HistoryCsv.claim;
import static com.example.ClaimShieldX.history.HistoryCsv.claims;
import static com.example.ClaimShieldX.history.HistoryCsv.garage;
import static com.example.ClaimShieldX.history.HistoryCsv.garages;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** A failure in the middle of an import leaves nothing half-stored, says so, and lets the client try again. */
class HistoryImportAtomicityTests extends HistoryApiTestBase {
    @MockitoSpyBean private HistoricalClaimRepository claimRepository;
    @MockitoSpyBean private GarageHistoryRepository garageRepository;
    @Autowired private HistoricalClaimRepository injected;

    private ListAppender<ILoggingEvent> logs;
    private Logger root;
    private Logger application;
    private Level previousApplicationLevel;

    /**
     * Captures everything at the levels the application really runs with (INFO), with the application's own packages turned up to
     * DEBUG so that nothing it chooses to log can hide. Framework internals stay at their defaults: turning Hibernate itself to
     * DEBUG makes it print every field of every entity it holds, which no application code can prevent.
     */
    @BeforeEach
    void captureLogs() {
        root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        application = (Logger) LoggerFactory.getLogger("com.example.ClaimShieldX");
        previousApplicationLevel = application.getLevel();
        logs = new ListAppender<>();
        logs.start();
        root.addAppender(logs);
        application.setLevel(Level.DEBUG);
    }

    @AfterEach
    void stopCapturingLogs() {
        root.detachAppender(logs);
        application.setLevel(previousApplicationLevel);
        org.mockito.Mockito.reset(claimRepository, garageRepository);
    }

    private String everythingLogged() {
        return logs.list.stream().map(e -> e.getFormattedMessage() + " " + (e.getThrowableProxy() == null ? "" : e.getThrowableProxy().getMessage()))
                .collect(Collectors.joining("\n"));
    }

    @Test
    void anUnexpectedFailureLeavesNoRowsMarksTheImportFailedAndReleasesTheKey() throws Exception {
        TestActors admin = admin();
        byte[] file = claims(claim("1"), claim("2").vehicle("VEH-2")).getBytes(StandardCharsets.UTF_8);
        String key = newKey();
        doThrow(new RuntimeException("simulated database failure")).when(claimRepository).findBySourceRecordIds(any(), any());

        importBytes(admin, file, "HISTORICAL_CLAIMS", "1", key, null).andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));

        assertThat(count("historical_claims", admin.organizationId())).isZero();
        assertThat(count("historical_import_rows", admin.organizationId())).isZero();
        assertThat(jdbc.queryForMap("select status, failure_code, total_rows from historical_imports where organization_id = ?", admin.organizationId()))
                .containsEntry("status", "FAILED").containsEntry("failure_code", "INTERNAL_ERROR").containsEntry("total_rows", 0);

        // The key was released: the same request, retried once the fault is gone, is a fresh import and succeeds.
        org.mockito.Mockito.reset(claimRepository);
        importBytes(admin, file, "HISTORICAL_CLAIMS", "1", key, null).andExpect(status().isAccepted()).andExpect(header().doesNotExist("Idempotent-Replay"))
                .andExpect(jsonPath("$.status").value("COMPLETED")).andExpect(jsonPath("$.counts.accepted").value(2));
        assertThat(count("historical_claims", admin.organizationId())).isEqualTo(2);
        assertThat(count("historical_imports", admin.organizationId())).isEqualTo(2);
    }

    @Test
    void aFailureInGarageHistoryIsJustAsClean() throws Exception {
        TestActors admin = admin();
        byte[] file = garages(garage("1")).getBytes(StandardCharsets.UTF_8);
        doThrow(new RuntimeException("simulated database failure")).when(garageRepository).findBySourceRecordIds(any(), any());

        importBytes(admin, file, "GARAGE_HISTORY", "1", newKey(), null).andExpect(status().isInternalServerError());

        assertThat(count("garage_history_records", admin.organizationId())).isZero();
        assertThat(jdbc.queryForObject("select status from historical_imports where organization_id = ?", String.class, admin.organizationId())).isEqualTo("FAILED");
    }

    @Test
    void theFailureDetailSentToTheClientNeverRevealsTheInternalCause() throws Exception {
        TestActors admin = admin();
        doThrow(new RuntimeException("password=hunter2 jdbc:postgresql://secret-host")).when(claimRepository).findBySourceRecordIds(any(), any());

        String body = importCsv(admin, claims(claim("1")), "HISTORICAL_CLAIMS").andExpect(status().isInternalServerError()).andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("hunter2").doesNotContain("secret-host").doesNotContain("RuntimeException");
        assertThat(jdbc.queryForObject("select failure_detail from historical_imports where organization_id = ?", String.class, admin.organizationId()))
                .doesNotContain("hunter2").doesNotContain("secret-host");
    }

    @Test
    void nothingFromAFileIsEverWrittenToTheLogs() throws Exception {
        TestActors admin = admin();
        String csv = claims(
                claim("LEAKCHECK-1").vehicle("LEAKCHECK-VEHICLE-TOKEN").policy("LEAKCHECK-POLICY-TOKEN").claimant("LEAKCHECK-CLAIMANT-TOKEN").garage("LEAKCHECK-GARAGE")
                        .summary("LEAKCHECK-REPAIR-SUMMARY-TEXT"),
                claim("LEAKCHECK-2").vehicle("LEAKCHECK-VEHICLE-TOKEN-2").type("LEAKCHECK-BAD-TYPE").incident("LEAKCHECK-BAD-DATE"));

        importCsv(admin, csv, "HISTORICAL_CLAIMS").andExpect(status().isAccepted());
        importCsv(admin, csv, "HISTORICAL_CLAIMS").andExpect(status().isAccepted());
        importBytes(admin, ("LEAKCHECK-NOT-A-HEADER,x\r\nLEAKCHECK-ROW,y\r\n").getBytes(StandardCharsets.UTF_8), "HISTORICAL_CLAIMS", "1", newKey(), null).andExpect(status().isAccepted());
        importBytes(admin, ("LEAKCHECK,\"unterminated\r\n").getBytes(StandardCharsets.UTF_8), "HISTORICAL_CLAIMS", "1", newKey(), null).andExpect(status().isAccepted());
        UUID claim = claimWith(admin, "LEAKCHECK-NEW", "LEAKCHECK-VEHICLE-TOKEN", "LEAKCHECK-POLICY-TOKEN", null, "LEAKCHECK-GARAGE", java.time.Instant.parse("2025-05-01T05:00:00Z"));
        matches(admin, claim).andExpect(status().isOk());

        String leaks = logs.list.stream().filter(e -> e.getFormattedMessage().contains("LEAKCHECK")).map(e -> e.getLoggerName() + " | " + e.getFormattedMessage().substring(0, Math.min(160, e.getFormattedMessage().length()))).collect(Collectors.joining("\n"));
        assertThat(leaks).as("log lines that contain file content").isEmpty();
    }

    @Test
    void theImportsOwnLogLineCarriesOnlyCounts() throws Exception {
        TestActors admin = admin();

        importCsv(admin, claims(claim("1"), claim("2").vehicle("VEH-2")), "HISTORICAL_CLAIMS").andExpect(status().isAccepted());

        List<String> lines = logs.list.stream().map(ILoggingEvent::getFormattedMessage).filter(m -> m.startsWith("History import finished")).toList();
        assertThat(lines).hasSize(1);
        assertThat(lines.get(0)).contains("rows=2").contains("accepted=2").contains("rejected=0").contains("skippedDuplicate=0")
                .doesNotContain("VEH-TOKEN").doesNotContain("HC-1").doesNotContain("CLM-HIST");
        assertThat(injected).isNotNull();
    }
}
