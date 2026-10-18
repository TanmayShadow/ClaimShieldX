package com.example.ClaimShieldX.analysis.reasoning;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The JSON Schemas the model must answer in, one per role. They mirror {@link Outputs}. */
public final class Schemas {
    private Schemas() {
    }

    public static Map<String, Object> h1() {
        return obj(Map.of("hypotheses", arr(obj(props(
                "title", str(), "explanation", str(), "supportingRefs", arr(str()), "checksNeeded", arr(str()), "unknowns", arr(str())),
                List.of("title", "explanation", "supportingRefs", "checksNeeded", "unknowns")))), List.of("hypotheses"));
    }

    public static Map<String, Object> h2() {
        Map<String, Object> legit = obj(props("explanation", str(), "supportingRefs", arr(str()), "checksNeeded", arr(str())), List.of("explanation", "supportingRefs", "checksNeeded"));
        return obj(Map.of("assessments", arr(obj(props("hypothesisId", str(), "legitimateExplanations", arr(legit), "counterEvidenceRefs", arr(str())),
                List.of("hypothesisId", "legitimateExplanations", "counterEvidenceRefs")))), List.of("assessments"));
    }

    public static Map<String, Object> h3() {
        Map<String, Object> support = new LinkedHashMap<>();
        support.put("type", "string");
        support.put("enum", List.of("SUPPORTED", "PARTIALLY_SUPPORTED", "NOT_SUPPORTED"));
        return obj(Map.of("reviews", arr(obj(props("hypothesisId", str(), "support", support, "reasons", arr(str()), "checkedRefs", arr(str())),
                List.of("hypothesisId", "support", "reasons", "checkedRefs")))), List.of("reviews"));
    }

    private static Map<String, Object> props(Object... pairs) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put((String) pairs[i], pairs[i + 1]);
        }
        return map;
    }

    private static Map<String, Object> obj(Map<String, Object> properties, List<String> required) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("type", "object");
        map.put("properties", properties);
        map.put("required", required);
        map.put("additionalProperties", false);
        return map;
    }

    private static Map<String, Object> arr(Map<String, Object> items) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("type", "array");
        map.put("items", items);
        return map;
    }

    private static Map<String, Object> str() {
        return Map.of("type", "string");
    }
}
