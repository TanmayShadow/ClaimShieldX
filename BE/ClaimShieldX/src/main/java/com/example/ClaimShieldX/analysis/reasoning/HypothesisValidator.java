package com.example.ClaimShieldX.analysis.reasoning;

import com.example.ClaimShieldX.analysis.snapshot.SnapshotService.Item;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Java is the authority over what a model said. Every reference must exist in the snapshot, every text is cleaned and screened, and a
 * hypothesis that cannot be traced to evidence that shows something is dropped with its reason recorded. Nothing the model wrote is
 * stored or shown before it has passed through here.
 */
public final class HypothesisValidator {
    public static final int MAX_HYPOTHESES = 4;

    public record Hypothesis(String id, String title, String explanation, List<String> supportingRefs, List<String> checksNeeded, List<String> unknowns) {
        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", id);
            m.put("title", title);
            m.put("explanation", explanation);
            m.put("supportingRefs", supportingRefs);
            m.put("checksNeeded", checksNeeded);
            m.put("unknowns", unknowns);
            return m;
        }


        public static Hypothesis fromMap(Map<String, Object> m) {
            return new Hypothesis((String) m.get("id"), (String) m.get("title"), (String) m.get("explanation"), (List<String>) m.get("supportingRefs"),
                    (List<String>) m.get("checksNeeded"), (List<String>) m.get("unknowns"));
        }
    }

    public record Rejected(String title, String reason) {
    }

    public record H1Result(List<Hypothesis> hypotheses, List<Rejected> rejected) {
        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("hypotheses", hypotheses.stream().map(Hypothesis::toMap).toList());
            m.put("rejected", rejected.stream().map(r -> Map.<String, Object>of("title", String.valueOf(r.title()), "reason", r.reason())).toList());
            return m;
        }
    }

    public record Legitimate(String explanation, List<String> supportingRefs, List<String> checksNeeded) {
    }

    public record Assessment(String hypothesisId, List<Legitimate> legitimate, List<String> counterEvidenceRefs) {
    }

    public record H2Result(List<Assessment> assessments, int droppedReferences) {
        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("assessments", assessments.stream().map(a -> {
                Map<String, Object> x = new LinkedHashMap<>();
                x.put("hypothesisId", a.hypothesisId());
                x.put("legitimateExplanations", a.legitimate().stream().map(l -> Map.<String, Object>of("explanation", l.explanation(), "supportingRefs", l.supportingRefs(), "checksNeeded", l.checksNeeded())).toList());
                x.put("counterEvidenceRefs", a.counterEvidenceRefs());
                return x;
            }).toList());
            m.put("droppedReferences", droppedReferences);
            return m;
        }
    }

    public record Review(String hypothesisId, String support, List<String> reasons, List<String> checkedRefs) {
    }

    public record H3Result(List<Review> reviews, int droppedReferences) {
        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("reviews", reviews.stream().map(r -> Map.<String, Object>of("hypothesisId", r.hypothesisId(), "support", r.support(), "reasons", r.reasons(), "checkedRefs", r.checkedRefs())).toList());
            m.put("droppedReferences", droppedReferences);
            return m;
        }
    }

    private HypothesisValidator() {
    }

    /**
     * Evidence a concern may rest on: a rule that flagged something, or an earlier claim that relates to this one. A gap, a missing document, a
     * rule that passed or could not run, and a plain claim or document value show nothing by themselves: missing evidence is reported as an
     * unknown elsewhere and is never turned into a hypothesis.
     */
    static boolean showsSomething(Item item) {
        return switch (item.type()) {
            case "RULE_RESULT" -> "FLAG".equals(String.valueOf(item.data().get("outcome")));
            case "HISTORICAL_RECORD", "RELATIONSHIP" -> true;
            default -> false;
        };
    }

    public static H1Result validateH1(Outputs.H1 out, Map<String, Item> evidence) {
        List<Hypothesis> valid = new ArrayList<>();
        List<Rejected> rejected = new ArrayList<>();
        int considered = 0;
        for (Outputs.Concern c : out.hypotheses()) {
            if (++considered > MAX_HYPOTHESES) {
                break;
            }
            String title = Text.clean(c.title(), 120);
            String explanation = Text.clean(c.explanation(), 600);
            List<String> checks = clean(c.checksNeeded(), 5, 200);
            List<String> unknowns = clean(c.unknowns(), 5, 200);
            if (title == null || title.isBlank() || explanation == null || explanation.isBlank()) {
                rejected.add(new Rejected(title, "EMPTY_TEXT"));
                continue;
            }
            if (Text.nonNeutral(title, explanation) || Text.nonNeutral(checks) || Text.nonNeutral(unknowns)) {
                rejected.add(new Rejected(title, "NON_NEUTRAL_WORDING"));
                continue;
            }
            List<String> refs = new ArrayList<>(new LinkedHashSet<>(c.supportingRefs().stream().map(r -> Text.clean(r, 160)).toList()));
            if (refs.isEmpty() || refs.stream().anyMatch(r -> !evidence.containsKey(r))) {
                rejected.add(new Rejected(title, refs.isEmpty() ? "NO_SUPPORTING_EVIDENCE" : "UNKNOWN_REFERENCE"));
                continue;
            }
            if (refs.stream().noneMatch(r -> showsSomething(evidence.get(r)))) {
                rejected.add(new Rejected(title, "NO_FLAGGED_EVIDENCE"));
                continue;
            }
            valid.add(new Hypothesis("H-" + (valid.size() + 1), title, explanation, List.copyOf(refs), checks, unknowns));
        }
        return new H1Result(List.copyOf(valid), List.copyOf(rejected));
    }

    public static H2Result validateH2(Outputs.H2 out, List<Hypothesis> hypotheses, Set<String> refs) {
        Set<String> ids = new LinkedHashSet<>(hypotheses.stream().map(Hypothesis::id).toList());
        Map<String, Assessment> byId = new LinkedHashMap<>();
        int dropped = 0;
        for (Outputs.Assessment a : out.assessments()) {
            if (a.hypothesisId() == null || !ids.contains(a.hypothesisId()) || byId.containsKey(a.hypothesisId())) {
                continue;
            }
            List<Legitimate> legit = new ArrayList<>();
            for (Outputs.Legitimate l : a.legitimateExplanations()) {
                if (legit.size() >= 4) {
                    break;
                }
                String text = Text.clean(l.explanation(), 400);
                List<String> checks = clean(l.checksNeeded(), 3, 200);
                if (text == null || text.isBlank() || Text.nonNeutral(text) || Text.nonNeutral(checks)) {
                    continue;
                }
                List<String> known = l.supportingRefs().stream().map(r -> Text.clean(r, 160)).filter(refs::contains).distinct().toList();
                dropped += l.supportingRefs().size() - known.size();
                legit.add(new Legitimate(text, known, checks));
            }
            List<String> counter = a.counterEvidenceRefs().stream().map(r -> Text.clean(r, 160)).filter(refs::contains).distinct().toList();
            dropped += a.counterEvidenceRefs().size() - counter.size();
            byId.put(a.hypothesisId(), new Assessment(a.hypothesisId(), List.copyOf(legit), counter));
        }
        return new H2Result(List.copyOf(byId.values()), dropped);
    }

    public static H3Result validateH3(Outputs.H3 out, List<Hypothesis> hypotheses, Set<String> refs) {
        Set<String> ids = new LinkedHashSet<>(hypotheses.stream().map(Hypothesis::id).toList());
        Map<String, Review> byId = new LinkedHashMap<>();
        int dropped = 0;
        for (Outputs.Review r : out.reviews()) {
            if (r.hypothesisId() == null || !ids.contains(r.hypothesisId()) || byId.containsKey(r.hypothesisId())
                    || !Set.of("SUPPORTED", "PARTIALLY_SUPPORTED", "NOT_SUPPORTED").contains(r.support())) {
                continue;
            }
            List<String> reasons = clean(r.reasons(), 3, 300).stream().filter(t -> !Text.nonNeutral(t)).toList();
            List<String> checked = r.checkedRefs().stream().map(x -> Text.clean(x, 160)).filter(refs::contains).distinct().toList();
            dropped += r.checkedRefs().size() - checked.size();
            byId.put(r.hypothesisId(), new Review(r.hypothesisId(), r.support(), reasons, checked));
        }
        return new H3Result(List.copyOf(byId.values()), dropped);
    }

    private static List<String> clean(List<String> texts, int maxCount, int maxLength) {
        return texts.stream().map(t -> Text.clean(t, maxLength)).filter(t -> t != null && !t.isBlank()).limit(maxCount).toList();
    }
}
