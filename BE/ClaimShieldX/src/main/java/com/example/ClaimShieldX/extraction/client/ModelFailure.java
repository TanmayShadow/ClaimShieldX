package com.example.ClaimShieldX.extraction.client;

import com.example.ClaimShieldX.extraction.model.ExtractionFailureCode;

/** The model could not produce a usable answer after everything allowed was tried. Carries what was spent trying. */
public class ModelFailure extends Exception {
    private final ExtractionFailureCode code;
    private final String modelId;
    private final int modelCalls;

    public ModelFailure(ExtractionFailureCode code, String modelId, int modelCalls, Throwable cause) {
        super(code.name(), cause);
        this.code = code;
        this.modelId = modelId;
        this.modelCalls = modelCalls;
    }

    public ExtractionFailureCode code() {
        return code;
    }

    /** The last model that was tried. */
    public String modelId() {
        return modelId;
    }

    public int modelCalls() {
        return modelCalls;
    }
}
