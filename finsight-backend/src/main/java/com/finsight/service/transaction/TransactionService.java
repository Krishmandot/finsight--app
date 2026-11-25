package com.finsight.service.transaction;

import com.finsight.api.dto.request.CreateTransactionRequest;
import com.finsight.api.dto.request.UpdateTransactionRequest;
import com.finsight.api.dto.response.CategoryResponse;
import com.finsight.api.dto.response.TransactionResponse;
import com.finsight.api.exception.BusinessRuleException;
import com.finsight.api.exception.ResourceNotFoundException;
import com.finsight.domain.entity.Category;
import com.finsight.domain.entity.ImportBatch;
import com.finsight.domain.entity.Transaction;
import com.finsight.domain.enums.CategorySource;
import com.finsight.domain.enums.ImportStatus;
import com.finsight.domain.enums.IngestionSource;
import com.finsight.domain.enums.TransactionReviewStatus;
import com.finsight.repository.ImportBatchRepository;
import com.finsight.repository.TransactionRepository;
import com.finsight.service.category.CategoryService;
import com.finsight.service.privacy.AuditLogger;
import com.finsight.service.privacy.DescriptionRedactor;
import jakarta.persistence.criteria.Predicate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns the transaction lifecycle. Every read and write is scoped to the owning user; another user's
 * transaction is indistinguishable from a missing one (404).
 *
 * <p>Review rules: new imported and API-created transactions start {@code PENDING} and are excluded from
 * reports. Approving includes the transaction in reports by default; pending or rejected transactions can
 * never be included.</p>
 */
@Service
@RequiredArgsConstructor
public class TransactionService {

    public static final int MAX_PAGE_SIZE = 100;
    public static final int MAX_EXPORT_ROWS = 50_000;
    public static final int MAX_BULK = 500;

    /** Public sort keys mapped to entity attributes. Anything else falls back to date. */
    private static final Map<String, String> SORTABLE = Map.of(
        "date", "transactionDate",
        "transactionDate", "transactionDate",
        "amount", "amount",
        "description", "redactedDescription",
        "type", "type",
        "status", "reviewStatus",
        "reviewStatus", "reviewStatus",
        "createdAt", "ingestedAt"
    );

    private final TransactionRepository transactionRepository;
    private final ImportBatchRepository importBatchRepository;
    private final CategoryService categoryService;
    private final AuditLogger auditLogger;

