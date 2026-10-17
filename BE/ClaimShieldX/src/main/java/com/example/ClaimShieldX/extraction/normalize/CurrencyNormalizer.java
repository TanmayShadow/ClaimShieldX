package com.example.ClaimShieldX.extraction.normalize;

import java.util.Currency;
import java.util.Locale;

/** Recognises the currency a document states, as an ISO code. The dollar sign alone is ambiguous and gives no value. */
public final class CurrencyNormalizer {
    public record Parsed(String code, String note) {
    }

    private CurrencyNormalizer() {
    }

    public static Parsed parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return new Parsed(null, "NOT_PARSEABLE");
        }
        String text = raw.strip().toUpperCase(Locale.ROOT).replace(".", "").replace(" ", "");
        if (text.equals("₹") || text.equals("RS") || text.equals("RUPEE") || text.equals("RUPEES") || text.equals("INR")) {
            return new Parsed("INR", null);
        }
        if (text.matches("[A-Z]{3}")) {
            try {
                return new Parsed(Currency.getInstance(text).getCurrencyCode(), null);
            } catch (IllegalArgumentException e) {
                return new Parsed(null, "NOT_RECOGNIZED");
            }
        }
        return new Parsed(null, text.equals("$") ? "AMBIGUOUS_SYMBOL" : "NOT_RECOGNIZED");
    }
}
