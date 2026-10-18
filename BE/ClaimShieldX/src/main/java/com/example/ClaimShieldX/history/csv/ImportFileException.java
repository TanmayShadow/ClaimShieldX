package com.example.ClaimShieldX.history.csv;

import com.example.ClaimShieldX.history.model.ImportFailureCode;

/** The whole file is unusable. The detail is safe to show: it never contains text from the file. */
public class ImportFileException extends Exception {
    private final ImportFailureCode code;
    private final String detail;

    public ImportFileException(ImportFailureCode code, String detail) {
        super(code + (detail == null ? "" : ": " + detail));
        this.code = code;
        this.detail = detail;
    }

    public ImportFailureCode code() {
        return code;
    }

    public String detail() {
        return detail;
    }
}
