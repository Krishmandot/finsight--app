package com.finsight.service.importing;

import com.finsight.api.dto.response.CategoryResponse;
import com.finsight.domain.enums.CategorySource;
import java.util.List;

/** What would be imported, shown to the user before anything is saved. */
public record ImportPreview(
    String fileName,
    ParsedStatement statement,
    List<PreviewRow> rows,
    long exactDuplicates,
    long possibleDuplicates,
    long categorized
) {
    public record PreviewRow(ParsedStatement.Row row, CategoryResponse suggestedCategory, CategorySource categorySource,
                             DuplicateDetector.Match duplicate) {
    }
}
