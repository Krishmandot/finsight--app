package com.finsight.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ProfileRequest(
    @NotBlank
    @Size(max = 100)
    String displayName,
    @NotBlank
    @Pattern(regexp = "[A-Z]{3}", message = "Currency must be a three-letter ISO code")
    String preferredCurrency
) {
}
