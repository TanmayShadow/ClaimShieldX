package com.example.ClaimShieldX.investigation.work;

import com.example.ClaimShieldX.investigation.model.Stage;
import com.example.ClaimShieldX.investigation.model.StageStatus;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Finds the handler of each stage. A stage nobody implements yet is reported as SKIPPED with a reason, so the public sequence of
 * stages is already the final one and each later module only registers its handler.
 */
@Component
public class StageHandlers {
    private final Map<Stage, StageHandler> handlers = new EnumMap<>(Stage.class);

    public StageHandlers(List<StageHandler> available) {
        for (StageHandler handler : available) {
            if (handlers.put(handler.stage(), handler) != null) {
                throw new IllegalStateException("Two handlers for stage " + handler.stage());
            }
        }
        for (Stage stage : Stage.values()) {
            handlers.computeIfAbsent(stage, NotAvailableYet::new);
        }
    }

    public StageHandler of(Stage stage) {
        return handlers.get(stage);
    }

    static final class NotAvailableYet implements StageHandler {
        private final Stage stage;

        NotAvailableYet(Stage stage) {
            this.stage = stage;
        }

        @Override
        public Stage stage() {
            return stage;
        }

        @Override
        public StageOutcome execute(StageContext context) {
            return new StageOutcome.Done(StageStatus.SKIPPED, Map.of("reason", "NOT_AVAILABLE_YET"), null);
        }
    }
}
