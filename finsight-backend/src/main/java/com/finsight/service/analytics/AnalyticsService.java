package com.finsight.service.analytics;

import com.finsight.domain.entity.Transaction;
import com.finsight.domain.enums.TransactionType;
import com.finsight.repository.TransactionRepository;
import com.finsight.repository.projection.CategoryTypeTotal;
import com.finsight.repository.projection.CurrencyCount;
import com.finsight.repository.projection.DayTypeTotal;
import com.finsight.repository.projection.MonthTypeTotal;
import com.finsight.repository.projection.TypeTotal;
import com.finsight.service.support.MoneyFormatter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Financial figures for dashboards, analytics and reports. All calculations use only reportable
 * transactions (approved, included in reports, not deleted) in a single currency; pending, rejected and
 * excluded transactions never contribute. Amounts in other currencies are reported separately, never mixed.
 */
@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private static final DateTimeFormatter DAY_MONTH = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH);
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final TransactionRepository transactionRepository;

    @Transactional(readOnly = true)
    public PeriodSummary summary(UUID userId, String currency, LocalDate from, LocalDate to) {
        return summarize(from, to, currency, transactionRepository.sumByType(userId, currency, from, to));
    }

    @Transactional(readOnly = true)
    public List<CategoryAmount> spendingByCategory(UUID userId, String currency, LocalDate from, LocalDate to) {
        return spendingByCategory(transactionRepository.sumByCategoryAndType(userId, currency, from, to));
    }

    @Transactional(readOnly = true)
    public List<CategoryAmount> incomeByCategory(UUID userId, String currency, LocalDate from, LocalDate to) {
        Map<UUID, Accumulator> byCategory = new LinkedHashMap<>();
        for (CategoryTypeTotal row : transactionRepository.sumByCategoryAndType(userId, currency, from, to)) {
            if (row.type() == TransactionType.CREDIT) {
                byCategory.computeIfAbsent(row.categoryId(), id -> new Accumulator(row.categoryName()))
                    .add(row.total(), row.count());
            }
        }
        return toShares(byCategory);
    }

    @Transactional(readOnly = true)
    public List<MonthlyPoint> monthlyTrend(UUID userId, String currency, YearMonth endMonth, int months) {
        int count = Math.max(1, Math.min(months, 36));
        YearMonth startMonth = endMonth.minusMonths(count - 1L);
        Map<YearMonth, BigDecimal[]> totals = new TreeMap<>();
        for (MonthTypeTotal row : transactionRepository.sumByMonthAndType(userId, currency,
            startMonth.atDay(1), endMonth.atEndOfMonth())) {
            BigDecimal[] values = totals.computeIfAbsent(YearMonth.of(row.year(), row.month()),
                m -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            accumulate(values, row.type(), row.total());
        }
        List<MonthlyPoint> points = new ArrayList<>();
        for (YearMonth month = startMonth; !month.isAfter(endMonth); month = month.plusMonths(1)) {
            BigDecimal[] values = totals.getOrDefault(month, new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            BigDecimal net = values[0].subtract(values[1]);
            points.add(new MonthlyPoint(month, values[0], values[1], net, PeriodSummary.savingsRate(values[0], net)));
        }
        return points;
    }

    @Transactional(readOnly = true)
    public List<DailyPoint> dailySeries(UUID userId, String currency, LocalDate from, LocalDate to) {
        Map<LocalDate, BigDecimal[]> totals = new HashMap<>();
        for (DayTypeTotal row : transactionRepository.sumByDayAndType(userId, currency, from, to)) {
            accumulate(totals.computeIfAbsent(row.date(), d -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO}),
                row.type(), row.total());
        }
        List<DailyPoint> points = new ArrayList<>();
        for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
            BigDecimal[] values = totals.getOrDefault(day, new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            points.add(new DailyPoint(day, values[0], values[1]));
        }
        return points;
    }

    @Transactional(readOnly = true)
    public CurrencyNotice currencyNotice(UUID userId, String currency, LocalDate from, LocalDate to) {
        Map<String, Long> counts = new TreeMap<>();
        for (CurrencyCount row : transactionRepository.countOtherCurrencies(userId, currency, from, to)) {
            counts.put(row.currency() == null ? "Unknown" : row.currency(), row.count());
        }
        return new CurrencyNotice(counts);
    }

    /** Per-category spending in two periods, ordered by the current period's amount. */
    @Transactional(readOnly = true)
    public List<CategoryComparison> compareCategories(UUID userId, String currency,
                                                      LocalDate currentFrom, LocalDate currentTo,
                                                      LocalDate previousFrom, LocalDate previousTo) {
        return compare(spendingByCategory(userId, currency, currentFrom, currentTo),
            spendingByCategory(userId, currency, previousFrom, previousTo));
    }

    /** Earliest reportable transaction date in this currency, used to avoid comparing against partial history. */
    @Transactional(readOnly = true)
    public Optional<LocalDate> firstReportableDate(UUID userId, String currency) {
        return transactionRepository.findFirstReportableDate(userId, currency);
    }

    /**
     * Observations about {@code month}. Each one is produced only when the underlying data supports it; with
     * too little history the list is short or empty rather than speculative.
     */
    @Transactional(readOnly = true)
    public List<Insight> insights(UUID userId, String currency, YearMonth month, LocalDate today, long pendingCount) {
        List<Insight> insights = new ArrayList<>();
        LocalDate monthStart = month.atDay(1);
        boolean isCurrentMonth = YearMonth.from(today).equals(month);
        LocalDate monthEnd = isCurrentMonth ? today : month.atEndOfMonth();
        String monthName = month.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH);
        Optional<LocalDate> firstDate = firstReportableDate(userId, currency);

        PeriodSummary current = summary(userId, currency, monthStart, monthEnd);

        // 1. Pace against the previous month, comparing like-for-like windows.
        YearMonth previousMonth = month.minusMonths(1);
        boolean hasFullPreviousMonth = firstDate.map(d -> !d.isAfter(previousMonth.atDay(1))).orElse(false);
        if (current.spending().signum() > 0 && hasFullPreviousMonth) {
            int day = isCurrentMonth ? Math.min(today.getDayOfMonth(), previousMonth.lengthOfMonth())
                : previousMonth.lengthOfMonth();
            LocalDate previousEnd = previousMonth.atDay(day);
            PeriodSummary previous = summary(userId, currency, previousMonth.atDay(1), previousEnd);
            if (previous.spending().signum() > 0) {
                double change = percentChange(current.spending(), previous.spending());
                String when = isCurrentMonth ? "at this point last month" : "in " +
                    previousMonth.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH);
                String detail = MoneyFormatter.formatWhole(current.spending(), currency)
                    + (isCurrentMonth ? " so far, compared with " : " compared with ")
                    + MoneyFormatter.formatWhole(previous.spending(), currency)
                    + (isCurrentMonth ? " by " + previousEnd.format(DAY_MONTH) + "." : ".");
                if (Math.abs(change) < 5) {
                    insights.add(new Insight(Insight.Tone.NEUTRAL, "Spending is level with " + when, detail));
                } else if (change > 0) {
                    insights.add(new Insight(change > 15 ? Insight.Tone.WARNING : Insight.Tone.NEUTRAL,
                        "Spending is " + Math.round(change) + "% higher than " + when, detail));
                } else {
                    insights.add(new Insight(Insight.Tone.POSITIVE,
                        "Spending is " + Math.round(-change) + "% lower than " + when, detail));
                }
            }
        }

        // 2. Savings rate for the month.
        if (current.income().signum() > 0) {
            BigDecimal rate = current.savingsRate();
            if (current.net().signum() < 0) {
                insights.add(new Insight(Insight.Tone.WARNING, "Spending exceeds income in " + monthName,
                    "You've spent " + MoneyFormatter.formatWhole(current.net().negate(), currency)
                        + " more than you've earned" + (isCurrentMonth ? " so far." : ".")));
            } else if (rate != null && rate.compareTo(BigDecimal.valueOf(20)) >= 0) {
                insights.add(new Insight(Insight.Tone.POSITIVE, "You've kept " + rate.setScale(0, RoundingMode.HALF_UP)
                    + "% of your income", MoneyFormatter.formatWhole(current.net(), currency) + " saved in "
                    + monthName + (isCurrentMonth ? " so far." : ".")));
            } else if (rate != null) {
                insights.add(new Insight(Insight.Tone.NEUTRAL, "Savings rate is " + rate.setScale(0, RoundingMode.HALF_UP)
                    + "% in " + monthName, MoneyFormatter.formatWhole(current.net(), currency)
                    + " left after spending. A common target is 20% or more."));
            }
        }

        // 3. Largest category, and a category running well above its own average.
        List<CategoryAmount> categories = spendingByCategory(userId, currency, monthStart, monthEnd);
        if (!categories.isEmpty() && current.expenseCount() >= 3) {
            CategoryAmount top = categories.get(0);
            insights.add(new Insight(Insight.Tone.NEUTRAL,
                top.name() + " is your largest spending category",
                Math.round(top.share()) + "% of " + monthName + " spending ("
                    + MoneyFormatter.formatWhole(top.amount(), currency) + ")."));
        }
        YearMonth averageStart = month.minusMonths(3);
        boolean hasThreeMonths = firstDate.map(d -> !d.isAfter(averageStart.atDay(1))).orElse(false);
        if (hasThreeMonths && current.spending().signum() > 0) {
            Map<UUID, CategoryAmount> history = new HashMap<>();
            spendingByCategory(userId, currency, averageStart.atDay(1), month.minusMonths(1).atEndOfMonth())
                .forEach(c -> history.put(c.categoryId(), c));
            BigDecimal threshold = current.spending().multiply(BigDecimal.valueOf(0.05));
            categories.stream()
                .filter(c -> c.categoryId() != null && history.containsKey(c.categoryId()))
                .map(c -> {
                    BigDecimal average = history.get(c.categoryId()).amount().divide(BigDecimal.valueOf(3), 2, RoundingMode.HALF_UP);
                    double ratio = average.signum() == 0 ? 0 : c.amount().divide(average, 4, RoundingMode.HALF_UP).doubleValue();
                    return Map.entry(c, Map.entry(average, ratio));
                })
                .filter(e -> e.getValue().getValue() >= 1.5
                    && e.getKey().amount().subtract(e.getValue().getKey()).compareTo(threshold) > 0)
                .max(Comparator.comparingDouble(e -> e.getValue().getValue()))
                .ifPresent(e -> insights.add(new Insight(Insight.Tone.WARNING,
                    e.getKey().name() + " is running above your usual",
                    MoneyFormatter.formatWhole(e.getKey().amount(), currency) + " in " + monthName + " versus a "
                        + "3-month average of " + MoneyFormatter.formatWhole(e.getValue().getKey(), currency) + ".")));
        }

        // 4. Largest single expense.
        if (current.expenseCount() >= 3) {
            transactionRepository.findReportable(userId, currency, monthStart, monthEnd).stream()
                .filter(t -> t.getType() == TransactionType.DEBIT)
                .max(Comparator.comparing(Transaction::getAmount))
                .ifPresent(t -> insights.add(new Insight(Insight.Tone.NEUTRAL, "Largest expense: "
                    + MoneyFormatter.formatWhole(t.getAmount(), currency),
                    truncate(t.getRedactedDescription(), 60) + " on " + t.getTransactionDate().format(DAY_MONTH) + ".")));
        }

        // 5. Uncategorized spending makes the breakdown less useful.
        categories.stream().filter(c -> c.categoryId() == null && c.share() >= 15).findFirst()
            .ifPresent(c -> insights.add(new Insight(Insight.Tone.NEUTRAL,
                Math.round(c.share()) + "% of spending is uncategorized",
                "Categorize these transactions to make the breakdown more accurate.")));

        // 6. Transactions waiting for review are not counted.
        if (pendingCount > 0) {
            insights.add(new Insight(Insight.Tone.NEUTRAL,
                pendingCount + (pendingCount == 1 ? " transaction is" : " transactions are") + " waiting for review",
                "Pending transactions aren't included in these figures until you approve them."));
        }
        return insights;
    }

    // ------------------------------------------------------------------------------------------------------

    static PeriodSummary summarize(LocalDate from, LocalDate to, String currency, List<TypeTotal> totals) {
        BigDecimal income = BigDecimal.ZERO;
        BigDecimal gross = BigDecimal.ZERO;
        BigDecimal refunds = BigDecimal.ZERO;
        BigDecimal transfers = BigDecimal.ZERO;
        long count = 0;
        long incomeCount = 0;
        long expenseCount = 0;
        for (TypeTotal row : totals) {
            BigDecimal total = row.total() == null ? BigDecimal.ZERO : row.total();
            long rowCount = row.count() == null ? 0 : row.count();
            count += rowCount;
            if (row.type() == null) {
                continue;
            }
            switch (row.type()) {
                case CREDIT -> {
                    income = income.add(total);
                    incomeCount += rowCount;
                }
                case DEBIT -> {
                    gross = gross.add(total);
                    expenseCount += rowCount;
                }
                case REFUND -> refunds = refunds.add(total);
                case TRANSFER -> transfers = transfers.add(total);
                case UNKNOWN -> { /* never counted towards income or spending */ }
            }
        }
        BigDecimal spending = gross.subtract(refunds);
        BigDecimal net = income.subtract(spending);
        return new PeriodSummary(from, to, currency, income, gross, refunds, spending, transfers, net,
            PeriodSummary.savingsRate(income, net), count, incomeCount, expenseCount);
    }

    static List<CategoryAmount> spendingByCategory(List<CategoryTypeTotal> rows) {
        Map<UUID, Accumulator> byCategory = new LinkedHashMap<>();
        for (CategoryTypeTotal row : rows) {
            if (row.type() == TransactionType.DEBIT) {
                byCategory.computeIfAbsent(row.categoryId(), id -> new Accumulator(row.categoryName()))
                    .add(row.total(), row.count());
            } else if (row.type() == TransactionType.REFUND) {
                byCategory.computeIfAbsent(row.categoryId(), id -> new Accumulator(row.categoryName()))
                    .add(row.total().negate(), 0);
            }
        }
        return toShares(byCategory);
    }

    static List<CategoryComparison> compare(List<CategoryAmount> current, List<CategoryAmount> previous) {
        Map<UUID, CategoryAmount> currentById = new LinkedHashMap<>();
        current.forEach(c -> currentById.put(c.categoryId(), c));
        Map<UUID, CategoryAmount> previousById = new LinkedHashMap<>();
        previous.forEach(c -> previousById.put(c.categoryId(), c));
        Set<UUID> ids = new LinkedHashSet<>(currentById.keySet());
        ids.addAll(previousById.keySet());

        List<CategoryComparison> result = new ArrayList<>();
        for (UUID id : ids) {
            CategoryAmount now = currentById.get(id);
            CategoryAmount before = previousById.get(id);
            BigDecimal nowAmount = now == null ? BigDecimal.ZERO : now.amount();
            BigDecimal beforeAmount = before == null ? BigDecimal.ZERO : before.amount();
            String name = now != null ? now.name() : before.name();
            Double percent = beforeAmount.signum() == 0 ? null : percentChange(nowAmount, beforeAmount);
            result.add(new CategoryComparison(id, name, nowAmount, beforeAmount, nowAmount.subtract(beforeAmount), percent));
        }
        result.sort(Comparator.comparing(CategoryComparison::current).reversed()
            .thenComparing(CategoryComparison::previous, Comparator.reverseOrder()));
        return result;
    }

    static double percentChange(BigDecimal current, BigDecimal previous) {
        if (previous.signum() == 0) {
            return 0;
        }
        return current.subtract(previous).multiply(HUNDRED).divide(previous.abs(), 2, RoundingMode.HALF_UP).doubleValue();
    }

    private static List<CategoryAmount> toShares(Map<UUID, Accumulator> byCategory) {
        BigDecimal total = byCategory.values().stream().map(a -> a.amount)
            .filter(a -> a.signum() > 0).reduce(BigDecimal.ZERO, BigDecimal::add);
        List<CategoryAmount> result = new ArrayList<>();
        byCategory.forEach((id, acc) -> {
            if (acc.amount.signum() > 0) {
                double share = total.signum() == 0 ? 0
                    : acc.amount.multiply(HUNDRED).divide(total, 2, RoundingMode.HALF_UP).doubleValue();
                result.add(new CategoryAmount(id, Objects.requireNonNullElse(acc.name, CategoryAmount.UNCATEGORIZED),
                    acc.amount, acc.count, share));
            }
        });
        result.sort(Comparator.comparing(CategoryAmount::amount).reversed());
        return result;
    }

    private static void accumulate(BigDecimal[] incomeAndSpending, TransactionType type, BigDecimal total) {
        if (type == null || total == null) {
            return;
        }
        switch (type) {
            case CREDIT -> incomeAndSpending[0] = incomeAndSpending[0].add(total);
            case DEBIT -> incomeAndSpending[1] = incomeAndSpending[1].add(total);
            case REFUND -> incomeAndSpending[1] = incomeAndSpending[1].subtract(total);
            default -> { /* transfers and unknown types are not income or spending */ }
        }
    }

    private static String truncate(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max - 1) + "…";
    }

    private static final class Accumulator {
        private final String name;
        private BigDecimal amount = BigDecimal.ZERO;
        private long count;

        Accumulator(String name) {
            this.name = name;
        }

        void add(BigDecimal value, long rows) {
            amount = amount.add(value == null ? BigDecimal.ZERO : value);
            count += rows;
        }
    }
}
