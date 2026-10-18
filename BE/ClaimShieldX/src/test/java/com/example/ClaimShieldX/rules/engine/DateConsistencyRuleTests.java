package com.example.ClaimShieldX.rules.engine;

import com.example.ClaimShieldX.extraction.schema.IssueCode;
import com.example.ClaimShieldX.rules.config.RuleProperties;
import com.example.ClaimShieldX.rules.model.InputReliability;
import com.example.ClaimShieldX.rules.model.RuleOutcome;
import com.example.ClaimShieldX.rules.model.RuleResult;
import com.example.ClaimShieldX.rules.model.RuleSeverity;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DateConsistencyRuleTests {
    private final DateConsistencyRule rule = new DateConsistencyRule();

    private RuleResult run(Inputs.Builder inputs) {
        return rule.evaluate(inputs.build(), RuleProperties.defaults());
    }

    private static Inputs.Builder both() {
        return Inputs.inputs().with(Inputs.invoice(), Inputs.estimate());
    }

    private static List<String> texts(Object list) {
        return ((List<?>) list).stream().map(Object::toString).toList();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> comparisons(RuleResult result) {
        return (List<Map<String, Object>>) result.values().get("comparisons");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> inconsistent(RuleResult result, String check) {
        return comparisons(result).stream().filter(c -> check.equals(c.get("check")) && "INCONSISTENT".equals(c.get("status"))).findFirst().orElseThrow();
    }

    @Test
    void consistentDatesAcrossTheClaimAndBothDocumentsPass() {
        RuleResult result = run(both());

        assertThat(result.outcome()).isEqualTo(RuleOutcome.PASS);
        assertThat(result.values()).containsEntry("comparisonsRun", 8).containsEntry("claimRecordComparisons", 3).containsEntry("inconsistent", 0);
    }

    @Test
    void withNoDocumentsReadOnlyTheClaimRecordIsCheckedAndTheResultIsIncompleteNotAPass() {
        RuleResult result = run(Inputs.inputs());

        assertThat(result.outcome()).isEqualTo(RuleOutcome.INCOMPLETE);
        assertThat(result.values()).containsEntry("comparisonsRun", 3);
        assertThat((List<?>) result.values().get("notChecked")).hasSize(2);
    }

    @Test
    void aDocumentThatWasNeverReadIsReportedAsNotChecked() {
        RuleResult result = run(Inputs.inputs().with(Inputs.invoice(), Inputs.estimate().notExtracted()));

        assertThat(result.outcome()).isEqualTo(RuleOutcome.INCOMPLETE);
        assertThat(texts(result.values().get("notChecked"))).containsExactly("Garage estimate has not been read successfully");
    }

    @Test
    void aReportDateBeforeTheIncidentIsMaterial() {
        RuleResult result = run(both().claim(Inputs.claimB().reported("2026-09-07T05:00:00Z").build()));

        assertThat(result.outcome()).isEqualTo(RuleOutcome.FLAG);
        assertThat(result.severity()).isEqualTo(RuleSeverity.REVIEW);
        assertThat(inconsistent(result, "REPORTED_NOT_BEFORE_INCIDENT")).containsEntry("differenceDays", -3L).containsEntry("material", true);
        assertThat(result.evidence()).anyMatch(e -> "incident.reportedAt".equals(e.claimField()));
    }

    @Test
    void reportingOnTheSameDayIsFine() {
        assertThat(run(both().claim(Inputs.claimB().reported("2026-09-10T07:00:00Z").build())).outcome()).isEqualTo(RuleOutcome.PASS);
    }

    @Test
    void anIncidentBeforeThePolicyStartOrAfterItsEndIsFlaggedAndTheBoundaryDaysAreInclusive() {
        assertThat(run(both().claim(Inputs.claimB().policy("2026-09-11T00:00:00Z", "2026-12-31T00:00:00Z").build())).outcome()).isEqualTo(RuleOutcome.FLAG);
        assertThat(run(both().claim(Inputs.claimB().policy("2026-01-01T00:00:00Z", "2026-09-09T00:00:00Z").build())).outcome()).isEqualTo(RuleOutcome.FLAG);
        assertThat(run(both().claim(Inputs.claimB().policy("2026-09-10T00:00:00Z", "2026-12-31T00:00:00Z").build())).outcome()).isEqualTo(RuleOutcome.PASS);
        assertThat(run(both().claim(Inputs.claimB().policy("2026-01-01T00:00:00Z", "2026-09-10T00:00:00Z").build())).outcome()).isEqualTo(RuleOutcome.PASS);
    }

    @Test
    void missingPolicyDatesAndReportDateJustMeanFewerComparisons() {
        RuleResult result = run(both().claim(Inputs.claimB().policy(null, null).reported(null).build()));

        assertThat(result.outcome()).isEqualTo(RuleOutcome.PASS);
        assertThat(result.values()).containsEntry("claimRecordComparisons", 0).containsEntry("comparisonsRun", 5);
    }

    @Test
    void anIncidentDateOnTheInvoiceThatDiffersFromTheClaimIsMaterial() {
        RuleResult result = run(Inputs.inputs().with(Inputs.invoice().set("incidentDate", "2026-09-08"), Inputs.estimate()));

        assertThat(result.outcome()).isEqualTo(RuleOutcome.FLAG);
        assertThat(result.severity()).isEqualTo(RuleSeverity.REVIEW);
        assertThat(inconsistent(result, "INCIDENT_DATE_AGREES")).containsEntry("subject", "Final invoice").containsEntry("differenceDays", -2L).containsEntry("qualifiers", List.of());
    }

    @Test
    void aOneDayGapNearMidnightMayBeATimeZoneEffectSoItIsNotMaterial() {
        // 00:30 on 11 September in India, which is still 10 September in UTC.
        RuleResult result = run(Inputs.inputs().claim(Inputs.claimB().incident("2026-09-10T19:00:00Z").reported("2026-09-11T19:00:00Z").build())
                .with(Inputs.invoice().set("incidentDate", "2026-09-10"), Inputs.estimate().set("incidentDate", "2026-09-11")));

        assertThat(result.outcome()).isEqualTo(RuleOutcome.FLAG);
        assertThat(result.severity()).isEqualTo(RuleSeverity.INFO);
        assertThat(inconsistent(result, "INCIDENT_DATE_AGREES")).containsEntry("qualifiers", List.of("NEAR_MIDNIGHT_BOUNDARY")).containsEntry("material", false);
    }

    @Test
    void aOneDayGapAtMidDayIsMaterial() {
        RuleResult result = run(Inputs.inputs().with(Inputs.invoice().set("incidentDate", "2026-09-09"), Inputs.estimate()));

        assertThat(result.severity()).isEqualTo(RuleSeverity.REVIEW);
        assertThat(inconsistent(result, "INCIDENT_DATE_AGREES")).containsEntry("differenceDays", -1L).containsEntry("qualifiers", List.of());
    }

    @Test
    void aDateThatReadsCorrectlyWithDayAndMonthSwappedIsNotMaterial() {
        // Printed 03/09/2026, read day-first as 3 September; the claim's incident is 9 March.
        RuleResult result = run(Inputs.inputs().claim(Inputs.claimB().incident("2026-03-09T05:00:00Z").reported("2026-03-10T05:00:00Z").build())
                .with(Inputs.invoice().set("incidentDate", "2026-09-03").printedAs("incidentDate", "03/09/2026").assumed("incidentDate").set("documentDate", "2026-03-15"),
                        Inputs.estimate().set("incidentDate", "2026-03-09").set("documentDate", "2026-03-12")));

        assertThat(result.outcome()).isEqualTo(RuleOutcome.FLAG);
        assertThat(result.severity()).isEqualTo(RuleSeverity.INFO);
        assertThat(texts(inconsistent(result, "INCIDENT_DATE_AGREES").get("qualifiers"))).containsExactlyInAnyOrder("FORMAT_ASSUMED", "DAY_MONTH_ORDER_AMBIGUOUS");
        assertThat(result.explanation()).contains("None of these is treated as material yet because");
    }

    @Test
    void aSwappedReadingThatDoesNotReconcileLeavesTheDiscrepancyMaterial() {
        RuleResult result = run(Inputs.inputs().with(Inputs.invoice().set("incidentDate", "2026-03-04").printedAs("incidentDate", "04/03/2026"), Inputs.estimate()));

        assertThat(result.severity()).isEqualTo(RuleSeverity.REVIEW);
        assertThat(inconsistent(result, "INCIDENT_DATE_AGREES")).containsEntry("qualifiers", List.of());
    }

    @Test
    void anAssumedDateFormatAloneMakesTheDiscrepancyNotYetMaterial() {
        RuleResult result = run(Inputs.inputs().with(Inputs.invoice().set("incidentDate", "2026-09-01").assumed("incidentDate"), Inputs.estimate()));

        assertThat(result.severity()).isEqualTo(RuleSeverity.INFO);
        assertThat(inconsistent(result, "INCIDENT_DATE_AGREES")).containsEntry("qualifiers", List.of("FORMAT_ASSUMED"));
    }

    @Test
    void aDocumentDatedBeforeTheIncidentIsMaterial() {
        RuleResult result = run(Inputs.inputs().with(Inputs.invoice().set("documentDate", "2026-09-05"), Inputs.estimate().set("documentDate", "2026-09-04")));

        assertThat(result.outcome()).isEqualTo(RuleOutcome.FLAG);
        assertThat(inconsistent(result, "DOCUMENT_NOT_BEFORE_INCIDENT")).containsEntry("subject", "Final invoice");
        assertThat(inconsistent(result, "DOCUMENT_NOT_BEFORE_INCIDENT").get("subject")).isIn("Final invoice", "Garage estimate");
        assertThat(result.values()).containsEntry("inconsistent", 2);
    }

    @Test
    void anEstimateDatedAfterTheFinalInvoiceIsInconsistent() {
        RuleResult result = run(Inputs.inputs().with(Inputs.invoice().set("documentDate", "2026-09-12"), Inputs.estimate().set("documentDate", "2026-09-14")));

        assertThat(result.outcome()).isEqualTo(RuleOutcome.FLAG);
        assertThat(inconsistent(result, "ESTIMATE_NOT_AFTER_INVOICE")).containsEntry("differenceDays", -2L);
    }

    @Test
    void repairDatesMustNotPrecedeTheIncidentOrEachOther() {
        assertThat(inconsistent(run(Inputs.inputs().with(Inputs.invoice().set("repairStartDate", "2026-09-05"), Inputs.estimate())), "REPAIR_START_NOT_BEFORE_INCIDENT")).isNotNull();
        assertThat(inconsistent(run(Inputs.inputs().with(Inputs.invoice().set("repairStartDate", "2026-09-12").set("repairCompletionDate", "2026-09-11"), Inputs.estimate())), "REPAIR_END_NOT_BEFORE_START")).isNotNull();
        assertThat(run(Inputs.inputs().with(Inputs.invoice().set("repairStartDate", "2026-09-12").set("repairCompletionDate", "2026-09-14"), Inputs.estimate())).outcome()).isEqualTo(RuleOutcome.PASS);
    }

    @Test
    void theToleranceInDaysComesFromConfiguration() {
        RuleProperties tolerant = new RuleProperties(RuleProperties.defaults().timeZone(), new BigDecimal("1.00"), new BigDecimal("0.01"), new BigDecimal("10.00"), new BigDecimal("1000.00"), 2, 180,
                new BigDecimal("0.80"), 2, 365, 10);
        RuleInputs twoOff = Inputs.inputs().with(Inputs.invoice().set("incidentDate", "2026-09-08"), Inputs.estimate()).build();
        RuleInputs threeOff = Inputs.inputs().with(Inputs.invoice().set("incidentDate", "2026-09-07"), Inputs.estimate()).build();

        assertThat(rule.evaluate(twoOff, tolerant).outcome()).isEqualTo(RuleOutcome.PASS);
        assertThat(rule.evaluate(threeOff, tolerant).outcome()).isEqualTo(RuleOutcome.FLAG);
        assertThat(rule.evaluate(twoOff, RuleProperties.defaults()).outcome()).isEqualTo(RuleOutcome.FLAG);
    }

    @Test
    void aDoubtfullyReadDocumentMakesTheDiscrepancyNotMaterial() {
        RuleResult result = run(Inputs.inputs().with(Inputs.invoice().set("incidentDate", "2026-09-08").issue(IssueCode.NO_SOURCE_QUOTE), Inputs.estimate()));

        assertThat(result.severity()).isEqualTo(RuleSeverity.INFO);
        assertThat(result.reliability()).isEqualTo(InputReliability.DOUBTFUL);
        assertThat(result.reliabilityReasons()).containsExactly("NO_SOURCE_QUOTE");
    }

    @Test
    void theExtractionsOwnDateIssueDoesNotMakeItsReadingDoubtful() {
        RuleResult result = run(Inputs.inputs().with(Inputs.invoice().set("incidentDate", "2026-09-08").issue(IssueCode.INCIDENT_DATE_MISMATCH), Inputs.estimate()));

        assertThat(result.severity()).isEqualTo(RuleSeverity.REVIEW);
        assertThat(result.reliability()).isEqualTo(InputReliability.RELIABLE);
    }

    @Test
    void aFlagStillReportsWhatCouldNotBeChecked() {
        RuleResult result = run(Inputs.inputs().claim(Inputs.claimB().reported("2026-09-07T05:00:00Z").build()).with(Inputs.invoice()));

        assertThat(result.outcome()).isEqualTo(RuleOutcome.FLAG);
        assertThat(texts(result.values().get("notChecked"))).containsExactly("Garage estimate has not been received");
    }

    @Test
    void swappedReadsOnlyNumericDatesThatAreValidTheOtherWayRound() {
        assertThat(DateConsistencyRule.swapped("03/09/2026")).isEqualTo(LocalDate.of(2026, 3, 9));
        assertThat(DateConsistencyRule.swapped("3.9.26")).isEqualTo(LocalDate.of(2026, 3, 9));
        assertThat(DateConsistencyRule.swapped("13/09/2026")).isNull();
        assertThat(DateConsistencyRule.swapped("05/05/2026")).isNull();
        assertThat(DateConsistencyRule.swapped("10 Sep 2026")).isNull();
        assertThat(DateConsistencyRule.swapped("2026-09-10")).isNull();
        assertThat(DateConsistencyRule.swapped("12/31/2026")).isEqualTo(LocalDate.of(2026, 12, 31));
        assertThat(DateConsistencyRule.swapped("31/12/2026")).isNull();
        assertThat(DateConsistencyRule.swapped("02/30/2026")).isNull();
        assertThat(DateConsistencyRule.swapped(null)).isNull();
    }

    @Test
    void theSameInputsGiveTheSameResult() {
        RuleInputs inputs = Inputs.inputs().with(Inputs.invoice().set("incidentDate", "2026-09-08"), Inputs.estimate()).build();

        assertThat(rule.evaluate(inputs, RuleProperties.defaults())).isEqualTo(rule.evaluate(inputs, RuleProperties.defaults()));
    }
}
