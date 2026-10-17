package com.example.ClaimShieldX.documents.service;

/**
 * Produces a display name that is safe to store and show. The result is never used as a storage key or a
 * path: it removes directory parts, control and invisible characters (including text-direction overrides),
 * and bounds the length.
 */
final class FileNameSanitizer {
    private static final int MAX_LENGTH = 255;

    private FileNameSanitizer() {
    }

    static String sanitize(String original, DetectedFileType type) {
        String name = original == null ? "" : original.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1);
        name = name.replaceAll("[\\p{Cntrl}\\p{Cf}]", "").replaceAll("\\s+", " ").strip();
        name = name.replaceAll("^\\.+", "").strip();
        if (name.isEmpty()) {
            return "document." + type.extension();
        }
        if (name.codePointCount(0, name.length()) > MAX_LENGTH) {
            int end = name.offsetByCodePoints(0, MAX_LENGTH);
            name = name.substring(0, end);
        }
        return name;
    }
}
