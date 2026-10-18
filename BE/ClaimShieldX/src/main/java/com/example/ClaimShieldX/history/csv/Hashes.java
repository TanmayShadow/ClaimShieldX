package com.example.ClaimShieldX.history.csv;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

final class Hashes {
    private Hashes() {
    }

    /** SHA-256 over the parts, each prefixed by a marker and joined with a separator that cannot appear in them, so null and "" differ. */
    static String sha256(String... parts) {
        StringBuilder canonical = new StringBuilder();
        for (String part : parts) {
            canonical.append(part == null ? '\u0000' : '\u0001').append(part == null ? "" : part).append('\u001f');
        }
        return hex(canonical.toString().getBytes(StandardCharsets.UTF_8));
    }

    static String hex(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available", e);
        }
    }
}
