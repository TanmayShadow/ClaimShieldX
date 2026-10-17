package com.example.ClaimShieldX.extraction.normalize;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.ResolverStyle;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns the text of a date, as printed in a document, into a calendar date. Numeric dates are read day first, as is
 * usual on Indian documents, and the note says so when the reading is genuinely ambiguous (03/04/2026). Time and zone
 * are ignored: documents are compared by calendar date. Anything it cannot read with confidence gives no value.
 */
public final class DateParser {
    private static final int MIN_YEAR = 2000;
    private static final int MAX_YEAR = 2100;

    private static final Pattern ISO = Pattern.compile("(\\d{4})[-/.](\\d{1,2})[-/.](\\d{1,2})");
    private static final Pattern DAY_FIRST = Pattern.compile("(\\d{1,2})[-/.](\\d{1,2})[-/.](\\d{4}|\\d{2})");
    private static final Pattern TIME_TAIL = Pattern.compile("[ T]\\d{1,2}:\\d{2}.*$");
    private static final Pattern ORDINAL = Pattern.compile("(?<=\\d)(st|nd|rd|th)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern SEPT = Pattern.compile("\\bsept\\b", Pattern.CASE_INSENSITIVE);

    private static final List<DateTimeFormatter> TEXTUAL = List.of(
            formatter("d MMM uuuu"), formatter("d MMMM uuuu"), formatter("d-MMM-uuuu"), formatter("d-MMMM-uuuu"),
            formatter("MMM d uuuu"), formatter("MMMM d uuuu"), formatter("d MMM uu"), formatter("d-MMM-uu"));

    public record Parsed(LocalDate value, String note) {
        static Parsed unparseable() {
            return new Parsed(null, "NOT_PARSEABLE");
        }
    }

    private DateParser() {
    }

    private static DateTimeFormatter formatter(String pattern) {
        return new DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern(pattern)
                .toFormatter(Locale.ENGLISH).withResolverStyle(ResolverStyle.STRICT);
    }

    public static Parsed parse(String raw) {
        if (raw == null) {
            return Parsed.unparseable();
        }
        String text = raw.strip().replace(",", " ");
        text = TIME_TAIL.matcher(text).replaceAll("");
        text = ORDINAL.matcher(text).replaceAll("");
        text = SEPT.matcher(text).replaceAll("Sep").replaceAll("\\s+", " ").strip();
        if (text.isEmpty() || text.length() > 30) {
            return Parsed.unparseable();
        }

        Matcher iso = ISO.matcher(text);
        if (iso.matches()) {
            return build(Integer.parseInt(iso.group(1)), Integer.parseInt(iso.group(2)), Integer.parseInt(iso.group(3)), null);
        }
        Matcher dayFirst = DAY_FIRST.matcher(text);
        if (dayFirst.matches()) {
            int first = Integer.parseInt(dayFirst.group(1));
            int second = Integer.parseInt(dayFirst.group(2));
            String yearText = dayFirst.group(3);
            int year = yearText.length() == 2 ? 2000 + Integer.parseInt(yearText) : Integer.parseInt(yearText);
            String note = null;
            if (yearText.length() == 2) {
                note = "TWO_DIGIT_YEAR_ASSUMED_20XX";
            }
            if (first <= 12 && second <= 12 && first != second) {
                note = note == null ? "DAY_FIRST_ASSUMED" : note + ";DAY_FIRST_ASSUMED";
            }
            return build(year, second, first, note);
        }
        String spaced = text.replace('-', ' ');
        for (DateTimeFormatter formatter : TEXTUAL) {
            try {
                LocalDate date = LocalDate.parse(spaced, formatter);
                String note = spaced.matches(".*\\s\\d{2}$") ? "TWO_DIGIT_YEAR_ASSUMED_20XX" : null;
                return inRange(date) ? new Parsed(date, note) : Parsed.unparseable();
            } catch (DateTimeException ignored) {
                // try the next format
            }
        }
        return Parsed.unparseable();
    }

    private static Parsed build(int year, int month, int day, String note) {
        try {
            LocalDate date = LocalDate.of(year, month, day);
            return inRange(date) ? new Parsed(date, note) : Parsed.unparseable();
        } catch (DateTimeException e) {
            return Parsed.unparseable();
        }
    }

    private static boolean inRange(LocalDate date) {
        return date.getYear() >= MIN_YEAR && date.getYear() <= MAX_YEAR;
    }
}
