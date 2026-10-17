package com.example.ClaimShieldX.extraction.client;

import com.example.ClaimShieldX.extraction.schema.ModelDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static com.example.ClaimShieldX.extraction.support.TestDocuments.invoice;
import static com.example.ClaimShieldX.extraction.support.TestDocuments.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ModelOutputParserTests {
    private final ModelDocument valid = invoice("G-1", "V-1", "12/09/2026");

    @Test
    void readsAnAnswerThatMatchesTheSchema() throws Exception {
        ModelDocument parsed = ModelOutputParser.parse(json(valid));

        assertThat(parsed).isEqualTo(valid);
    }

    @Test
    void rejectsAnUnknownPropertyAtAnyLevel() {
        String valid = json(this.valid);

        assertThatThrownBy(() -> ModelOutputParser.parse(valid.replaceFirst("\\{", "{\"verdict\":\"fraud\","))).isInstanceOf(ModelOutputParser.MalformedOutputException.class);
        assertThatThrownBy(() -> ModelOutputParser.parse(valid.replace("\"unknownReason\":null", "\"unknownReason\":null,\"confidence\":0.99")))
                .isInstanceOf(ModelOutputParser.MalformedOutputException.class);
    }

    @Test
    void rejectsAWrongType() {
        assertThatThrownBy(() -> ModelOutputParser.parse(json(valid).replace("\"page\":1", "\"page\":\"first\"")))
                .isInstanceOf(ModelOutputParser.MalformedOutputException.class);
        assertThatThrownBy(() -> ModelOutputParser.parse(json(valid).replace("\"lineItems\":[", "\"lineItems\":{\"x\":[")))
                .isInstanceOf(ModelOutputParser.MalformedOutputException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "null", "[]", "42", "\"text\"", "not json", "{", "{\"header\":", "```json\n{}\n```", "{} {}", "{} trailing",
            "{\"header\": {} // comment\n}"})
    void rejectsAnythingThatIsNotExactlyOneJsonObject(String text) {
        assertThatThrownBy(() -> ModelOutputParser.parse(text)).isInstanceOf(ModelOutputParser.MalformedOutputException.class);
    }

    @Test
    void acceptsAnEmptyObjectBecauseTheProcessorTreatsMissingPartsAsUnknown() throws Exception {
        assertThat(ModelOutputParser.parse("{}")).isEqualTo(new ModelDocument(null, null, null, null));
    }

    @Test
    void rejectsExtremeNestingAndOversizedStrings() {
        String deep = "{\"header\":" + "{\"a\":".repeat(30) + "1" + "}".repeat(30) + "}";
        String huge = "{\"header\":{\"documentNumber\":{\"value\":\"" + "x".repeat(300_000) + "\"}}}";

        assertThatThrownBy(() -> ModelOutputParser.parse(deep)).isInstanceOf(ModelOutputParser.MalformedOutputException.class);
        assertThatThrownBy(() -> ModelOutputParser.parse(huge)).isInstanceOf(ModelOutputParser.MalformedOutputException.class);
    }

    @Test
    void theErrorNeverEchoesTheModelText() {
        String secret = "SECRET-TEXT-FROM-THE-DOCUMENT";

        assertThatThrownBy(() -> ModelOutputParser.parse("{\"header\":{\"documentNumber\":{\"value\":\"" + secret + "\",\"surprise\":1}}}"))
                .isInstanceOf(ModelOutputParser.MalformedOutputException.class)
                .satisfies(e -> {
                    assertThat(e.getMessage()).doesNotContain(secret);
                });
    }
}
