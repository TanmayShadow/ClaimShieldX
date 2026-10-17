package com.example.ClaimShieldX.extraction.support;

import com.example.ClaimShieldX.extraction.client.ExtractionModelClient;
import com.example.ClaimShieldX.extraction.client.ModelCallException;
import com.example.ClaimShieldX.extraction.client.ModelRequest;
import com.example.ClaimShieldX.extraction.client.ModelResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A scripted stand-in for the model. Each call takes the next scripted step: a JSON answer, a failure, or code that runs
 * (for example to wait on a latch). A call with nothing scripted is a test bug and fails loudly with an Error, so a missing
 * step can never be hidden by a retry.
 */
@Component
@ConditionalOnProperty(name = "app.extraction.provider", havingValue = "fake")
public class FakeExtractionModelClient implements ExtractionModelClient {
    public interface Step {
        ModelResponse run(ModelRequest request) throws ModelCallException;
    }

    private final ConcurrentLinkedDeque<Step> script = new ConcurrentLinkedDeque<>();
    private final List<ModelRequest> requests = new CopyOnWriteArrayList<>();

    public void reset() {
        script.clear();
        requests.clear();
    }

    public FakeExtractionModelClient thenReturn(String json) {
        script.addLast(request -> new ModelResponse(json, 1200, 450));
        return this;
    }

    public FakeExtractionModelClient thenFail(ModelCallException failure) {
        script.addLast(request -> {
            throw failure;
        });
        return this;
    }

    public FakeExtractionModelClient then(Step step) {
        script.addLast(step);
        return this;
    }

    public List<ModelRequest> requests() {
        return List.copyOf(requests);
    }

    public int callCount() {
        return requests.size();
    }

    @Override
    public ModelResponse extract(ModelRequest request) throws ModelCallException {
        requests.add(request);
        Step step = script.pollFirst();
        if (step == null) {
            throw new AssertionError("The fake model was called with nothing scripted (call " + requests.size() + ")");
        }
        return step.run(request);
    }
}
