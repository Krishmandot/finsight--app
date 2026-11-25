package com.finsight.service.report;

public enum ReportPeriodType {
    /** Monday to Sunday. */
    WEEKLY("Weekly", "week"),
    /** Consecutive 15-day windows. */
    FORTNIGHTLY("Fortnightly", "fortnight"),
    /** Calendar month. */
    MONTHLY("Monthly", "month");

    private final String label;
    private final String noun;

    ReportPeriodType(String label, String noun) {
        this.label = label;
        this.noun = noun;
    }

    public String label() {
        return label;
    }

    public String noun() {
        return noun;
    }
}
