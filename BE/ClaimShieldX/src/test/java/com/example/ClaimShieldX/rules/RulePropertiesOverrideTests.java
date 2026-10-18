package com.example.ClaimShieldX.rules;

import com.example.ClaimShieldX.rules.config.RuleProperties;
import com.example.ClaimShieldX.support.TestActors;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TestPropertySource(properties = {"app.rules.variance-percent-threshold=5.00", "app.rules.time-zone=UTC", "app.rules.near-duplicate-similarity=0.90", "app.rules.date-tolerance-days=2"})
class RulePropertiesOverrideTests extends RuleApiTestBase {
    @Autowired RuleProperties properties;

    @Test
    void changedSettingsAreBoundShownInTheCatalogueAndStoredWithTheRun() throws Exception {
        TestActors actor = investigator();
        UUID claim = cleanClaim(actor, "CLM-OVERRIDDEN");

        assertThat(properties.variancePercentThreshold()).isEqualByComparingTo("5.00");
        assertThat(properties.timeZone()).isEqualTo(ZoneId.of("UTC"));
        assertThat(properties.dateToleranceDays()).isEqualTo(2);
        mockMvc.perform(get("/api/v1/rules").header("Authorization", actor.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configuration.variancePercentThreshold").value("5.00"))
                .andExpect(jsonPath("$.configuration.timeZone").value("UTC"))
                .andExpect(jsonPath("$.configuration.dateToleranceDays").value(2));
        String run = runOk(actor, claim);
        assertThat(JsonPath.<Map<String, Object>>read(run, "$.configuration")).isEqualTo(properties.snapshot());
        assertThat(JsonPath.<String>read(run, "$.configuration.variancePercentThreshold")).isEqualTo("5.00");
    }

    @Test
    void theNewThresholdChangesWhatIsFlagged() throws Exception {
        TestActors actor = investigator();
        UUID claim = ruleClaim(actor, "CLM-STRICT");
        com.example.ClaimShieldX.rules.engine.Inputs.Doc invoice = com.example.ClaimShieldX.rules.engine.Inputs.invoice().set("grandTotal", "16000.00")
                .clearLines().line("Front bumper replacement", "2", "5000.00", "10000.00").line("Headlamp assembly left", "1", "3500.00", "3500.00")
                .set("subtotal", "13500.00").set("taxTotal", "2500.00");
        attach(actor, claim, invoice);
        attach(actor, claim, com.example.ClaimShieldX.rules.engine.Inputs.estimate().set("grandTotal", "15000.00"));

        // 6.67% above the estimate: under the default 10% threshold, so it would pass there, but over this test's 5%.
        assertThat(outcome(runOk(actor, claim), "ESTIMATE_INVOICE_VARIANCE")).isEqualTo("FLAG");
    }
}
