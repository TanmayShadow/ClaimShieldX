package com.example.ClaimShieldX.extraction.client;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The JSON Schema the model must answer in (schema version itemized-cost-2). It mirrors the records in the schema package
 * property for property; a test compares the two so they cannot drift apart. The strict parser rejects anything outside it.
 */
public final class ExtractionSchemas {
    static final List<String> HEADER_FIELDS = List.of("documentNumber", "documentDate", "incidentDate", "repairStartDate",
            "repairCompletionDate", "garageIdentifier", "vehicleIdentifier", "currency", "repairDescription");
    /** Cells of a row. The row's own source (page, quote) comes first in the record and the schema. */
    static final List<String> LINE_ITEM_CELLS = List.of("description", "category", "quantity", "unitPrice", "tax", "lineTotal");
    static final List<String> TOTAL_FIELDS = List.of("subtotal", "taxTotal", "discountTotal", "grandTotal");
    static final List<String> WARNING_CODES = List.of("ILLEGIBLE_REGION", "HANDWRITTEN_ALTERATION", "INSTRUCTION_LIKE_TEXT", "OTHER_PROBLEM");

    private ExtractionSchemas() {
    }

    public static Map<String, Object> modelOutput() {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("type", "object");
        root.put("properties", orderedMap(
                "header", object(HEADER_FIELDS),
                "lineItems", Map.of("type", "array", "items", lineItem()),
                "totals", object(TOTAL_FIELDS),
                "warnings", Map.of("type", "array", "items", warning())));
        root.put("required", List.of("header", "lineItems", "totals", "warnings"));
        root.put("additionalProperties", false);
        return root;
    }

    private static Map<String, Object> object(List<String> fields) {
        Map<String, Object> properties = new LinkedHashMap<>();
        fields.forEach(name -> properties.put(name, field()));
        Map<String, Object> object = new LinkedHashMap<>();
        object.put("type", "object");
        object.put("properties", properties);
        object.put("required", fields);
        object.put("additionalProperties", false);
        return object;
    }

    private static Map<String, Object> lineItem() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("page", nullable("integer"));
        properties.put("quote", nullable("string"));
        LINE_ITEM_CELLS.forEach(name -> properties.put(name, nullable("string")));
        Map<String, Object> object = new LinkedHashMap<>();
        object.put("type", "object");
        object.put("properties", properties);
        object.put("required", List.copyOf(properties.keySet()));
        object.put("additionalProperties", false);
        return object;
    }

    private static Map<String, Object> field() {
        Map<String, Object> field = new LinkedHashMap<>();
        field.put("type", "object");
        field.put("properties", orderedMap(
                "value", nullable("string"),
                "page", nullable("integer"),
                "quote", nullable("string"),
                "unknownReason", nullable("string")));
        field.put("required", List.of("value", "page", "quote", "unknownReason"));
        field.put("additionalProperties", false);
        return field;
    }

    private static Map<String, Object> warning() {
        Map<String, Object> warning = new LinkedHashMap<>();
        warning.put("type", "object");
        warning.put("properties", orderedMap(
                "code", Map.of("type", "string", "enum", WARNING_CODES),
                "page", nullable("integer"),
                "quote", nullable("string")));
        warning.put("required", List.of("code", "page", "quote"));
        warning.put("additionalProperties", false);
        return warning;
    }

    private static Map<String, Object> nullable(String type) {
        return Map.of("type", List.of(type, "null"));
    }

    private static Map<String, Object> orderedMap(Object... keysAndValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            map.put((String) keysAndValues[i], keysAndValues[i + 1]);
        }
        return map;
    }
}
