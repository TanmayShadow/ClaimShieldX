package com.example.ClaimShieldX.analysis.stage;

import com.example.ClaimShieldX.analysis.findings.FindingStore;
import com.example.ClaimShieldX.analysis.findings.ScoreCalculator;
import com.example.ClaimShieldX.analysis.reasoning.AnalysisStore;
import com.example.ClaimShieldX.analysis.report.ReportService;
import com.example.ClaimShieldX.analysis.snapshot.SnapshotService;
import com.example.ClaimShieldX.analysis.snapshot.SnapshotService.Item;
import com.example.ClaimShieldX.investigation.model.InvestigationErrorCode;
import com.example.ClaimShieldX.investigation.model.Stage;
import com.example.ClaimShieldX.investigation.model.StageStatus;
import com.example.ClaimShieldX.investigation.work.InvestigationStore;
import com.example.ClaimShieldX.investigation.work.StageContext;
import com.example.ClaimShieldX.investigation.work.StageHandler;
import com.example.ClaimShieldX.investigation.work.StageOutcome;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Writes the report: the review-priority score with its components, the completeness (PARTIAL, with the sections that are unavailable, when
 * a stage could not finish) and a neutral summary. The report points at its snapshot and never changes; the findings are listed beside it.
 */
@Component
public class ReportStageHandler implements StageHandler {
    private final SnapshotService snapshots;
    private final FindingStore findings;
    private final AnalysisStore analyses;
    private final ScoreCalculator calculator;
    private final ReportService reports;
    private final InvestigationStore investigationStore;

    public ReportStageHandler(SnapshotService snapshots, FindingStore findings, AnalysisStore analyses, ScoreCalculator calculator, ReportService reports, InvestigationStore investigationStore) {
        this.snapshots = snapshots;
        this.findings = findings;
        this.analyses = analyses;
        this.calculator = calculator;
        this.reports = reports;
        this.investigationStore = investigationStore;
    }

    @Override
    public Stage stage() {
        return Stage.GENERATING_REPORT;
    }

    @Override
    public StageOutcome execute(StageContext context) {
        var org = context.organizationId();
        var snapshot = snapshots.find(org, context.investigationId()).orElse(null);
        if (snapshot == null) {
            return new StageOutcome.Permanent(InvestigationErrorCode.INTERNAL_ERROR, Map.of("reason", "NO_SNAPSHOT"));
        }
        List<Item> items = snapshots.items(org, snapshot.id());
        var rows = findings.list(org, context.investigationId(), false);
        ScoreCalculator.Score score = calculator.calculate(items);

        List<String> unavailable = new ArrayList<>();
        boolean concerns = HypothesesStageHandler.hasConcerns(items);
        var h1 = analyses.find(org, context.investigationId(), "H1").orElse(null);
        if (concerns && (h1 == null || !h1.ok())) {
            unavailable.add("SUSPICION_ANALYSIS");
        } else if (h1 != null && h1.ok() && !HypothesesStageHandler.hypothesesOf(h1).isEmpty()) {
            var h2 = analyses.find(org, context.investigationId(), "H2").orElse(null);
            var h3 = analyses.find(org, context.investigationId(), "H3").orElse(null);
            if (h2 == null || !h2.ok()) {
                unavailable.add("COUNTER_EVIDENCE");
            }
            if (h3 == null || !h3.ok()) {
                unavailable.add("EVIDENCE_REVIEW");
            }
        }
        if (someDocumentCouldNotBeRead(context)) {
            unavailable.add("DOCUMENT_READING");
        }

        long needReview = rows.stream().filter(r -> r.validationState().equals("VALIDATED") && r.status().equals("NEEDS_REVIEW")).count();
        long noted = rows.stream().filter(r -> r.validationState().equals("VALIDATED") && r.status().equals("NOTED")).count();
        long gaps = rows.stream().filter(r -> r.type().equals("UNKNOWN")).count();
        long hypotheses = rows.stream().filter(r -> r.validationState().equals("VALIDATED") && r.type().equals("HYPOTHESIS")).count();
        String summary = "Review-priority score " + score.total() + " of 100 (" + score.band() + "). " + needReview + " item" + (needReview == 1 ? "" : "s") + " to review, " + noted + " noted, "
                + gaps + " evidence gap" + (gaps == 1 ? "" : "s") + (hypotheses > 0 ? ", " + hypotheses + " hypothes" + (hypotheses == 1 ? "is" : "es") + " to verify" : "")
                + (unavailable.isEmpty() ? "" : ". Unavailable: " + String.join(", ", unavailable).toLowerCase().replace('_', ' '))
                + ". This ranks where a person should look first; it is not a finding about the claim, and a person decides.";

        UUID reportId = reports.generate(org, context.claimId(), context.investigationId(), snapshot.id(), snapshot.ruleSetVersion(), calculator.properties().version(), score, unavailable, summary);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("reportId", reportId.toString());
        out.put("score", score.total());
        out.put("band", score.band());
        out.put("recommendedAction", score.recommendedAction());
        out.put("completeness", unavailable.isEmpty() ? "COMPLETE" : "PARTIAL");
        return StageOutcome.ok(out);
    }


    /**
     * A document that was read but needs a person's review is not missing: its values are in the evidence, labelled as needing review. Only a
     * document that could not be read at all (or is still being read) leaves a hole in the report.
     */
    private boolean someDocumentCouldNotBeRead(StageContext context) {
        if (investigationStore.stageStatuses(context.investigationId()).get(Stage.EXTRACTING) != StageStatus.DEGRADED) {
            return false;
        }
        Object documents = investigationStore.stageSummary(context.investigationId(), Stage.EXTRACTING).get("documents");
        return documents instanceof List<?> list && list.stream().anyMatch(d -> d instanceof Map<?, ?> m
                && java.util.Set.of("FAILED_FOR_GOOD", "FAILED_LATER_MAY_PASS", "BUSY").contains(String.valueOf(m.get("outcome"))));
    }
}
