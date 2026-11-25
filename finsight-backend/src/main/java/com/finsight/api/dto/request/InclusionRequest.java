package com.finsight.api.dto.request;

import jakarta.validation.constraints.NotNull;

public record InclusionRequest(@NotNull Boolean included) {
}