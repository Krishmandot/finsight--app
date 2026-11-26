package com.finsight.service.intelligence;

import java.util.List;

/**
 * An explainable financial health score out of 100.
 *
 * @param available   false when there is no data to score
 * @param provisional true when only the current, unfinished month was available
 * @param factors     every factor with its effective weight; excluded factors say why
 * @param tips        specific suggestions, weakest factor first
 */
public record HealthScore(boolean available, int score, String label, boolean provisional,
                          List<Factor> factors, List<String> tips) {

    /**
     * @param weight effective weight in percent after excluded factors are removed (0 when excluded)
     * @param score  0-100 for this factor (0 when excluded)
     */
    public record Factor(String name, boolean included, int weight, int score, String detail) {
    }

    public static HealthScore unavailable(String reason) {
        return new HealthScore(false, 0, "Not enough data", false, List.of(), List.of(reason));
    }
}
