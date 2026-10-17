package com.example.ClaimShieldX.extraction.schema;

/** Why an extraction needs a person's attention. The wording of each issue is neutral: none of these is a fraud finding. */
public enum IssueCode {
    UNREADABLE_DOCUMENT,
    REQUIRED_FIELD_UNKNOWN,
    VALUE_NOT_PARSEABLE,
    PAGE_OUT_OF_RANGE,
    NO_SOURCE_QUOTE,
    LINE_TOTAL_MISMATCH,
    SUBTOTAL_MISMATCH,
    GRAND_TOTAL_MISMATCH,
    CURRENCY_MISMATCH,
    IDENTIFIER_MISMATCH,
    NO_IDENTIFIER_TO_CROSS_CHECK,
    DOCUMENT_DATE_BEFORE_INCIDENT,
    INCIDENT_DATE_MISMATCH,
    REPAIR_DATES_OUT_OF_ORDER,
    MODEL_REPORTED_PROBLEM,
    TOO_MANY_LINE_ITEMS,
    DATE_FORMAT_ASSUMED,
    AMOUNT_FORMAT_ASSUMED
}
