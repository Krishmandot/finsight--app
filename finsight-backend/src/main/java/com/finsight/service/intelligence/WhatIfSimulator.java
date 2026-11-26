package com.finsight.service.intelligence;

import com.finsight.domain.enums.TransactionType;
import com.finsight.service.support.MoneyFormatter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What-If Financial Simulator.
 *
 * <p>{@link #baseline} turns the last 90 days (or less, for newer users) into average monthly income and spending
 * per category. {@link #simulate} then applies monthly reductions to chosen categories and projects the extra
 * money saved over a number of months, optionally growing at an annual return (deposits at month end).
 */
public final class WhatIfSimulator {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final int MAX_MONTHS = 60;

    private WhatIfSimulator() {
    }

    /** @param monthlyAverage average spending per month in this category */
    public record CategorySpend(String name, BigDecimal monthlyAverage, BigDecimal sharePercent) {
    }

    public record Baseline(boolean hasData, int windowDays, BigDecimal monthlyIncome, BigDecimal monthlySpending,
                           BigDecimal monthlySavings, List<CategorySpend> categories) {
    }

    public record Applied(String category, BigDecimal reduction) {
    }

    /** @param extraSaved cumulative extra money saved by the end of {@code month} */
    public record Point(int month, BigDecimal extraSaved) {
    }

    /**
     * @param monthlyReduction       total of all applied reductions, per month
     * @param newMonthlySavings      baseline savings plus the reduction, per month
     * @param extraSaved             extra money saved over the whole period (including growth, if any)
     * @param projectedTotalSavings  baseline savings over the period plus {@code extraSaved}
     * @param percentOfSpending      the reduction as a share of average monthly spending
     * @param savingsRateBefore      baseline savings as a percentage of income, or null without income
     * @param savingsRateAfter       savings rate with the reduction applied, or null without income
     */
    public record Result(BigDecimal monthlyReduction, BigDecimal newMonthlySavings, BigDecimal extraSaved,
                         BigDecimal projectedTotalSavings, BigDecimal percentOfSpending,
                         BigDecimal savingsRateBefore, BigDecimal savingsRateAfter, int months,
                         List<Applied> applied, List<Point> points, String summary) {
    }

    public static Baseline baseline(List<LedgerEntry> entries, LocalDate today, int maxCategories) {
        List<LedgerEntry> valid = entries.stream()
            .filter(e -> e.date() != null && e.amount() != null && !e.date().isAfter(today))
            .toList();
        LocalDate first = valid.stream().map(LedgerEntry::date).min(LocalDate::compareTo).orElse(null);
        if (first == null) {
            return new Baseline(false, 0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, List.of());
        }
        long daysSinceFirst = ChronoUnit.DAYS.between(first, today) + 1;
        int windowDays = (int) Math.max(30, Math.min(90, daysSinceFirst));
        LocalDate from = today.minusDays(windowDays - 1L);
        BigDecimal months = BigDecimal.valueOf(windowDays).divide(BigDecimal.valueOf(30), 6, RoundingMode.HALF_UP);

        BigDecimal income = BigDecimal.ZERO;
        Map<String, BigDecimal> byCategory = new LinkedHashMap<>();
        for (LedgerEntry entry : valid) {
            if (entry.date().isBefore(from)) {
                continue;
            }
            if (entry.type() == TransactionType.CREDIT) {
                income = income.add(entry.amount());
            } else if (entry.type() == TransactionType.DEBIT) {
                byCategory.merge(entry.categoryOrDefault(), entry.amount(), BigDecimal::add);
            } else if (entry.type() == TransactionType.REFUND) {
                byCategory.merge(entry.categoryOrDefault(), entry.amount().negate(), BigDecimal::add);
            }
        }
        BigDecimal spending = byCategory.values().stream().filter(v -> v.signum() > 0)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        List<CategorySpend> categories = new ArrayList<>();
        byCategory.forEach((name, total) -> {
            if (total.signum() > 0 && !LedgerEntry.UNCATEGORIZED.equals(name)) {
                BigDecimal share = spending.signum() == 0 ? BigDecimal.ZERO
                    : total.multiply(HUNDRED).divide(spending, 1, RoundingMode.HALF_UP);
                categories.add(new CategorySpend(name, total.divide(months, 0, RoundingMode.HALF_UP), share));
            }
        });
        categories.sort(Comparator.comparing(CategorySpend::monthlyAverage).reversed());
        List<CategorySpend> top = categories.subList(0, Math.min(Math.max(1, maxCategories), categories.size()));

        BigDecimal monthlyIncome = income.divide(months, 0, RoundingMode.HALF_UP);
        BigDecimal monthlySpending = spending.divide(months, 0, RoundingMode.HALF_UP);
        return new Baseline(true, windowDays, monthlyIncome, monthlySpending, monthlyIncome.subtract(monthlySpending),
            List.copyOf(top));
    }

    /**
     * @param reductions        category name to monthly reduction; each is limited to that category's average
     * @param annualReturnPercent expected yearly return on the extra savings, or null/zero for none
     */
    public static Result simulate(Baseline baseline, Map<String, BigDecimal> reductions, int months,
                                  BigDecimal annualReturnPercent, String currency) {
        int horizon = Math.max(1, Math.min(months, MAX_MONTHS));
        Map<String, BigDecimal> limits = new LinkedHashMap<>();
        baseline.categories().forEach(c -> limits.put(c.name(), c.monthlyAverage()));

        List<Applied> applied = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        if (reductions != null) {
            for (Map.Entry<String, BigDecimal> entry : reductions.entrySet()) {
                BigDecimal limit = limits.get(entry.getKey());
                BigDecimal wanted = entry.getValue();
                if (limit == null || wanted == null || wanted.signum() <= 0) {
                    continue;
                }
                BigDecimal capped = wanted.min(limit);
                applied.add(new Applied(entry.getKey(), capped));
                total = total.add(capped);
            }
        }

        double monthlyRate = annualReturnPercent == null ? 0 : annualReturnPercent.doubleValue() / 1200.0;
        List<Point> points = new ArrayList<>();
        double balance = 0;
        for (int m = 1; m <= horizon; m++) {
            balance = balance * (1 + monthlyRate) + total.doubleValue();
            points.add(new Point(m, BigDecimal.valueOf(balance).setScale(2, RoundingMode.HALF_UP)));
        }
        BigDecimal extra = points.get(points.size() - 1).extraSaved();
        BigDecimal newSavings = baseline.monthlySavings().add(total);
        BigDecimal projected = baseline.monthlySavings().multiply(BigDecimal.valueOf(horizon)).add(extra);
        BigDecimal percentOfSpending = baseline.monthlySpending().signum() == 0 ? BigDecimal.ZERO
            : total.multiply(HUNDRED).divide(baseline.monthlySpending(), 1, RoundingMode.HALF_UP);
        BigDecimal before = rate(baseline.monthlySavings(), baseline.monthlyIncome());
        BigDecimal after = rate(newSavings, baseline.monthlyIncome());

        return new Result(total, newSavings, extra, projected, percentOfSpending, before, after, horizon,
            List.copyOf(applied), List.copyOf(points), summary(applied, total, horizon, extra, currency));
    }

    private static BigDecimal rate(BigDecimal savings, BigDecimal income) {
        if (income.signum() <= 0) {
            return null;
        }
        return savings.multiply(HUNDRED).divide(income, 1, RoundingMode.HALF_UP);
    }

    private static String summary(List<Applied> applied, BigDecimal total, int months, BigDecimal extra, String currency) {
        String period = months + (months == 1 ? " month" : " months");
        if (applied.isEmpty()) {
            return "Move a slider to see how a change in spending adds up over " + period + ".";
        }
        String what = applied.size() == 1
            ? "reduce " + applied.get(0).category() + " by " + MoneyFormatter.formatWhole(total, currency)
            : "cut " + MoneyFormatter.formatWhole(total, currency) + " across " + applied.size() + " categories";
        return "If you " + what + " each month, you could save an extra " + MoneyFormatter.formatWhole(extra, currency)
            + " over " + period + ".";
    }
}
