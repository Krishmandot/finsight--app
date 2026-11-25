package com.finsight.repository.projection;

import com.finsight.domain.enums.TransactionType;
import java.math.BigDecimal;
import java.util.UUID;

/** {@code categoryId} and {@code categoryName} are null for uncategorized transactions. */
public record CategoryTypeTotal(UUID categoryId, String categoryName, TransactionType type,
                                BigDecimal total, Long count) {
}
