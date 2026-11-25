package com.finsight.service.analytics;

import java.math.BigDecimal;
import java.time.YearMonth;

/** One month of the trend. {@code savingsRate} is null when the month had no income. */
public record MonthlyPoint(YearMonth month, BigDecimal income, BigDecimal spending, BigDecimal net,
                           BigDecimal savingsRate) {

    public boolean hasData() {
        return income.signum() != 0 || spending.signum() != 0;
    }
}
