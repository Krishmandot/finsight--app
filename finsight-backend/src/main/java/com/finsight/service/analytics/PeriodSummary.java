package com.finsight.service.analytics;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

/**
 * Totals for reportable transactions (approved, included, not deleted) in one currency and date range.
 *
 * <ul>
 *   <li>{@code income}: sum of CREDIT transactions</li>
 *   <li>{@code grossSpending}: sum of DEBIT transactions</li>
 *   <li>{@code refunds}: sum of REFUND transactions, which reduce spending</li>
 *   <li>{@code spending}: grossSpending − refunds</li>
 *   <li>{@code transfers}: TRANSFER transactions; movements between own accounts, never income or spending</li>
 *   <li>{@code net}: income − spending (what was saved)</li>
 *   <li>{@code savingsRate}: net / income as a percentage, or null when there is no income</li>
 * </ul>
 */
public record PeriodSummary(
    LocalDate from,
    LocalDate to,
    String currency,
    BigDecimal income,
    BigDecimal grossSpending,
    BigDecimal refunds,
    BigDecimal spending,
    BigDecimal transfers,
    BigDecimal net,
    BigDecimal savingsRate,
    long transactionCount,
    long incomeCount,
    long expenseCount
) {
    public boolean hasData() {
        return transactionCount > 0;
    }

    static BigDecimal savingsRate(BigDecimal income, BigDecimal net) {
        if (income == null || income.signum() <= 0) {
            return null;
        }
        return net.multiply(BigDecimal.valueOf(100)).divide(income, 1, RoundingMode.HALF_UP);
    }
}
