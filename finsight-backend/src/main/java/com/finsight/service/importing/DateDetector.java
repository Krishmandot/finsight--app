package com.finsight.service.importing;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Detects which date format a statement uses by trying every supported pattern against all date cells
 * and picking the one that parses the most. Day-first patterns win ties (the convention for INR
 * statements), and the caller is told when the choice was ambiguous so the user can correct it.
 */
public final class DateDetector {

    /** Supported patterns in tie-break priority order. Day-first precedes month-first. */
    public static final List<String> PATTERNS = List.of(
        "d/M/uuuu", "d-M-uuuu", "d.M.uuuu", "uuuu-M-d", "uuuu/M/d", "d MMM uuuu", "d-MMM-uuuu", "d MMMM uuuu",
        "d/MMM/uuuu", "d-MMM-uu", "d MMM uu", "d/M/uu", "d-M-uu", "d.M.uu", "uuuuMMdd",
        "M/d/uuuu", "M-d-uuuu", "M/d/uu", "MMM d uuuu", "MMMM d uuuu"
    );

    private static final Map<String, String> MONTH_FIRST_EQUIVALENT = Map.of(
        "d/M/uuuu", "M/d/uuuu", "d-M-uuuu", "M-d-uuuu", "d/M/uu", "M/d/uu");

    private static final Map<String, DateTimeFormatter> FORMATTERS = new LinkedHashMap<>();
    private static final Pattern TIME_SUFFIX = Pattern.compile("[ T]\\d{1,2}:\\d{2}(:\\d{2})?(\\.\\d+)?\\s*(am|pm|AM|PM)?.*$");

    static {
        for (String pattern : PATTERNS) {
            FORMATTERS.put(pattern, new DateTimeFormatterBuilder()
                .parseCaseInsensitive()
                .appendPattern(pattern)
                .toFormatter(Locale.ENGLISH)
                .withResolverStyle(ResolverStyle.STRICT));
        }
    }

    public record Detection(String pattern, boolean ambiguous, int matched, int total) {
    }

    private DateDetector() {
    }

    /** Strips a trailing time, commas and repeated spaces: "Nov 3, 2026 10:15" becomes "Nov 3 2026". */
    public static String clean(String raw) {
        if (raw == null) {
            return "";
        }
        String value = raw.trim().replace(",", " ").replaceAll("\\s+", " ");
        return TIME_SUFFIX.matcher(value).replaceFirst("").trim();
    }

    public static Optional<LocalDate> parse(String raw, String pattern) {
        DateTimeFormatter formatter = FORMATTERS.get(pattern);
        if (formatter == null) {
            return Optional.empty();
        }
        String value = clean(raw);
        if (value.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(LocalDate.parse(value, formatter));
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }

    public static Optional<Detection> detect(Collection<String> samples) {
        List<String> values = samples.stream().map(DateDetector::clean).filter(s -> !s.isEmpty()).limit(1000).toList();
        if (values.isEmpty()) {
            return Optional.empty();
        }
        String best = null;
        int bestCount = 0;
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String pattern : PATTERNS) {
            int count = 0;
            for (String value : values) {
                if (parse(value, pattern).isPresent()) {
                    count++;
                }
            }
            counts.put(pattern, count);
            if (count > bestCount) {
                best = pattern;
                bestCount = count;
            }
        }
        if (best == null) {
            return Optional.empty();
        }
        String alternative = MONTH_FIRST_EQUIVALENT.get(best);
        boolean ambiguous = alternative != null && counts.getOrDefault(alternative, 0) == bestCount;
        return Optional.of(new Detection(best, ambiguous, bestCount, values.size()));
    }

    /** Human-readable example of a pattern, e.g. "26/11/2026". */
    public static String example(String pattern) {
        return LocalDate.of(2026, 11, 26).format(FORMATTERS.get(pattern));
    }
}
