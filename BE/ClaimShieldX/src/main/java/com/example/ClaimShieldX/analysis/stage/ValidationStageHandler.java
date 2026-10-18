package com.example.ClaimShieldX.analysis.stage;

import com.example.ClaimShieldX.analysis.findings.FindingDraft;
import com.example.ClaimShieldX.analysis.findings.FindingFactory;
import com.example.ClaimShieldX.analysis.findings.FindingStore;
import com.example.ClaimShieldX.analysis.reasoning.AnalysisStore;
import com.example.ClaimShieldX.analysis.reasoning.HypothesisValidator;
import com.example.ClaimShieldX.analysis.reasoning.HypothesisValidator.Assessment;
import com.example.ClaimShieldX.analysis.reasoning.HypothesisValidator.Hypothesis;
import com.example.ClaimShieldX.analysis.reasoning.HypothesisValidator.Legitimate;
import com.example.ClaimShieldX.analysis.reasoning.HypothesisValidator.Review;
import com.example.ClaimShieldX.analysis.reasoning.Outputs;
import com.example.ClaimShieldX.analysis.reasoning.Prompts;
import com.example.ClaimShieldX.analysis.reasoning.ReasoningCaller;
import com.example.ClaimShieldX.analysis.reasoning.ReasoningFailure;
import com.example.ClaimShieldX.analysis.reasoning.Schemas;
import com.example.ClaimShieldX.analysis.snapshot.SnapshotService;
import com.example.ClaimShieldX.analysis.snapshot.SnapshotService.Item;
import com.example.ClaimShieldX.investigation.model.InvestigationErrorCode;
import com.example.ClaimShieldX.investigation.model.Stage;
import com.example.ClaimShieldX.investigation.work.StageContext;
import com.example.ClaimShieldX.investigation.work.StageHandler;
import com.example.ClaimShieldX.investigation.work.StageOutcome;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Builds the findings. The rules' own results always become findings. Each hypothesis goes to the evidence reviewer (H3) and becomes a
 * published finding only if the reviewer supports it at least in part; otherwise it is kept as rejected with the reason. If the reviewer
 * cannot answer, no unreviewed hypothesis is published. Findings are replaced, not added, on a re-run.
 */
@Component
public class ValidationStageHandler implements StageHandler {
    private final SnapshotService snapshots;
    private final AnalysisStore analyses;
    private final FindingStore findings;
    private final ReasoningCaller caller;

    public ValidationStageHandler(SnapshotService snapshots, AnalysisStore analyses, FindingStore findings, ReasoningCaller caller) {
        this.snapshots = snapshots;
        this.analyses = analyses;
        this.findings = findings;
        this.caller = caller;
    }

    @Override
    public Stage stage() {
        return Stage.VALIDATING_FINDINGS;
    }

    @Override
    public boolean degradesWhenRetriesRunOut() {
        return true;
    }

