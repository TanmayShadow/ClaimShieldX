package com.example.ClaimShieldX.investigation.model;

/**
 * Why an investigation or a stage did not go as planned. The code and its fixed message are all a client ever sees; exception
 * text and anything from a document stay in the application log, if anywhere.
 */
public enum InvestigationErrorCode {
    EXTRACTION_FAILED("A document could not be read."),
    MODEL_UNAVAILABLE("The AI service was unavailable."),
    MODEL_QUOTA_EXHAUSTED("The AI service quota was reached."),
    INVALID_MODEL_OUTPUT("The AI service did not return usable output."),
    STORAGE_UNAVAILABLE("A stored document could not be read."),
    TIMEOUT("The investigation did not finish within the allowed number of attempts."),
    INTERNAL_ERROR("An internal error stopped the investigation.");

    private final String message;

    InvestigationErrorCode(String message) {
        this.message = message;
    }

    public String message() {
        return message;
    }
}
