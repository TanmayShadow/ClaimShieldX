package com.example.ClaimShieldX.extraction.client;

import java.time.Duration;

/** A failed model call, already sorted into the kind of failure that decides what happens next. */
public class ModelCallException extends Exception {
    public enum Category {
        /** Server error, timeout or network problem. Worth retrying a few times. */
        TRANSIENT,
        /** Too many requests in a short time. Retried after the delay the service asked for. */
        RATE_LIMITED,
        /** The daily quota of this model is used up. Not retried on this model; the next model in the chain is tried. */
        QUOTA_EXHAUSTED,
        /** The answer was cut off at the output limit. Retried: it is not always repeatable. */
        TRUNCATED_OUTPUT,
        /** The model says the input itself is invalid. Retrying the same file cannot help. */
        REJECTED_INPUT,
        /** The model declined to read the document for safety or policy reasons. */
        BLOCKED,
        /** The key, the model name or the project is wrong. An operator must fix it. */
        NOT_CONFIGURED
    }

    private final Category category;
    private final Duration retryAfter;

    public ModelCallException(Category category, String message, Throwable cause) {
        this(category, message, cause, null);
    }

    public ModelCallException(Category category, String message, Throwable cause, Duration retryAfter) {
        super(message, cause);
        this.category = category;
        this.retryAfter = retryAfter;
    }

    public Category category() {
        return category;
    }

    /** What the service asked us to wait, when it said; otherwise null. */
    public Duration retryAfter() {
        return retryAfter;
    }
}
