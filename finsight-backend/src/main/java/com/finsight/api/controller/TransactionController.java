package com.finsight.api.controller;

import com.finsight.api.dto.request.BulkReviewRequest;
import com.finsight.api.dto.request.CreateTransactionRequest;
import com.finsight.api.dto.request.InclusionRequest;
import com.finsight.api.dto.request.ReviewTransactionRequest;
import com.finsight.api.dto.request.UpdateTransactionRequest;
import com.finsight.api.dto.response.PageResponse;
import com.finsight.api.dto.response.TransactionResponse;
import com.finsight.domain.entity.AppUser;
import com.finsight.domain.enums.IngestionSource;
import com.finsight.domain.enums.TransactionReviewStatus;
import com.finsight.domain.enums.TransactionType;
import com.finsight.service.report.CsvExportService;
import com.finsight.service.transaction.TransactionFilter;
import com.finsight.service.transaction.TransactionService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/transactions")
@Validated
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;
    private final CsvExportService csvExportService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TransactionResponse create(
        @AuthenticationPrincipal AppUser user,
        @Valid @RequestBody CreateTransactionRequest request
    ) {
        return transactionService.create(user.getId(), request);
    }

    /**
     * Lists the caller's transactions, newest first by default. Sort keys: date, amount, description, type,
     * status, createdAt (e.g. {@code sort=amount,desc}). Page size is capped at 100.
     */
    @GetMapping
    public PageResponse<TransactionResponse> search(
        @AuthenticationPrincipal AppUser user,
        @RequestParam(name = "q", required = false) String query,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
        @RequestParam(required = false) UUID categoryId,
        @RequestParam(defaultValue = "false") boolean uncategorized,
        @RequestParam(name = "type", required = false) Set<TransactionType> types,
        @RequestParam(name = "status", required = false) Set<TransactionReviewStatus> statuses,
        @RequestParam(required = false) Boolean inReports,
        @RequestParam(required = false) IngestionSource source,
        @RequestParam(name = "duplicates", defaultValue = "false") boolean duplicatesOnly,
        @RequestParam(required = false) UUID importBatchId,
        @ParameterObject @PageableDefault(size = 25) Pageable pageable
    ) {
        TransactionFilter filter = new TransactionFilter(query, from, to, categoryId, uncategorized, types, statuses,
            inReports, source, duplicatesOnly, importBatchId);
        return PageResponse.of(transactionService.search(user.getId(), filter, pageable));
    }

    @GetMapping(value = "/export", produces = "text/csv")
    public ResponseEntity<byte[]> export(
        @AuthenticationPrincipal AppUser user,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        TransactionFilter filter = TransactionFilter.builder().from(from).to(to).build();
        byte[] csv = csvExportService.transactions(transactionService.findForExport(user.getId(), filter));
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"finsight-transactions.csv\"")
            .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
            .body(csv);
    }

    @GetMapping("/pending-count")
    public Map<String, Long> pendingCount(@AuthenticationPrincipal AppUser user) {
        return Map.of("pending", transactionService.pendingCount(user.getId()));
    }

    @GetMapping("/{transactionId}")
    public TransactionResponse findById(
        @AuthenticationPrincipal AppUser user,
        @PathVariable UUID transactionId
    ) {
        return transactionService.findById(user.getId(), transactionId);
    }

    @PutMapping("/{transactionId}")
    public TransactionResponse update(
        @AuthenticationPrincipal AppUser user,
        @PathVariable UUID transactionId,
        @Valid @RequestBody UpdateTransactionRequest request
    ) {
        return transactionService.update(user.getId(), transactionId, request);
    }

    @PatchMapping("/{transactionId}/review")
    public TransactionResponse updateReviewStatus(
        @AuthenticationPrincipal AppUser user,
        @PathVariable UUID transactionId,
        @Valid @RequestBody ReviewTransactionRequest request
    ) {
        return transactionService.updateReviewStatus(user.getId(), transactionId, request.status(),
            request.includeInReports());
    }

    @PostMapping("/review")
    public Map<String, Integer> bulkReview(
        @AuthenticationPrincipal AppUser user,
        @Valid @RequestBody BulkReviewRequest request
    ) {
        return Map.of("updated", transactionService.bulkReview(user.getId(), request.transactionIds(), request.status()));
    }

    @PatchMapping("/{transactionId}/inclusion")
    public TransactionResponse updateInclusion(
        @AuthenticationPrincipal AppUser user,
        @PathVariable UUID transactionId,
        @Valid @RequestBody InclusionRequest request
    ) {
        return transactionService.updateInclusion(user.getId(), transactionId, request.included());
    }

    @DeleteMapping("/{transactionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AppUser user, @PathVariable UUID transactionId) {
        transactionService.delete(user.getId(), transactionId);
    }
}
