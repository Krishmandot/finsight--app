package com.finsight.service.report;

import com.finsight.api.dto.response.TransactionResponse;
import com.finsight.domain.enums.TransactionReviewStatus;
import com.finsight.domain.enums.TransactionType;
import com.finsight.repository.TransactionRepository;
import com.finsight.service.analytics.AnalyticsService;
import com.finsight.service.analytics.PeriodSummary;
import com.finsight.service.transaction.TransactionFilter;
import com.finsight.service.transaction.TransactionService;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReportService {

    private final AnalyticsService analyticsService;
    private final TransactionService transactionService;
    private final TransactionRepository transactionRepository;

    @Transactional(readOnly = true)
    public FinancialReport generate(UUID userId, String currency, ReportPeriodType type, LocalDate anchor) {
        ReportPeriod period = ReportPeriod.containing(type, anchor);
        ReportPeriod previous = period.previous();

        PeriodSummary summary = analyticsService.summary(userId, currency, period.start(), period.end());
        PeriodSummary previousSummary = analyticsService.summary(userId, currency, previous.start(), previous.end());

        List<TransactionResponse> transactions = transactionRepository
            .findReportable(userId, currency, period.start(), period.end()).stream()
            .map(transactionService::toResponse)
            .toList();
        List<TransactionResponse> largest = transactions.stream()
            .filter(t -> t.type() == TransactionType.DEBIT)
            .sorted(Comparator.comparing(TransactionResponse::amount).reversed())
            .limit(5)
            .toList();

        TransactionFilter inPeriod = TransactionFilter.builder().from(period.start()).to(period.end()).build();
        FinancialReport.ExcludedCounts excluded = new FinancialReport.ExcludedCounts(
            transactionService.count(userId, inPeriod.toBuilder().statuses(Set.of(TransactionReviewStatus.PENDING)).build()),
            transactionService.count(userId, inPeriod.toBuilder().statuses(Set.of(TransactionReviewStatus.REJECTED)).build()),
            transactionService.count(userId, inPeriod.toBuilder()
                .statuses(Set.of(TransactionReviewStatus.APPROVED)).inReports(false).build()));

        return new FinancialReport(
            period,
            previous,
            currency,
            summary,
            previousSummary.hasData() ? previousSummary : null,
            analyticsService.compareCategories(userId, currency, period.start(), period.end(),
                previous.start(), previous.end()),
            analyticsService.dailySeries(userId, currency, period.start(), period.end()),
            transactions,
            largest,
            excluded,
            analyticsService.currencyNotice(userId, currency, period.start(), period.end())
        );
    }
}
