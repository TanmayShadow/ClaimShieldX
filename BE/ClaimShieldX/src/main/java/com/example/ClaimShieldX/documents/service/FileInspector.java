package com.example.ClaimShieldX.documents.service;

import com.example.ClaimShieldX.exceptions.EmptyFileException;
import com.example.ClaimShieldX.exceptions.RequestValidationException;
import com.example.ClaimShieldX.exceptions.UnsupportedFileTypeException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Reads an upload once, as a stream, to learn its checksum, size and real type. Nothing is held in memory but a small buffer. */
final class FileInspector {
    record Inspection(String sha256, long sizeBytes, DetectedFileType type) {
    }

    private FileInspector() {
    }

    static Inspection inspect(MultipartFile file) {
        try (InputStream raw = file.getInputStream()) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (DigestInputStream in = new DigestInputStream(raw, digest)) {
                byte[] header = in.readNBytes(16);
                long size = header.length;
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    size += read;
                }
                if (size == 0) {
                    throw new EmptyFileException();
                }
                DetectedFileType type = DetectedFileType.detect(header).orElseThrow(UnsupportedFileTypeException::new);
                return new Inspection(HexFormat.of().formatHex(digest.digest()), size, type);
            }
        } catch (IOException e) {
            throw RequestValidationException.of("file", "could not be read");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by the Java platform", e);
        }
    }
}
