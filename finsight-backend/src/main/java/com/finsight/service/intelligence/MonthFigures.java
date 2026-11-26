package com.finsight.service.intelligence;

import java.math.BigDecimal;
import java.time.YearMonth;

/**
 * Income and net spending for one calendar month.
 *
 * @param complete false for the current, still unfinished month
 */
public record MonthFigures(YearMonth month, BigDecimal income, BigDecimal spending, boolean complete) {

    public BigDecimal net() {
        return income.subtract(spending);
    }
}
