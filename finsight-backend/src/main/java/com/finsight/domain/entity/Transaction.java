package com.finsight.domain.entity;

import com.finsight.domain.enums.CategorySource;
import com.finsight.domain.enums.IngestionSource;
import com.finsight.domain.enums.TransactionReviewStatus;
import com.finsight.domain.enums.TransactionType;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A single money movement owned by one user.
 *
 * <p>Reporting invariant: a transaction counts towards dashboards, analytics and reports only when it is
 * not deleted, its review status is {@code APPROVED} and {@code includedInReports} is true. The database
 * enforces that only approved transactions can be included.</p>
 */
@Entity
@Table(name = "transactions")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@ToString(of = {"id", "type", "reviewStatus"})
@EqualsAndHashCode(of = "id")
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "amount", precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", length = 3)
    private String currency;

    @Column(name = "transaction_date")
    private LocalDate transactionDate;

    /** Description with account/card numbers masked before storage. */
    @Column(name = "redacted_description", columnDefinition = "TEXT")
    private String redactedDescription;

    @Column(name = "notes", length = 1000)
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", length = 20)
    private TransactionType type;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @Enumerated(EnumType.STRING)
    @Column(name = "category_source", length = 20)
    private CategorySource categorySource;

    @Enumerated(EnumType.STRING)
    @Column(name = "review_status", nullable = false, length = 20)
    @Builder.Default
    private TransactionReviewStatus reviewStatus = TransactionReviewStatus.PENDING;

    @Column(name = "included_in_reports", nullable = false)
    @Builder.Default
    private boolean includedInReports = false;

    @Column(name = "is_duplicate", nullable = false)
    @Builder.Default
    private boolean isDuplicate = false;

    @Column(name = "duplicate_of_id")
    private UUID duplicateOfId;

    @Column(name = "import_batch_id", updatable = false)
    private UUID importBatchId;

    @CreatedDate
    @Column(name = "ingested_at", nullable = false, updatable = false)
    private LocalDateTime ingestedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "ingestion_source", length = 20)
    private IngestionSource ingestionSource;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public boolean countsInReports() {
        return deletedAt == null && reviewStatus == TransactionReviewStatus.APPROVED && includedInReports;
    }
}
