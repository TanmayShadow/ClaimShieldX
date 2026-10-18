package com.example.ClaimShieldX.rules;

import com.example.ClaimShieldX.claims.model.Claim;
import com.example.ClaimShieldX.claims.repository.ClaimsRepository;
import com.example.ClaimShieldX.rules.config.RuleProperties;
import com.example.ClaimShieldX.rules.engine.Inputs;
import com.example.ClaimShieldX.rules.engine.RuleEngine;
import com.example.ClaimShieldX.rules.engine.RuleInputs;
import com.example.ClaimShieldX.rules.model.RuleResult;
import com.example.ClaimShieldX.rules.service.RuleInputLoader;
import com.example.ClaimShieldX.support.TestActors;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** The guide's exit criterion: the same inputs and rule version produce the same results, and a stored run is exactly what the rules compute from what it used. */
class RuleReproducibilityTests extends RuleApiTestBase {
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired RuleInputLoader loader;
    @Autowired RuleEngine engine;
    @Autowired RuleProperties properties;
    @Autowired ClaimsRepository claims;

    /** Through text, so a number is read back the way the database returns it (an int node, not a long node, for the same value). */
    private static JsonNode viaText(Object value) throws Exception {
        return JSON.readTree(JSON.writeValueAsString(value));
    }

    /** A claim that makes most rules say something: an arithmetic error, a repeated line, a large variance, a repeated file and an earlier invoice. */
    private UUID busyClaim(TestActors actor) {
        UUID claim = ruleClaim(actor, "CLM-BUSY");
        String shared = Inputs.invoice().sha256;
        attach(actor, claim, Inputs.invoice().sha(shared).set("grandTotal", "30000.00")
                .clearLines().line("Wiper blade set", "1", "800.00", "800.00").line("Wiper blade set", "1", "800.00", "800.00").line("Front bumper replacement", "2", "5000.00", "10000.00"));
        attach(actor, claim, Inputs.estimate().set("grandTotal", "12000.00"));
        attach(actor, ruleClaim(actor, "CLM-ELSEWHERE"), Inputs.invoice().sha(shared));
        return claim;
    }

    @Test
    void loadingTheSameClaimTwiceGivesEqualInputsAndTheEngineGivesEqualResults() {
        TestActors actor = investigator();
        UUID id = busyClaim(actor);
        Claim claim = claims.findByIdAndOrganizationId(id, actor.organizationId()).orElseThrow();

        RuleInputs first = loader.load(actor.organizationId(), claim);
        RuleInputs second = loader.load(actor.organizationId(), claim);

        assertThat(second).isEqualTo(first);
        assertThat(engine.evaluate(second, properties)).isEqualTo(engine.evaluate(first, properties));
        assertThat(engine.evaluate(first, properties)).filteredOn(r -> r.outcome() == com.example.ClaimShieldX.rules.model.RuleOutcome.FLAG).hasSizeGreaterThanOrEqualTo(4);
    }

    @Test
    void aStoredRunIsExactlyWhatTheRulesComputeFromTheInputsItUsed() throws Exception {
        TestActors actor = investigator();
        UUID id = busyClaim(actor);
        String run = runOk(actor, id);

        Claim claim = claims.findByIdAndOrganizationId(id, actor.organizationId()).orElseThrow();
        List<RuleResult> expected = engine.evaluate(loader.load(actor.organizationId(), claim), properties);

        assertThat(JsonPath.<List<Object>>read(run, "$.results")).hasSameSizeAs(expected);
        for (RuleResult e : expected) {
            JsonNode stored = JSON.valueToTree(result(run, e.ruleId()));
            assertThat(stored.get("outcome").asText()).as(e.ruleId()).isEqualTo(e.outcome().name());
            assertThat(stored.has("severity") ? stored.get("severity").asText() : null).as(e.ruleId()).isEqualTo(e.severity() == null ? null : e.severity().name());
            assertThat(stored.get("reliability").asText()).isEqualTo(e.reliability().name());
            assertThat(stored.get("explanation").asText()).as(e.ruleId()).isEqualTo(e.explanation());
            assertThat(stored.get("values")).as(e.ruleId()).isEqualTo(viaText(e.values()));
            assertThat(stored.get("evidence")).as(e.ruleId()).isEqualTo(viaText(e.evidence()));
            assertThat(stored.get("considerations")).as(e.ruleId()).isEqualTo(viaText(e.considerations()));
        }
    }

    @Test
    void anotherRunWithNothingChangedReturnsTheSameFingerprintAndResultsEvenAfterAnUnrelatedClaimIsAdded() throws Exception {
        TestActors actor = investigator();
        UUID id = busyClaim(actor);
        String first = runOk(actor, id);

        ruleClaim(actor, "CLM-UNRELATED");
        String second = runOk(actor, id);

        assertThat(JsonPath.<String>read(second, "$.id")).isEqualTo(JsonPath.<String>read(first, "$.id"));
        assertThat(runCount(id)).isEqualTo(1);
    }

    @Test
    void theStoredConfigurationIsTheOneInForceAndItsHashMatches() throws Exception {
        TestActors actor = investigator();
        UUID id = cleanClaim(actor, "CLM-CONFIG");
        String run = runOk(actor, id);

        assertThat(JsonPath.<java.util.Map<String, Object>>read(run, "$.configuration")).isEqualTo(properties.snapshot());
        assertThat(JsonPath.<String>read(run, "$.configurationHash")).matches("[0-9a-f]{64}");
        assertThat(JsonPath.<String>read(run, "$.inputFingerprint")).matches("[0-9a-f]{64}");
    }
}
