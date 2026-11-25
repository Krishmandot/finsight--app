package com.finsight.service.report;

import com.finsight.api.dto.response.TransactionResponse;
import com.finsight.service.analytics.CategoryComparison;
import com.finsight.service.analytics.CurrencyNotice;
import com.finsight.service.analytics.DailyPoint;
import com.finsight.service.analytics.PeriodSummary;
import java.util.List;

/**
 * Everything shown on a report page and written to its exports.
 *
 * @param previousSummary null when the previous period has no reportable data (no comparison is made)
 * @param transactions    reportable transactions in the period, newest first
 * @param excluded        transactions in the period that are not counted, by reason
 */
public record FinancialReport(
    ReportPeriod period,
    ReportPeriod previousPeriod,
    String currency,
    PeriodSummary summary,
    PeriodSummary previousSummary,
    List<CategoryComparison> categories,
    List<DailyPoint> daily,
    List<TransactionResponse> transactions,
    List<TransactionResponse> largestExpenses,
    ExcludedCounts excluded,
    CurrencyNotice otherCurrencies
) {
    public boolean hasComparison() {
        return previousSummary != null;
    }

    public record ExcludedCounts(long pending, long rejected, long excludedApproved) {
        public long total() {
            return pending + rejected + excludedApproved;
        }
    }
}
