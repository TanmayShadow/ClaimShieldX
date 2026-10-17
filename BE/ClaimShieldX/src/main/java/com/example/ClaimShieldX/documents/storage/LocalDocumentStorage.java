package com.example.ClaimShieldX.documents.storage;

import com.example.ClaimShieldX.exceptions.StorageObjectNotFoundException;
import com.example.ClaimShieldX.exceptions.StorageUnavailableException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Development storage on the local disk, outside the repository by default. Not for deployment: Cloud Run's
 * disk is not durable, which is why the cloud setup uses {@link GcsDocumentStorage}.
 */
public class LocalDocumentStorage implements DocumentStorage {
    private static final Pattern SAFE_KEY = Pattern.compile("[A-Za-z0-9][A-Za-z0-9/_.-]*");

    private final Path root;

    public LocalDocumentStorage(Path root) {
        this.root = root.toAbsolutePath().normalize();
        try {
            createOwnerOnlyDirectories(this.root);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot create the local document storage directory " + this.root, e);
        }
    }

    @Override
    public void put(String objectKey, InputStream content, long sizeBytes, String contentType) {
        Path target = resolve(objectKey);
        Path temp = null;
        try {
            createOwnerOnlyDirectories(target.getParent());
            temp = target.resolveSibling(".upload-" + UUID.randomUUID());
            long copied = Files.copy(content, temp);
            if (copied != sizeBytes) {
                throw new IOException("Stored " + copied + " bytes but expected " + sizeBytes);
            }
            setOwnerOnly(temp);
            // No REPLACE_EXISTING: an existing object makes this fail instead of being overwritten.
            Files.move(temp, target);
            temp = null;
        } catch (IOException e) {
            throw new StorageUnavailableException("Could not write the document to local storage", e);
        } finally {
            if (temp != null) {
                try {
                    Files.deleteIfExists(temp);
                } catch (IOException ignored) {
                    // best effort: a leftover .upload-* file is never served
                }
            }
        }
    }

    @Override
    public InputStream open(String objectKey) {
        Path path = resolve(objectKey);
        try {
            return Files.newInputStream(path);
        } catch (NoSuchFileException e) {
            throw new StorageObjectNotFoundException("Document object not found");
        } catch (IOException e) {
            throw new StorageUnavailableException("Could not read the document from local storage", e);
        }
    }

    @Override
    public void delete(String objectKey) {
        try {
            Files.deleteIfExists(resolve(objectKey));
        } catch (IOException e) {
            throw new StorageUnavailableException("Could not delete the document from local storage", e);
        }
    }

    // Keys are server-generated, but a key that could leave the storage root is refused anyway.
    private Path resolve(String objectKey) {
        if (objectKey == null || !SAFE_KEY.matcher(objectKey).matches() || objectKey.contains("..") || objectKey.contains("//")) {
            throw new IllegalArgumentException("Unsafe storage key");
        }
        Path path = root.resolve(objectKey).normalize();
        if (!path.startsWith(root) || path.equals(root)) {
            throw new IllegalArgumentException("Unsafe storage key");
        }
        return path;
    }

    private void createOwnerOnlyDirectories(Path directory) throws IOException {
        Files.createDirectories(directory);
        for (Path p = directory; p != null && p.startsWith(root); p = p.getParent()) {
            setOwnerOnlyDirectory(p);
            if (p.equals(root)) {
                break;
            }
        }
    }

    private void setOwnerOnly(Path file) throws IOException {
        try {
            Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------"));
        } catch (UnsupportedOperationException ignored) {
            // not a POSIX file system
        }
    }

    private void setOwnerOnlyDirectory(Path directory) throws IOException {
        try {
            Files.setPosixFilePermissions(directory, PosixFilePermissions.fromString("rwx------"));
        } catch (UnsupportedOperationException ignored) {
            // not a POSIX file system
        }
    }
}
