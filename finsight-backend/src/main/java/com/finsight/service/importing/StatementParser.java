package com.finsight.service.importing;

import com.finsight.api.exception.IngestionException;
import com.finsight.domain.enums.ImportFormat;

/**
 * Turns the bytes of one statement format into rows. Implementations must never guess silently: rows
 * they cannot interpret go to {@link ParsedStatement#skipped()} with a reason, and assumptions go to
 * {@link ParsedStatement#warnings()}. New formats (for example notification exports) plug in by adding
 * another implementation.
 */
public interface StatementParser {

    ImportFormat format();

    /** True when this parser recognizes the file (by name and content signature). */
    boolean supports(String fileName, byte[] content);

    /** @throws IngestionException when the file as a whole cannot be read */
    ParsedStatement parse(byte[] content, ImportOptions options);
}
