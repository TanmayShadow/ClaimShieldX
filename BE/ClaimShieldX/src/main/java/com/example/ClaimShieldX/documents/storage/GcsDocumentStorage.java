package com.example.ClaimShieldX.documents.storage;

import com.example.ClaimShieldX.exceptions.StorageObjectNotFoundException;
import com.example.ClaimShieldX.exceptions.StorageUnavailableException;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.Channels;

/**
 * Private Google Cloud Storage bucket. The bucket must have public access prevention enforced and grant the
 * runtime identity only object read, create and delete. Credentials come from Application Default Credentials
 * (locally) or the attached service account (Cloud Run); nothing secret is configured here.
 */
public class GcsDocumentStorage implements DocumentStorage {
    private final Storage storage;
    private final String bucket;

    public GcsDocumentStorage(Storage storage, String bucket) {
        this.storage = storage;
        this.bucket = bucket;
    }

    @Override
    public void put(String objectKey, InputStream content, long sizeBytes, String contentType) {
        BlobInfo blobInfo = BlobInfo.newBuilder(BlobId.of(bucket, objectKey)).setContentType(contentType).build();
        try {
            // doesNotExist: never overwrite an existing object.
            storage.createFrom(blobInfo, content, Storage.BlobWriteOption.doesNotExist());
        } catch (IOException | StorageException e) {
            throw new StorageUnavailableException("Could not write the document to Cloud Storage", e);
        }
    }

    @Override
    public InputStream open(String objectKey) {
        try {
            Blob blob = storage.get(BlobId.of(bucket, objectKey));
            if (blob == null) {
                throw new StorageObjectNotFoundException("Document object not found");
            }
            return Channels.newInputStream(blob.reader());
        } catch (StorageException e) {
            throw new StorageUnavailableException("Could not read the document from Cloud Storage", e);
        }
    }

    @Override
    public void delete(String objectKey) {
        try {
            storage.delete(BlobId.of(bucket, objectKey));
        } catch (StorageException e) {
            throw new StorageUnavailableException("Could not delete the document from Cloud Storage", e);
        }
    }
}
