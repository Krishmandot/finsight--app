package com.finsight.domain.enums;

/** Which kind of transaction a category is meant for. {@code ANY} categories apply to both. */
public enum CategoryKind {
    EXPENSE,
    INCOME,
    ANY;

    public boolean accepts(TransactionType type) {
        return switch (this) {
            case ANY -> true;
            case INCOME -> type == TransactionType.CREDIT;
            case EXPENSE -> type == TransactionType.DEBIT || type == TransactionType.REFUND;
        };
    }
}
