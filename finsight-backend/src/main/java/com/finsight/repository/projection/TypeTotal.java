package com.finsight.repository.projection;

import com.finsight.domain.enums.TransactionType;
import java.math.BigDecimal;

public record TypeTotal(TransactionType type, BigDecimal total, Long count) {
}
