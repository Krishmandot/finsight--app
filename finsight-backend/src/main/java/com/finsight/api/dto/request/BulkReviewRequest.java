package com.finsight.api.dto.request;

import com.finsight.domain.enums.TransactionReviewStatus;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record BulkReviewRequest(
    @NotEmpty
    @Size(max = 500, message = "At most 500 transactions can be reviewed at once")
    List<UUID> transactionIds,
    @NotNull
    TransactionReviewStatus status
) {
}
