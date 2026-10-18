package com.example.ClaimShieldX.analysis.support;

import com.example.ClaimShieldX.analysis.reasoning.ReasoningModelClient;
import com.example.ClaimShieldX.extraction.client.ModelCallException;
import com.example.ClaimShieldX.extraction.client.ModelResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A scripted stand-in for the reasoning model, scripted per role (H1, H2, H3). A role with nothing scripted answers "nothing to report",
 * which is a valid answer, so tests that do not care about reasoning are not disturbed; tests that do care script every role they expect.
 */
@Component
@ConditionalOnProperty(name = "app.extraction.provider", havingValue = "fake")
public class FakeReasoningClient implements ReasoningModelClient {
    public interface Step {
        ModelResponse run(Request request) throws ModelCallException;
    }

    private final Map<String, ConcurrentLinkedDeque<Step>> scripts = new ConcurrentHashMap<>();
    private final List<Request> requests = new CopyOnWriteArrayList<>();

    public void reset() {
        scripts.clear();
        requests.clear();
    }

    public FakeReasoningClient thenReturn(String role, String json) {
        return then(role, request -> new ModelResponse(json, 900, 300));
    }

    public FakeReasoningClient thenFail(String role, ModelCallException failure) {
        return then(role, request -> {
            throw failure;
        });
    }

    public FakeReasoningClient then(String role, Step step) {
        scripts.computeIfAbsent(role, r -> new ConcurrentLinkedDeque<>()).addLast(step);
        return this;
    }

    public List<Request> requests() {
        return List.copyOf(requests);
    }

    public List<Request> requests(String role) {
        return requests.stream().filter(r -> r.role().equals(role)).toList();
    }

    public int callCount() {
        return requests.size();
    }

    @Override
    public ModelResponse generate(Request request) throws ModelCallException {
        requests.add(request);
        ConcurrentLinkedDeque<Step> script = scripts.get(request.role());
        Step step = script == null ? null : script.pollFirst();
        if (step == null) {
            return new ModelResponse(switch (request.role()) {
                case "H1" -> "{\"hypotheses\":[]}";
                case "H2" -> "{\"assessments\":[]}";
                default -> "{\"reviews\":[]}";
            }, 10, 5);
        }
        return step.run(request);
    }
}
