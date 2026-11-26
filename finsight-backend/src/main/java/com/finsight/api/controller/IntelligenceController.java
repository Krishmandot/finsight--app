package com.finsight.api.controller;

import com.finsight.api.exception.BusinessRuleException;
import com.finsight.domain.entity.AppUser;
import com.finsight.service.auth.AuthService;
import com.finsight.service.intelligence.BudgetService;
import com.finsight.service.intelligence.HealthScore;
import com.finsight.service.intelligence.IntelligenceService;
import com.finsight.service.intelligence.LeakReport;
import com.finsight.service.intelligence.WhatIfSimulator;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Financial intelligence: SpendLeak Detector, Financial Health Score and the What-If Simulator.
 * Everything is computed server-side in Java from the caller's approved transactions in their preferred currency.
 */
@RestController
@RequestMapping("/api/v1/intelligence")
public class IntelligenceController {

    private static final int MAX_REDUCTION_ROWS = 20;

    /** @param reductions category name to monthly reduction; {@code months} defaults to 6 */
    public record WhatIfRequest(Map<String, BigDecimal> reductions, Integer months, BigDecimal annualReturnPercent) {
    }

    public record BudgetRequest(BigDecimal amount) {
    }

    /** {@code amount} is null when no budget is set. */
    public record BudgetResponse(BigDecimal amount) {
    }

    private final IntelligenceService intelligenceService;
    private final BudgetService budgetService;
    private final AuthService authService;

    public IntelligenceController(IntelligenceService intelligenceService, BudgetService budgetService,
                                  AuthService authService) {
        this.intelligenceService = intelligenceService;
        this.budgetService = budgetService;
        this.authService = authService;
    }

    @GetMapping("/leaks")
    public LeakReport leaks(@AuthenticationPrincipal AppUser user) {
        return intelligenceService.leaks(user.getId(), currency(user), LocalDate.now());
    }

    @GetMapping("/health-score")
    public HealthScore healthScore(@AuthenticationPrincipal AppUser user) {
        return intelligenceService.healthScore(user.getId(), currency(user), LocalDate.now());
    }

    @GetMapping("/what-if/baseline")
    public WhatIfSimulator.Baseline baseline(@AuthenticationPrincipal AppUser user) {
        return intelligenceService.whatIfBaseline(user.getId(), currency(user), LocalDate.now());
    }

    @PostMapping("/what-if")
    public WhatIfSimulator.Result simulate(@AuthenticationPrincipal AppUser user, @RequestBody WhatIfRequest request) {
        if (request.reductions() != null && request.reductions().size() > MAX_REDUCTION_ROWS) {
            throw new BusinessRuleException("Too many categories in one simulation");
        }
        BigDecimal rate = request.annualReturnPercent();
        if (rate != null && (rate.signum() < 0 || rate.compareTo(BigDecimal.valueOf(30)) > 0)) {
            throw new BusinessRuleException("The annual return must be between 0 and 30 percent");
        }
        int months = request.months() == null ? 6 : request.months();
        return intelligenceService.simulate(user.getId(), currency(user), LocalDate.now(),
            request.reductions(), months, rate);
    }

    @GetMapping("/budget")
    public BudgetResponse budget(@AuthenticationPrincipal AppUser user) {
        return new BudgetResponse(budgetService.find(user.getId()).orElse(null));
    }

    @PutMapping("/budget")
    public BudgetResponse setBudget(@AuthenticationPrincipal AppUser user, @RequestBody BudgetRequest request) {
        budgetService.save(user.getId(), request.amount());
        return new BudgetResponse(budgetService.find(user.getId()).orElse(null));
    }

    @DeleteMapping("/budget")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clearBudget(@AuthenticationPrincipal AppUser user) {
        budgetService.clear(user.getId());
    }

    private String currency(AppUser user) {
        return authService.findUser(user.getId()).getPreferredCurrency();
    }
}
