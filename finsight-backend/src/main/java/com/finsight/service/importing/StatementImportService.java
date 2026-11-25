package com.finsight.service.importing;

import com.finsight.api.exception.IngestionException;
import com.finsight.api.exception.ResourceNotFoundException;
import com.finsight.domain.entity.ImportBatch;
import com.finsight.domain.entity.Transaction;
import com.finsight.domain.enums.ImportFormat;
import com.finsight.domain.enums.ImportStatus;
import com.finsight.domain.enums.IngestionSource;
import com.finsight.domain.enums.TransactionReviewStatus;
import com.finsight.repository.ImportBatchRepository;
import com.finsight.repository.TransactionRepository;
import com.finsight.service.categorization.CategorizationService;
import com.finsight.service.category.CategoryService;
import com.finsight.service.privacy.AuditLogger;
import com.finsight.service.transaction.TransactionFilter;
import com.finsight.service.transaction.TransactionService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Statement import workflow:
 * <ol>
 *   <li>{@link #preview}: validate and parse the file, suggest categories, flag duplicates. Nothing is stored.</li>
 *   <li>{@link #commit}: store the rows as <em>pending</em> transactions in an import batch. They are not counted
 *       anywhere until the user approves them in the review queue.</li>
 * </ol>
 * The uploaded file itself is never stored; only the parsed, redacted rows are.
 */
@Service
@RequiredArgsConstructor
public class StatementImportService {

    public static final int MAX_FILE_BYTES = 5 * 1024 * 1024;

    private final List<StatementParser> parsers;
    private final TransactionRepository transactionRepository;
    private final ImportBatchRepository importBatchRepository;
    private final CategorizationService categorizationService;
    private final CategoryService categoryService;
    private final TransactionService transactionService;
    private final AuditLogger auditLogger;

    @Transactional(readOnly = true)
    public ImportPreview preview(UUID userId, String fileName, byte[] content, ImportOptions options) {
        ParsedStatement statement = parse(fileName, content, options);
        List<DuplicateDetector.Match> duplicates = detectDuplicates(userId, statement.rows());
        CategorizationService.Suggester suggester = categorizationService.suggesterFor(userId);

        List<ImportPreview.PreviewRow> rows = new ArrayList<>();
        long exact = 0;
        long possible = 0;
        long categorized = 0;
        for (int i = 0; i < statement.rows().size(); i++) {
            ParsedStatement.Row row = statement.rows().get(i);
            DuplicateDetector.Match match = duplicates.get(i);
            Optional<CategorizationService.Suggestion> suggestion = suggester.suggest(row.description(), row.type());
            rows.add(new ImportPreview.PreviewRow(row,
                suggestion.map(s -> categoryService.toResponse(s.category())).orElse(null),
                suggestion.map(CategorizationService.Suggestion::source).orElse(null),
                match));
            exact += match.kind() == DuplicateDetector.Kind.EXACT ? 1 : 0;
            possible += match.kind() == DuplicateDetector.Kind.POSSIBLE ? 1 : 0;
            categorized += suggestion.isPresent() ? 1 : 0;
        }
        return new ImportPreview(safeFileName(fileName), statement, rows, exact, possible, categorized);
    }

    @Transactional
    public ImportResult commit(UUID userId, String fileName, byte[] content, ImportOptions options) {
        ParsedStatement statement = parse(fileName, content, options);
        if (statement.rows().isEmpty()) {
            throw new IngestionException("There are no rows to import");
        }
        List<DuplicateDetector.Match> duplicates = detectDuplicates(userId, statement.rows());
        CategorizationService.Suggester suggester = categorizationService.suggesterFor(userId);

        ImportBatch batch = importBatchRepository.save(ImportBatch.builder()
            .userId(userId)
            .fileName(safeFileName(fileName))
            .fileFormat(statement.format())
            .status(ImportStatus.IN_REVIEW)
            .totalRows(statement.rows().size() + statement.skipped().size())
            .skippedRows(statement.skipped().size())
            .build());

        IngestionSource source = statement.format() == ImportFormat.PDF ? IngestionSource.PDF : IngestionSource.STATEMENT_IMPORT;
        List<Transaction> toSave = new ArrayList<>();
        int duplicatesSkipped = 0;
        int flagged = 0;
        for (int i = 0; i < statement.rows().size(); i++) {
            ParsedStatement.Row row = statement.rows().get(i);
            DuplicateDetector.Match match = duplicates.get(i);
            if (match.kind() == DuplicateDetector.Kind.EXACT && options.skipExactDuplicates()) {
                duplicatesSkipped++;
                continue;
            }
            boolean duplicate = match.kind() != DuplicateDetector.Kind.NONE;
            flagged += duplicate ? 1 : 0;
            Optional<CategorizationService.Suggestion> suggestion = suggester.suggest(row.description(), row.type());
            toSave.add(Transaction.builder()
                .userId(userId)
                .amount(row.amount())
                .currency(row.currency())
                .transactionDate(row.date())
                .redactedDescription(row.description())
                .type(row.type())
                .category(suggestion.map(CategorizationService.Suggestion::category).orElse(null))
                .categorySource(suggestion.map(CategorizationService.Suggestion::source).orElse(null))
                .reviewStatus(TransactionReviewStatus.PENDING)
                .includedInReports(false)
                .isDuplicate(duplicate)
                .duplicateOfId(match.existingId())
                .importBatchId(batch.getId())
                .ingestionSource(source)
                .build());
        }
        transactionRepository.saveAll(toSave);

        batch.setImportedRows(toSave.size());
        batch.setDuplicateRows(duplicatesSkipped + flagged);
        if (toSave.isEmpty()) {
            batch.setStatus(ImportStatus.COMPLETED);
            batch.setCompletedAt(LocalDateTime.now());
        }
        importBatchRepository.save(batch);
        auditLogger.record(userId, "STATEMENT_IMPORTED", "ImportBatch", batch.getId().toString(),
            "Statement imported for review (" + statement.format() + ")");
        return new ImportResult(batch.getId(), toSave.size(), duplicatesSkipped, flagged, statement.skipped().size());
    }

    @Transactional(readOnly = true)
    public List<ImportBatchSummary> recentBatches(UUID userId) {
        return importBatchRepository.findTop20ByUserIdOrderByCreatedAtDesc(userId).stream()
            .map(this::toSummary)
            .toList();
    }

    @Transactional(readOnly = true)
    public ImportBatchSummary findBatch(UUID userId, UUID batchId) {
        return toSummary(findOwnedBatch(userId, batchId));
    }

    /** Rejects every transaction of the batch that is still pending. */
    @Transactional
    public int rejectPending(UUID userId, UUID batchId) {
        findOwnedBatch(userId, batchId);
        List<UUID> pending = transactionService.findForExport(userId, TransactionFilter.builder()
                .importBatchId(batchId).statuses(Set.of(TransactionReviewStatus.PENDING)).build())
            .stream().map(t -> t.id()).toList();
        int done = 0;
        for (int start = 0; start < pending.size(); start += TransactionService.MAX_BULK) {
            done += transactionService.bulkReview(userId,
                pending.subList(start, Math.min(pending.size(), start + TransactionService.MAX_BULK)),
                TransactionReviewStatus.REJECTED);
        }
        return done;
    }

    ParsedStatement parse(String fileName, byte[] content, ImportOptions options) {
        validate(fileName, content);
        StatementParser parser = parsers.stream()
            .filter(p -> p.supports(fileName, content))
            .findFirst()
            .orElseThrow(() -> new IngestionException("Unsupported file. Upload a CSV statement (.csv) or a text-based PDF statement."));
        return parser.parse(content, options);
    }

    private List<DuplicateDetector.Match> detectDuplicates(UUID userId, List<ParsedStatement.Row> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }
        LocalDate from = rows.stream().map(ParsedStatement.Row::date).min(Comparator.naturalOrder()).orElseThrow();
        LocalDate to = rows.stream().map(ParsedStatement.Row::date).max(Comparator.naturalOrder()).orElseThrow();
        return new DuplicateDetector(transactionRepository.findDuplicateCandidates(userId, from, to)).matchAll(rows);
    }

    private static void validate(String fileName, byte[] content) {
        if (content == null || content.length == 0) {
            throw new IngestionException("The file is empty");
        }
        if (content.length > MAX_FILE_BYTES) {
            throw new IngestionException("The file is larger than 5 MB");
        }
        String name = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        if (!(name.endsWith(".csv") || name.endsWith(".txt") || name.endsWith(".pdf"))) {
            throw new IngestionException("Only .csv and .pdf statements are supported");
        }
        if (name.endsWith(".pdf") && !(content.length > 4 && content[0] == '%' && content[1] == 'P'
            && content[2] == 'D' && content[3] == 'F')) {
            throw new IngestionException("This file has a .pdf extension but isn't a PDF");
        }
    }

    /** Keeps only the base name with safe characters; never trust client-supplied paths. */
    static String safeFileName(String fileName) {
        String base = fileName == null ? "statement" : fileName.replace('\\', '/');
        base = base.substring(base.lastIndexOf('/') + 1).replaceAll("[^A-Za-z0-9._ ()-]", "_").trim();
        if (base.isEmpty()) {
            base = "statement";
        }
        return base.length() > 120 ? base.substring(base.length() - 120) : base;
    }

    private ImportBatch findOwnedBatch(UUID userId, UUID batchId) {
        return importBatchRepository.findByIdAndUserId(batchId, userId)
            .orElseThrow(() -> new ResourceNotFoundException("Import", batchId));
    }

    private ImportBatchSummary toSummary(ImportBatch batch) {
        long pending = transactionRepository.countByImportBatchIdAndReviewStatusAndDeletedAtIsNull(
            batch.getId(), TransactionReviewStatus.PENDING);
        return new ImportBatchSummary(batch.getId(), batch.getFileName(), batch.getFileFormat(), batch.getStatus(),
            batch.getTotalRows(), batch.getImportedRows(), batch.getDuplicateRows(), batch.getSkippedRows(), pending,
            batch.getCreatedAt(), batch.getCompletedAt());
    }
}
