package com.example.ClaimShieldX.analysis.reasoning;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;

import java.util.List;

/** The typed shapes of what the three model roles may answer. Anything outside them is rejected; nothing is trusted until validated. */
public final class Outputs {
    private static final JsonMapper MAPPER = JsonMapper.builder().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();

    private Outputs() {
    }

    public record H1(List<Concern> hypotheses) {
        public H1 {
            hypotheses = hypotheses == null ? List.of() : List.copyOf(hypotheses);
        }
    }

    public record Concern(String title, String explanation, List<String> supportingRefs, List<String> checksNeeded, List<String> unknowns) {
        public Concern {
            supportingRefs = supportingRefs == null ? List.of() : List.copyOf(supportingRefs);
            checksNeeded = checksNeeded == null ? List.of() : List.copyOf(checksNeeded);
            unknowns = unknowns == null ? List.of() : List.copyOf(unknowns);
        }
    }

    public record H2(List<Assessment> assessments) {
        public H2 {
            assessments = assessments == null ? List.of() : List.copyOf(assessments);
        }
    }

    public record Assessment(String hypothesisId, List<Legitimate> legitimateExplanations, List<String> counterEvidenceRefs) {
        public Assessment {
            legitimateExplanations = legitimateExplanations == null ? List.of() : List.copyOf(legitimateExplanations);
            counterEvidenceRefs = counterEvidenceRefs == null ? List.of() : List.copyOf(counterEvidenceRefs);
        }
    }

    public record Legitimate(String explanation, List<String> supportingRefs, List<String> checksNeeded) {
        public Legitimate {
            supportingRefs = supportingRefs == null ? List.of() : List.copyOf(supportingRefs);
            checksNeeded = checksNeeded == null ? List.of() : List.copyOf(checksNeeded);
        }
    }

    public record H3(List<Review> reviews) {
        public H3 {
            reviews = reviews == null ? List.of() : List.copyOf(reviews);
        }
    }

    public record Review(String hypothesisId, String support, List<String> reasons, List<String> checkedRefs) {
        public Review {
            reasons = reasons == null ? List.of() : List.copyOf(reasons);
            checkedRefs = checkedRefs == null ? List.of() : List.copyOf(checkedRefs);
        }
    }

    public static <T> ReasoningCaller.Parser<T> parserFor(Class<T> type) {
        return json -> {
            try {
                return MAPPER.readValue(json, type);
            } catch (Exception e) {
                throw new ReasoningCaller.MalformedOutput("The model's answer does not match the expected shape");
            }
        };
    }
}
