package com.finsight.repository.projection;

import com.finsight.domain.enums.TransactionType;
import java.math.BigDecimal;

public record MonthTypeTotal(Integer year, Integer month, TransactionType type, BigDecimal total) {
}
