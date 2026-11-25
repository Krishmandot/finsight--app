package com.finsight.service.report;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.Locale;

/**
 * A closed date range for a report. Weekly periods run Monday–Sunday, monthly periods are calendar months
 * and fortnightly periods are fixed, consecutive 15-day windows counted from {@link #FORTNIGHT_ANCHOR}, so
 * every date belongs to exactly one period and the previous period is always the same length.
 */
public record ReportPeriod(ReportPeriodType type, LocalDate start, LocalDate end) {

    public static final LocalDate FORTNIGHT_ANCHOR = LocalDate.of(2024, 1, 1);
    public static final int FORTNIGHT_DAYS = 15;

    private static final DateTimeFormatter DAY_MONTH = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH);
    private static final DateTimeFormatter DAY_MONTH_YEAR = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter MONTH_YEAR = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH);

    public static ReportPeriod containing(ReportPeriodType type, LocalDate date) {
        return switch (type) {
            case WEEKLY -> {
                LocalDate monday = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                yield new ReportPeriod(type, monday, monday.plusDays(6));
            }
            case FORTNIGHTLY -> {
                long days = ChronoUnit.DAYS.between(FORTNIGHT_ANCHOR, date);
                long index = Math.floorDiv(days, FORTNIGHT_DAYS);
                LocalDate start = FORTNIGHT_ANCHOR.plusDays(index * FORTNIGHT_DAYS);
                yield new ReportPeriod(type, start, start.plusDays(FORTNIGHT_DAYS - 1L));
            }
            case MONTHLY -> {
                YearMonth month = YearMonth.from(date);
                yield new ReportPeriod(type, month.atDay(1), month.atEndOfMonth());
            }
        };
    }

    public ReportPeriod previous() {
        return containing(type, start.minusDays(1));
    }

    public ReportPeriod next() {
        return containing(type, end.plusDays(1));
    }

    public long days() {
        return ChronoUnit.DAYS.between(start, end) + 1;
    }

    public boolean contains(LocalDate date) {
        return !date.isBefore(start) && !date.isAfter(end);
    }

    public String label() {
        if (type == ReportPeriodType.MONTHLY) {
            return start.format(MONTH_YEAR);
        }
        if (start.getYear() == end.getYear()) {
            return start.format(DAY_MONTH) + " – " + end.format(DAY_MONTH_YEAR);
        }
        return start.format(DAY_MONTH_YEAR) + " – " + end.format(DAY_MONTH_YEAR);
    }
}
