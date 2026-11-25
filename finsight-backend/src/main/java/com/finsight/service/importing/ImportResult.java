package com.finsight.service.importing;

import java.util.UUID;

/**
 * @param imported           rows stored as pending transactions
 * @param duplicatesSkipped  exact duplicates that were not stored
 * @param flaggedDuplicates  stored rows marked as possible duplicates for the user to decide on
 * @param skippedRows        rows that could not be parsed
 */
public record ImportResult(UUID batchId, int imported, int duplicatesSkipped, int flaggedDuplicates, int skippedRows) {
}
