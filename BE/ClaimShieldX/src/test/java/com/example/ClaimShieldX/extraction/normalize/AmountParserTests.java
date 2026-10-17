package com.example.ClaimShieldX.extraction.normalize;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class AmountParserTests {
    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            45000       | 45000
            45000.50    | 45000.50
            45,000.50   | 45000.50
            1,23,456.50 | 123456.50
            12,34,567   | 1234567
            1,234,567.8 | 1234567.8
            0.50        | 0.50
            .50x        | NOPE
            ₹ 1,23,456  | 123456
            Rs. 4,500   | 4500
            Rs 4500.00  | 4500.00
            INR 4,500/- | 4500
            4,500/-     | 4500
            (1,200.00)  | -1200.00
            -500        | -500
            −500        | -500
            +500        | 500
            007         | 7
            """)
    void readsIndianAndWesternMoney(String text, String expected) {
        AmountParser.Parsed parsed = AmountParser.parseMoney(text.strip());

        if (expected.equals("NOPE")) {
            assertThat(parsed.value()).isNull();
        } else {
            assertThat(parsed.value()).isEqualByComparingTo(new BigDecimal(expected));
        }
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            12,50       | 12.50
            1,23        | 1.23
            150,5       | 150.5
            1.234,56    | 1234.56
            1.234.567   | 1234567
            """)
    void readsDecimalCommasAndDotThousandsAndSaysItAssumed(String text, String expected) {
        AmountParser.Parsed parsed = AmountParser.parseMoney(text);

        assertThat(parsed.value()).isEqualByComparingTo(new BigDecimal(expected));
        assertThat(parsed.note()).isNotBlank();
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "12O0", "1 234", "1,2,3", "1,234,56x", "12,3456", "1..2", "1.2.3", "--5", "5-", "1e5", "0x10",
            "1,234,5", "99,99,99,99,999,999,999", "1.23456", "1000000000000", "٣٤٥", "NaN", "Infinity", "Rs.", "₹", "-"})
    void refusesWhatItCannotReadWithConfidence(String text) {
        assertThat(AmountParser.parseMoney(text).value()).as(text).isNull();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void blankOrMissingTextGivesNothing(String text) {
        assertThat(AmountParser.parseMoney(text).value()).isNull();
        assertThat(AmountParser.parseQuantity(text).value()).isNull();
    }

    @Test
    void unparseableTextIsMarkedAsSuch() {
        assertThat(AmountParser.parseMoney("twelve").note()).isEqualTo("NOT_PARSEABLE");
    }

    @Test
    void aPlainAmountCarriesNoNote() {
        assertThat(AmountParser.parseMoney("45,000.50").note()).isNull();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            2        | 2
            1.5      | 1.5
            2 Nos    | 2
            4 pcs    | 4
            1.5 ltr  | 1.5
            3 hrs    | 3
            1,000    | 1000
            """)
    void aQuantityMayCarryAUnit(String text, String expected) {
        assertThat(AmountParser.parseQuantity(text).value()).isEqualByComparingTo(new BigDecimal(expected));
    }

    @ParameterizedTest
    @ValueSource(strings = {"-2", "(2)", "many", "two pieces"})
    void aQuantityIsNeverNegativeOrWords(String text) {
        assertThat(AmountParser.parseQuantity(text).value()).isNull();
    }

    @Test
    void theLimitIsExclusiveAndGuardsAgainstAbsurdValues() {
        assertThat(AmountParser.parseMoney("999999999999.99").value()).isNotNull();
        assertThat(AmountParser.parseMoney("1000000000000").value()).isNull();
        assertThat(AmountParser.parseMoney("9".repeat(30)).value()).isNull();
    }

    @Test
    void keepsUpToFourDecimalsButNoMore() {
        assertThat(AmountParser.parseMoney("1.2345").value()).isEqualByComparingTo("1.2345");
        assertThat(AmountParser.parseMoney("1.23456").value()).isNull();
    }
}
