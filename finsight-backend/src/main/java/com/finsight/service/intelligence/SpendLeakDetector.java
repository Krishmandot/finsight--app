package com.finsight.service.intelligence;

import com.finsight.domain.enums.TransactionType;
import com.finsight.service.support.MoneyFormatter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * SpendLeak Detector: finds money that quietly drains away.
 *
 * <ol>
 *   <li><b>Recurring payments</b>: the same payee charged at a steady interval (about monthly or weekly) for a
 *       steady amount, at least three times, and still active.</li>
 *   <li><b>Unusual spikes</b>: a recent expense far above what is normal for its category.</li>
 *   <li><b>Possible duplicates</b>: the same payee and amount charged twice within a day.</li>
 *   <li><b>Category surges</b>: a category whose last 30 days are well above its own earlier monthly average.</li>
 * </ol>
 *
 * The input must already be limited to reportable transactions in one currency. Each detector reports only what
 * the data supports; with too little history it reports less rather than guessing.
 */
public final class SpendLeakDetector {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final int WINDOW_DAYS = 30;
    private static final int MIN_HISTORY_DAYS = 90;
    private static final List<String> ESSENTIAL_WORDS = List.of(
        "rent", "emi", "loan", "insurance", "tax", "utilit", "electric", "water", "bill", "fee", "tuition",
        "education", "mortgage");

    private SpendLeakDetector() {
    }

    public static LeakReport detect(List<LedgerEntry> entries, LocalDate today, String currency) {
        List<LedgerEntry> all = entries.stream()
            .filter(e -> e.date() != null && e.amount() != null && e.amount().signum() > 0 && !e.date().isAfter(today))
            .sorted(Comparator.comparing(LedgerEntry::date))
            .toList();
        List<LedgerEntry> debits = all.stream().filter(LedgerEntry::isDebit).toList();
        if (debits.isEmpty()) {
            return new LeakReport(List.of(), BigDecimal.ZERO, BigDecimal.ZERO, false,
                "No approved spending to analyse yet. Import or add transactions and approve them in Review.",
                today, today);
        }
        LocalDate first = all.get(0).date();
        long spanDays = ChronoUnit.DAYS.between(first, today) + 1;
        BigDecimal avgMonthlySpend = netSpending(all, first, today)
            .divide(BigDecimal.valueOf(Math.max(1.0, spanDays / 30.4)), 2, RoundingMode.HALF_UP);

        List<SpendLeak> leaks = new ArrayList<>();
        Set<String> recurringKeys = new HashSet<>();
        detectRecurring(debits, today, currency, avgMonthlySpend, leaks, recurringKeys);
        detectSpikes(debits, today, currency, avgMonthlySpend, recurringKeys, leaks);
        detectDuplicates(debits, today, currency, avgMonthlySpend, recurringKeys, leaks);
        boolean surgeChecked = detectSurges(all, first, today, currency, leaks);

        leaks.sort(Comparator.comparing(SpendLeak::severity)
            .thenComparing(SpendLeak::impact, Comparator.reverseOrder()));
        BigDecimal monthly = BigDecimal.ZERO;
        BigDecimal oneOff = BigDecimal.ZERO;
        for (SpendLeak leak : leaks) {
            if (leak.monthlyImpact()) {
                monthly = monthly.add(leak.impact());
            } else {
                oneOff = oneOff.add(leak.impact());
            }
        }
        boolean enough = spanDays >= MIN_HISTORY_DAYS && surgeChecked;
        String note = enough ? null : "Based on " + spanDays + " days of history. Recurring-payment and trend checks "
            + "become reliable after about 3 months of approved transactions.";
        return new LeakReport(List.copyOf(leaks), monthly, oneOff, enough, note, first, today);
    }

    // ---------------------------------------------------------------------------------------------------------
    // 1. Recurring payments

