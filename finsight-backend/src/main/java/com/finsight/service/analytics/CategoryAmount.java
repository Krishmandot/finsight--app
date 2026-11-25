package com.finsight.service.analytics;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Amount attributed to one category. {@code categoryId} is null for uncategorized transactions.
 *
 * @param share percentage of the total across all categories in the same breakdown (0–100)
 */
public record CategoryAmount(UUID categoryId, String name, BigDecimal amount, long count, double share) {

    public static final String UNCATEGORIZED = "Uncategorized";
}
