package com.finsight.service.importing;

import com.finsight.domain.enums.ImportFormat;
import com.finsight.domain.enums.ImportStatus;
import java.time.LocalDateTime;
import java.util.UUID;

public record ImportBatchSummary(
    UUID id,
    String fileName,
    ImportFormat format,
    ImportStatus status,
    int totalRows,
    int importedRows,
    int duplicateRows,
    int skippedRows,
    long pendingRows,
    LocalDateTime createdAt,
    LocalDateTime completedAt
) {
}
