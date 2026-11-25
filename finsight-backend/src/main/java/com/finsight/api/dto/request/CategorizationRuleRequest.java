package com.finsight.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * @param applyToPending also re-categorize the user's pending transactions that match the keyword and whose
 *                       category was not chosen by the user.
 */
public record CategorizationRuleRequest(
    @NotBlank
    @Size(min = 3, max = 100, message = "Keyword must be between 3 and 100 characters")
    String keyword,
    @NotNull
    UUID categoryId,
    Boolean applyToPending
) {
}
