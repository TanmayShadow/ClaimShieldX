package com.example.ClaimShieldX.analysis.stage;

import com.example.ClaimShieldX.analysis.reasoning.AnalysisStore;
import com.example.ClaimShieldX.analysis.reasoning.HypothesisValidator;
import com.example.ClaimShieldX.analysis.reasoning.HypothesisValidator.Hypothesis;
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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * H1 (suspicion analyst) and H2 (counter-evidence analyst). They run only when a rule found something worth a second look; on a clean
 * claim the model is not asked, so it cannot invent concerns and the free quota is not spent. Each role's validated answer is stored
 * before the stage ends, so a retry asks only the roles that have not answered. A role that cannot answer is recorded as unavailable:
 * the report then says the section is unavailable, never that nothing was found.
 */
@Component
public class HypothesesStageHandler implements StageHandler {
    private final SnapshotService snapshots;
    private final AnalysisStore analyses;
    private final ReasoningCaller caller;

    public HypothesesStageHandler(SnapshotService snapshots, AnalysisStore analyses, ReasoningCaller caller) {
        this.snapshots = snapshots;
        this.analyses = analyses;
        this.caller = caller;
    }

    @Override
    public Stage stage() {
        return Stage.BUILDING_HYPOTHESES;
    }

    @Override
    public boolean degradesWhenRetriesRunOut() {
        return true;
    }

    /** True when at least one rule result is a flag: there is something for the analysts to look at. */
    public static boolean hasConcerns(List<Item> items) {
        return items.stream().anyMatch(i -> i.type().equals("RULE_RESULT") && "FLAG".equals(i.data().get("outcome")));
    }

    @Override
    public StageOutcome execute(StageContext context) {
        var snapshot = snapshots.find(context.organizationId(), context.investigationId()).orElse(null);
        if (snapshot == null) {
            return new StageOutcome.Permanent(InvestigationErrorCode.INTERNAL_ERROR, Map.of("reason", "NO_SNAPSHOT"));
        }
        List<Item> items = snapshots.items(context.organizationId(), snapshot.id());
        Map<String, Item> byRef = items.stream().collect(Collectors.toMap(Item::ref, Function.identity()));
        Map<String, Object> summary = new LinkedHashMap<>();
        if (!hasConcerns(items)) {
            summary.put("reason", "NO_CONCERNS_TO_ANALYZE");
            return StageOutcome.skipped(summary);
        }

        var h1 = analyses.find(context.organizationId(), context.investigationId(), "H1").orElse(null);
        if (h1 == null) {
            if (context.shouldStop().getAsBoolean()) {
                return new StageOutcome.Interrupted();
            }
            try {
                var answer = caller.call("H1", Prompts.H1_SYSTEM, Prompts.user("Propose hypotheses a reviewer could check.", items, null), Schemas.h1(), Outputs.parserFor(Outputs.H1.class));
                var result = HypothesisValidator.validateH1(answer.value(), byRef);
                analyses.saveOk(context.organizationId(), context.claimId(), context.investigationId(), "H1", answer.modelId(), answer.calls(), answer.inputTokens(), answer.outputTokens(), result.toMap());
            } catch (ReasoningFailure f) {
                return failed("H1", f, context, summary);
            }
            h1 = analyses.find(context.organizationId(), context.investigationId(), "H1").orElseThrow();
        }
        if (!h1.ok()) {
            summary.put("h1", "UNAVAILABLE");
            return StageOutcome.degraded(summary);
        }
        List<Hypothesis> hypotheses = hypothesesOf(h1);
        summary.put("hypotheses", hypotheses.size());
        summary.put("h1Rejected", ((List<?>) h1.result().getOrDefault("rejected", List.of())).size());
        if (hypotheses.isEmpty()) {
            return StageOutcome.ok(summary);
        }

        var h2 = analyses.find(context.organizationId(), context.investigationId(), "H2").orElse(null);
        if (h2 == null) {
            if (context.shouldStop().getAsBoolean()) {
                return new StageOutcome.Interrupted();
            }
            try {
                var answer = caller.call("H2", Prompts.H2_SYSTEM, Prompts.user("Give the legitimate explanations and counter-evidence for each hypothesis.", items,
                        hypotheses.stream().map(h -> Map.of("id", h.id(), "title", h.title(), "explanation", h.explanation(), "supportingRefs", h.supportingRefs())).toList()),
                        Schemas.h2(), Outputs.parserFor(Outputs.H2.class));
                var result = HypothesisValidator.validateH2(answer.value(), hypotheses, byRef.keySet());
                analyses.saveOk(context.organizationId(), context.claimId(), context.investigationId(), "H2", answer.modelId(), answer.calls(), answer.inputTokens(), answer.outputTokens(), result.toMap());
            } catch (ReasoningFailure f) {
                return failed("H2", f, context, summary);
            }
            h2 = analyses.find(context.organizationId(), context.investigationId(), "H2").orElseThrow();
        }
        summary.put("h2", h2.ok() ? "OK" : "UNAVAILABLE");
        return h2.ok() ? StageOutcome.ok(summary) : StageOutcome.degraded(summary);
    }

    @SuppressWarnings("unchecked")
    static List<Hypothesis> hypothesesOf(AnalysisStore.Analysis h1) {
        Object list = h1.result().get("hypotheses");
        return list instanceof List<?> l ? l.stream().map(o -> Hypothesis.fromMap((Map<String, Object>) o)).toList() : List.of();
    }

    /** A failure that may pass is retried by the runner; one that cannot is recorded as the role being unavailable and the stage carries on. */
    private StageOutcome failed(String role, ReasoningFailure f, StageContext context, Map<String, Object> summary) {
        summary.put(role.toLowerCase(), "FAILED_" + f.code());
        if (f.code().retryable()) {
            return new StageOutcome.Transient(f.code().investigationCode(), summary);
        }
        analyses.saveUnavailable(context.organizationId(), context.claimId(), context.investigationId(), role, f.modelId(), f.calls(), f.code().name());
        return StageOutcome.degraded(summary);
    }
}
