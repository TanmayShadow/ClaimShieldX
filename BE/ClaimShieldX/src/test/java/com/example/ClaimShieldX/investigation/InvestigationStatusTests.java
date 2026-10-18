package com.example.ClaimShieldX.investigation;

import com.example.ClaimShieldX.investigation.model.InvestigationStatus;
import com.example.ClaimShieldX.investigation.model.Stage;
import com.example.ClaimShieldX.investigation.model.StageStatus;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InvestigationStatusTests {

    @Test
    void theStagesAreInTheContractsOrderAndEachHasARunningStatusOfTheSameName() {
        assertThat(Stage.values()).extracting(Enum::name).containsExactly("PREPARING_EVIDENCE", "EXTRACTING", "RUNNING_RULES", "BUILDING_HYPOTHESES", "VALIDATING_FINDINGS", "GENERATING_REPORT");
        for (Stage stage : Stage.values()) {
            assertThat(InvestigationStatus.of(stage).runningStage()).contains(stage);
        }
        assertThat(Stage.GENERATING_REPORT.next()).isNull();
        assertThat(Stage.PREPARING_EVIDENCE.next()).isEqualTo(Stage.EXTRACTING);
    }

    @Test
    void onlyTheThreeFinishedStatusesAreNotActive() {
        List<InvestigationStatus> inactive = Arrays.stream(InvestigationStatus.values()).filter(s -> !s.isActive()).toList();

        assertThat(inactive).containsExactlyInAnyOrder(InvestigationStatus.COMPLETED, InvestigationStatus.FAILED, InvestigationStatus.CANCELLED);
    }

    @Test
    void waitingQueuedAndRetryStatusesAreNotRunningStages() {
        assertThat(InvestigationStatus.QUEUED.runningStage()).isEmpty();
        assertThat(InvestigationStatus.WAITING_FOR_EVIDENCE.runningStage()).isEmpty();
        assertThat(InvestigationStatus.RETRY_WAIT.runningStage()).isEmpty();
        assertThat(InvestigationStatus.COMPLETED.runningStage()).isEmpty();
    }

    @Test
    void aSettledStageIsOneThatNeverHasToRunAgain() {
        assertThat(Arrays.stream(StageStatus.values()).filter(StageStatus::isSettled)).containsExactlyInAnyOrder(StageStatus.OK, StageStatus.DEGRADED, StageStatus.SKIPPED);
    }
}
