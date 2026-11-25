package com.finsight.api.controller;

import com.finsight.api.exception.IngestionException;
import com.finsight.domain.entity.AppUser;
import com.finsight.service.auth.AuthService;
import com.finsight.service.importing.ImportBatchSummary;
import com.finsight.service.importing.ImportOptions;
import com.finsight.service.importing.ImportPreview;
import com.finsight.service.importing.ImportResult;
import com.finsight.service.importing.StatementImportService;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Statement import. {@code /preview} parses without saving; {@code /} stores the rows as pending
 * transactions for review.
 */
@RestController
@RequestMapping("/api/v1/imports")
@RequiredArgsConstructor
public class ImportController {

    private final StatementImportService importService;
    private final AuthService authService;

    @PostMapping(value = "/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ImportPreview preview(@AuthenticationPrincipal AppUser user,
                                 @RequestPart("file") MultipartFile file,
                                 @RequestParam(required = false) String dateFormat,
                                 @RequestParam(required = false) ImportOptions.AmountConvention amountConvention,
                                 @RequestParam(required = false) String pdfPassword) {
        return importService.preview(user.getId(), file.getOriginalFilename(), bytes(file),
            options(user, dateFormat, amountConvention, pdfPassword, true));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ImportResult commit(@AuthenticationPrincipal AppUser user,
                               @RequestPart("file") MultipartFile file,
                               @RequestParam(required = false) String dateFormat,
                               @RequestParam(required = false) ImportOptions.AmountConvention amountConvention,
                               @RequestParam(required = false) String pdfPassword,
                               @RequestParam(defaultValue = "true") boolean skipExactDuplicates) {
        return importService.commit(user.getId(), file.getOriginalFilename(), bytes(file),
            options(user, dateFormat, amountConvention, pdfPassword, skipExactDuplicates));
    }

    @GetMapping
    public List<ImportBatchSummary> recent(@AuthenticationPrincipal AppUser user) {
        return importService.recentBatches(user.getId());
    }

    @GetMapping("/{batchId}")
    public ImportBatchSummary batch(@AuthenticationPrincipal AppUser user, @PathVariable UUID batchId) {
        return importService.findBatch(user.getId(), batchId);
    }

    @PostMapping("/{batchId}/reject-pending")
    public Map<String, Integer> rejectPending(@AuthenticationPrincipal AppUser user, @PathVariable UUID batchId) {
        return Map.of("rejected", importService.rejectPending(user.getId(), batchId));
    }

    private ImportOptions options(AppUser user, String dateFormat, ImportOptions.AmountConvention convention,
                                  String pdfPassword, boolean skipExactDuplicates) {
        String currency = authService.findUser(user.getId()).getPreferredCurrency();
        return ImportOptions.defaults(currency)
            .withDateFormat(dateFormat)
            .withAmountConvention(convention)
            .withPdfPassword(pdfPassword)
            .withSkipExactDuplicates(skipExactDuplicates);
    }

    private static byte[] bytes(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IngestionException("The file is empty");
        }
        if (file.getSize() > StatementImportService.MAX_FILE_BYTES) {
            throw new IngestionException("The file is larger than 5 MB");
        }
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new IngestionException("The upload could not be read");
        }
    }
}
