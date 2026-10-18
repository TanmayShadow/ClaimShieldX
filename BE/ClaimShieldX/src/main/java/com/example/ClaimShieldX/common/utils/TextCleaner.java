package com.example.ClaimShieldX.common.utils;

/** Cleans text that came out of a document before it is stored or shown. The document is untrusted input. */
public final class TextCleaner {
    private TextCleaner() {
    }

    /**
     * Replaces control characters, invisible characters and text-direction overrides with spaces, collapses whitespace,
     * and cuts the text to {@code maxLength}. Returns null for blank input.
     */
    public static String clean(String raw, int maxLength) {
        if (raw == null) {
            return null;
        }
        StringBuilder out = new StringBuilder(Math.min(raw.length(), maxLength + 16));
        raw.codePoints().forEach(cp -> out.appendCodePoint(isUnsafe(cp) ? ' ' : cp));
        String cleaned = out.toString().replaceAll("\\s+", " ").strip();
        if (cleaned.isEmpty()) {
            return null;
        }
        if (cleaned.length() <= maxLength) {
            return cleaned;
        }
        int cut = maxLength;
        if (cut > 0 && Character.isHighSurrogate(cleaned.charAt(cut - 1))) {
            cut--; // never leave half of a character
        }
        return cleaned.substring(0, cut).strip();
    }

    /** For comparing identifiers: invisible characters and every kind of whitespace are ignored, case is not. */
    public static String compact(String raw) {
        if (raw == null) {
            return null;
        }
        StringBuilder out = new StringBuilder(raw.length());
        raw.codePoints().filter(cp -> !isUnsafe(cp) && !Character.isWhitespace(cp)).forEach(out::appendCodePoint);
        return out.toString();
    }

    private static boolean isUnsafe(int cp) {
        return Character.isISOControl(cp)
                || (cp >= 0x200B && cp <= 0x200F)
                || (cp >= 0x202A && cp <= 0x202E)
                || (cp >= 0x2066 && cp <= 0x2069)
                || cp == 0xFEFF || cp == 0x00AD;
    }
}
