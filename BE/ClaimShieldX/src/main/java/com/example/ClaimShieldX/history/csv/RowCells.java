package com.example.ClaimShieldX.history.csv;

import com.example.ClaimShieldX.common.utils.TextCleaner;
import com.example.ClaimShieldX.history.model.RowError;
import com.example.ClaimShieldX.history.model.RowErrorCode;

import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.regex.Pattern;

/**
 * The cells of one row, read by column name with strict, documented formats. Every problem is collected as a row error that
 * names the column and the kind of problem but never repeats the cell's text, so a bad row cannot leak its content into
 * results or logs. Nothing here guesses: a value is either exactly in the documented format or it is an error.
 */
final class RowCells {
    /** A point in time, and whether the file gave a time of day or only a date. */
    record TimePoint(Instant instant, String precision) {
        static final String DATE = "DATE";
        static final String DATETIME = "DATETIME";

        LocalDate dateIn(ZoneId zone) {
            return instant.atZone(zone).toLocalDate();
        }
    }

    private static final Pattern MONEY = Pattern.compile("\\d{1,13}(\\.\\d{1,2})?");
    private static final Pattern WHOLE = Pattern.compile("\\d{1,9}");
    private static final Pattern ISO_DATE = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");
    private static final Pattern LABEL = Pattern.compile("[A-Z][A-Z0-9_]{0,39}");
    private static final Pattern CODE = Pattern.compile("[A-Z0-9_]{1,40}");
    private static final Pattern CURRENCY = Pattern.compile("[A-Z]{3}");
    private static final int MIN_YEAR = 1990;
    private static final int MAX_YEAR = 2100;
    static final int MAX_CODES = 20;

    private final Map<String, String> values = new HashMap<>();
    private final List<RowError> errors = new ArrayList<>();
    private final ZoneId zone;

    RowCells(List<String> header, List<String> cells, ZoneId zone) {
        for (int i = 0; i < header.size(); i++) {
            values.put(header.get(i), cells.get(i));
        }
        this.zone = zone;
    }

    List<RowError> errors() {
        return errors;
    }

    boolean hasErrors() {
        return !errors.isEmpty();
    }

    void error(String field, RowErrorCode code, String message) {
        errors.add(new RowError(field, code, message));
    }

