package com.example.ClaimShieldX.documents.service;

import java.util.Optional;

/** File types accepted for upload, identified from the first bytes of the content, never from a name or declared type. */
public enum DetectedFileType {
    PDF("application/pdf", "pdf"),
    JPEG("image/jpeg", "jpg"),
    PNG("image/png", "png");

    private final String contentType;
    private final String extension;

    DetectedFileType(String contentType, String extension) {
        this.contentType = contentType;
        this.extension = extension;
    }

    public String contentType() {
        return contentType;
    }

    public String extension() {
        return extension;
    }

    public static Optional<DetectedFileType> detect(byte[] header) {
        if (startsWith(header, 0x25, 0x50, 0x44, 0x46, 0x2D)) {
            return Optional.of(PDF);
        }
        if (startsWith(header, 0xFF, 0xD8, 0xFF)) {
            return Optional.of(JPEG);
        }
        if (startsWith(header, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)) {
            return Optional.of(PNG);
        }
        return Optional.empty();
    }

    private static boolean startsWith(byte[] header, int... expected) {
        if (header.length < expected.length) {
            return false;
        }
        for (int i = 0; i < expected.length; i++) {
            if ((header[i] & 0xFF) != expected[i]) {
                return false;
            }
        }
        return true;
    }
}
