package com.finsight.api.dto.request;

import com.finsight.domain.enums.TransactionType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record UpdateTransactionRequest(
    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be at least 0.01")
    @DecimalMax(value = "9999999999999.99", message = "Amount is too large")
    @Digits(integer = 13, fraction = 2, message = "Amount can have at most 2 decimal places")
    BigDecimal amount,
    @NotBlank(message = "Currency is required")
    @Pattern(regexp = "[A-Z]{3}", message = "Currency must be a three-letter ISO code")
    String currency,
    @NotNull(message = "Date is required")
    @ValidTransactionDate
    LocalDate transactionDate,
    @NotBlank(message = "Description is required")
    @Size(max = 2000, message = "Description must be at most 2000 characters")
    String description,
    @NotNull(message = "Type is required")
    TransactionType type,
    UUID categoryId,
    @Size(max = 1000, message = "Notes must be at most 1000 characters")
    String notes
) {
    public UpdateTransactionRequest(BigDecimal amount, String currency, LocalDate transactionDate, String description,
                                    TransactionType type, UUID categoryId) {
        this(amount, currency, transactionDate, description, type, categoryId, null);
    }
}
