package com.finsight.service.privacy;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Masks account and card numbers in free-text descriptions before they are stored. Any run of 8 or more
 * digits (optionally separated by spaces or dashes, as card numbers often are) keeps only its last four
 * digits: {@code "ACH 001234567890"} becomes {@code "ACH ••••7890"}. Short numbers such as amounts,
 * dates and store numbers are left alone.
 */
public final class DescriptionRedactor {

    private static final Pattern LONG_NUMBER = Pattern.compile("(?<![\\d])\\d(?:[ -]?\\d){7,}(?![\\d])");
    private static final String MASK = "••••";

    private DescriptionRedactor() {
    }

    public static String redact(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        Matcher matcher = LONG_NUMBER.matcher(text);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String digits = matcher.group().replaceAll("[ -]", "");
            matcher.appendReplacement(result, Matcher.quoteReplacement(MASK + digits.substring(digits.length() - 4)));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    /** Collapses whitespace, trims and redacts. Returns an empty string for null input. */
    public static String clean(String text) {
        if (text == null) {
            return "";
        }
        return redact(text.replaceAll("\\s+", " ").trim());
    }
}
