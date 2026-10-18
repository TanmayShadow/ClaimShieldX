package com.example.ClaimShieldX.analysis.reasoning;

import java.util.List;
import java.util.regex.Pattern;

/** Cleaning and screening of model-written text before it is stored or shown. */
public final class Text {
    private static final Pattern FORBIDDEN = Pattern.compile("(?i)\\b(fraud\\w*|scam\\w*|guilty|criminal\\w*|cheat\\w*|illegal\\w*|forg(?:e|ed|ery)\\w*|fabricat\\w*|counterfeit\\w*|dishonest\\w*|crook\\w*|liar|lying|swindl\\w*)\\b");

    private Text() {
    }

    /** Control characters out, whitespace collapsed, cut to {@code max}. Null stays null. */
    public static String clean(String text, int max) {
        if (text == null) {
            return null;
        }
        String cleaned = text.replaceAll("[\\p{Cntrl}&&[^\\n\\t]]", " ").replaceAll("\\s+", " ").strip();
        return cleaned.length() <= max ? cleaned : cleaned.substring(0, max).stripTrailing();
    }

    /** The model must not call anything fraud or accuse anyone. Such text is rejected, not edited. */
    public static boolean nonNeutral(String... texts) {
        for (String text : texts) {
            if (text != null && FORBIDDEN.matcher(text).find()) {
                return true;
            }
        }
        return false;
    }

    public static boolean nonNeutral(List<String> texts) {
        return texts.stream().anyMatch(t -> nonNeutral(t));
    }
}
