package com.example.ClaimShieldX.investigation;

import com.example.ClaimShieldX.investigation.model.Stage;
import com.example.ClaimShieldX.investigation.work.StageContext;
import com.example.ClaimShieldX.investigation.work.StageHandler;
import com.example.ClaimShieldX.investigation.work.StageOutcome;
import com.example.ClaimShieldX.support.TestActors;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import java.sql.Timestamp;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/** The real background worker: scheduled polling, its own threads and the heartbeat, with short intervals so the test is quick. */
/**
 * The context of this class has a live background worker polling the one shared queue. Spring would keep that context cached for the rest of
 * the run, and its worker would take investigations that other test classes created, so the context is closed as soon as this class is done.
 */
@org.springframework.test.annotation.DirtiesContext(classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
@Import(InvestigationWorkerTests.SlowStage.class)
@TestPropertySource(properties = {
        "app.investigation.worker.enabled=true",
        "app.investigation.worker.poll-interval=200ms",
        "app.investigation.lease=3s",
        "app.investigation.heartbeat=1s"})
class InvestigationWorkerTests extends InvestigationApiTestBase {

    /** A stage that takes longer than the lease, so only the heartbeat can keep the investigation from being taken over. */
    @TestConfiguration
    static class SlowStage {
        /** The real hypotheses stage is taken out so the slow one is the only handler of that stage. */
        @Bean
        static org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor withoutTheRealHypothesesStage() {
            return registry -> registry.removeBeanDefinition("hypothesesStageHandler");
        }

        @Bean
        StageHandler slowHypotheses() {
            return new StageHandler() {
                @Override
                public Stage stage() {
                    return Stage.BUILDING_HYPOTHESES;
                }

                @Override
                public StageOutcome execute(StageContext context) throws Exception {
                    Thread.sleep(5_000);
                    return StageOutcome.ok(Map.of("slow", true));
                }
            };
        }
    }

    @Test
    void theBackgroundWorkerPicksUpAQueuedInvestigationAndRunsItToTheEndOnItsOwn() throws Exception {
        TestActors actor = actor();
        UUID claim = claimWithKnownFacts(actor);
        uploadOk(actor, claim, pdf(com.example.ClaimShieldX.extraction.support.TestPdfs.pdf(1, ++seed)), "FINAL_INVOICE");
        model.thenReturn(com.example.ClaimShieldX.extraction.support.TestDocuments.json(com.example.ClaimShieldX.extraction.support.TestDocuments.invoice(GARAGE, VEHICLE, "12/09/2026")));
        UUID id = createOk(actor, claim);

        await().atMost(Duration.ofSeconds(60)).pollInterval(Duration.ofMillis(250)).until(() -> "COMPLETED".equals(investigationStatus(id)));

        assertThat(stageStatus(id, "EXTRACTING")).isEqualTo("OK");
        assertThat(stageStatus(id, "BUILDING_HYPOTHESES")).isEqualTo("OK");
        assertThat(jdbc.queryForObject("select lease_owner is null from investigations where id = ?", Boolean.class, id)).isTrue();
    }

    @Test
    void theHeartbeatKeepsTheLeaseAliveWhileAStageRunsLongerThanTheLease() throws Exception {
        TestActors actor = actor();
        UUID claim = claimWithKnownFacts(actor);
        uploadOk(actor, claim, pdf(com.example.ClaimShieldX.extraction.support.TestPdfs.pdf(1, ++seed)), "FINAL_INVOICE");
        model.thenReturn(com.example.ClaimShieldX.extraction.support.TestDocuments.json(com.example.ClaimShieldX.extraction.support.TestDocuments.invoice(GARAGE, VEHICLE, "12/09/2026")));
        UUID id = createOk(actor, claim);

        await().atMost(Duration.ofSeconds(60)).pollInterval(Duration.ofMillis(100)).until(() -> "BUILDING_HYPOTHESES".equals(investigationStatus(id)));
        Timestamp first = jdbc.queryForObject("select lease_expires_at from investigations where id = ?", Timestamp.class, id);
        String owner = jdbc.queryForObject("select lease_owner from investigations where id = ?", String.class, id);
        Thread.sleep(2_500);
        Timestamp later = jdbc.queryForObject("select lease_expires_at from investigations where id = ?", Timestamp.class, id);

        assertThat(later).as("the lease was renewed while the stage was still running").isAfter(first);
        assertThat(jdbc.queryForObject("select lease_owner from investigations where id = ?", String.class, id)).isEqualTo(owner);
        assertThat(investigationStatus(id)).isEqualTo("BUILDING_HYPOTHESES");
        await().atMost(Duration.ofSeconds(60)).until(() -> "COMPLETED".equals(investigationStatus(id)));
        assertThat(jdbc.queryForObject("select attempt from investigations where id = ?", Integer.class, id)).as("never taken over, so never counted as a crash").isEqualTo(1);
    }
}
