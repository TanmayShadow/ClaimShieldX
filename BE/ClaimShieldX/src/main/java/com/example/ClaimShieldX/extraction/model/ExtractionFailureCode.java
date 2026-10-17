package com.example.ClaimShieldX.extraction.model;

/**
 * Why an extraction attempt produced no result. Stored with the attempt so a failure can be understood later. Only the
 * transient kinds (rate limit, unavailable, unusable output) are retried automatically, and only a few times.
 */
public enum ExtractionFailureCode {
    /** The original is larger than the model accepts inline. Upload a smaller or compressed copy. */
    DOCUMENT_TOO_LARGE(false),
    /** The file is damaged, password-protected, or has more pages than the limit. */
    UNREADABLE_FILE(false),
    /** The stored original could not be read (the document store is unavailable or the object is missing). */
    STORAGE_UNAVAILABLE(true),
    /** The stored original is not the file that was uploaded (its size or SHA-256 no longer matches). It is never sent to the model. */
    STORED_FILE_MISMATCH(false),
    /** The model refused the input as invalid; retrying the same file will not help. */
    MODEL_REJECTED_INPUT(false),
    /** The model declined to read the document (safety or content policy). */
    MODEL_BLOCKED(false),
    /** The key, the model name or the project setup is wrong. An operator must fix it. */
    MODEL_NOT_CONFIGURED(false),
    /** Every model in the chain was rate limited or out of quota. Try again later. */
    MODEL_RATE_LIMITED(true),
    /** The model service failed or timed out on every attempt. */
    MODEL_UNAVAILABLE(true),
    /** The model answered, but never with output that matches the schema. */
    MODEL_OUTPUT_INVALID(true),
    INTERNAL_ERROR(true);

    private final boolean retryable;

    ExtractionFailureCode(boolean retryable) {
        this.retryable = retryable;
    }

    /** False when running the same file again cannot help: a different file or an operator's fix is needed. */
    public boolean retryable() {
        return retryable;
    }
}
