package com.example.ClaimShieldX.history.csv;

import com.example.ClaimShieldX.history.model.ImportFailureCode;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.IOException;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads a CSV file strictly. The bytes must be valid UTF-8 (a leading byte-order mark is allowed and dropped), the text must
 * not contain NUL, quoting must be correct, and there must be at most {@code maxRows} data rows. Anything else makes the
 * whole file unusable. It reads columns by position only; naming and checking the columns is done against a template.
 */
public final class StrictCsvReader {
    public record Row(int number, List<String> cells) {
    }

    /** {@code header} holds the first record as written; row numbers start at 1 for the first data row. */
    public record Parsed(List<String> header, List<Row> rows) {
    }

    private static final CSVFormat FORMAT = CSVFormat.RFC4180.builder().setIgnoreEmptyLines(true).setTrim(false).build();

    private StrictCsvReader() {
    }

    public static Parsed read(byte[] bytes, int maxRows) throws ImportFileException {
        if (bytes.length == 0) {
            throw new ImportFileException(ImportFailureCode.EMPTY_FILE, "The file is empty");
        }
        String text = decode(bytes);
        if (text.isBlank()) {
            throw new ImportFileException(ImportFailureCode.EMPTY_FILE, "The file is empty");
        }

        List<String> header = null;
        List<Row> rows = new ArrayList<>();
        try (CSVParser parser = FORMAT.parse(new StringReader(text))) {
            for (CSVRecord record : parser) {
                List<String> cells = List.of(record.values());
                if (header == null) {
                    header = cells;
                    continue;
                }
                if (rows.size() >= maxRows) {
                    throw new ImportFileException(ImportFailureCode.TOO_MANY_ROWS, "The file has more than " + maxRows + " data rows");
                }
                rows.add(new Row(rows.size() + 1, cells));
            }
        } catch (IOException | RuntimeException e) {
            // A parsing error can quote the offending text, so nothing from it is kept.
            throw new ImportFileException(ImportFailureCode.INVALID_CSV, "The file is not valid CSV (check quotes and delimiters)");
        }
        if (header == null || rows.isEmpty()) {
            throw new ImportFileException(ImportFailureCode.EMPTY_FILE, "The file has no data rows");
        }
        return new Parsed(header, List.copyOf(rows));
    }

    private static String decode(byte[] bytes) throws ImportFileException {
        String text;
        try {
            text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException e) {
            throw new ImportFileException(ImportFailureCode.INVALID_ENCODING, "The file is not valid UTF-8");
        }
        if (text.startsWith("﻿")) {
            text = text.substring(1);
        }
        if (text.indexOf('\0') >= 0) {
            throw new ImportFileException(ImportFailureCode.INVALID_ENCODING, "The file contains NUL characters; it is not a text file");
        }
        return text;
    }
}
