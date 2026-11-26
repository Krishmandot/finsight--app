package com.finsight.service.intelligence;

import com.finsight.service.support.MoneyFormatter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Financial Health Score. Four factors, each scored 0-100 from the user's own monthly figures:
 *
 * <table>
 *   <tr><td>Savings rate (30)</td><td>share of income kept; 20% or more scores 100</td></tr>
 *   <tr><td>Budget adherence (25)</td><td>spending against the user's monthly budget; up to 100% of budget scores
 *       100, 150% or more scores 0</td></tr>
 *   <tr><td>Spending consistency (25)</td><td>how little monthly spending varies; no variation scores 100, a
 *       coefficient of variation of 0.5 or more scores 0 (needs 3 months)</td></tr>
 *   <tr><td>Cash-flow stability (20)</td><td>months with income at or above spending; overspending lowers the
 *       month's score in proportion</td></tr>
 * </table>
 *
 * A factor without enough data (no budget, fewer than 3 months, no income) is left out and the remaining
 * weights are rescaled to 100, so the score never rewards or punishes missing data.
 */
public final class HealthScoreCalculator {

    static final int W_SAVINGS = 30;
    static final int W_BUDGET = 25;
    static final int W_CONSISTENCY = 25;
    static final int W_CASHFLOW = 20;
    private static final double SAVINGS_TARGET = 0.20;

    private HealthScoreCalculator() {
    }

