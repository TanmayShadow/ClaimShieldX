package com.example.ClaimShieldX.documents.storage;

import com.example.ClaimShieldX.exceptions.StorageObjectNotFoundException;
import com.example.ClaimShieldX.exceptions.StorageUnavailableException;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.contrib.nio.testing.LocalStorageHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * IMPORTANT: these run against Google's in-memory fake of Cloud Storage, not a real bucket. They prove our
 * adapter uses the client correctly; they do NOT prove the deployed bucket, permissions or credentials work.
 */
class GcsDocumentStorageTests {
    private static final String BUCKET = "claimshield-test-bucket";

    private Storage fake;
    private GcsDocumentStorage storage;

    @BeforeEach
    void setUp() {
        // Buckets are implicit in the fake (it does not implement bucket creation).
        fake = LocalStorageHelper.getOptions().getService();
        storage = new GcsDocumentStorage(fake, BUCKET);
    }

    private static byte[] read(InputStream in) throws Exception {
        try (in) {
            return in.readAllBytes();
        }
    }

    @Test
    void storesAndReadsBackTheExactBytesWithTheContentType() throws Exception {
        byte[] content = {5, 4, 3, 2, 1, 0, 9, 8};

        storage.put("documents/org/claim/key1", new ByteArrayInputStream(content), content.length, "application/pdf");

        assertThat(read(storage.open("documents/org/claim/key1"))).isEqualTo(content);
        assertThat(fake.get(BUCKET, "documents/org/claim/key1").getContentType()).isEqualTo("application/pdf");
    }

    @Test
    void openingAMissingObjectIsNotFound() {
        assertThatThrownBy(() -> storage.open("documents/org/claim/never")).isInstanceOf(StorageObjectNotFoundException.class);
    }

    @Test
    void deletingRemovesTheObjectAndIsIdempotent() {
        storage.put("documents/org/claim/key2", new ByteArrayInputStream(new byte[]{1}), 1, "image/png");

        storage.delete("documents/org/claim/key2");
        storage.delete("documents/org/claim/key2");

        assertThat(fake.get(BUCKET, "documents/org/claim/key2")).isNull();
    }

    @Test
    void refusesToOverwriteAnExistingObjectAndKeepsTheOriginal() throws Exception {
        storage.put("documents/org/claim/key4", new ByteArrayInputStream(new byte[]{1, 1}), 2, "application/pdf");

        assertThatThrownBy(() -> storage.put("documents/org/claim/key4", new ByteArrayInputStream(new byte[]{9, 9}), 2, "application/pdf"))
                .isInstanceOf(StorageUnavailableException.class);

        assertThat(read(storage.open("documents/org/claim/key4"))).isEqualTo(new byte[]{1, 1});
    }

    @Test
    void objectsLandInTheConfiguredBucketOnly() {
        storage.put("documents/org/claim/key3", new ByteArrayInputStream(new byte[]{1, 2}), 2, "image/jpeg");

        assertThat(fake.get(BUCKET, "documents/org/claim/key3")).isNotNull();
        assertThat(fake.get("some-other-bucket", "documents/org/claim/key3")).isNull();
    }
}
