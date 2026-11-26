package com.finsight.service.intelligence;

import com.finsight.domain.enums.TransactionType;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A reportable transaction reduced to the fields the intelligence algorithms need. Keeping the algorithms
 * independent of the JPA entity makes them plain, easily tested Java.
 *
 * @param category category name, or null when the transaction is uncategorized
 */
public record LedgerEntry(LocalDate date, BigDecimal amount, TransactionType type, String description, String category) {

    public static final String UNCATEGORIZED = "Uncategorized";

    public boolean isDebit() {
        return type == TransactionType.DEBIT;
    }

    public String categoryOrDefault() {
        return category == null || category.isBlank() ? UNCATEGORIZED : category;
    }
}
