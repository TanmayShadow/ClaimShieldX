package com.example.ClaimShieldX.analysis.reasoning;

import com.example.ClaimShieldX.investigation.model.InvestigationErrorCode;

/** No usable answer from the model after everything allowed was tried. The code decides whether trying again later could help. */
public class ReasoningFailure extends Exception {
    public enum Code {
        RATE_LIMITED(true, InvestigationErrorCode.MODEL_QUOTA_EXHAUSTED),
        UNAVAILABLE(true, InvestigationErrorCode.MODEL_UNAVAILABLE),
        OUTPUT_INVALID(true, InvestigationErrorCode.INVALID_MODEL_OUTPUT),
        BLOCKED(false, InvestigationErrorCode.INVALID_MODEL_OUTPUT),
        NOT_CONFIGURED(false, InvestigationErrorCode.MODEL_UNAVAILABLE),
        REJECTED(false, InvestigationErrorCode.INVALID_MODEL_OUTPUT);

        private final boolean retryable;
        private final InvestigationErrorCode investigationCode;

        Code(boolean retryable, InvestigationErrorCode investigationCode) {
            this.retryable = retryable;
            this.investigationCode = investigationCode;
        }

        public boolean retryable() {
            return retryable;
        }

        public InvestigationErrorCode investigationCode() {
            return investigationCode;
        }
    }

    private final Code code;
    private final String modelId;
    private final int calls;

    public ReasoningFailure(Code code, String modelId, int calls, Throwable cause) {
        super(code.name(), cause);
        this.code = code;
        this.modelId = modelId;
        this.calls = calls;
    }

    public Code code() {
        return code;
    }

    public String modelId() {
        return modelId;
    }

    public int calls() {
        return calls;
    }
}
