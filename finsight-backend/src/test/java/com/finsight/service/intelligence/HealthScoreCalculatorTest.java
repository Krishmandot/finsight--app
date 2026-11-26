package com.finsight.service.intelligence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.finsight.domain.enums.TransactionType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class HealthScoreCalculatorTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 26);

    private static List<MonthFigures> months(String income, String... spending) {
        List<MonthFigures> list = new ArrayList<>();
        for (int i = 0; i < spending.length; i++) {
            list.add(new MonthFigures(YearMonth.from(TODAY).minusMonths(spending.length - i),
                new BigDecimal(income), new BigDecimal(spending[i]), true));
        }
        return list;
    }

    private static HealthScore.Factor factor(HealthScore score, String name) {
        return score.factors().stream().filter(f -> f.name().equals(name)).findFirst().orElseThrow();
    }

    @Test
    void steadySaverScoresExcellent() {
        HealthScore score = HealthScoreCalculator.calculate(months("50000", "30000", "30000", "30000", "30000"), null, "INR");
        assertTrue(score.available());
        assertEquals(100, score.score());
        assertEquals("Excellent", score.label());
        assertFalse(factor(score, "Budget adherence").included());
        assertEquals(0, factor(score, "Budget adherence").weight());
    }

    @Test
    void overspenderScoresLowAndGetsSavingsTip() {
        HealthScore score = HealthScoreCalculator.calculate(months("30000", "36000", "36000", "36000"), null, "INR");
        assertTrue(score.score() < 60);
        assertEquals(0, factor(score, "Savings rate").score());
        assertTrue(score.tips().stream().anyMatch(t -> t.contains("20%")));
    }

    @Test
    void budgetFactorScoresOverspendingProportionally() {
        HealthScore score = HealthScoreCalculator.calculate(months("50000", "36000", "36000", "36000"),
            new BigDecimal("30000"), "INR");
        HealthScore.Factor budget = factor(score, "Budget adherence");
        assertTrue(budget.included());
        assertEquals(60, budget.score()); // 120% of budget => (1.5 - 1.2) / 0.5
        assertTrue(budget.detail().contains("0 of 3"));
    }

    @Test
    void withinBudgetScoresFull() {
        HealthScore score = HealthScoreCalculator.calculate(months("50000", "20000", "25000", "28000"),
            new BigDecimal("30000"), "INR");
        assertEquals(100, factor(score, "Budget adherence").score());
    }

    @Test
    void erraticSpendingLowersConsistency() {
        HealthScore calm = HealthScoreCalculator.calculate(months("60000", "30000", "31000", "30500"), null, "INR");
        HealthScore wild = HealthScoreCalculator.calculate(months("60000", "10000", "50000", "20000"), null, "INR");
        assertTrue(factor(calm, "Spending consistency").score() > factor(wild, "Spending consistency").score());
    }

    @Test
    void consistencyNeedsThreeMonths() {
        HealthScore score = HealthScoreCalculator.calculate(months("50000", "30000", "30000"), null, "INR");
        assertFalse(factor(score, "Spending consistency").included());
    }

    @Test
    void effectiveWeightsAlwaysAddUpToAboutOneHundred() {
        HealthScore withBudget = HealthScoreCalculator.calculate(months("50000", "30000", "30000", "30000"),
            new BigDecimal("40000"), "INR");
        HealthScore without = HealthScoreCalculator.calculate(months("50000", "30000"), null, "INR");
        for (HealthScore s : List.of(withBudget, without)) {
            int total = s.factors().stream().mapToInt(HealthScore.Factor::weight).sum();
            assertTrue(total >= 99 && total <= 101, "weights add to " + total);
        }
    }

    @Test
    void cashFlowDropsWhenSpendingExceedsIncome() {
        HealthScore score = HealthScoreCalculator.calculate(months("40000", "30000", "52000", "30000"), null, "INR");
        HealthScore.Factor flow = factor(score, "Cash-flow stability");
        assertTrue(flow.score() < 100 && flow.score() > 0);
        assertTrue(flow.detail().contains("2 of 3"));
    }

    @Test
    void noDataMeansNoScore() {
        HealthScore score = HealthScoreCalculator.calculate(List.of(), null, "INR");
        assertFalse(score.available());
    }

    @Test
    void onlyCurrentMonthGivesProvisionalScore() {
        List<MonthFigures> only = List.of(new MonthFigures(YearMonth.from(TODAY), new BigDecimal("50000"),
            new BigDecimal("20000"), false));
        HealthScore score = HealthScoreCalculator.calculate(only, null, "INR");
        assertTrue(score.available());
        assertTrue(score.provisional());
    }

    @Test
    void monthlyFiguresNetRefundsAndIgnoreTransfers() {
        List<LedgerEntry> entries = List.of(
            new LedgerEntry(LocalDate.of(2026, 10, 3), new BigDecimal("50000"), TransactionType.CREDIT, "Salary", "Salary"),
            new LedgerEntry(LocalDate.of(2026, 10, 9), new BigDecimal("10000"), TransactionType.DEBIT, "Rent", "Rent"),
            new LedgerEntry(LocalDate.of(2026, 10, 12), new BigDecimal("1000"), TransactionType.REFUND, "Refund", "Shopping"),
            new LedgerEntry(LocalDate.of(2026, 10, 15), new BigDecimal("99999"), TransactionType.TRANSFER, "Own account", null),
            new LedgerEntry(LocalDate.of(2026, 11, 5), new BigDecimal("2000"), TransactionType.DEBIT, "Cafe", "Restaurants"));
        List<MonthFigures> figures = HealthScoreCalculator.monthlyFigures(entries, TODAY, 6);

        assertEquals(2, figures.size());
        MonthFigures october = figures.get(0);
        assertEquals(0, october.spending().compareTo(new BigDecimal("9000")));
        assertEquals(0, october.income().compareTo(new BigDecimal("50000")));
        assertTrue(october.complete());
        assertFalse(figures.get(1).complete());
    }

    @Test
    void labelBoundaries() {
        assertEquals("Excellent", HealthScoreCalculator.label(80));
        assertEquals("Good", HealthScoreCalculator.label(79));
        assertEquals("Fair", HealthScoreCalculator.label(50));
        assertEquals("Needs attention", HealthScoreCalculator.label(49));
        assertEquals("At risk", HealthScoreCalculator.label(34));
    }
}
