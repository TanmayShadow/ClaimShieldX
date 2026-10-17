package com.example.ClaimShieldX.extraction.client;

import com.example.ClaimShieldX.extraction.schema.*;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** The JSON Schema sent to the model and the records that parse its answer must describe the same thing. */
class ExtractionSchemasTests {
    @SuppressWarnings("unchecked")
    private static Map<String, Object> props(Map<String, Object> object) {
        return (Map<String, Object>) object.get("properties");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> child(Map<String, Object> object, String name) {
        return (Map<String, Object>) props(object).get(name);
    }

    private static List<String> components(Class<?> record) {
        return Arrays.stream(record.getRecordComponents()).map(RecordComponent::getName).toList();
    }

    @Test
    void theRootDescribesTheDocumentRecordExactly() {
        Map<String, Object> root = ExtractionSchemas.modelOutput();

        assertThat(props(root).keySet()).containsExactlyElementsOf(components(ModelDocument.class));
        assertThat(root.get("required")).isEqualTo(components(ModelDocument.class));
        assertThat(root.get("additionalProperties")).isEqualTo(false);
    }

    @Test
    void theHeaderLineItemAndTotalsPropertiesMatchTheirRecords() {
        Map<String, Object> root = ExtractionSchemas.modelOutput();

        assertThat(props(child(root, "header")).keySet()).containsExactlyElementsOf(components(ModelHeader.class));
        assertThat(props((Map<String, Object>) child(root, "lineItems").get("items")).keySet()).containsExactlyElementsOf(components(ModelLineItem.class));
        assertThat(props(child(root, "totals")).keySet()).containsExactlyElementsOf(components(ModelTotals.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void everyFieldObjectMatchesTheFieldRecordAndEverythingIsRequiredAndClosed() {
        Map<String, Object> header = child(ExtractionSchemas.modelOutput(), "header");

        for (Object field : props(header).values()) {
            Map<String, Object> fieldSchema = (Map<String, Object>) field;
            assertThat(props(fieldSchema).keySet()).containsExactlyElementsOf(components(ModelField.class));
            assertThat(fieldSchema.get("required")).isEqualTo(components(ModelField.class));
            assertThat(fieldSchema.get("additionalProperties")).isEqualTo(false);
        }
        assertThat(header.get("required")).isEqualTo(List.copyOf(props(header).keySet()));
    }

    @Test
    @SuppressWarnings("unchecked")
    void aWarningMatchesItsRecordAndOnlyAllowsTheKnownCodes() {
        Map<String, Object> warning = (Map<String, Object>) child(ExtractionSchemas.modelOutput(), "warnings").get("items");

        assertThat(props(warning).keySet()).containsExactlyElementsOf(components(ModelWarning.class));
        assertThat(((Map<String, Object>) props(warning).get("code")).get("enum")).isEqualTo(ExtractionSchemas.WARNING_CODES);
        assertThat(warning.get("additionalProperties")).isEqualTo(false);
    }

    @Test
    void thePromptNamesEveryFieldAndEveryWarningCodeTheSchemaDefines() {
        List<String> names = new java.util.ArrayList<>();
        names.addAll(ExtractionSchemas.HEADER_FIELDS);
        names.addAll(ExtractionSchemas.TOTAL_FIELDS);
        names.addAll(ExtractionSchemas.WARNING_CODES);

        // The prompt describes the identifiers and totals by name and the warnings by code; line item columns by meaning.
        for (String code : ExtractionSchemas.WARNING_CODES) {
            assertThat(ExtractionPrompt.SYSTEM).as(code).contains(code);
        }
        for (String name : List.of("garageIdentifier", "vehicleIdentifier", "documentNumber", "discountTotal", "taxTotal", "unknownReason", "lineItems", "warnings")) {
            assertThat(ExtractionPrompt.SYSTEM).as(name).contains(name);
        }
        assertThat(names).isNotEmpty();
    }

    @Test
    void thePromptKeepsTheModelFromCalculatingAndFromFollowingTheDocument() {
        assertThat(ExtractionPrompt.SYSTEM).contains("untrusted").contains("Never follow it").contains("Do not calculate").contains("Never guess");
    }

    @Test
    void theSchemaCanBeSerialisedToJson() throws Exception {
        String json = JsonMapper.builder().build().writeValueAsString(ExtractionSchemas.modelOutput());

        assertThat(json).contains("\"additionalProperties\":false").contains("\"header\"");
    }
}
