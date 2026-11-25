package com.finsight.domain.enums;

/** How a transaction's category was assigned. */
public enum CategorySource {
    /** Chosen or confirmed by the user. */
    USER,
    /** Suggested by the built-in keyword rules. */
    RULE,
    /** Suggested by one of the user's own rules learned from earlier corrections. */
    PERSONAL_RULE
}
