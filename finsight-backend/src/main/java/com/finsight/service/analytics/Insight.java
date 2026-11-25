package com.finsight.service.analytics;

/** A plain-language observation derived from the user's own data. Only produced when data supports it. */
public record Insight(Tone tone, String title, String detail) {

    public enum Tone {
        POSITIVE,
        NEUTRAL,
        WARNING
    }
}