    /** Monthly income and net spending for the last {@code count} calendar months that have any activity. */
    public static List<MonthFigures> monthlyFigures(List<LedgerEntry> entries, LocalDate today, int count) {
        YearMonth current = YearMonth.from(today);
        YearMonth earliest = current.minusMonths(Math.max(1, count) - 1L);
        Map<YearMonth, BigDecimal[]> totals = new TreeMap<>();
        for (LedgerEntry entry : entries) {
            if (entry.date() == null || entry.amount() == null || entry.type() == null) {
                continue;
            }
            YearMonth month = YearMonth.from(entry.date());
            if (month.isBefore(earliest) || month.isAfter(current)) {
                continue;
            }
            BigDecimal[] sums = totals.computeIfAbsent(month, m -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            switch (entry.type()) {
                case CREDIT -> sums[0] = sums[0].add(entry.amount());
                case DEBIT -> sums[1] = sums[1].add(entry.amount());
                case REFUND -> sums[1] = sums[1].subtract(entry.amount());
                default -> { /* transfers and unknown types are neither income nor spending */ }
            }
        }
        List<MonthFigures> result = new ArrayList<>();
        totals.forEach((month, sums) -> {
            if (sums[0].signum() != 0 || sums[1].signum() != 0) {
                result.add(new MonthFigures(month, sums[0], sums[1], month.isBefore(current)));
            }
        });
        return result;
    }

    /** @param monthlyBudget the user's monthly budget, or null when none is set */
    public static HealthScore calculate(List<MonthFigures> months, BigDecimal monthlyBudget, String currency) {
        List<MonthFigures> used = months.stream().filter(MonthFigures::complete).toList();
        boolean provisional = false;
        if (used.isEmpty()) {
            used = months;
            provisional = !months.isEmpty();
        }
        if (used.isEmpty()) {
            return HealthScore.unavailable("Add and approve some transactions to see your score.");
        }
        int n = used.size();
        List<Candidate> factors = new ArrayList<>();

        // Savings rate (pooled over months that have income)
        BigDecimal income = BigDecimal.ZERO;
        BigDecimal net = BigDecimal.ZERO;
        for (MonthFigures m : used) {
            if (m.income().signum() > 0) {
                income = income.add(m.income());
                net = net.add(m.net());
            }
        }
        if (income.signum() > 0) {
            double rate = net.doubleValue() / income.doubleValue();
            factors.add(Candidate.of("Savings rate", W_SAVINGS, clamp(rate / SAVINGS_TARGET * 100),
                "You keep " + Math.round(rate * 100) + "% of your income on average. A common target is 20% or more."));
        } else {
            factors.add(Candidate.excluded("Savings rate", "No income recorded in these months."));
        }

        // Budget adherence
        if (monthlyBudget != null && monthlyBudget.signum() > 0) {
            double total = 0;
            int within = 0;
            for (MonthFigures m : used) {
                double ratio = m.spending().doubleValue() / monthlyBudget.doubleValue();
                total += ratio <= 1 ? 100 : clamp((1.5 - ratio) / 0.5 * 100);
                within += ratio <= 1 ? 1 : 0;
            }
            factors.add(Candidate.of("Budget adherence", W_BUDGET, total / n,
                "Within your " + MoneyFormatter.formatWhole(monthlyBudget, currency) + " budget in " + within
                    + " of " + n + (n == 1 ? " month." : " months.")));
        } else {
            factors.add(Candidate.excluded("Budget adherence", "Set a monthly budget to include this factor."));
        }

        // Spending consistency
        List<Double> spends = used.stream().map(m -> m.spending().doubleValue()).toList();
        double mean = spends.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        if (n >= 3 && mean > 0) {
            double variance = spends.stream().mapToDouble(s -> (s - mean) * (s - mean)).sum() / n;
            double cv = Math.sqrt(variance) / mean;
            factors.add(Candidate.of("Spending consistency", W_CONSISTENCY, clamp((1 - cv / 0.5) * 100),
                "Monthly spending varies by about " + Math.round(cv * 100) + "% around its average."));
        } else {
            factors.add(Candidate.excluded("Spending consistency", "Needs at least 3 months of spending."));
        }

        // Cash-flow stability
        double flowTotal = 0;
        int positive = 0;
        for (MonthFigures m : used) {
            if (m.net().signum() >= 0) {
                flowTotal += 100;
                positive++;
            } else {
                BigDecimal base = m.income().signum() > 0 ? m.income() : m.spending();
                double shortfall = m.net().negate().doubleValue() / base.doubleValue();
                flowTotal += clamp(100 - shortfall * 200);
            }
        }
        factors.add(Candidate.of("Cash-flow stability", W_CASHFLOW, flowTotal / n,
            "Income covered spending in " + positive + " of " + n + (n == 1 ? " month." : " months.")));

        double weightSum = factors.stream().filter(f -> f.included).mapToInt(f -> f.weight).sum();
        double weighted = factors.stream().filter(f -> f.included).mapToDouble(f -> f.score * f.weight).sum();
        int score = (int) Math.round(weighted / weightSum);

        List<HealthScore.Factor> view = new ArrayList<>();
        for (Candidate f : factors) {
            view.add(new HealthScore.Factor(f.name, f.included,
                f.included ? (int) Math.round(f.weight * 100 / weightSum) : 0,
                f.included ? (int) Math.round(f.score) : 0, f.detail));
        }
        return new HealthScore(true, score, label(score), provisional, view,
            tips(factors, income, net, n, used, monthlyBudget, currency, provisional));
    }

    static String label(int score) {
        if (score >= 80) {
            return "Excellent";
        }
        if (score >= 65) {
            return "Good";
        }
        if (score >= 50) {
            return "Fair";
        }
        return score >= 35 ? "Needs attention" : "At risk";
    }

    private static List<String> tips(List<Candidate> factors, BigDecimal income, BigDecimal net, int n,
                                     List<MonthFigures> used, BigDecimal budget, String currency, boolean provisional) {
        List<String> tips = new ArrayList<>();
        if (provisional) {
            tips.add("This score uses only the current month so far. It becomes more reliable once a full month is complete.");
        }
        List<Candidate> weak = factors.stream().filter(f -> f.included && f.score < 70)
            .sorted(Comparator.comparingDouble(f -> f.score)).toList();
        for (Candidate f : weak) {
            switch (f.name) {
                case "Savings rate" -> {
                    double monthlyIncome = income.doubleValue() / n;
                    double target = monthlyIncome * SAVINGS_TARGET - net.doubleValue() / n;
                    tips.add("Aim to keep 20% of your income. That means saving about "
                        + MoneyFormatter.formatWhole(BigDecimal.valueOf(Math.max(0, target)).setScale(0, RoundingMode.HALF_UP), currency)
                        + " more each month. Try the What-If Simulator to see which category to trim.");
                }
                case "Budget adherence" -> {
                    long over = used.stream().filter(m -> budget != null && m.spending().compareTo(budget) > 0).count();
                    tips.add("You went over budget in " + over + (over == 1 ? " month. " : " months. ")
                        + "Either trim your biggest category or set a more realistic budget you can keep.");
                }
                case "Spending consistency" -> tips.add(
                    "Your monthly spending swings a lot. Plan fixed amounts for bills and put a cap on discretionary categories.");
                case "Cash-flow stability" -> tips.add(
                    "Some months you spent more than you earned. Build a small buffer so a bad month does not turn into debt.");
                default -> { /* no tip */ }
            }
        }
        if (weak.isEmpty() && !provisional) {
            tips.add("Your habits look healthy. Keep your savings rate steady and review SpendLeaks monthly.");
        }
        return tips;
    }

    private static double clamp(double value) {
        return Math.max(0, Math.min(100, value));
    }

    private static final class Candidate {
        final String name;
        final boolean included;
        final int weight;
        final double score;
        final String detail;

        private Candidate(String name, boolean included, int weight, double score, String detail) {
            this.name = name;
            this.included = included;
            this.weight = weight;
            this.score = score;
            this.detail = detail;
        }

        static Candidate of(String name, int weight, double score, String detail) {
            return new Candidate(name, true, weight, score, detail);
        }

        static Candidate excluded(String name, String detail) {
            return new Candidate(name, false, 0, 0, detail);
        }
    }
}
