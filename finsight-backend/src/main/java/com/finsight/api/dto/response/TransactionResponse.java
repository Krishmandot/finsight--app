package com.finsight.api.dto.response;

import com.finsight.domain.enums.CategorySource;
import com.finsight.domain.enums.IngestionSource;
import com.finsight.domain.enums.TransactionReviewStatus;
import com.finsight.domain.enums.TransactionType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record TransactionResponse(
    UUID id,
    BigDecimal amount,
    String currency,
    LocalDate transactionDate,
    String description,
    TransactionType type,
    CategoryResponse category,
    TransactionReviewStatus reviewStatus,
    boolean includedInReports,
    IngestionSource source,
    boolean duplicate,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    String notes,
    CategorySource categorySource,
    UUID importBatchId,
    UUID duplicateOfId
) {
    public boolean countsInReports() {
        return reviewStatus == TransactionReviewStatus.APPROVED && includedInReports;
    }
}
