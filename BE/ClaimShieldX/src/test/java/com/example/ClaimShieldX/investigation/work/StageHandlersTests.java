package com.example.ClaimShieldX.investigation.work;

import com.example.ClaimShieldX.investigation.model.Stage;
import com.example.ClaimShieldX.investigation.model.StageStatus;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StageHandlersTests {
    private static StageHandler handlerFor(Stage stage) {
        return new StageHandler() {
            @Override
            public Stage stage() {
                return stage;
            }

            @Override
            public StageOutcome execute(StageContext context) {
                return StageOutcome.ok(Map.of("handled", stage.name()));
            }
        };
    }

    @Test
    void everyStageHasAHandlerAndTheOnesNobodyImplementedYetAreSkippedWithAReason() throws Exception {
        StageHandlers handlers = new StageHandlers(List.of(handlerFor(Stage.RUNNING_RULES)));

        for (Stage stage : Stage.values()) {
            assertThat(handlers.of(stage)).isNotNull();
            assertThat(handlers.of(stage).stage()).isEqualTo(stage);
        }
        StageOutcome pending = handlers.of(Stage.BUILDING_HYPOTHESES).execute(null);
        assertThat(pending).isInstanceOfSatisfying(StageOutcome.Done.class, done -> {
            assertThat(done.status()).isEqualTo(StageStatus.SKIPPED);
            assertThat(done.summary()).containsEntry("reason", "NOT_AVAILABLE_YET");
        });
        assertThat(handlers.of(Stage.RUNNING_RULES).execute(null)).isInstanceOfSatisfying(StageOutcome.Done.class, done -> assertThat(done.summary()).containsEntry("handled", "RUNNING_RULES"));
    }

    @Test
    void aRealHandlerReplacesThePlaceholderForItsStage() throws Exception {
        StageHandlers handlers = new StageHandlers(List.of(handlerFor(Stage.GENERATING_REPORT)));

        assertThat(handlers.of(Stage.GENERATING_REPORT).execute(null)).isInstanceOfSatisfying(StageOutcome.Done.class, done -> assertThat(done.status()).isEqualTo(StageStatus.OK));
    }

    @Test
    void twoHandlersForOneStageIsAConfigurationErrorNotALastOneWins() {
        assertThatThrownBy(() -> new StageHandlers(List.of(handlerFor(Stage.EXTRACTING), handlerFor(Stage.EXTRACTING)))).isInstanceOf(IllegalStateException.class).hasMessageContaining("EXTRACTING");
    }

    @Test
    void aFinishedOutcomeCannotCarryAnUnsettledStatus() {
        assertThatThrownBy(() -> new StageOutcome.Done(StageStatus.RUNNING, Map.of(), null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new StageOutcome.Done(StageStatus.FAILED, Map.of(), null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new StageOutcome.Done(StageStatus.PENDING, Map.of(), null)).isInstanceOf(IllegalArgumentException.class);
    }
}
