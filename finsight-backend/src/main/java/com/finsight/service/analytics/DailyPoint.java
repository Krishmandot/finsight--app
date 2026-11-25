package com.finsight.service.analytics;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DailyPoint(LocalDate date, BigDecimal income, BigDecimal spending) {
}
