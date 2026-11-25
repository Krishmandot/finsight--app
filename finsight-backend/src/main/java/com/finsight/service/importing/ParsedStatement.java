package com.finsight.service.importing;

import com.finsight.domain.enums.ImportFormat;
import com.finsight.domain.enums.TransactionType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Result of parsing a statement file, before anything is stored.
 *
 * @param headers     column labels shown when adjusting the mapping (CSV only)
 * @param mapping     the mapping used (CSV only; null for PDF)
 * @param dateFormat  the date pattern used
 * @param rows        rows that were parsed successfully
 * @param skipped     rows that could not be used, with the reason
 * @param warnings    assumptions the user should double-check
 */
public record ParsedStatement(
    ImportFormat format,
    List<String> headers,
    ColumnMapping mapping,
    String dateFormat,
    List<Row> rows,
    List<SkippedRow> skipped,
    List<String> warnings
) {
    /** One usable statement row. {@code amount} is always positive; direction is carried by {@code type}. */
    public record Row(int lineNumber, LocalDate date, String description, BigDecimal amount, TransactionType type,
                      String currency) {
    }

    public record SkippedRow(int lineNumber, String reason) {
    }
}
