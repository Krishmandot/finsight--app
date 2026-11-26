package com.finsight.service.intelligence;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Result of a SpendLeak analysis.
 *
 * @param monthlyImpact money flagged per month (recurring discretionary payments plus category surges)
 * @param oneOffImpact  money flagged as one-off events (unusual spikes and possible double charges)
 * @param historyNote   explanation shown when there is too little history for reliable detection, else null
 */
public record LeakReport(List<SpendLeak> leaks, BigDecimal monthlyImpact, BigDecimal oneOffImpact,
                         boolean enoughHistory, String historyNote, LocalDate from, LocalDate to) {
}
