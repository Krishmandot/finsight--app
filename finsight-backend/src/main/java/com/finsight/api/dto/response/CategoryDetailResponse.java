package com.finsight.api.dto.response;

/** A category plus how many of the current user's active transactions use it. */
public record CategoryDetailResponse(CategoryResponse category, long transactionCount) {
}
