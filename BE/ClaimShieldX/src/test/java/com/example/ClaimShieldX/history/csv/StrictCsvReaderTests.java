package com.example.ClaimShieldX.history.csv;

import com.example.ClaimShieldX.history.model.ImportFailureCode;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StrictCsvReaderTests {
    private static StrictCsvReader.Parsed read(String text) throws ImportFileException {
        return StrictCsvReader.read(text.getBytes(StandardCharsets.UTF_8), 10_000);
    }

    private static void assertFails(byte[] bytes, int maxRows, ImportFailureCode code) {
        assertThatThrownBy(() -> StrictCsvReader.read(bytes, maxRows)).isInstanceOfSatisfying(ImportFileException.class, e -> assertThat(e.code()).isEqualTo(code));
    }

    private static void assertFails(String text, ImportFailureCode code) {
        assertFails(text.getBytes(StandardCharsets.UTF_8), 10_000, code);
    }

    @Test
    void readsAHeaderAndNumbersTheDataRowsFromOne() throws Exception {
        StrictCsvReader.Parsed parsed = read("a,b,c\r\n1,2,3\r\n4,5,6\r\n");

        assertThat(parsed.header()).containsExactly("a", "b", "c");
        assertThat(parsed.rows()).extracting(StrictCsvReader.Row::number).containsExactly(1, 2);
        assertThat(parsed.rows().get(1).cells()).containsExactly("4", "5", "6");
    }

    @Test
    void acceptsEveryLineEndingAndAMissingFinalNewline() throws Exception {
        for (String text : List.of("a,b\r\n1,2\r\n", "a,b\n1,2\n", "a,b\r1,2\r", "a,b\n1,2")) {
            StrictCsvReader.Parsed parsed = read(text);

            assertThat(parsed.rows()).as(text).hasSize(1);
            assertThat(parsed.rows().get(0).cells()).as(text).containsExactly("1", "2");
        }
    }

    @Test
    void handlesQuotedCommasQuotesAndLineBreaksInsideACell() throws Exception {
        StrictCsvReader.Parsed parsed = read("id,note\r\n1,\"front, bumper\"\r\n2,\"said \"\"ok\"\"\"\r\n3,\"line one\r\nline two\"\r\n");

        assertThat(parsed.rows()).hasSize(3);
        assertThat(parsed.rows().get(0).cells().get(1)).isEqualTo("front, bumper");
        assertThat(parsed.rows().get(1).cells().get(1)).isEqualTo("said \"ok\"");
        assertThat(parsed.rows().get(2).cells().get(1)).isEqualTo("line one\r\nline two");
        assertThat(parsed.rows().get(2).number()).isEqualTo(3);
    }

    @Test
    void dropsALeadingByteOrderMarkAndKeepsNonLatinText() throws Exception {
        byte[] body = "﻿id,note\r\n1,बम्पर बदला\r\n".getBytes(StandardCharsets.UTF_8);

        StrictCsvReader.Parsed parsed = StrictCsvReader.read(body, 10);

        assertThat(parsed.header()).containsExactly("id", "note");
        assertThat(parsed.rows().get(0).cells().get(1)).isEqualTo("बम्पर बदला");
    }

    @Test
    void ignoresBlankLinesAndKeepsEmptyCellsAsEmpty() throws Exception {
        StrictCsvReader.Parsed parsed = read("a,b,c\r\n\r\n1,,3\r\n\r\n,,\r\n");

        assertThat(parsed.rows()).hasSize(2);
        assertThat(parsed.rows().get(0).cells()).containsExactly("1", "", "3");
        assertThat(parsed.rows().get(1).cells()).containsExactly("", "", "");
    }

    @Test
    void doesNotCheckRowLengthsItselfBecauseThatIsARowErrorNotAFileError() throws Exception {
        StrictCsvReader.Parsed parsed = read("a,b,c\r\n1,2\r\n1,2,3,4\r\n");

        assertThat(parsed.rows().get(0).cells()).hasSize(2);
        assertThat(parsed.rows().get(1).cells()).hasSize(4);
    }

    @Test
    void anEmptyOrBlankOrHeaderOnlyFileIsEmpty() {
        assertFails(new byte[0], 10, ImportFailureCode.EMPTY_FILE);
        assertFails("   \r\n\t\r\n", ImportFailureCode.EMPTY_FILE);
        assertFails("﻿", ImportFailureCode.EMPTY_FILE);
        assertFails("a,b,c\r\n", ImportFailureCode.EMPTY_FILE);
        assertFails("a,b,c", ImportFailureCode.EMPTY_FILE);
        assertFails("a,b,c\r\n\r\n\r\n", ImportFailureCode.EMPTY_FILE);
    }

    @Test
    void bytesThatAreNotValidUtf8AreRefused() {
        assertFails(new byte[]{'a', ',', 'b', '\n', (byte) 0xC3, 0x28, '\n'}, 10, ImportFailureCode.INVALID_ENCODING);
        assertFails(new byte[]{(byte) 0xFF, (byte) 0xFE, 'a', 0, ',', 0, 'b', 0}, 10, ImportFailureCode.INVALID_ENCODING);
        assertFails(new byte[]{'a', ',', 'b', '\n', (byte) 0xE9, ',', '1'}, 10, ImportFailureCode.INVALID_ENCODING); // Latin-1 "e acute"
    }

    @Test
    void textWithNulAndBinaryFilesAreRefused() {
        assertFails("a,b\r\n1,2\u00003\r\n", ImportFailureCode.INVALID_ENCODING);
        assertFails(new byte[]{(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0, 0, 0, 13}, 10, ImportFailureCode.INVALID_ENCODING);
    }

    @Test
    void brokenQuotingMakesTheFileInvalidCsv() {
        assertFails("a,b\r\n1,\"unterminated\r\n2,3\r\n", ImportFailureCode.INVALID_CSV);
        assertFails("a,b\r\n1,\"ok\"trailing\r\n", ImportFailureCode.INVALID_CSV);
    }

    @Test
    void aQuoteInTheMiddleOfAnUnquotedCellIsKeptAsTextLikeMostExportersWriteIt() throws Exception {
        StrictCsvReader.Parsed parsed = read("a,b\r\n1,a 5\" screen\r\n");

        assertThat(parsed.rows().get(0).cells()).containsExactly("1", "a 5\" screen");
    }

    @Test
    void theRowLimitIsInclusiveAndOneMoreIsTooMany() throws Exception {
        StringBuilder text = new StringBuilder("a\r\n");
        for (int i = 0; i < 100; i++) {
            text.append(i).append("\r\n");
        }

        assertThat(StrictCsvReader.read(text.toString().getBytes(StandardCharsets.UTF_8), 100).rows()).hasSize(100);
        assertFails(text.toString().getBytes(StandardCharsets.UTF_8), 99, ImportFailureCode.TOO_MANY_ROWS);
    }

    @Test
    void failureDetailsNeverContainTextFromTheFile() {
        String secret = "SECRET-TOKEN-FROM-THE-FILE";

        for (String bad : List.of("a,b\r\n1,\"" + secret + "\r\n", "a,b\r\n1,\"" + secret + "\"trailing\r\n", secret + "\u0000,b\r\n1,2\r\n")) {
            assertThatThrownBy(() -> read(bad)).isInstanceOfSatisfying(ImportFileException.class, e -> {
                assertThat(e.getMessage()).doesNotContain(secret);
                assertThat(e.detail()).doesNotContain(secret);
            });
        }
    }

    @Test
    void aLargeSingleCellIsStillReadByTheReader() throws Exception {
        String big = "x".repeat(200_000);

        StrictCsvReader.Parsed parsed = read("a,b\r\n1," + big + "\r\n");

        assertThat(parsed.rows().get(0).cells().get(1)).hasSize(200_000);
    }

    @Test
    void theHeaderIsReturnedAsWrittenForTheTemplateCheck() throws Exception {
        StrictCsvReader.Parsed parsed = read(" Source_Record_ID , vehicle_token\r\n1,2\r\n");

        assertThat(parsed.header()).containsExactly(" Source_Record_ID ", " vehicle_token");
    }
}
