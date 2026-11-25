package com.finsight.service.importing;

import com.finsight.domain.enums.TransactionType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses amounts as banks print them: {@code 1,234.56}, {@code 1,23,456.00}, {@code ₹ 450}, {@code (12.00)},
 * {@code -12.00}, {@code 12.00 Dr}, {@code 1.234,56}. A trailing or leading Dr/Cr marker is returned as a
 * direction hint.
 */
public final class AmountParser {

    public record ParsedAmount(BigDecimal value, TransactionType directionHint) {
    }

    private static final Pattern MARKER = Pattern.compile("(?i)^(dr|cr|debit|credit)\\.?\\s*|\\s*\\b(dr|cr|debit|credit)\\.?$");
    private static final Pattern CURRENCY = Pattern.compile("(?i)(rs\\.?|inr|usd|eur|gbp|aed|sgd|aud|cad|[₹$€£¥])");
    private static final Pattern NUMERIC = Pattern.compile("^\\d+(\\.\\d+)?$");

    private AmountParser() {
    }

    public static Optional<ParsedAmount> parse(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String value = raw.replace(' ', ' ').trim();
        if (value.isEmpty() || value.equals("-") || value.equals("—")) {
            return Optional.empty();
        }

        TransactionType hint = null;
        Matcher marker = MARKER.matcher(value);
        if (marker.find()) {
            String token = (marker.group(1) != null ? marker.group(1) : marker.group(2)).toLowerCase(Locale.ROOT);
            hint = token.startsWith("d") ? TransactionType.DEBIT : TransactionType.CREDIT;
            value = marker.replaceFirst("").trim();
        }

        value = CURRENCY.matcher(value).replaceAll("").replace(" ", "").replace("'", "");
        boolean negative = false;
        if (value.startsWith("(") && value.endsWith(")")) {
            negative = true;
            value = value.substring(1, value.length() - 1);
        }
        if (value.startsWith("-")) {
            negative = !negative;
            value = value.substring(1);
        } else if (value.endsWith("-")) {
            negative = !negative;
            value = value.substring(0, value.length() - 1);
        } else if (value.startsWith("+")) {
            value = value.substring(1);
        }

        value = normalizeSeparators(value);
        if (value == null || !NUMERIC.matcher(value).matches()) {
            return Optional.empty();
        }
        BigDecimal amount = new BigDecimal(value).setScale(2, RoundingMode.HALF_UP);
        return Optional.of(new ParsedAmount(negative ? amount.negate() : amount, hint));
    }

    /** Converts grouping/decimal separators to plain "1234.56". Returns null when the format is inconsistent. */
    static String normalizeSeparators(String value) {
        int lastDot = value.lastIndexOf('.');
        int lastComma = value.lastIndexOf(',');
        if (lastDot >= 0 && lastComma >= 0) {
            if (lastComma > lastDot) {
                // 1.234,56 (European)
                return value.replace(".", "").replace(',', '.');
            }
            // 1,234.56 or 1,23,456.78
            return value.replace(",", "");
        }
        if (lastComma >= 0) {
            String[] parts = value.split(",", -1);
            String tail = parts[parts.length - 1];
            if (parts.length == 2 && tail.length() <= 2 && !tail.isEmpty()) {
                return value.replace(',', '.'); // 12,50 (decimal comma)
            }
            return value.replace(",", "");       // 1,234 or 1,23,456
        }
        if (lastDot >= 0 && value.indexOf('.') != lastDot) {
            String tail = value.substring(lastDot + 1);
            if (tail.length() == 3) {
                return value.replace(".", "");   // 1.234.567 (European grouping)
            }
            return null;
        }
        return value;
    }
}
