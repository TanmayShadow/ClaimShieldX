package com.example.ClaimShieldX.documents.storage;

import com.example.ClaimShieldX.exceptions.StorageObjectNotFoundException;
import com.example.ClaimShieldX.exceptions.StorageUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalDocumentStorageTests {
    @TempDir Path root;

    private LocalDocumentStorage storage;

    @BeforeEach
    void setUp() {
        storage = new LocalDocumentStorage(root.resolve("store"));
    }

    private static InputStream stream(byte[] bytes) {
        return new ByteArrayInputStream(bytes);
    }

    private static byte[] read(InputStream in) throws Exception {
        try (in) {
            return in.readAllBytes();
        }
    }

    @Test
    void storesAndReadsBackTheExactBytes() throws Exception {
        byte[] content = {1, 2, 3, 4, 5, 6, 7, 8, 9};

        storage.put("documents/org/claim/abc", stream(content), content.length, "application/pdf");

        assertThat(read(storage.open("documents/org/claim/abc"))).isEqualTo(content);
    }

    @Test
    void refusesToOverwriteAnExistingObjectAndKeepsTheOriginal() throws Exception {
        storage.put("documents/a/b/key1", stream(new byte[]{1, 1, 1}), 3, "application/pdf");

        assertThatThrownBy(() -> storage.put("documents/a/b/key1", stream(new byte[]{9, 9, 9}), 3, "application/pdf"))
                .isInstanceOf(StorageUnavailableException.class);

        assertThat(read(storage.open("documents/a/b/key1"))).isEqualTo(new byte[]{1, 1, 1});
    }

    @Test
    void aSizeMismatchFailsAndLeavesNothingBehind() throws Exception {
        assertThatThrownBy(() -> storage.put("documents/a/b/key2", stream(new byte[]{1, 2, 3}), 10, "application/pdf"))
                .isInstanceOf(StorageUnavailableException.class);

        assertThatThrownBy(() -> storage.open("documents/a/b/key2")).isInstanceOf(StorageObjectNotFoundException.class);
        try (Stream<Path> files = Files.walk(root.resolve("store"))) {
            assertThat(files.filter(Files::isRegularFile)).as("no partial or temporary file may remain").isEmpty();
        }
    }

    @Test
    void openingAMissingObjectIsNotFound() {
        assertThatThrownBy(() -> storage.open("documents/a/b/never")).isInstanceOf(StorageObjectNotFoundException.class);
    }

    @Test
    void deletingIsIdempotent() throws Exception {
        storage.put("documents/a/b/key3", stream(new byte[]{1}), 1, "application/pdf");

        storage.delete("documents/a/b/key3");
        storage.delete("documents/a/b/key3");
        storage.delete("documents/a/b/never-existed");

        assertThatThrownBy(() -> storage.open("documents/a/b/key3")).isInstanceOf(StorageObjectNotFoundException.class);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"../escape", "a/../../escape", "/etc/passwd", "a\\b", ".hidden", "a//b", "..", "a/..", "key with space", "caf\u00e9"})
    void keysThatCouldLeaveTheStorageFolderAreRefused(String key) {
        assertThatThrownBy(() -> storage.put(key, stream(new byte[]{1}), 1, "application/pdf")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> storage.open(key)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> storage.delete(key)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nothingIsEverWrittenOutsideTheStorageFolder() throws Exception {
        for (String key : new String[]{"../outside", "a/../../outside"}) {
            try {
                storage.put(key, stream(new byte[]{1}), 1, "application/pdf");
            } catch (IllegalArgumentException expected) {
                // refused
            }
        }
        try (Stream<Path> files = Files.walk(root)) {
            assertThat(files.filter(Files::isRegularFile)).isEmpty();
        }
    }

    @Test
    void filesAndFoldersAreReadableByTheOwnerOnly() throws Exception {
        storage.put("documents/org/claim/perm", stream(new byte[]{1}), 1, "application/pdf");
        Path file = root.resolve("store/documents/org/claim/perm");
        if (!Files.getFileStore(file).supportsFileAttributeView("posix")) {
            return;
        }

        assertThat(Files.getPosixFilePermissions(file)).containsExactlyInAnyOrder(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);
        assertThat(Files.getPosixFilePermissions(file.getParent())).isSubsetOf(Set.of(
                PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE));
    }

    @Test
    void keysAreNeverInterpretedAsAbsolutePaths() {
        assertThatThrownBy(() -> storage.open(root.resolve("store/documents").toString())).isInstanceOf(IllegalArgumentException.class);
    }
}
