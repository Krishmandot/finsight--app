package com.finsight.service.intelligence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.finsight.domain.enums.TransactionType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SpendLeakDetectorTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 26);

    private static LedgerEntry debit(LocalDate date, String amount, String description, String category) {
        return new LedgerEntry(date, new BigDecimal(amount), TransactionType.DEBIT, description, category);
    }

    private static Optional<SpendLeak> find(LeakReport report, SpendLeak.Type type) {
        return report.leaks().stream().filter(l -> l.type() == type).findFirst();
    }

    /** Four months of ordinary background spending so averages and history exist. */
    private static List<LedgerEntry> background() {
        List<LedgerEntry> entries = new ArrayList<>();
        for (int day = 0; day < 130; day += 5) {
            entries.add(debit(TODAY.minusDays(day), "400", "Local store purchase " + day, "Groceries"));
        }
        return entries;
    }

    @Test
    void detectsMonthlySubscriptionAndExplainsYearlyCost() {
        List<LedgerEntry> entries = background();
        for (int i = 0; i < 5; i++) {
            entries.add(debit(TODAY.minusDays(3 + 30L * i), "649", "NETFLIX.COM 88" + i, "Entertainment"));
        }
        LeakReport report = SpendLeakDetector.detect(entries, TODAY, "INR");

        SpendLeak leak = find(report, SpendLeak.Type.RECURRING_PAYMENT).orElseThrow();
        assertEquals("Recurring payment: Netflix Com", leak.title());
        assertEquals(0, leak.impact().compareTo(new BigDecimal("649")));
        assertTrue(leak.detail().contains("5 charges"));
        assertTrue(leak.monthlyImpact());
        assertTrue(report.monthlyImpact().compareTo(new BigDecimal("649")) >= 0);
    }

    @Test
    void ignoresSubscriptionThatStoppedMonthsAgo() {
        List<LedgerEntry> entries = background();
        for (int i = 0; i < 4; i++) {
            entries.add(debit(TODAY.minusDays(100 + 30L * i), "299", "OLDGYM MEMBERSHIP", "Health"));
        }
        assertFalse(find(SpendLeakDetector.detect(entries, TODAY, "INR"), SpendLeak.Type.RECURRING_PAYMENT).isPresent());
    }

    @Test
    void ignoresIrregularPurchasesAtSameMerchant() {
        List<LedgerEntry> entries = background();
        int[] gaps = {2, 9, 4, 17, 6};
        int[] amounts = {180, 460, 95, 720, 310};
        int day = 1;
        for (int i = 0; i < gaps.length; i++) {
            day += gaps[i];
            entries.add(debit(TODAY.minusDays(day), String.valueOf(amounts[i]), "SWIGGY ORDER " + i, "Restaurants"));
        }
        assertFalse(find(SpendLeakDetector.detect(entries, TODAY, "INR"), SpendLeak.Type.RECURRING_PAYMENT).isPresent());
    }

    @Test
    void fixedEssentialCostIsNotCountedAsLeakage() {
        List<LedgerEntry> entries = background();
        for (int i = 0; i < 4; i++) {
            entries.add(debit(TODAY.minusDays(2 + 30L * i), "12000", "HOUSE RENT LANDLORD", "Rent"));
        }
        SpendLeak leak = find(SpendLeakDetector.detect(entries, TODAY, "INR"), SpendLeak.Type.RECURRING_PAYMENT).orElseThrow();
        assertEquals(SpendLeak.Severity.LOW, leak.severity());
        assertEquals(0, leak.impact().signum());
    }

    @Test
    void flagsPriceIncreaseOnSubscription() {
        List<LedgerEntry> entries = background();
        String[] amounts = {"199", "199", "199", "219", "219"};
        for (int i = 0; i < 5; i++) {
            entries.add(debit(TODAY.minusDays(3 + 30L * (4 - i)), amounts[i], "STREAMBOX PLUS", "Entertainment"));
        }
        SpendLeak leak = find(SpendLeakDetector.detect(entries, TODAY, "INR"), SpendLeak.Type.RECURRING_PAYMENT).orElseThrow();
        assertTrue(leak.detail().contains("The price has risen from"));
        assertEquals(0, leak.impact().compareTo(new BigDecimal("219")));
    }

    @Test
    void flagsUnusuallyLargeExpenseInUsualCategory() {
        List<LedgerEntry> entries = background();
        for (int i = 0; i < 7; i++) {
            entries.add(debit(TODAY.minusDays(40 + 9L * i), "800", "Fashion shop " + i, "Shopping"));
        }
        entries.add(debit(TODAY.minusDays(4), "9500", "Electronics showroom", "Shopping"));

        SpendLeak spike = find(SpendLeakDetector.detect(entries, TODAY, "INR"), SpendLeak.Type.UNUSUAL_SPIKE).orElseThrow();
        assertEquals(SpendLeak.Severity.HIGH, spike.severity());
        assertEquals(0, spike.impact().compareTo(new BigDecimal("8700")));
        assertFalse(spike.monthlyImpact());
    }

    @Test
    void doesNotFlagSpikeWithoutEnoughCategoryHistory() {
        List<LedgerEntry> entries = background();
        entries.add(debit(TODAY.minusDays(60), "800", "Fashion shop", "Shopping"));
        entries.add(debit(TODAY.minusDays(4), "9500", "Electronics showroom", "Shopping"));
        assertFalse(find(SpendLeakDetector.detect(entries, TODAY, "INR"), SpendLeak.Type.UNUSUAL_SPIKE).isPresent());
    }

    @Test
    void flagsSameAmountChargedTwiceByTheSameMerchant() {
        List<LedgerEntry> entries = background();
        entries.add(debit(TODAY.minusDays(6), "1499", "ZOMATO ORDER 5521", "Restaurants"));
        entries.add(debit(TODAY.minusDays(6), "1499", "ZOMATO ORDER 5522", "Restaurants"));

        SpendLeak duplicate = find(SpendLeakDetector.detect(entries, TODAY, "INR"), SpendLeak.Type.POSSIBLE_DUPLICATE).orElseThrow();
        assertTrue(duplicate.title().contains("Zomato"));
        assertEquals(0, duplicate.impact().compareTo(new BigDecimal("1499")));
    }

    @Test
    void doesNotFlagSameAmountOnDifferentDaysAsDuplicate() {
        List<LedgerEntry> entries = background();
        entries.add(debit(TODAY.minusDays(12), "250", "CHAI POINT", "Restaurants"));
        entries.add(debit(TODAY.minusDays(8), "250", "CHAI POINT", "Restaurants"));
        assertFalse(find(SpendLeakDetector.detect(entries, TODAY, "INR"), SpendLeak.Type.POSSIBLE_DUPLICATE).isPresent());
    }

    @Test
    void reportsCategoryUpThirtyEightPercentAgainstItsOwnAverage() {
        List<LedgerEntry> entries = new ArrayList<>();
        // Three earlier 30-day windows: 5,000 on Restaurants and 8,000 on Groceries each.
        for (int window = 1; window <= 3; window++) {
            LocalDate end = TODAY.minusDays(30L * window);
            entries.add(debit(end.minusDays(2), "2500", "Cafe A", "Restaurants"));
            entries.add(debit(end.minusDays(15), "2500", "Cafe B", "Restaurants"));
            entries.add(debit(end.minusDays(5), "8000", "Supermarket", "Groceries"));
        }
        // Current window: Restaurants 6,900 (+38%), Groceries unchanged.
        entries.add(debit(TODAY.minusDays(3), "2300", "Cafe C", "Restaurants"));
        entries.add(debit(TODAY.minusDays(10), "2300", "Cafe D", "Restaurants"));
        entries.add(debit(TODAY.minusDays(20), "2300", "Cafe E", "Restaurants"));
        entries.add(debit(TODAY.minusDays(7), "8000", "Supermarket", "Groceries"));

        LeakReport report = SpendLeakDetector.detect(entries, TODAY, "INR");
        SpendLeak surge = find(report, SpendLeak.Type.CATEGORY_SURGE).orElseThrow();
        assertEquals("Restaurants spending is up 38%", surge.title());
        assertEquals(0, surge.impact().compareTo(new BigDecimal("1900")));
        assertEquals(1, report.leaks().stream().filter(l -> l.type() == SpendLeak.Type.CATEGORY_SURGE).count());
    }

    @Test
    void reportsLimitedHistoryInsteadOfGuessing() {
        List<LedgerEntry> entries = new ArrayList<>();
        for (int day = 0; day < 40; day += 4) {
            entries.add(debit(TODAY.minusDays(day), "500", "Cafe " + day, "Restaurants"));
        }
        LeakReport report = SpendLeakDetector.detect(entries, TODAY, "INR");
        assertFalse(report.enoughHistory());
        assertNotNull(report.historyNote());
        assertFalse(find(report, SpendLeak.Type.CATEGORY_SURGE).isPresent());
    }

    @Test
    void emptyInputGivesEmptyReport() {
        LeakReport report = SpendLeakDetector.detect(List.of(), TODAY, "INR");
        assertTrue(report.leaks().isEmpty());
        assertEquals(0, report.monthlyImpact().signum());
        assertFalse(report.enoughHistory());
    }

    @Test
    void merchantKeyIgnoresReferenceNumbersAndBankWords() {
        assertEquals("netflix com", MerchantKey.of("UPI/NETFLIX.COM/8812/Payment"));
        assertEquals(MerchantKey.of("ZOMATO ORDER 5521"), MerchantKey.of("zomato order 9984"));
        assertEquals("", MerchantKey.of("1234 5678"));
        assertEquals("Netflix Com", MerchantKey.display("netflix com"));
    }
}