    @Transactional
    public TransactionResponse create(UUID userId, CreateTransactionRequest request) {
        Category category = categoryService.resolveForTransaction(userId, request.categoryId(), request.type());
        boolean approve = Boolean.TRUE.equals(request.autoApprove());
        Transaction transaction = Transaction.builder()
            .userId(userId)
            .amount(request.amount())
            .currency(request.currency())
            .transactionDate(request.transactionDate())
            .redactedDescription(DescriptionRedactor.clean(request.description()))
            .notes(cleanNotes(request.notes()))
            .type(request.type())
            .category(category)
            .categorySource(category == null ? null : CategorySource.USER)
            .reviewStatus(approve ? TransactionReviewStatus.APPROVED : TransactionReviewStatus.PENDING)
            .includedInReports(approve)
            .ingestionSource(IngestionSource.MANUAL)
            .build();

        Transaction saved = transactionRepository.save(transaction);
        auditLogger.record(userId, "TRANSACTION_CREATED", "Transaction", saved.getId().toString(),
            approve ? "Manual transaction created and approved" : "Manual transaction created for review");
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public Page<TransactionResponse> findAll(UUID userId, Pageable pageable) {
        return search(userId, TransactionFilter.none(), pageable);
    }

    @Transactional(readOnly = true)
    public Page<TransactionResponse> search(UUID userId, TransactionFilter filter, Pageable pageable) {
        int pageSize = Math.min(Math.max(pageable.getPageSize(), 1), MAX_PAGE_SIZE);
        Pageable bounded = PageRequest.of(Math.max(pageable.getPageNumber(), 0), pageSize, safeSort(pageable.getSort()));
        return transactionRepository.findAll(specification(userId, filter), bounded).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public long count(UUID userId, TransactionFilter filter) {
        return transactionRepository.count(specification(userId, filter));
    }

    /** All matching transactions (newest first), capped at {@link #MAX_EXPORT_ROWS}, for exports. */
    @Transactional(readOnly = true)
    public List<TransactionResponse> findForExport(UUID userId, TransactionFilter filter) {
        Pageable limit = PageRequest.of(0, MAX_EXPORT_ROWS, safeSort(Sort.unsorted()));
        return transactionRepository.findAll(specification(userId, filter), limit).map(this::toResponse).getContent();
    }

    @Transactional(readOnly = true)
    public List<TransactionResponse> recent(UUID userId, int limit) {
        Pageable page = PageRequest.of(0, Math.min(Math.max(limit, 1), MAX_PAGE_SIZE), safeSort(Sort.unsorted()));
        TransactionFilter notRejected = TransactionFilter.builder()
            .statuses(Set.of(TransactionReviewStatus.APPROVED, TransactionReviewStatus.PENDING)).build();
        return transactionRepository.findAll(specification(userId, notRejected), page).map(this::toResponse).getContent();
    }

    @Transactional(readOnly = true)
    public long pendingCount(UUID userId) {
        return transactionRepository.countByUserIdAndReviewStatusAndDeletedAtIsNull(userId, TransactionReviewStatus.PENDING);
    }

    @Transactional(readOnly = true)
    public long activeCount(UUID userId) {
        return transactionRepository.countByUserIdAndDeletedAtIsNull(userId);
    }

    @Transactional(readOnly = true)
    public TransactionResponse findById(UUID userId, UUID transactionId) {
        return toResponse(findOwned(userId, transactionId));
    }

    @Transactional
    public TransactionResponse update(UUID userId, UUID transactionId, UpdateTransactionRequest request) {
        Transaction transaction = findOwned(userId, transactionId);
        Category category = categoryService.resolveForTransaction(userId, request.categoryId(), request.type());
        UUID previousCategoryId = transaction.getCategory() == null ? null : transaction.getCategory().getId();

        transaction.setAmount(request.amount());
        transaction.setCurrency(request.currency());
        transaction.setTransactionDate(request.transactionDate());
        transaction.setRedactedDescription(DescriptionRedactor.clean(request.description()));
        transaction.setNotes(cleanNotes(request.notes()));
        transaction.setType(request.type());
        transaction.setCategory(category);
        if (!Objects.equals(previousCategoryId, request.categoryId())) {
            transaction.setCategorySource(category == null ? null : CategorySource.USER);
        }

        Transaction saved = transactionRepository.save(transaction);
        auditLogger.record(userId, "TRANSACTION_UPDATED", "Transaction", saved.getId().toString(),
            "Transaction details updated");
        return toResponse(saved);
    }

    @Transactional
    public TransactionResponse updateReviewStatus(UUID userId, UUID transactionId, TransactionReviewStatus status) {
        return updateReviewStatus(userId, transactionId, status, null);
    }

    @Transactional
    public TransactionResponse updateReviewStatus(UUID userId, UUID transactionId, TransactionReviewStatus status,
                                                  Boolean includeInReports) {
        Transaction transaction = findOwned(userId, transactionId);
        applyReview(transaction, status, includeInReports);
        Transaction saved = transactionRepository.save(transaction);
        auditLogger.record(userId, "TRANSACTION_REVIEW_CHANGED", "Transaction", saved.getId().toString(),
            "Transaction review status changed");
        refreshImportBatches(userId, List.of(saved));
        return toResponse(saved);
    }

    /**
     * Applies one review decision to many transactions. Transactions that do not exist or belong to someone
     * else are ignored.
     *
     * @return number of transactions updated
     */
    @Transactional
    public int bulkReview(UUID userId, Collection<UUID> transactionIds, TransactionReviewStatus status) {
        if (transactionIds == null || transactionIds.isEmpty()) {
            return 0;
        }
        if (transactionIds.size() > MAX_BULK) {
            throw new BusinessRuleException("At most " + MAX_BULK + " transactions can be reviewed at once");
        }
        List<Transaction> owned = transactionRepository.findAllByIdInAndUserIdAndDeletedAtIsNull(
            new LinkedHashSet<>(transactionIds), userId);
        owned.forEach(transaction -> applyReview(transaction, status, null));
        transactionRepository.saveAll(owned);
        auditLogger.record(userId, "TRANSACTIONS_BULK_REVIEWED", "Transaction", String.valueOf(owned.size()),
            "Bulk review status change");
        refreshImportBatches(userId, owned);
        return owned.size();
    }

    @Transactional
    public TransactionResponse updateInclusion(UUID userId, UUID transactionId, boolean included) {
        Transaction transaction = findOwned(userId, transactionId);
        if (included && transaction.getReviewStatus() != TransactionReviewStatus.APPROVED) {
            throw BusinessRuleException.conflict("Only approved transactions can be included in financial reports");
        }
        transaction.setIncludedInReports(included);

        Transaction saved = transactionRepository.save(transaction);
        auditLogger.record(userId, "TRANSACTION_INCLUSION_CHANGED", "Transaction", saved.getId().toString(),
            "Transaction report inclusion changed");
        return toResponse(saved);
    }

    @Transactional
    public void delete(UUID userId, UUID transactionId) {
        Transaction transaction = findOwned(userId, transactionId);
        transaction.setDeletedAt(LocalDateTime.now());
        transaction.setIncludedInReports(false);
        transactionRepository.save(transaction);
        auditLogger.record(userId, "TRANSACTION_DELETED", "Transaction", transaction.getId().toString(),
            "Transaction soft-deleted by owner");
        refreshImportBatches(userId, List.of(transaction));
    }

    public TransactionResponse toResponse(Transaction transaction) {
        CategoryResponse category = transaction.getCategory() == null
            ? null : categoryService.toResponse(transaction.getCategory());
        return new TransactionResponse(
            transaction.getId(), transaction.getAmount(), transaction.getCurrency(),
            transaction.getTransactionDate(), transaction.getRedactedDescription(), transaction.getType(), category,
            transaction.getReviewStatus(), transaction.isIncludedInReports(), transaction.getIngestionSource(),
            transaction.isDuplicate(), transaction.getIngestedAt(), transaction.getUpdatedAt(),
            transaction.getNotes(), transaction.getCategorySource(), transaction.getImportBatchId(),
            transaction.getDuplicateOfId()
        );
    }

    static Specification<Transaction> specification(UUID userId, TransactionFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("userId"), userId));
            predicates.add(cb.isNull(root.get("deletedAt")));
            if (filter == null) {
                return cb.and(predicates.toArray(Predicate[]::new));
            }
            if (filter.query() != null && !filter.query().isBlank()) {
                String pattern = "%" + escapeLike(filter.query().trim().toLowerCase()) + "%";
                predicates.add(cb.or(
                    cb.like(cb.lower(root.get("redactedDescription")), pattern, '\\'),
                    cb.like(cb.lower(cb.coalesce(root.get("notes"), "")), pattern, '\\')));
            }
            if (filter.from() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("transactionDate"), filter.from()));
            }
            if (filter.to() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("transactionDate"), filter.to()));
            }
            if (filter.uncategorized()) {
                predicates.add(cb.isNull(root.get("category")));
            } else if (filter.categoryId() != null) {
                predicates.add(cb.equal(root.get("category").get("id"), filter.categoryId()));
            }
            if (filter.types() != null && !filter.types().isEmpty()) {
                predicates.add(root.get("type").in(filter.types()));
            }
            if (filter.statuses() != null && !filter.statuses().isEmpty()) {
                predicates.add(root.get("reviewStatus").in(filter.statuses()));
            }
            if (filter.inReports() != null) {
                predicates.add(cb.equal(root.get("includedInReports"), filter.inReports()));
            }
            if (filter.source() != null) {
                predicates.add(cb.equal(root.get("ingestionSource"), filter.source()));
            }
            if (filter.duplicatesOnly()) {
                predicates.add(cb.isTrue(root.get("isDuplicate")));
            }
            if (filter.importBatchId() != null) {
                predicates.add(cb.equal(root.get("importBatchId"), filter.importBatchId()));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    static Sort safeSort(Sort requested) {
        List<Sort.Order> orders = new ArrayList<>();
        if (requested != null) {
            for (Sort.Order order : requested) {
                String property = SORTABLE.get(order.getProperty());
                if (property != null) {
                    orders.add(new Sort.Order(order.getDirection(), property));
                }
            }
        }
        if (orders.stream().noneMatch(o -> o.getProperty().equals("transactionDate"))) {
            orders.add(Sort.Order.desc("transactionDate"));
        }
        orders.add(Sort.Order.desc("ingestedAt"));
        orders.add(Sort.Order.asc("id"));
        return Sort.by(orders);
    }

    private static void applyReview(Transaction transaction, TransactionReviewStatus status, Boolean includeInReports) {
        transaction.setReviewStatus(status);
        if (status == TransactionReviewStatus.APPROVED) {
            transaction.setIncludedInReports(includeInReports == null || includeInReports);
        } else {
            transaction.setIncludedInReports(false);
        }
    }

    private void refreshImportBatches(UUID userId, Collection<Transaction> touched) {
        touched.stream().map(Transaction::getImportBatchId).filter(Objects::nonNull).distinct().forEach(batchId ->
            importBatchRepository.findByIdAndUserId(batchId, userId).ifPresent(batch -> refreshBatch(batch)));
    }

    private void refreshBatch(ImportBatch batch) {
        long pending = transactionRepository.countByImportBatchIdAndReviewStatusAndDeletedAtIsNull(
            batch.getId(), TransactionReviewStatus.PENDING);
        if (pending == 0 && batch.getStatus() != ImportStatus.COMPLETED) {
            batch.setStatus(ImportStatus.COMPLETED);
            batch.setCompletedAt(LocalDateTime.now());
            importBatchRepository.save(batch);
        } else if (pending > 0 && batch.getStatus() == ImportStatus.COMPLETED) {
            batch.setStatus(ImportStatus.IN_REVIEW);
            batch.setCompletedAt(null);
            importBatchRepository.save(batch);
        }
    }

    private Transaction findOwned(UUID userId, UUID transactionId) {
        return transactionRepository.findByIdAndUserIdAndDeletedAtIsNull(transactionId, userId)
            .orElseThrow(() -> new ResourceNotFoundException("Transaction", transactionId));
    }

    private static String cleanNotes(String notes) {
        if (notes == null || notes.isBlank()) {
            return null;
        }
        String cleaned = DescriptionRedactor.clean(notes);
        return cleaned.length() > 1000 ? cleaned.substring(0, 1000) : cleaned;
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
