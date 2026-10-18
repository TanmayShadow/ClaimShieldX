package com.example.ClaimShieldX.investigation;

import com.example.ClaimShieldX.investigation.model.Stage;
import com.example.ClaimShieldX.investigation.work.StageContext;
import com.example.ClaimShieldX.investigation.work.StageHandler;
import com.example.ClaimShieldX.investigation.work.StageOutcome;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/** A stage handler whose answers the test scripts, one per call; with nothing scripted it answers OK. It counts its calls. */
public final class ScriptedHandler implements StageHandler {
    private final Stage stage;
    private final boolean degrades;
    private final ConcurrentLinkedDeque<Function<StageContext, StageOutcome>> script = new ConcurrentLinkedDeque<>();
    private final AtomicInteger calls = new AtomicInteger();
    private final List<StageContext> contexts = new CopyOnWriteArrayList<>();

    public ScriptedHandler(Stage stage) {
        this(stage, false);
    }

    public ScriptedHandler(Stage stage, boolean degradesWhenRetriesRunOut) {
        this.stage = stage;
        this.degrades = degradesWhenRetriesRunOut;
    }

    public ScriptedHandler then(StageOutcome outcome) {
        script.addLast(context -> outcome);
        return this;
    }

    public ScriptedHandler thenDo(Function<StageContext, StageOutcome> step) {
        script.addLast(step);
        return this;
    }

    public ScriptedHandler thenThrow(RuntimeException failure) {
        script.addLast(context -> {
            throw failure;
        });
        return this;
    }

    public int calls() {
        return calls.get();
    }

    public List<StageContext> contexts() {
        return contexts;
    }

    @Override
    public Stage stage() {
        return stage;
    }

    @Override
    public boolean degradesWhenRetriesRunOut() {
        return degrades;
    }

    @Override
    public StageOutcome execute(StageContext context) {
        calls.incrementAndGet();
        contexts.add(context);
        Function<StageContext, StageOutcome> step = script.pollFirst();
        return step == null ? StageOutcome.ok(Map.of()) : step.apply(context);
    }
}
