package com.finsight.service.intelligence;

import java.math.BigDecimal;

/**
 * One finding from the SpendLeak Detector.
 *
 * @param impact money at stake: per month for {@link Type#RECURRING_PAYMENT} and {@link Type#CATEGORY_SURGE},
 *               a one-off amount for {@link Type#UNUSUAL_SPIKE} and {@link Type#POSSIBLE_DUPLICATE}
 * @param action what the user can do about it
 */
public record SpendLeak(Type type, Severity severity, String title, String detail, String action,
                        BigDecimal impact, String category) {

    public enum Type {
        RECURRING_PAYMENT,
        UNUSUAL_SPIKE,
        POSSIBLE_DUPLICATE,
        CATEGORY_SURGE
    }

    public enum Severity {
        HIGH,
        MEDIUM,
        LOW
    }

    /** True when {@link #impact()} repeats every month rather than being a single event. */
    public boolean monthlyImpact() {
        return type == Type.RECURRING_PAYMENT || type == Type.CATEGORY_SURGE;
    }
}
