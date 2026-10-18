package com.example.ClaimShieldX.investigation.work;

import com.example.ClaimShieldX.investigation.model.Stage;
import com.example.ClaimShieldX.investigation.model.StageStatus;
import com.example.ClaimShieldX.rules.dto.RuleRunResponse;
import com.example.ClaimShieldX.rules.service.RuleRunService;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Runs the deterministic rules (B7) and the history lookup they include. Running again with nothing new returns the stored run,
 * so a retry or a crash repeats nothing. The rules do not depend on a model, so this stage works even when extraction was degraded.
 */
@Component
public class RulesStageHandler implements StageHandler {
    private final RuleRunService ruleRunService;
    private final com.example.ClaimShieldX.analysis.snapshot.SnapshotService snapshotService;

    public RulesStageHandler(RuleRunService ruleRunService, com.example.ClaimShieldX.analysis.snapshot.SnapshotService snapshotService) {
        this.ruleRunService = ruleRunService;
        this.snapshotService = snapshotService;
    }

    @Override
    public Stage stage() {
        return Stage.RUNNING_RULES;
    }

    @Override
    public StageOutcome execute(StageContext context) {
        RuleRunService.RunOutcome outcome = ruleRunService.run(context.organizationId(), context.claimId(), context.requestedBy());
        RuleRunResponse run = outcome.run();
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("ruleRunId", run.id().toString());
        summary.put("reusedExistingRun", !outcome.created());
        summary.put("pass", run.counts().pass());
        summary.put("flag", run.counts().flag());
        summary.put("incomplete", run.counts().incomplete());
        summary.put("notApplicable", run.counts().notApplicable());
        summary.put("error", run.counts().error());
        // The evidence is frozen here: from now on the analysis, the findings and the report refer to this snapshot only.
        summary.put("snapshotId", snapshotService.freeze(context.organizationId(), context.claimId(), context.investigationId(), run.id()).toString());
        // A rule that failed internally is isolated and reported as ERROR in the run; the stage says so rather than claiming a clean pass.
        return new StageOutcome.Done(run.counts().error() > 0 ? StageStatus.DEGRADED : StageStatus.OK, summary, run.id());
    }
}
