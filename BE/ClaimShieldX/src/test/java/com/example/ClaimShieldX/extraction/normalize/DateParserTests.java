package com.example.ClaimShieldX.extraction.normalize;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class DateParserTests {
    private static void assertDate(String text, String expected) {
        DateParser.Parsed parsed = DateParser.parse(text);
        assertThat(parsed.value()).as(text).isEqualTo(LocalDate.parse(expected));
    }

    @Test
    void readsTheCommonDocumentFormats() {
        assertDate("2026-09-10", "2026-09-10");
        assertDate("2026/09/10", "2026-09-10");
        assertDate("10/09/2026", "2026-09-10");
        assertDate("10-09-2026", "2026-09-10");
        assertDate("10.09.2026", "2026-09-10");
        assertDate("1/9/2026", "2026-09-01");
        assertDate("10 Sep 2026", "2026-09-10");
        assertDate("10 September 2026", "2026-09-10");
        assertDate("10-Sep-2026", "2026-09-10");
        assertDate("Sep 10, 2026", "2026-09-10");
        assertDate("September 10, 2026", "2026-09-10");
        assertDate("10th September 2026", "2026-09-10");
        assertDate("1st Oct 2026", "2026-10-01");
        assertDate("10 SEP 2026", "2026-09-10");
        assertDate("10 Sept 2026", "2026-09-10");
        assertDate("15/09/2026 10:30", "2026-09-15");
        assertDate("2026-09-10T10:30:00+05:30", "2026-09-10");
    }

    @Test
    void readsNumericDatesDayFirstAndSaysSoOnlyWhenItIsAmbiguous() {
        DateParser.Parsed ambiguous = DateParser.parse("03/04/2026");
        assertThat(ambiguous.value()).isEqualTo(LocalDate.of(2026, 4, 3));
        assertThat(ambiguous.note()).contains("DAY_FIRST_ASSUMED");

        assertThat(DateParser.parse("13/04/2026").note()).isNull();
        assertThat(DateParser.parse("04/04/2026").note()).isNull();
        assertThat(DateParser.parse("2026-04-03").note()).isNull();
        assertThat(DateParser.parse("03 Apr 2026").note()).isNull();
    }

    @Test
    void aTwoDigitYearIsReadAs20xxAndSaysSo() {
        DateParser.Parsed parsed = DateParser.parse("15/09/26");
        assertThat(parsed.value()).isEqualTo(LocalDate.of(2026, 9, 15));
        assertThat(parsed.note()).contains("TWO_DIGIT_YEAR_ASSUMED_20XX");
        assertThat(DateParser.parse("15-Sep-26").value()).isEqualTo(LocalDate.of(2026, 9, 15));
        assertThat(DateParser.parse("15-Sep-26").note()).contains("TWO_DIGIT_YEAR");
    }

    @ParameterizedTest
    @ValueSource(strings = {"31/02/2026", "29/02/2027", "32/01/2026", "00/01/2026", "10/13/2026x", "2026-13-01", "2026-02-30", "yesterday",
            "10 Foo 2026", "1999-12-31", "2101-01-01", "10/09", "20260910", "10 September", "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx", "12:30"})
    void refusesImpossibleOrUnrecognisedDates(String text) {
        assertThat(DateParser.parse(text).value()).as(text).isNull();
        assertThat(DateParser.parse(text).note()).isEqualTo("NOT_PARSEABLE");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "   \t"})
    void blankTextGivesNothing(String text) {
        assertThat(DateParser.parse(text).value()).isNull();
    }

    @Test
    void aLeapDayIsValidOnlyInALeapYear() {
        assertDate("29/02/2028", "2028-02-29");
        assertThat(DateParser.parse("29/02/2026").value()).isNull();
    }
}