    private static void detectRecurring(List<LedgerEntry> debits, LocalDate today, String currency,
                                        BigDecimal avgMonthlySpend, List<SpendLeak> out, Set<String> recurringKeys) {
        Map<String, List<LedgerEntry>> byKey = new LinkedHashMap<>();
        for (LedgerEntry entry : debits) {
            String key = MerchantKey.of(entry.description());
            if (!key.isEmpty()) {
                byKey.computeIfAbsent(key, k -> new ArrayList<>()).add(entry);
            }
        }
        for (Map.Entry<String, List<LedgerEntry>> group : byKey.entrySet()) {
            List<LedgerEntry> charges = group.getValue();
            if (charges.size() < 3) {
                continue;
            }
            List<Long> gaps = new ArrayList<>();
            for (int i = 1; i < charges.size(); i++) {
                gaps.add(ChronoUnit.DAYS.between(charges.get(i - 1).date(), charges.get(i).date()));
            }
            long medianGap = medianLong(gaps);
            boolean monthly = medianGap >= 26 && medianGap <= 34 && share(gaps, 24, 38) >= 0.6;
            boolean weekly = !monthly && charges.size() >= 4 && medianGap >= 6 && medianGap <= 8
                && share(gaps, 5, 9) >= 0.6;
            if (!monthly && !weekly) {
                continue;
            }
            // Steady amount: most consecutive charges differ by at most 15%. Judging neighbours (not the overall
            // median) lets a single price rise through, which is exactly what a subscription does.
            long steadyPairs = 0;
            for (int i = 1; i < charges.size(); i++) {
                BigDecimal previous = charges.get(i - 1).amount();
                if (charges.get(i).amount().subtract(previous).abs()
                    .compareTo(previous.multiply(BigDecimal.valueOf(0.15))) <= 0) {
                    steadyPairs++;
                }
            }
            if ((double) steadyPairs / (charges.size() - 1) < 0.75) {
                continue;
            }
            // The current price: the middle value of the latest three charges.
            BigDecimal typical = median(charges.subList(charges.size() - 3, charges.size()).stream()
                .map(LedgerEntry::amount).toList());
            LedgerEntry firstCharge = charges.get(0);
            LedgerEntry lastCharge = charges.get(charges.size() - 1);
            long activeWithin = monthly ? 45 : 14;
            if (lastCharge.date().isBefore(today.minusDays(activeWithin))) {
                continue; // stopped: not a current drain
            }
            recurringKeys.add(group.getKey());

            BigDecimal monthlyCost = monthly ? typical
                : typical.multiply(BigDecimal.valueOf(52)).divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP);
            BigDecimal yearly = monthlyCost.multiply(BigDecimal.valueOf(12));
            String category = lastCharge.categoryOrDefault();
            boolean essential = isEssential(category);
            SpendLeak.Severity severity = essential ? SpendLeak.Severity.LOW : severityByShare(monthlyCost, avgMonthlySpend);

            StringBuilder detail = new StringBuilder(money(typical, currency))
                .append(monthly ? " every month" : " every week")
                .append(" since ").append(firstCharge.date().format(DAY))
                .append(" (").append(charges.size()).append(" charges). That is about ")
                .append(money(yearly, currency)).append(" a year.");
            if (lastCharge.amount().compareTo(firstCharge.amount().multiply(BigDecimal.valueOf(1.10))) >= 0) {
                detail.append(" The price has risen from ").append(money(firstCharge.amount(), currency))
                    .append(" to ").append(money(lastCharge.amount(), currency)).append(".");
            }
            String action = essential
                ? "This looks like a fixed essential cost. Compare plans or providers once a year to be sure you are on the best deal."
                : "Still using it? If not, cancel or downgrade to save about " + money(yearly, currency) + " a year.";
            out.add(new SpendLeak(SpendLeak.Type.RECURRING_PAYMENT, severity,
                "Recurring payment: " + MerchantKey.display(group.getKey()), detail.toString(), action,
                essential ? BigDecimal.ZERO : monthlyCost, category));
        }
    }

    // ---------------------------------------------------------------------------------------------------------
    // 2. Unusual spikes

    private static void detectSpikes(List<LedgerEntry> debits, LocalDate today, String currency,
                                     BigDecimal avgMonthlySpend, Set<String> recurringKeys, List<SpendLeak> out) {
        LocalDate windowStart = today.minusDays(WINDOW_DAYS - 1L);
        LocalDate historyStart = windowStart.minusDays(120);
        Map<String, List<BigDecimal>> history = new LinkedHashMap<>();
        for (LedgerEntry entry : debits) {
            if (!entry.date().isBefore(historyStart) && entry.date().isBefore(windowStart)) {
                history.computeIfAbsent(entry.categoryOrDefault(), c -> new ArrayList<>()).add(entry.amount());
            }
        }
        BigDecimal floor = avgMonthlySpend.multiply(BigDecimal.valueOf(0.02));
        List<SpendLeak> found = new ArrayList<>();
        for (LedgerEntry entry : debits) {
            if (entry.date().isBefore(windowStart) || recurringKeys.contains(MerchantKey.of(entry.description()))) {
                continue;
            }
            List<BigDecimal> past = history.get(entry.categoryOrDefault());
            if (past == null || past.size() < 5 || entry.amount().compareTo(floor) < 0) {
                continue;
            }
            BigDecimal median = median(past);
            double mean = past.stream().mapToDouble(BigDecimal::doubleValue).average().orElse(0);
            double sd = standardDeviation(past.stream().mapToDouble(BigDecimal::doubleValue).toArray());
            double amount = entry.amount().doubleValue();
            if (amount > 3 * median.doubleValue() && amount > mean + 2 * sd) {
                BigDecimal excess = entry.amount().subtract(median);
                SpendLeak.Severity severity = amount >= 5 * median.doubleValue()
                    ? SpendLeak.Severity.HIGH : SpendLeak.Severity.MEDIUM;
                String what = entry.description() == null || entry.description().isBlank() ? ""
                    : " (" + truncate(entry.description(), 40) + ")";
                found.add(new SpendLeak(SpendLeak.Type.UNUSUAL_SPIKE, severity,
                    "Unusually large " + entry.categoryOrDefault() + " expense: " + money(entry.amount(), currency),
                    "On " + entry.date().format(DAY) + what + ". Your typical " + entry.categoryOrDefault()
                        + " expense is about " + money(median, currency) + ".",
                    "Check this was intended. If it was a one-off, no action is needed; otherwise see whether it can be split, delayed or negotiated.",
                    excess, entry.categoryOrDefault()));
            }
        }
        found.sort(Comparator.comparing(SpendLeak::impact).reversed());
        out.addAll(found.subList(0, Math.min(3, found.size())));
    }

    // ---------------------------------------------------------------------------------------------------------
    // 3. Possible duplicate charges

    private static void detectDuplicates(List<LedgerEntry> debits, LocalDate today, String currency,
                                         BigDecimal avgMonthlySpend, Set<String> recurringKeys, List<SpendLeak> out) {
        LocalDate windowStart = today.minusDays(59);
        Map<String, List<LedgerEntry>> byKey = new LinkedHashMap<>();
        for (LedgerEntry entry : debits) {
            String key = MerchantKey.of(entry.description());
            if (!entry.date().isBefore(windowStart) && !key.isEmpty() && !recurringKeys.contains(key)) {
                byKey.computeIfAbsent(key, k -> new ArrayList<>()).add(entry);
            }
        }
        BigDecimal floor = avgMonthlySpend.multiply(BigDecimal.valueOf(0.02));
        for (Map.Entry<String, List<LedgerEntry>> group : byKey.entrySet()) {
            List<LedgerEntry> charges = group.getValue();
            for (int i = 0; i + 1 < charges.size(); i++) {
                LedgerEntry a = charges.get(i);
                LedgerEntry b = charges.get(i + 1);
                if (a.amount().compareTo(b.amount()) == 0 && ChronoUnit.DAYS.between(a.date(), b.date()) <= 1) {
                    String when = a.date().equals(b.date()) ? "on " + a.date().format(DAY)
                        : "on " + a.date().format(DAY) + " and " + b.date().format(DAY);
                    out.add(new SpendLeak(SpendLeak.Type.POSSIBLE_DUPLICATE,
                        a.amount().compareTo(floor) >= 0 ? SpendLeak.Severity.MEDIUM : SpendLeak.Severity.LOW,
                        "Possible double charge at " + MerchantKey.display(group.getKey()),
                        money(a.amount(), currency) + " was charged twice " + when + ".",
                        "If this was not intentional, ask the merchant or your bank for a refund.",
                        a.amount(), a.categoryOrDefault()));
                    i++; // the pair is consumed
                }
            }
        }
    }

    // ---------------------------------------------------------------------------------------------------------
    // 4. Category surges

    /** @return true when enough earlier history existed to run the check */
    private static boolean detectSurges(List<LedgerEntry> all, LocalDate first, LocalDate today, String currency,
                                        List<SpendLeak> out) {
        Map<String, BigDecimal> current = netByCategory(all, today.minusDays(WINDOW_DAYS - 1L), today);
        List<Map<String, BigDecimal>> prior = new ArrayList<>();
        for (int k = 1; k <= 3; k++) {
            LocalDate end = today.minusDays(30L * k);
            LocalDate start = end.minusDays(WINDOW_DAYS - 1L);
            if (!first.isAfter(start)) { // the window is fully covered by data
                prior.add(netByCategory(all, start, end));
            }
        }
        if (prior.size() < 2) {
            return false;
        }
        Map<String, BigDecimal> average = new TreeMap<>();
        for (Map<String, BigDecimal> window : prior) {
            window.forEach((category, amount) -> average.merge(category, amount, BigDecimal::add));
        }
        average.replaceAll((category, total) -> total.divide(BigDecimal.valueOf(prior.size()), 2, RoundingMode.HALF_UP));
        BigDecimal averageTotal = average.values().stream().filter(v -> v.signum() > 0)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        for (Map.Entry<String, BigDecimal> entry : current.entrySet()) {
            String category = entry.getKey();
            BigDecimal now = entry.getValue();
            BigDecimal usual = average.getOrDefault(category, BigDecimal.ZERO);
            if (LedgerEntry.UNCATEGORIZED.equals(category) || now.signum() <= 0 || usual.signum() <= 0) {
                continue;
            }
            BigDecimal excess = now.subtract(usual);
            boolean muchHigher = now.compareTo(usual.multiply(BigDecimal.valueOf(1.25))) >= 0;
            boolean material = excess.compareTo(averageTotal.multiply(BigDecimal.valueOf(0.03))) >= 0;
            if (muchHigher && material) {
                long percent = excess.multiply(HUNDRED).divide(usual, 0, RoundingMode.HALF_UP).longValue();
                boolean big = percent >= 50 || excess.compareTo(averageTotal.multiply(BigDecimal.valueOf(0.10))) >= 0;
                out.add(new SpendLeak(SpendLeak.Type.CATEGORY_SURGE,
                    big ? SpendLeak.Severity.HIGH : SpendLeak.Severity.MEDIUM,
                    category + " spending is up " + percent + "%",
                    money(now, currency) + " in the last 30 days, compared with a usual " + money(usual, currency)
                        + " a month.",
                    "Getting back to your usual level would save about " + money(excess, currency) + " a month.",
                    excess, category));
            }
        }
        return true;
    }

    // ---------------------------------------------------------------------------------------------------------
    // helpers

    private static Map<String, BigDecimal> netByCategory(List<LedgerEntry> entries, LocalDate from, LocalDate to) {
        Map<String, BigDecimal> totals = new LinkedHashMap<>();
        for (LedgerEntry entry : entries) {
            if (entry.date().isBefore(from) || entry.date().isAfter(to)) {
                continue;
            }
            if (entry.type() == TransactionType.DEBIT) {
                totals.merge(entry.categoryOrDefault(), entry.amount(), BigDecimal::add);
            } else if (entry.type() == TransactionType.REFUND) {
                totals.merge(entry.categoryOrDefault(), entry.amount().negate(), BigDecimal::add);
            }
        }
        return totals;
    }

    private static BigDecimal netSpending(List<LedgerEntry> entries, LocalDate from, LocalDate to) {
        return netByCategory(entries, from, to).values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static SpendLeak.Severity severityByShare(BigDecimal cost, BigDecimal avgMonthlySpend) {
        if (avgMonthlySpend.signum() <= 0) {
            return SpendLeak.Severity.LOW;
        }
        double share = cost.doubleValue() / avgMonthlySpend.doubleValue();
        return share >= 0.10 ? SpendLeak.Severity.HIGH : share >= 0.03 ? SpendLeak.Severity.MEDIUM : SpendLeak.Severity.LOW;
    }

    private static boolean isEssential(String category) {
        String lower = category.toLowerCase(Locale.ROOT);
        return ESSENTIAL_WORDS.stream().anyMatch(lower::contains);
    }

    private static double share(List<Long> gaps, long min, long max) {
        return gaps.stream().filter(g -> g >= min && g <= max).count() / (double) gaps.size();
    }

    static long medianLong(List<Long> values) {
        List<Long> sorted = values.stream().sorted().toList();
        return sorted.get(sorted.size() / 2);
    }

    static BigDecimal median(List<BigDecimal> values) {
        List<BigDecimal> sorted = values.stream().sorted().toList();
        int n = sorted.size();
        if (n % 2 == 1) {
            return sorted.get(n / 2);
        }
        return sorted.get(n / 2 - 1).add(sorted.get(n / 2)).divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
    }

    static double standardDeviation(double[] values) {
        if (values.length == 0) {
            return 0;
        }
        double mean = 0;
        for (double v : values) {
            mean += v;
        }
        mean /= values.length;
        double sum = 0;
        for (double v : values) {
            sum += (v - mean) * (v - mean);
        }
        return Math.sqrt(sum / values.length);
    }

    private static String money(BigDecimal amount, String currency) {
        return MoneyFormatter.formatWhole(amount, currency);
    }

    private static String truncate(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max - 1) + "…";
    }
}
