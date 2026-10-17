package com.example.ClaimShieldX.extraction.normalize;

import java.math.BigDecimal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns the text of a money amount or a quantity, as printed in an Indian motor-repair document, into a BigDecimal. It
 * understands Indian (1,23,456.50) and Western (123,456.50) grouping, rupee markers, parentheses and a leading minus
 * for negatives. It never guesses: text it cannot read with confidence gives no value.
 */
public final class AmountParser {
    /** Anything at or above this is treated as unreadable: no repair costs a trillion, and it keeps database columns safe. */
    private static final BigDecimal LIMIT = new BigDecimal("1000000000000");
    private static final int MAX_DECIMALS = 4;

    private static final Pattern CURRENCY_MARKERS = Pattern.compile("(?i)(₹|\\brs\\b\\.?|\\binr\\b|\\brupees?\\b|/-)");
    private static final Pattern GROUPED_THOUSANDS = Pattern.compile("\\d{1,3}(,\\d{2,3})*,\\d{3}");
    private static final Pattern PLAIN_GROUPED = Pattern.compile("\\d{1,3}(,\\d{2,3})*,\\d{3}|\\d+");
    private static final Pattern DOT_GROUPED = Pattern.compile("\\d{1,3}(\\.\\d{3})+");
    private static final Pattern DECIMAL_COMMA = Pattern.compile("\\d+,\\d{1,2}");
    private static final Pattern QUANTITY = Pattern.compile("\\s*([0-9][0-9,.]*)\\s*[A-Za-z./]{0,8}\\s*");

    public record Parsed(BigDecimal value, String note) {
        static Parsed of(BigDecimal value, String note) {
            return new Parsed(value, note);
        }

        static Parsed unparseable() {
            return new Parsed(null, "NOT_PARSEABLE");
        }
    }

    private AmountParser() {
    }

    public static Parsed parseMoney(String raw) {
        return parse(raw);
    }

    /** A quantity may carry a unit ("2 Nos", "1.5 ltr"); the unit is dropped. */
    public static Parsed parseQuantity(String raw) {
        if (raw == null) {
            return Parsed.unparseable();
        }
        String text = raw.strip();
        Matcher unit = QUANTITY.matcher(text);
        if (unit.matches()) {
            text = unit.group(1);
        }
        Parsed parsed = parse(text);
        if (parsed.value() != null && parsed.value().signum() < 0) {
            return Parsed.unparseable();
        }
        return parsed;
    }

    private static Parsed parse(String raw) {
        if (raw == null) {
            return Parsed.unparseable();
        }
        String text = raw.strip().replace('−', '-').replace(' ', ' ');
        boolean negative = false;
        if (text.startsWith("(") && text.endsWith(")")) {
            negative = true;
            text = text.substring(1, text.length() - 1);
        }
        text = CURRENCY_MARKERS.matcher(text).replaceAll(" ").strip();
        if (text.startsWith("-")) {
            negative = true;
            text = text.substring(1).strip();
        } else if (text.startsWith("+")) {
            text = text.substring(1).strip();
        }
        if (text.isEmpty() || text.length() > 24 || !text.matches("[0-9][0-9,.]*")) {
            return Parsed.unparseable();
        }

        String note = null;
        String plain;
        int lastComma = text.lastIndexOf(',');
        int lastDot = text.lastIndexOf('.');
        if (lastComma >= 0 && lastDot >= 0) {
            if (lastDot > lastComma) {
                String integer = text.substring(0, lastDot);
                if (!PLAIN_GROUPED.matcher(integer).matches()) {
                    return Parsed.unparseable();
                }
                plain = integer.replace(",", "") + text.substring(lastDot);
            } else {
                String integer = text.substring(0, lastComma);
                if (!DOT_GROUPED.matcher(integer).matches() && !integer.matches("\\d+")) {
                    return Parsed.unparseable();
                }
                plain = integer.replace(".", "") + "." + text.substring(lastComma + 1);
                note = "DECIMAL_COMMA_ASSUMED";
            }
        } else if (lastComma >= 0) {
            if (GROUPED_THOUSANDS.matcher(text).matches()) {
                plain = text.replace(",", "");
            } else if (DECIMAL_COMMA.matcher(text).matches()) {
                plain = text.replace(',', '.');
                note = "DECIMAL_COMMA_ASSUMED";
            } else {
                return Parsed.unparseable();
            }
        } else if (lastDot >= 0) {
            if (text.indexOf('.') != lastDot) {
                if (!DOT_GROUPED.matcher(text).matches()) {
                    return Parsed.unparseable();
                }
                plain = text.replace(".", "");
                note = "DOT_THOUSANDS_ASSUMED";
            } else {
                plain = text;
            }
        } else {
            plain = text;
        }

        BigDecimal value;
        try {
            value = new BigDecimal(plain);
        } catch (NumberFormatException e) {
            return Parsed.unparseable();
        }
        if (value.scale() > MAX_DECIMALS || value.compareTo(LIMIT) >= 0) {
            return Parsed.unparseable();
        }
        return Parsed.of(negative ? value.negate() : value, note);
    }
}
