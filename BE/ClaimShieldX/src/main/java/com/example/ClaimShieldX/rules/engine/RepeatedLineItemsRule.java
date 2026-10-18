package com.example.ClaimShieldX.rules.engine;

import com.example.ClaimShieldX.documents.model.DocumentType;
import com.example.ClaimShieldX.extraction.schema.IssueCode;
import com.example.ClaimShieldX.rules.config.RuleProperties;
import com.example.ClaimShieldX.rules.engine.Extracted.Line;
import com.example.ClaimShieldX.rules.engine.RuleInputs.DocumentFacts;
import com.example.ClaimShieldX.rules.model.EvidenceRef;
import com.example.ClaimShieldX.rules.model.RuleDefinition;
import com.example.ClaimShieldX.rules.model.RuleResult;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Finds line items on the final invoice that are repeated. Comparison is only attempted where it is reliable: a line needs a
 * readable description of at least the configured number of words (a bare "Labour" is too generic) and a readable unit price.
 * EXACT: same words in the same order and the same unit price. NEAR: the descriptions share at least the configured share of
 * their words and either the unit price or the line amount is the same. Quantities may differ.
 */
public final class RepeatedLineItemsRule implements Rule {
    public static final RuleDefinition DEFINITION = new RuleDefinition("REPEATED_LINE_ITEMS", "1", "Repeated line items",
            "Finds line items on the final invoice that repeat an earlier line (same description and unit price, or nearly the same) and shows the amount involved.",
            List.of("A final invoice that has been read, with line descriptions and unit prices"),
            List.of("Identical parts or labour legitimately appear more than once (left and right sides, separate repair stages).", "A description that is generic or was copied across rows by the garage's software.",
                    "A misread description (check the source text shown)."));

    private static final Set<IssueCode> OWN_FINDINGS = Set.of(IssueCode.LINE_TOTAL_MISMATCH, IssueCode.SUBTOTAL_MISMATCH, IssueCode.GRAND_TOTAL_MISMATCH);

    private record Candidate(Line line, List<String> words) {
    }

    @Override
    public RuleDefinition definition() {
        return DEFINITION;
    }

    @Override
    public RuleResult evaluate(RuleInputs inputs, RuleProperties config) {
        DocumentFacts invoice = Docs.newest(inputs, DocumentType.FINAL_INVOICE);
        if (invoice == null) {
            return Docs.notApplicable(inputs, DocumentType.FINAL_INVOICE)
                    ? Results.notApplicable(DEFINITION, "The checklist for this claim does not expect a final invoice at its stage.", Results.values())
                    : Results.incomplete(DEFINITION, "No final invoice has been received, so its line items could not be compared.", List.of("FINAL_INVOICE"), Results.values(), List.of());
        }
        Extracted x = Extracted.of(invoice);
        if (x == null) {
            return Results.incomplete(DEFINITION, "The final invoice has not been read successfully, so its line items could not be compared.",
                    List.of("FINAL_INVOICE_EXTRACTION"), Map.of("invoiceDocumentId", invoice.id().toString()), List.of(EvidenceRef.document(invoice.id(), "FINAL_INVOICE")));
        }
        List<String> doubts = x.doubts(OWN_FINDINGS);
        List<Line> lines = x.lines();
        List<Candidate> candidates = new ArrayList<>();
        for (Line line : lines) {
            String description = line.descriptionText();
            List<String> words = words(description);
            if (words.size() >= config.minDescriptionWords() && line.unitPriceValue() != null) {
                candidates.add(new Candidate(line, words));
            }
        }
        Map<String, Object> values = Results.values();
        values.put("invoiceDocumentId", invoice.id().toString());
        values.put("lineItems", lines.size());
        values.put("comparableLines", candidates.size());
        values.put("minimumDescriptionWords", config.minDescriptionWords());
        values.put("nearDuplicateSimilarity", config.nearDuplicateSimilarity().toPlainString());
        if (lines.isEmpty()) {
            return Results.incomplete(DEFINITION, "No line items could be read from the final invoice, so they could not be compared.",
                    List.of("INVOICE_LINE_ITEMS"), values, List.of(EvidenceRef.document(invoice.id(), "FINAL_INVOICE")));
        }
        if (lines.size() < 2) {
            return Results.pass(DEFINITION, Extracted.reliability(doubts), doubts, "The final invoice has a single line item, so nothing can repeat.",
                    values, List.of(EvidenceRef.document(invoice.id(), "FINAL_INVOICE")));
        }
        if (candidates.size() < 2) {
            return Results.incomplete(DEFINITION, "Too few line items have a readable description of enough words and a readable unit price to compare (" + candidates.size() + " of " + lines.size() + ").",
                    List.of("READABLE_LINE_DESCRIPTIONS_AND_PRICES"), values, List.of(EvidenceRef.document(invoice.id(), "FINAL_INVOICE")));
        }

        int n = candidates.size();
        int[] parent = new int[n];
        for (int i = 0; i < n; i++) {
            parent[i] = i;
        }
        boolean[][] exact = new boolean[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                Candidate a = candidates.get(i);
                Candidate b = candidates.get(j);
                boolean samePrice = a.line().unitPriceValue().compareTo(b.line().unitPriceValue()) == 0;
                boolean sameWordsInOrder = a.words().equals(b.words());
                boolean sameAmount = a.line().lineTotalValue() != null && b.line().lineTotalValue() != null && a.line().lineTotalValue().compareTo(b.line().lineTotalValue()) == 0;
                if (sameWordsInOrder && samePrice) {
                    exact[i][j] = true;
                    union(parent, i, j);
                } else if ((samePrice || sameAmount) && similarity(a.words(), b.words()).compareTo(config.nearDuplicateSimilarity()) >= 0) {
                    union(parent, i, j);
                }
            }
        }