    /** The trimmed cell, or null when it is empty or the column is absent from the file. */
    private String raw(String column) {
        String value = values.get(column);
        if (value == null) {
            return null;
        }
        String trimmed = value.strip();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private boolean missing(String column, boolean required) {
        if (raw(column) != null) {
            return false;
        }
        if (required) {
            error(column, RowErrorCode.MISSING_REQUIRED, "is required");
        }
        return true;
    }

    /**
     * An opaque identifier: kept exactly as written after trimming (matching is case-sensitive). Control and invisible
     * characters are refused instead of being cleaned, because cleaning would change what the token matches.
     */
    String token(String column, boolean required, int maxLength) {
        if (missing(column, required)) {
            return null;
        }
        String value = raw(column);
        if (value.length() > maxLength) {
            error(column, RowErrorCode.TOO_LONG, "cannot exceed " + maxLength + " characters");
            return null;
        }
        if (value.codePoints().anyMatch(cp -> Character.isISOControl(cp) || (cp >= 0x200B && cp <= 0x200F) || (cp >= 0x202A && cp <= 0x202E)
                || (cp >= 0x2066 && cp <= 0x2069) || cp == 0xFEFF)) {
            error(column, RowErrorCode.INVALID_TOKEN, "contains control or invisible characters");
            return null;
        }
        return value;
    }

    /** A label such as a status: letters, digits and underscore, starting with a letter; capitals are applied. */
    String label(String column, boolean required) {
        if (missing(column, required)) {
            return null;
        }
        String value = raw(column).toUpperCase(Locale.ROOT);
        if (!LABEL.matcher(value).matches()) {
            error(column, RowErrorCode.INVALID_TOKEN, "must be 1 to 40 letters, digits or underscores, starting with a letter");
            return null;
        }
        return value;
    }

    String oneOf(String column, boolean required, Set<String> allowed) {
        if (missing(column, required)) {
            return null;
        }
        String value = raw(column).toUpperCase(Locale.ROOT);
        if (!allowed.contains(value)) {
            error(column, RowErrorCode.INVALID_VALUE, "must be one of " + String.join(", ", new TreeSet<>(allowed)));
            return null;
        }
        return value;
    }

    String currency(String column, boolean required) {
        if (missing(column, required)) {
            return null;
        }
        String value = raw(column);
        if (!CURRENCY.matcher(value).matches()) {
            error(column, RowErrorCode.INVALID_VALUE, "must be a three-letter currency code in capitals, for example INR");
            return null;
        }
        return value;
    }

    /** Free text: control and direction characters are replaced, whitespace is collapsed, and it is cut to the limit. */
    String text(String column, int maxLength) {
        String value = values.get(column);
        if (value == null || value.isBlank()) {
            return null;
        }
        if (value.strip().length() > maxLength * 4) {
            error(column, RowErrorCode.TOO_LONG, "cannot exceed " + maxLength + " characters");
            return null;
        }
        String cleaned = TextCleaner.clean(value, maxLength + 1);
        if (cleaned != null && cleaned.length() > maxLength) {
            error(column, RowErrorCode.TOO_LONG, "cannot exceed " + maxLength + " characters");
            return null;
        }
        return cleaned;
    }

    /** A non-negative amount with at most two decimals, written plainly: 38500.00 or 38500. */
    BigDecimal money(String column, boolean required) {
        if (missing(column, required)) {
            return null;
        }
        String value = raw(column);
        if (!MONEY.matcher(value).matches()) {
            error(column, RowErrorCode.INVALID_NUMBER, "must be a plain non-negative decimal with at most 2 decimals (no grouping or currency symbol)");
            return null;
        }
        return new BigDecimal(value).setScale(2, java.math.RoundingMode.UNNECESSARY);
    }

    Integer whole(String column, boolean required, int min) {
        if (missing(column, required)) {
            return null;
        }
        String value = raw(column);
        if (!WHOLE.matcher(value).matches()) {
            error(column, RowErrorCode.INVALID_NUMBER, "must be a whole number from " + min);
            return null;
        }
        int number = Integer.parseInt(value);
        if (number < min) {
            error(column, RowErrorCode.INVALID_NUMBER, "must be a whole number from " + min);
            return null;
        }
        return number;
    }

    /** An ISO-8601 date-time with an offset, or an ISO date. A date alone means the start of that day in the configured zone. */
    TimePoint dateTime(String column, boolean required) {
        if (missing(column, required)) {
            return null;
        }
        String value = raw(column);
        try {
            if (ISO_DATE.matcher(value).matches()) {
                LocalDate date = LocalDate.parse(value);
                return inRange(date) ? new TimePoint(date.atStartOfDay(zone).toInstant(), TimePoint.DATE) : badDate(column);
            }
            OffsetDateTime moment = OffsetDateTime.parse(value);
            return inRange(moment.toLocalDate()) ? new TimePoint(moment.toInstant(), TimePoint.DATETIME) : badDate(column);
        } catch (DateTimeException e) {
            return badDate(column);
        }
    }

    LocalDate date(String column, boolean required) {
        if (missing(column, required)) {
            return null;
        }
        String value = raw(column);
        try {
            if (ISO_DATE.matcher(value).matches()) {
                LocalDate date = LocalDate.parse(value);
                if (inRange(date)) {
                    return date;
                }
            }
        } catch (DateTimeException ignored) {
            // falls through to the error below
        }
        error(column, RowErrorCode.INVALID_DATE, "must be an ISO date such as 2025-03-14");
        return null;
    }

    /** Up to {@value #MAX_CODES} codes separated by a vertical bar; duplicates are dropped, the first order is kept. */
    List<String> codes(String column) {
        if (raw(column) == null) {
            return List.of();
        }
        LinkedHashSet<String> codes = new LinkedHashSet<>();
        for (String part : raw(column).split("\\|", -1)) {
            String code = part.strip().toUpperCase(Locale.ROOT);
            if (!CODE.matcher(code).matches()) {
                error(column, RowErrorCode.INVALID_TOKEN, "must be codes of 1 to 40 capital letters, digits or underscores separated by |");
                return List.of();
            }
            codes.add(code);
        }
        if (codes.size() > MAX_CODES) {
            error(column, RowErrorCode.TOO_LONG, "cannot hold more than " + MAX_CODES + " codes");
            return List.of();
        }
        return List.copyOf(codes);
    }

    private TimePoint badDate(String column) {
        error(column, RowErrorCode.INVALID_DATE, "must be an ISO-8601 date-time with an offset (2025-03-14T11:00:00+05:30) or a date (2025-03-14)");
        return null;
    }

    private static boolean inRange(LocalDate date) {
        return date.getYear() >= MIN_YEAR && date.getYear() <= MAX_YEAR;
    }
}
