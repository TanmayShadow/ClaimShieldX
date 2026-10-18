package com.example.ClaimShieldX.history.dto;

/**
 * The four honest answers of a history lookup. They are kept apart on purpose: "nothing matched" is only meaningful when
 * there was history to match against, and a failed import is not the same as no history.
 */
public enum MatchStatus {
    MATCHES_FOUND,
    NO_MATCH_FOUND,
    NO_HISTORICAL_DATA_AVAILABLE,
    HISTORY_IMPORT_FAILED
}
