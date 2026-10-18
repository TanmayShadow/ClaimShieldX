package com.example.ClaimShieldX.rules.engine;

import java.util.Locale;

/**
 * The comparable form of an invoice number: ASCII letters and digits only, lower-case, so "INV/2026/0042", "inv-2026-0042" and
 * "INV 2026 0042" are the same number. Leading zeros are kept ("0042" is not "42"). The database query that finds candidate
 * invoices uses the same definition, so the two cannot disagree.
 */
public final class InvoiceKey {
    private InvoiceKey() {
    }

    /** Null when nothing comparable is left (an empty or non-ASCII number), in which case the invoice cannot be compared. */
    public static String of(String invoiceNumber) {
        if (invoiceNumber == null) {
            return null;
        }
        String key = invoiceNumber.replaceAll("[^A-Za-z0-9]", "").toLowerCase(Locale.ROOT);
        return key.isEmpty() ? null : key;
    }
}
