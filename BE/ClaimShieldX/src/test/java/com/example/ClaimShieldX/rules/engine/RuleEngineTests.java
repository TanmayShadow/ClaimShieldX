package com.example.ClaimShieldX.rules.engine;

import com.example.ClaimShieldX.rules.config.RuleProperties;
import com.example.ClaimShieldX.rules.model.InputReliability;
import com.example.ClaimShieldX.rules.model.RuleDefinition;
import com.example.ClaimShieldX.rules.model.RuleOutcome;
import com.example.ClaimShieldX.rules.model.RuleResult;
import com.example.ClaimShieldX.rules.model.RuleSeverity;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RuleEngineTests {
    private final RuleEngine engine = new RuleEngine();

    @Test
    void everyRuleHasAUniqueIdAndAVersionAndTheSetHasAVersion() {
        List<RuleDefinition> definitions = engine.definitions();

        assertThat(definitions).extracting(RuleDefinition::id).containsExactly("REQUIRED_EVIDENCE", "INVOICE_ARITHMETIC", "ESTIMATE_INVOICE_VARIANCE", "DATE_CONSISTENCY",
                "DOCUMENT_FILE_DUPLICATE", "INVOICE_IDENTITY_DUPLICATE", "REPEATED_LINE_ITEMS", "HISTORICAL_RELATIONSHIP");
        assertThat(new HashSet<>(definitions.stream().map(RuleDefinition::id).toList())).hasSameSizeAs(definitions);
        assertThat(definitions).allSatisfy(d -> {
            assertThat(d.version()).isNotBlank();
            assertThat(d.label()).isNotBlank();
            assertThat(d.description()).isNotBlank();
            assertThat(d.considerations()).isNotEmpty();
        });
        assertThat(RuleEngine.RULE_SET_VERSION).isNotBlank();
    }

    @Test
    void theIdsAreTheOnesTheChecklistAlreadyUses() {
        Set<String> engineIds = engine.definitions().stream().map(RuleDefinition::id).collect(Collectors.toSet());
        for (com.example.ClaimShieldX.readiness.model.InvestigationCheck check : com.example.ClaimShieldX.readiness.model.InvestigationCheck.values()) {
            if (check != com.example.ClaimShieldX.readiness.model.InvestigationCheck.REPAIR_SCOPE_REVIEW) {
                assertThat(engineIds).contains(check.name());
            }
        }
    }

    @Test
    void everyRuleProducesOneResultInTheFixedOrderWithItsOwnIdAndVersion() {
        RuleInputs inputs = Inputs.inputs().with(Inputs.invoice(), Inputs.estimate()).build();

        List<RuleResult> results = engine.evaluate(inputs, RuleProperties.defaults());

        assertThat(results).extracting(RuleResult::ruleId).isEqualTo(engine.definitions().stream().map(RuleDefinition::id).toList());
        assertThat(results).extracting(RuleResult::ruleVersion).containsOnly("1");
    }

    @Test
    void aCleanClaimGivesNoFlagAndAllTheRulesThatCouldNotRunSayWhy() {
        List<RuleResult> results = engine.evaluate(Inputs.inputs().with(Inputs.invoice(), Inputs.estimate()).build(), RuleProperties.defaults());

        assertThat(results).filteredOn(r -> r.outcome() == RuleOutcome.FLAG).isEmpty();
        Map<String, RuleOutcome> byId = results.stream().collect(Collectors.toMap(RuleResult::ruleId, RuleResult::outcome));
        assertThat(byId).containsEntry("INVOICE_ARITHMETIC", RuleOutcome.PASS).containsEntry("ESTIMATE_INVOICE_VARIANCE", RuleOutcome.PASS)
                .containsEntry("DATE_CONSISTENCY", RuleOutcome.PASS).containsEntry("HISTORICAL_RELATIONSHIP", RuleOutcome.INCOMPLETE);
    }

    @Test
    void theSameInputsAndConfigurationAlwaysGiveEqualResults() {
        RuleInputs inputs = Inputs.inputs().with(Inputs.invoice().set("grandTotal", "17930.00"), Inputs.estimate().set("grandTotal", "9000.00"))
                .history(Inputs.history("MATCHES_FOUND", Inputs.historyMatch("SAME_VEHICLE", "EXACT", "EARLIER", 45))).build();

        List<RuleResult> first = engine.evaluate(inputs, RuleProperties.defaults());
        for (int i = 0; i < 20; i++) {
            assertThat(engine.evaluate(inputs, RuleProperties.defaults())).isEqualTo(first);
        }
    }

    @Test
    void theOrderInWhichDocumentsAreSuppliedDoesNotChangeTheResults() {
        Inputs.Doc invoice = Inputs.invoice();
        Inputs.Doc estimate = Inputs.estimate();
        Inputs.Doc photo = Inputs.plain(com.example.ClaimShieldX.documents.model.DocumentType.DAMAGE_PHOTOGRAPH);

        List<RuleResult> a = engine.evaluate(Inputs.inputs().with(invoice, estimate, photo).build(), RuleProperties.defaults());
        List<RuleResult> b = engine.evaluate(Inputs.inputs().with(photo, estimate, invoice).build(), RuleProperties.defaults());

        assertThat(a).isEqualTo(b);
    }

    @Test
    void aRuleThatThrowsBecomesAnErrorResultAndTheOthersStillRun() {
        Rule broken = new Rule() {
            @Override
            public RuleDefinition definition() {
                return new RuleDefinition("BROKEN", "1", "Broken", "Always fails", List.of(), List.of("none"));
            }

            @Override
            public RuleResult evaluate(RuleInputs inputs, RuleProperties config) {
                throw new IllegalStateException("secret value 12345 must not leak");
            }
        };
        RuleEngine engine = new RuleEngine(List.of(new RequiredEvidenceRule(), broken, new HistoricalRelationshipRule()));

        List<RuleResult> results = engine.evaluate(Inputs.inputs().build(), RuleProperties.defaults());

        assertThat(results).extracting(RuleResult::outcome).containsExactly(RuleOutcome.PASS, RuleOutcome.ERROR, RuleOutcome.INCOMPLETE);
        RuleResult error = results.get(1);
        assertThat(error.ruleId()).isEqualTo("BROKEN");
        assertThat(error.explanation()).doesNotContain("12345").contains("internal error");
        assertThat(error.values()).containsEntry("errorType", "IllegalStateException").doesNotContainValue("secret value 12345 must not leak");
        assertThat(error.severity()).isNull();
    }

    @Test
    void aFailingRuleIsLoggedByIdAndTypeOnlyNeverWithItsMessageOrTheClaimsData() {
        ch.qos.logback.classic.Logger logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(RuleEngine.class);
        ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> appender = new ch.qos.logback.core.read.ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            Rule broken = new Rule() {
                @Override
                public RuleDefinition definition() {
                    return new RuleDefinition("BROKEN", "1", "Broken", "Always fails", List.of(), List.of("none"));
                }

                @Override
                public RuleResult evaluate(RuleInputs inputs, RuleProperties config) {
                    throw new IllegalStateException("grand total 17930.00 for VEH-1");
                }
            };
            new RuleEngine(List.of(broken)).evaluate(Inputs.inputs().with(Inputs.invoice()).build(), RuleProperties.defaults());

            assertThat(appender.list).singleElement().satisfies(event -> {
                assertThat(event.getLevel()).isEqualTo(ch.qos.logback.classic.Level.ERROR);
                assertThat(event.getFormattedMessage()).contains("BROKEN").contains("IllegalStateException").doesNotContain("17930").doesNotContain("VEH-1");
                assertThat(event.getThrowableProxy()).isNull();
            });
        } finally {
            logger.detachAppender(appender);
        }
    }

    @Test
    void aSeverityIsGivenExactlyWhenTheOutcomeIsAFlag() {
        assertThatThrownBy(() -> new RuleResult("X", "1", RuleOutcome.PASS, RuleSeverity.INFO, InputReliability.RELIABLE, List.of(), "e", Map.of(), List.of(), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RuleResult("X", "1", RuleOutcome.FLAG, null, InputReliability.RELIABLE, List.of(), "e", Map.of(), List.of(), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void noRuleEverUsesFraudLanguage() {
        RuleInputs inputs = Inputs.inputs().with(Inputs.invoice().set("grandTotal", "17930.00").set("documentDate", "2026-09-01"), Inputs.estimate().set("grandTotal", "5000.00"))
                .claim(Inputs.claimB().reported("2026-09-01T00:00:00Z").build())
                .history(Inputs.history("MATCHES_FOUND", Inputs.historyMatch("SAME_VEHICLE", "EXACT", "EARLIER", 45))).build();

        for (RuleResult result : engine.evaluate(inputs, RuleProperties.defaults())) {
            String text = (result.explanation() + " " + String.join(" ", result.considerations())).toLowerCase();
            assertThat(text).doesNotContain("fraud").doesNotContain("fraudulent").doesNotContain("suspicious").doesNotContain("guilty").doesNotContain("reject");
        }
    }

    @Test
    void propertiesRejectNonsense() {
        RuleProperties d = RuleProperties.defaults();
        assertThatThrownBy(() -> new RuleProperties(d.timeZone(), new BigDecimal("-1"), d.lineBaseAllowance(), d.variancePercentThreshold(), d.varianceMinimumIncrease(), 0, 180, d.nearDuplicateSimilarity(), 2, 365, 10))
                .hasMessageContaining("round-off-allowance");
        assertThatThrownBy(() -> new RuleProperties(d.timeZone(), d.roundOffAllowance(), d.lineBaseAllowance(), d.variancePercentThreshold(), d.varianceMinimumIncrease(), -1, 180, d.nearDuplicateSimilarity(), 2, 365, 10))
                .hasMessageContaining("date-tolerance-days");
        assertThatThrownBy(() -> new RuleProperties(d.timeZone(), d.roundOffAllowance(), d.lineBaseAllowance(), d.variancePercentThreshold(), d.varianceMinimumIncrease(), 0, 800, d.nearDuplicateSimilarity(), 2, 365, 10))
                .hasMessageContaining("near-midnight-minutes");
        assertThatThrownBy(() -> new RuleProperties(d.timeZone(), d.roundOffAllowance(), d.lineBaseAllowance(), d.variancePercentThreshold(), d.varianceMinimumIncrease(), 0, 180, new BigDecimal("1.5"), 2, 365, 10))
                .hasMessageContaining("near-duplicate-similarity");
        assertThatThrownBy(() -> new RuleProperties(d.timeZone(), d.roundOffAllowance(), d.lineBaseAllowance(), d.variancePercentThreshold(), d.varianceMinimumIncrease(), 0, 180, BigDecimal.ZERO, 2, 365, 10))
                .hasMessageContaining("near-duplicate-similarity");
        assertThatThrownBy(() -> new RuleProperties(d.timeZone(), d.roundOffAllowance(), d.lineBaseAllowance(), d.variancePercentThreshold(), d.varianceMinimumIncrease(), 0, 180, d.nearDuplicateSimilarity(), 0, 365, 10))
                .hasMessageContaining("min-description-words");
        assertThatThrownBy(() -> new RuleProperties(d.timeZone(), d.roundOffAllowance(), d.lineBaseAllowance(), d.variancePercentThreshold(), d.varianceMinimumIncrease(), 0, 180, d.nearDuplicateSimilarity(), 2, 365, 0))
                .hasMessageContaining("max-listed-matches");
    }
}
