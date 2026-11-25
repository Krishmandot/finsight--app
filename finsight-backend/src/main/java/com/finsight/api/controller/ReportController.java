package com.finsight.api.controller;

import com.finsight.domain.entity.AppUser;
import com.finsight.service.auth.AuthService;
import com.finsight.service.report.CsvExportService;
import com.finsight.service.report.FinancialReport;
import com.finsight.service.report.ReportPeriodType;
import com.finsight.service.report.ReportService;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Weekly, fortnightly (15-day) and monthly reports for the period containing {@code date}. */
@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;
    private final CsvExportService csvExportService;
    private final AuthService authService;

    @GetMapping
    public FinancialReport report(@AuthenticationPrincipal AppUser user,
                                  @RequestParam(defaultValue = "MONTHLY") ReportPeriodType period,
                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                  @RequestParam(required = false) String currency) {
        return generate(user, period, date, currency);
    }

    @GetMapping(value = "/export", produces = "text/csv")
    public ResponseEntity<byte[]> export(@AuthenticationPrincipal AppUser user,
                                         @RequestParam(defaultValue = "MONTHLY") ReportPeriodType period,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                         @RequestParam(required = false) String currency) {
        FinancialReport report = generate(user, period, date, currency);
        String fileName = "finsight-" + period.noun() + "-" + report.period().start() + ".csv";
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
            .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
            .body(csvExportService.report(report));
    }

    private FinancialReport generate(AppUser user, ReportPeriodType period, LocalDate date, String currency) {
        String code = currency != null && currency.matches("[A-Z]{3}") ? currency
            : authService.findUser(user.getId()).getPreferredCurrency();
        return reportService.generate(user.getId(), code, period, date == null ? LocalDate.now() : date);
    }
}
