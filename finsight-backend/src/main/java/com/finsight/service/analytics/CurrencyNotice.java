package com.finsight.service.analytics;

import java.util.Map;

/**
 * Reportable transactions in other currencies, which are left out of totals rather than mixed in.
 *
 * @param counts currency code → number of transactions
 */
public record CurrencyNotice(Map<String, Long> counts) {

    public long total() {
        return counts.values().stream().mapToLong(Long::longValue).sum();
    }

    public boolean isEmpty() {
        return counts.isEmpty();
    }
}
