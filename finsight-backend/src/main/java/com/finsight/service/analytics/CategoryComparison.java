package com.finsight.service.analytics;

import java.math.BigDecimal;
import java.util.UUID;

/** Spending in one category for two periods. {@code changePercent} is null when the earlier amount is zero. */
public record CategoryComparison(UUID categoryId, String name, BigDecimal current, BigDecimal previous,
                                 BigDecimal change, Double changePercent) {
}
