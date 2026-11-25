package com.finsight.repository;

import com.finsight.domain.entity.Transaction;
import com.finsight.domain.enums.TransactionReviewStatus;
import com.finsight.repository.projection.CategoryTypeTotal;
import com.finsight.repository.projection.CurrencyCount;
import com.finsight.repository.projection.DayTypeTotal;
import com.finsight.repository.projection.MonthTypeTotal;
import com.finsight.repository.projection.TypeTotal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Repository;

/**
 * Every query is scoped by {@code userId}. Aggregate queries only count "reportable" rows: not deleted,
 * approved and included in reports, in a single currency.
 */
@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID>, JpaSpecificationExecutor<Transaction> {

    String REPORTABLE = " t.userId = :userId and t.deletedAt is null and t.includedInReports = true"
        + " and t.reviewStatus = com.finsight.domain.enums.TransactionReviewStatus.APPROVED"
        + " and t.currency = :currency and t.transactionDate between :from and :to ";

    @EntityGraph(attributePaths = "category")
    Page<Transaction> findAllByUserIdAndDeletedAtIsNull(UUID userId, Pageable pageable);

    @Override
    @NonNull
    @EntityGraph(attributePaths = "category")
    Page<Transaction> findAll(@NonNull Specification<Transaction> spec, @NonNull Pageable pageable);

    @Override
    @NonNull
    @EntityGraph(attributePaths = "category")
    List<Transaction> findAll(@NonNull Specification<Transaction> spec);

    @EntityGraph(attributePaths = "category")
    Optional<Transaction> findByIdAndUserIdAndDeletedAtIsNull(UUID id, UUID userId);

    @EntityGraph(attributePaths = "category")
    List<Transaction> findAllByIdInAndUserIdAndDeletedAtIsNull(Collection<UUID> ids, UUID userId);

    long countByUserIdAndReviewStatusAndDeletedAtIsNull(UUID userId, TransactionReviewStatus status);

    long countByUserIdAndDeletedAtIsNull(UUID userId);

    long countByCategoryIdAndDeletedAtIsNull(UUID categoryId);

    /** Rows of {@code [categoryId, count]} for the user's active, categorized transactions. */
    @Query("select t.category.id, count(t) from Transaction t where t.userId = :userId and t.deletedAt is null"
        + " and t.category is not null group by t.category.id")
    List<Object[]> countActiveByCategory(@Param("userId") UUID userId);

    @Query("select count(t) from Transaction t where t.userId = :userId and t.category.id = :categoryId"
        + " and t.deletedAt is null and t.type in :types")
    long countIncompatibleWithKind(@Param("userId") UUID userId, @Param("categoryId") UUID categoryId,
                                   @Param("types") Collection<com.finsight.domain.enums.TransactionType> types);

    long countByImportBatchIdAndReviewStatusAndDeletedAtIsNull(UUID importBatchId, TransactionReviewStatus status);

    /** Existing rows that an imported row could duplicate: same user, overlapping dates, not rejected. */
    @Query("select t from Transaction t where t.userId = :userId and t.deletedAt is null"
        + " and t.reviewStatus <> com.finsight.domain.enums.TransactionReviewStatus.REJECTED"
        + " and t.transactionDate between :from and :to")
    List<Transaction> findDuplicateCandidates(@Param("userId") UUID userId,
                                              @Param("from") LocalDate from,
                                              @Param("to") LocalDate to);

    @Query("select t from Transaction t left join fetch t.category where" + REPORTABLE
        + " order by t.transactionDate desc, t.ingestedAt desc")
    List<Transaction> findReportable(@Param("userId") UUID userId, @Param("currency") String currency,
                                     @Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("select new com.finsight.repository.projection.TypeTotal(t.type, sum(t.amount), count(t))"
        + " from Transaction t where" + REPORTABLE + " group by t.type")
    List<TypeTotal> sumByType(@Param("userId") UUID userId, @Param("currency") String currency,
                              @Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("select new com.finsight.repository.projection.CategoryTypeTotal(c.id, c.name, t.type, sum(t.amount), count(t))"
        + " from Transaction t left join t.category c where" + REPORTABLE + " group by c.id, c.name, t.type")
    List<CategoryTypeTotal> sumByCategoryAndType(@Param("userId") UUID userId, @Param("currency") String currency,
                                                 @Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("select new com.finsight.repository.projection.MonthTypeTotal("
        + " extract(year from t.transactionDate), extract(month from t.transactionDate), t.type, sum(t.amount))"
        + " from Transaction t where" + REPORTABLE
        + " group by extract(year from t.transactionDate), extract(month from t.transactionDate), t.type")
    List<MonthTypeTotal> sumByMonthAndType(@Param("userId") UUID userId, @Param("currency") String currency,
                                           @Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("select new com.finsight.repository.projection.DayTypeTotal(t.transactionDate, t.type, sum(t.amount))"
        + " from Transaction t where" + REPORTABLE + " group by t.transactionDate, t.type")
    List<DayTypeTotal> sumByDayAndType(@Param("userId") UUID userId, @Param("currency") String currency,
                                       @Param("from") LocalDate from, @Param("to") LocalDate to);

    /** Reportable rows in currencies other than the one being analysed (they are never summed together). */
    @Query("select new com.finsight.repository.projection.CurrencyCount(t.currency, count(t)) from Transaction t"
        + " where t.userId = :userId and t.deletedAt is null and t.includedInReports = true"
        + " and t.reviewStatus = com.finsight.domain.enums.TransactionReviewStatus.APPROVED"
        + " and t.currency <> :currency and t.transactionDate between :from and :to group by t.currency")
    List<CurrencyCount> countOtherCurrencies(@Param("userId") UUID userId, @Param("currency") String currency,
                                             @Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("select min(t.transactionDate) from Transaction t where t.userId = :userId and t.deletedAt is null"
        + " and t.includedInReports = true and t.currency = :currency")
    Optional<LocalDate> findFirstReportableDate(@Param("userId") UUID userId, @Param("currency") String currency);
}