    @Override
    @SuppressWarnings("unchecked")
    public StageOutcome execute(StageContext context) {
        var org = context.organizationId();
        var snapshot = snapshots.find(org, context.investigationId()).orElse(null);
        if (snapshot == null) {
            return new StageOutcome.Permanent(InvestigationErrorCode.INTERNAL_ERROR, Map.of("reason", "NO_SNAPSHOT"));
        }
        List<Item> items = snapshots.items(org, snapshot.id());
        Map<String, Item> byRef = items.stream().collect(Collectors.toMap(Item::ref, Function.identity()));
        List<FindingDraft> drafts = new ArrayList<>(FindingFactory.deterministic(items));
        Map<String, Object> summary = new LinkedHashMap<>();
        boolean degraded = false;

        boolean concerns = HypothesesStageHandler.hasConcerns(items);
        var h1 = analyses.find(org, context.investigationId(), "H1").orElse(null);
        if (concerns && h1 == null) {
            // The hypotheses stage ran out of retries before the suspicion analyst answered.
            analyses.saveUnavailable(org, context.claimId(), context.investigationId(), "H1", null, 0, "MODEL_UNAVAILABLE");
            h1 = analyses.find(org, context.investigationId(), "H1").orElseThrow();
        }
        List<Hypothesis> hypotheses = h1 != null && h1.ok() ? HypothesesStageHandler.hypothesesOf(h1) : List.of();
        degraded |= concerns && (h1 == null || !h1.ok());

        if (!hypotheses.isEmpty()) {
            var h2 = analyses.find(org, context.investigationId(), "H2").orElse(null);
            if (h2 == null) {
                analyses.saveUnavailable(org, context.claimId(), context.investigationId(), "H2", null, 0, "MODEL_UNAVAILABLE");
                h2 = analyses.find(org, context.investigationId(), "H2").orElseThrow();
            }
            Map<String, Assessment> assessments = h2.ok() ? assessmentsOf(h2.result()) : Map.of();
            degraded |= !h2.ok();

            var h3 = analyses.find(org, context.investigationId(), "H3").orElse(null);
            if (h3 == null) {
                if (context.shouldStop().getAsBoolean()) {
                    return new StageOutcome.Interrupted();
                }
                try {
                    var answer = caller.call("H3", Prompts.H3_SYSTEM, Prompts.user("Review each hypothesis against the evidence and the counter-evidence.", items,
                            hypotheses.stream().map(h -> {
                                Map<String, Object> m = new LinkedHashMap<>(h.toMap());
                                Assessment a = assessments.get(h.id());
                                m.put("legitimateExplanations", a == null ? List.of() : a.legitimate().stream().map(Legitimate::explanation).toList());
                                m.put("counterEvidenceRefs", a == null ? List.of() : a.counterEvidenceRefs());
                                return m;
                            }).toList()), Schemas.h3(), Outputs.parserFor(Outputs.H3.class));
                    var result = HypothesisValidator.validateH3(answer.value(), hypotheses, byRef.keySet());
                    analyses.saveOk(org, context.claimId(), context.investigationId(), "H3", answer.modelId(), answer.calls(), answer.inputTokens(), answer.outputTokens(), result.toMap());
                } catch (ReasoningFailure f) {
                    summary.put("h3", "FAILED_" + f.code());
                    if (f.code().retryable()) {
                        return new StageOutcome.Transient(f.code().investigationCode(), summary);
                    }
                    analyses.saveUnavailable(org, context.claimId(), context.investigationId(), "H3", f.modelId(), f.calls(), f.code().name());
                }
                h3 = analyses.find(org, context.investigationId(), "H3").orElseThrow();
            }
            Map<String, Review> reviews = h3.ok() ? reviewsOf(h3.result()) : Map.of();
            degraded |= !h3.ok();
            for (Hypothesis h : hypotheses) {
                drafts.add(FindingFactory.hypothesis(h, assessments.get(h.id()), reviews.get(h.id()), h3.ok()));
            }
        }

        findings.replace(org, context.claimId(), context.investigationId(), snapshot.id(), drafts);
        summary.put("findings", drafts.size());
        summary.put("published", drafts.stream().filter(FindingDraft::validated).count());
        summary.put("rejectedHypotheses", drafts.stream().filter(d -> !d.validated()).count());
        return degraded ? StageOutcome.degraded(summary) : StageOutcome.ok(summary);
    }

    @SuppressWarnings("unchecked")
    static Map<String, Assessment> assessmentsOf(Map<String, Object> result) {
        Map<String, Assessment> map = new LinkedHashMap<>();
        if (result.get("assessments") instanceof List<?> list) {
            for (Object o : list) {
                Map<String, Object> a = (Map<String, Object>) o;
                List<Legitimate> legit = new ArrayList<>();
                for (Object l : (List<Object>) a.getOrDefault("legitimateExplanations", List.of())) {
                    Map<String, Object> lm = (Map<String, Object>) l;
                    legit.add(new Legitimate((String) lm.get("explanation"), (List<String>) lm.get("supportingRefs"), (List<String>) lm.get("checksNeeded")));
                }
                map.put((String) a.get("hypothesisId"), new Assessment((String) a.get("hypothesisId"), legit, (List<String>) a.getOrDefault("counterEvidenceRefs", List.of())));
            }
        }
        return map;
    }

    @SuppressWarnings("unchecked")
    static Map<String, Review> reviewsOf(Map<String, Object> result) {
        Map<String, Review> map = new LinkedHashMap<>();
        if (result.get("reviews") instanceof List<?> list) {
            for (Object o : list) {
                Map<String, Object> r = (Map<String, Object>) o;
                map.put((String) r.get("hypothesisId"), new Review((String) r.get("hypothesisId"), (String) r.get("support"), (List<String>) r.get("reasons"), (List<String>) r.get("checkedRefs")));
            }
        }
        return map;
    }
}
