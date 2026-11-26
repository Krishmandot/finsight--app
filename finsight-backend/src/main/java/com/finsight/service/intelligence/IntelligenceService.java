package com.finsight.service.intelligence;

import com.finsight.domain.entity.Transaction;
import com.finsight.repository.TransactionRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Loads a user's reportable transactions (approved, included, one currency) and hands them to the
 * SpendLeak, Health Score and What-If algorithms. All the calculation lives in those plain Java classes.
 */
@Service
public class IntelligenceService {

    /** Calendar months of history loaded, including the current one. */
    private static final int HISTORY_MONTHS = 8;
    private static final int SLIDER_CATEGORIES = 6;

    private final TransactionRepository transactionRepository;
    private final BudgetService budgetService;

    public IntelligenceService(TransactionRepository transactionRepository, BudgetService budgetService) {
        this.transactionRepository = transactionRepository;
        this.budgetService = budgetService;
    }

    @Transactional(readOnly = true)
    public LeakReport leaks(UUID userId, String currency, LocalDate today) {
        return SpendLeakDetector.detect(ledger(userId, currency, today), today, currency);
    }

    @Transactional(readOnly = true)
    public HealthScore healthScore(UUID userId, String currency, LocalDate today) {
        List<MonthFigures> months = HealthScoreCalculator.monthlyFigures(ledger(userId, currency, today), today, 6);
        BigDecimal budget = budgetService.find(userId).orElse(null);
        return HealthScoreCalculator.calculate(months, budget, currency);
    }

    @Transactional(readOnly = true)
    public WhatIfSimulator.Baseline whatIfBaseline(UUID userId, String currency, LocalDate today) {
        return WhatIfSimulator.baseline(ledger(userId, currency, today), today, SLIDER_CATEGORIES);
    }

    @Transactional(readOnly = true)
    public WhatIfSimulator.Result simulate(UUID userId, String currency, LocalDate today,
                                           Map<String, BigDecimal> reductions, int months, BigDecimal annualReturnPercent) {
        return WhatIfSimulator.simulate(whatIfBaseline(userId, currency, today), reductions, months,
            annualReturnPercent, currency);
    }

    private List<LedgerEntry> ledger(UUID userId, String currency, LocalDate today) {
        LocalDate from = YearMonth.from(today).minusMonths(HISTORY_MONTHS - 1L).atDay(1);
        return transactionRepository.findReportable(userId, currency, from, today).stream()
            .filter(t -> t.getTransactionDate() != null && t.getAmount() != null && t.getType() != null)
            .map(IntelligenceService::toEntry)
            .toList();
    }

    private static LedgerEntry toEntry(Transaction t) {
        String category = t.getCategory() == null ? null : t.getCategory().getName();
        return new LedgerEntry(t.getTransactionDate(), t.getAmount(), t.getType(), t.getRedactedDescription(), category);
    }
}