        Map<Integer, List<Integer>> components = new TreeMap<>();
        for (int i = 0; i < n; i++) {
            components.computeIfAbsent(find(parent, i), k -> new ArrayList<>()).add(i);
        }
        List<Map<String, Object>> groups = new ArrayList<>();
        List<EvidenceRef> evidence = new ArrayList<>();
        boolean anyExact = false;
        BigDecimal repeatedTotal = BigDecimal.ZERO;
        List<List<Integer>> ordered = new ArrayList<>(components.values());
        ordered.sort(java.util.Comparator.comparing(members -> members.get(0)));
        for (List<Integer> members : ordered) {
            if (members.size() < 2) {
                continue;
            }
            boolean allExact = true;
            for (int a = 0; a < members.size(); a++) {
                for (int b = a + 1; b < members.size(); b++) {
                    allExact &= exact[members.get(a)][members.get(b)];
                }
            }
            anyExact |= allExact;
            BigDecimal repeated = BigDecimal.ZERO;
            List<Map<String, Object>> memberEntries = new ArrayList<>();
            for (int k = 0; k < members.size(); k++) {
                Line line = candidates.get(members.get(k)).line();
                if (k > 0 && line.lineTotalValue() != null) {
                    repeated = repeated.add(line.lineTotalValue());
                }
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("line", line.index() + 1);
                m.put("description", line.descriptionText());
                m.put("quantity", line.quantityValue() == null ? null : line.quantityValue().stripTrailingZeros().toPlainString());
                m.put("unitPrice", Results.plain(line.unitPriceValue()));
                m.put("lineTotal", line.lineTotalValue() == null ? null : Results.plain(line.lineTotalValue()));
                memberEntries.add(m);
                evidence.add(x.lineRef(line));
            }
            repeatedTotal = repeatedTotal.add(repeated);
            Map<String, Object> group = new LinkedHashMap<>();
            group.put("kind", allExact ? "EXACT" : "NEAR");
            group.put("lines", memberEntries);
            group.put("amountOfRepeatedLines", Results.plain(repeated));
            groups.add(group);
        }
        values.put("groups", groups);
        values.put("amountOfRepeatedLines", Results.plain(repeatedTotal));
        if (groups.isEmpty()) {
            return Results.pass(DEFINITION, Extracted.reliability(doubts), doubts, "None of the " + n + " comparable line items repeats another.", values,
                    List.of(EvidenceRef.document(invoice.id(), "FINAL_INVOICE")));
        }
        return Results.flag(DEFINITION, Results.severity(anyExact, doubts), Extracted.reliability(doubts), doubts,
                groups.size() + " group" + (groups.size() == 1 ? "" : "s") + " of repeated line items found on the final invoice, involving "
                        + Results.plain(repeatedTotal) + " in the repeated lines after the first of each group. "
                        + (anyExact ? "At least one group has the same description and unit price." : "The repeats are near-identical rather than exact."),
                values, evidence);
    }

    /** Lower-case words made only of letters and digits, in order, so punctuation and spacing do not hide a repeat. */
    static List<String> words(String description) {
        if (description == null) {
            return List.of();
        }
        return Arrays.stream(description.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+")).filter(w -> !w.isEmpty()).toList();
    }

    /** Share of distinct words the two descriptions have in common (intersection over union), at four decimals. */
    static BigDecimal similarity(List<String> a, List<String> b) {
        Set<String> left = new LinkedHashSet<>(a);
        Set<String> right = new LinkedHashSet<>(b);
        Set<String> common = new LinkedHashSet<>(left);
        common.retainAll(right);
        Set<String> all = new LinkedHashSet<>(left);
        all.addAll(right);
        if (all.isEmpty()) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(common.size()).divide(BigDecimal.valueOf(all.size()), 4, RoundingMode.HALF_UP);
    }

    private static int find(int[] parent, int i) {
        while (parent[i] != i) {
            parent[i] = parent[parent[i]];
            i = parent[i];
        }
        return i;
    }

    private static void union(int[] parent, int a, int b) {
        parent[find(parent, a)] = find(parent, b);
    }
}
