package com.example.ClaimShieldX.analysis.findings;

import com.example.ClaimShieldX.analysis.snapshot.SnapshotService.Item;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A transparent, deterministic review-priority score from the rule results of the snapshot. Rules:
 * <ul>
 *   <li>Only FLAG results add points; a missing document or a check that could not run is a completeness matter and adds nothing.</li>
 *   <li>The two duplicate signals (same file, same invoice number and issuer) describe one underlying fact, so only the larger counts.</li>
 *   <li>Model hypotheses add nothing: they interpret the same evidence, and counting them would count it twice.</li>
 *   <li>A shared garage never scores: no rule produces it.</li>
 *   <li>The total is capped at 100. The score is never a probability and never says anything is fraudulent.</li>
 * </ul>
 */
@Component
public class ScoreCalculator {
    public record Component(String ruleId, String severity, int points, String note) {
    }

    public record Score(int total, String band, String recommendedAction, List<Component> components) {
    }

    private final ScoreProperties properties;

    public ScoreCalculator(ScoreProperties properties) {
        this.properties = properties;
    }

    public ScoreProperties properties() {
        return properties;
    }

    public Score calculate(List<Item> items) {
        List<Component> components = new ArrayList<>();
        boolean anyMaterial = false;
        boolean anyGap = false;
        Component duplicate = null;
        for (Item item : items) {
            if (!item.ref().startsWith("RULE.")) {
                continue;
            }
            Map<String, Object> data = item.data();
            String ruleId = String.valueOf(data.get("ruleId"));
            String outcome = String.valueOf(data.get("outcome"));
            if (outcome.equals("INCOMPLETE")) {
                anyGap = true;
                continue;
            }
            if (!outcome.equals("FLAG")) {
                continue;
            }
            String severity = String.valueOf(data.get("severity"));
            anyMaterial |= severity.equals("REVIEW");
            int points = (severity.equals("REVIEW") ? properties.reviewPoints() : properties.infoPoints()).getOrDefault(ruleId, 0);
            Component component = new Component(ruleId, severity, points, null);
            if (ruleId.equals("DOCUMENT_FILE_DUPLICATE") || ruleId.equals("INVOICE_IDENTITY_DUPLICATE")) {
                if (duplicate == null || points > duplicate.points()) {
                    duplicate = component;
                }
                continue;
            }
            components.add(component);
        }
        if (duplicate != null) {
            components.add(new Component(duplicate.ruleId(), duplicate.severity(), duplicate.points(), "The same-file and same-invoice signals describe one fact, so only the larger is counted"));
        }
        components.sort(java.util.Comparator.comparingInt(Component::points).reversed().thenComparing(Component::ruleId));
        int total = Math.min(100, components.stream().mapToInt(Component::points).sum());
        String band = total >= properties.highFrom() ? "HIGH" : total >= properties.mediumFrom() ? "MEDIUM" : "LOW";
        String action = anyMaterial || total >= properties.mediumFrom() ? "MANUAL_REVIEW" : anyGap ? "REQUEST_MORE_EVIDENCE" : "NO_ADDITIONAL_CHECK_INDICATED";
        return new Score(total, band, action, List.copyOf(components));
    }

    public static List<Map<String, Object>> toMaps(List<Component> components) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Component c : components) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("ruleId", c.ruleId());
            m.put("severity", c.severity());
            m.put("points", c.points());
            if (c.note() != null) {
                m.put("note", c.note());
            }
            out.add(m);
        }
        return out;
    }
}
