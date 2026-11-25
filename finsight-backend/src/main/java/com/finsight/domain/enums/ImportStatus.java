package com.finsight.domain.enums;

public enum ImportStatus {
    /** Rows were stored as pending transactions and still await review. */
    IN_REVIEW,
    /** No pending rows remain in the batch. */
    COMPLETED
}
