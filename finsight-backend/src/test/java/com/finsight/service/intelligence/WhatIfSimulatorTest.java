package com.finsight.service.intelligence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.finsight.domain.enums.TransactionType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WhatIfSimulatorTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 26);

    /** 90 days of data: income 3 x 50,000; Restaurants 3 x 6,000; Groceries 3 x 9,000; Rent 3 x 15,000. */
    private static WhatIfSimulator.Baseline baseline() {
        List<LedgerEntry> entries = new ArrayList<>();
        for (int month = 0; month < 3; month++) {
            LocalDate base = TODAY.minusDays(30L * month);
            entries.add(new LedgerEntry(base.minusDays(1), new BigDecimal("50000"), TransactionType.CREDIT, "Salary", "Salary"));
            entries.add(new LedgerEntry(base.minusDays(2), new BigDecimal("6000"), TransactionType.DEBIT, "Cafe", "Restaurants"));
            entries.add(new LedgerEntry(base.minusDays(3), new BigDecimal("9000"), TransactionType.DEBIT, "Store", "Groceries"));
            entries.add(new LedgerEntry(base.minusDays(4), new BigDecimal("15000"), TransactionType.DEBIT, "Landlord", "Rent"));
        }
        // anchor the oldest data exactly 89 days back so the window is a full 90 days
        entries.add(new LedgerEntry(TODAY.minusDays(89), new BigDecimal("1"), TransactionType.DEBIT, "Tiny", "Other"));
        return WhatIfSimulator.baseline(entries, TODAY, 6);
    }

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }

    @Test
    void baselineAveragesPerMonthAndOrdersCategoriesBySpend() {
        WhatIfSimulator.Baseline baseline = baseline();
        assertTrue(baseline.hasData());
        assertEquals(90, baseline.windowDays());
        assertEquals(0, baseline.monthlyIncome().compareTo(bd("50000")));
        assertEquals("Rent", baseline.categories().get(0).name());
        assertEquals("Groceries", baseline.categories().get(1).name());
        assertEquals(0, baseline.categories().get(2).monthlyAverage().compareTo(bd("6000")));
        assertEquals(0, baseline.monthlySavings().compareTo(baseline.monthlyIncome().subtract(baseline.monthlySpending())));
    }

    @Test
    void reducingFoodByFifteenHundredSavesNineThousandInSixMonths() {
        WhatIfSimulator.Result result = WhatIfSimulator.simulate(baseline(), Map.of("Restaurants", bd("1500")), 6, null, "INR");
        assertEquals(0, result.extraSaved().compareTo(bd("9000")));
        assertEquals(6, result.points().size());
        assertEquals(0, result.points().get(0).extraSaved().compareTo(bd("1500")));
        assertEquals(0, result.points().get(5).extraSaved().compareTo(bd("9000")));
        assertTrue(result.summary().contains("Restaurants"));
        assertTrue(result.summary().contains("6 months"));
    }

    @Test
    void savingsRateImprovesWithAReduction() {
        WhatIfSimulator.Result result = WhatIfSimulator.simulate(baseline(), Map.of("Restaurants", bd("2000")), 12, null, "INR");
        assertTrue(result.savingsRateAfter().compareTo(result.savingsRateBefore()) > 0);
        assertEquals(0, result.newMonthlySavings().subtract(baseline().monthlySavings()).compareTo(bd("2000")));
    }

    @Test
    void reductionIsCappedAtWhatIsActuallySpent() {
        WhatIfSimulator.Result result = WhatIfSimulator.simulate(baseline(), Map.of("Restaurants", bd("999999")), 3, null, "INR");
        assertEquals(0, result.monthlyReduction().compareTo(bd("6000")));
    }

    @Test
    void unknownCategoryAndNegativeAmountsAreIgnored() {
        WhatIfSimulator.Result result = WhatIfSimulator.simulate(baseline(),
            Map.of("Casino", bd("500"), "Restaurants", bd("-100")), 6, null, "INR");
        assertEquals(0, result.monthlyReduction().signum());
        assertTrue(result.applied().isEmpty());
        assertEquals(0, result.extraSaved().signum());
    }

    @Test
    void annualReturnGrowsTheSavings() {
        WhatIfSimulator.Result result = WhatIfSimulator.simulate(baseline(), Map.of("Restaurants", bd("1500")), 6, bd("12"), "INR");
        // 1,500 deposited at the end of each month at 1% a month: 1500 * ((1.01^6 - 1) / 0.01) = 9,228.02
        assertEquals(0, result.extraSaved().compareTo(bd("9228.02")));
    }

    @Test
    void horizonIsClamped() {
        assertEquals(1, WhatIfSimulator.simulate(baseline(), Map.of(), 0, null, "INR").months());
        assertEquals(60, WhatIfSimulator.simulate(baseline(), Map.of(), 500, null, "INR").months());
    }

    @Test
    void noDataGivesEmptyBaseline() {
        WhatIfSimulator.Baseline empty = WhatIfSimulator.baseline(List.of(), TODAY, 6);
        assertFalse(empty.hasData());
        WhatIfSimulator.Result result = WhatIfSimulator.simulate(empty, Map.of("Restaurants", bd("100")), 6, null, "INR");
        assertEquals(0, result.extraSaved().signum());
    }
}
