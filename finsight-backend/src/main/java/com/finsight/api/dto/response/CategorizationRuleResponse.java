package com.finsight.api.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

public record CategorizationRuleResponse(UUID id, String keyword, CategoryResponse category, LocalDateTime createdAt) {
}
