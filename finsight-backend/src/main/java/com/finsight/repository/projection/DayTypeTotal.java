package com.finsight.repository.projection;

import com.finsight.domain.enums.TransactionType;
import java.math.BigDecimal;
import java.time.LocalDate;

public record DayTypeTotal(LocalDate date, TransactionType type, BigDecimal total) {
}
