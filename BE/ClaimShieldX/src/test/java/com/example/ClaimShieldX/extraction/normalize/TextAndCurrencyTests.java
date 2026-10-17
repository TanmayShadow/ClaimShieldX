package com.example.ClaimShieldX.extraction.normalize;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class TextAndCurrencyTests {
    @Test
    void cleaningCollapsesWhitespaceAndStripsInvisibleAndDirectionControlCharacters() {
        assertThat(TextCleaner.clean("  front \n bumper\t replaced  ", 100)).isEqualTo("front bumper replaced");
        assertThat(TextCleaner.clean("abc‮def​ghi\u0000jkl", 100)).isEqualTo("abc def ghi jkl");
        assertThat(TextCleaner.clean("⁦hidden⁩", 100)).isEqualTo("hidden");
    }

    @Test
    void cleaningCutsToTheLimitAndGivesNullForBlank() {
        assertThat(TextCleaner.clean("a".repeat(500), 10)).hasSize(10);
        assertThat(TextCleaner.clean("   \n\t", 10)).isNull();
        assertThat(TextCleaner.clean(null, 10)).isNull();
        assertThat(TextCleaner.clean("‮​", 10)).isNull();
    }

    @Test
    void cuttingNeverSplitsASurrogatePair() {
        String text = "ab" + "😀".repeat(10);

        String cut = TextCleaner.clean(text, 5);

        assertThat(cut).isNotNull().hasSizeLessThanOrEqualTo(5);
        for (int i = 0; i < cut.length(); i++) {
            char c = cut.charAt(i);
            if (Character.isHighSurrogate(c)) {
                assertThat(i + 1 < cut.length() && Character.isLowSurrogate(cut.charAt(i + 1))).as("high surrogate at " + i + " must be paired").isTrue();
            }
            if (Character.isLowSurrogate(c)) {
                assertThat(i > 0 && Character.isHighSurrogate(cut.charAt(i - 1))).as("low surrogate at " + i + " must be paired").isTrue();
            }
        }
    }

    @Test
    void comparingIdentifiersIgnoresWhitespaceAndInvisibleCharactersButNotCase() {
        assertThat(TextCleaner.compact("VEH - TOKEN​-1001")).isEqualTo("VEH-TOKEN-1001");
        assertThat(TextCleaner.compact("veh-token-1001")).isNotEqualTo(TextCleaner.compact("VEH-TOKEN-1001"));
        assertThat(TextCleaner.compact(null)).isNull();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            INR     | INR
            inr     | INR
            ₹       | INR
            Rs      | INR
            Rs.     | INR
            Rupees  | INR
            USD     | USD
            eur     | EUR
            """)
    void recognisesCurrencies(String text, String expected) {
        assertThat(CurrencyNormalizer.parse(text).code()).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            $        | AMBIGUOUS_SYMBOL
            XYZ      | NOT_RECOGNIZED
            rupee tk | NOT_RECOGNIZED
            """)
    void doesNotGuessAnUnclearCurrency(String text, String note) {
        CurrencyNormalizer.Parsed parsed = CurrencyNormalizer.parse(text);

        assertThat(parsed.code()).isNull();
        assertThat(parsed.note()).isEqualTo(note);
    }

    @Test
    void blankCurrencyGivesNothing() {
        assertThat(CurrencyNormalizer.parse(null).code()).isNull();
        assertThat(CurrencyNormalizer.parse("  ").code()).isNull();
    }
}
