package com.finsight.api.controller;

import com.finsight.domain.entity.AppUser;
import com.finsight.service.analytics.AnalyticsService;
import com.finsight.service.analytics.CategoryAmount;
import com.finsight.service.analytics.CategoryComparison;
import com.finsight.service.analytics.CurrencyNotice;
import com.finsight.service.analytics.Insight;
import com.finsight.service.analytics.MonthlyPoint;
import com.finsight.service.analytics.PeriodSummary;
import com.finsight.service.auth.AuthService;
import com.finsight.service.transaction.TransactionService;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only figures computed from reportable transactions. {@code currency} defaults to the caller's
 * preferred currency.
 */
@RestController
@RequestMapping("/api/v1/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsService analyticsService;
    private final AuthService authService;
    private final TransactionService transactionService;

    public record MonthOverview(YearMonth month, PeriodSummary summary, List<CategoryAmount> spendingByCategory,
                                List<Insight> insights, CurrencyNotice otherCurrencies, long pendingReview) {
    }

    public record MonthComparison(PeriodSummary first, PeriodSummary second, List<CategoryComparison> categories) {
    }

    @GetMapping("/summary")
    public PeriodSummary summary(@AuthenticationPrincipal AppUser user,
                                 @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                 @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                 @RequestParam(required = false) String currency) {
        return analyticsService.summary(user.getId(), currency(user, currency), from, to);
    }

    @GetMapping("/month")
    public MonthOverview month(@AuthenticationPrincipal AppUser user,
                               @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth month,
                               @RequestParam(required = false) String currency) {
        YearMonth target = month == null ? YearMonth.now() : month;
        String code = currency(user, currency);
        LocalDate from = target.atDay(1);
        LocalDate to = target.atEndOfMonth();
        long pending = transactionService.pendingCount(user.getId());
        return new MonthOverview(target,
            analyticsService.summary(user.getId(), code, from, to),
            analyticsService.spendingByCategory(user.getId(), code, from, to),
            analyticsService.insights(user.getId(), code, target, LocalDate.now(), pending),
            analyticsService.currencyNotice(user.getId(), code, from, to),
            pending);
    }

    @GetMapping("/trend")
    public List<MonthlyPoint> trend(@AuthenticationPrincipal AppUser user,
                                    @RequestParam(defaultValue = "6") int months,
                                    @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth end,
                                    @RequestParam(required = false) String currency) {
        return analyticsService.monthlyTrend(user.getId(), currency(user, currency),
            end == null ? YearMonth.now() : end, months);
    }

    @GetMapping("/compare")
    public MonthComparison compare(@AuthenticationPrincipal AppUser user,
                                   @RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth first,
                                   @RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth second,
                                   @RequestParam(required = false) String currency) {
        String code = currency(user, currency);
        return new MonthComparison(
            analyticsService.summary(user.getId(), code, first.atDay(1), first.atEndOfMonth()),
            analyticsService.summary(user.getId(), code, second.atDay(1), second.atEndOfMonth()),
            analyticsService.compareCategories(user.getId(), code, second.atDay(1), second.atEndOfMonth(),
                first.atDay(1), first.atEndOfMonth()));
    }

    private String currency(AppUser user, String requested) {
        if (requested != null && requested.matches("[A-Z]{3}")) {
            return requested;
        }
        return authService.findUser(user.getId()).getPreferredCurrency();
    }
}
