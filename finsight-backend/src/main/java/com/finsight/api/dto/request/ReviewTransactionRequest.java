package com.finsight.api.dto.request;

import com.finsight.domain.enums.TransactionReviewStatus;
import jakarta.validation.constraints.NotNull;

/**
 * @param includeInReports only used when approving; defaults to true. Pending and rejected transactions are
 *                         always excluded from reports.
 */
public record ReviewTransactionRequest(@NotNull TransactionReviewStatus status, Boolean includeInReports) {

    public ReviewTransactionRequest(TransactionReviewStatus status) {
        this(status, null);
    }
}
