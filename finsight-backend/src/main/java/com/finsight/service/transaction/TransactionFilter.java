package com.finsight.service.transaction;

import com.finsight.domain.enums.IngestionSource;
import com.finsight.domain.enums.TransactionReviewStatus;
import com.finsight.domain.enums.TransactionType;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

/**
 * Optional criteria for listing a user's transactions. Null or empty values mean "no restriction".
 *
 * @param query          case-insensitive text matched against description and notes
 * @param uncategorized  when true, only transactions without a category (ignores {@code categoryId})
 * @param inReports      true: only transactions counted in reports; false: only those not counted
 */
public record TransactionFilter(
    String query,
    LocalDate from,
    LocalDate to,
    UUID categoryId,
    boolean uncategorized,
    Set<TransactionType> types,
    Set<TransactionReviewStatus> statuses,
    Boolean inReports,
    IngestionSource source,
    boolean duplicatesOnly,
    UUID importBatchId
) {
    public static TransactionFilter none() {
        return builder().build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder toBuilder() {
        Builder b = new Builder();
        b.query = query;
        b.from = from;
        b.to = to;
        b.categoryId = categoryId;
        b.uncategorized = uncategorized;
        b.types = types;
        b.statuses = statuses;
        b.inReports = inReports;
        b.source = source;
        b.duplicatesOnly = duplicatesOnly;
        b.importBatchId = importBatchId;
        return b;
    }

    public static final class Builder {
        private String query;
        private LocalDate from;
        private LocalDate to;
        private UUID categoryId;
        private boolean uncategorized;
        private Set<TransactionType> types;
        private Set<TransactionReviewStatus> statuses;
        private Boolean inReports;
        private IngestionSource source;
        private boolean duplicatesOnly;
        private UUID importBatchId;

        public Builder query(String value) { this.query = value; return this; }
        public Builder from(LocalDate value) { this.from = value; return this; }
        public Builder to(LocalDate value) { this.to = value; return this; }
        public Builder categoryId(UUID value) { this.categoryId = value; return this; }
        public Builder uncategorized(boolean value) { this.uncategorized = value; return this; }
        public Builder types(Set<TransactionType> value) { this.types = value; return this; }
        public Builder statuses(Set<TransactionReviewStatus> value) { this.statuses = value; return this; }
        public Builder inReports(Boolean value) { this.inReports = value; return this; }
        public Builder source(IngestionSource value) { this.source = value; return this; }
        public Builder duplicatesOnly(boolean value) { this.duplicatesOnly = value; return this; }
        public Builder importBatchId(UUID value) { this.importBatchId = value; return this; }

        public TransactionFilter build() {
            return new TransactionFilter(query, from, to, categoryId, uncategorized, types, statuses, inReports,
                source, duplicatesOnly, importBatchId);
        }
    }
}
