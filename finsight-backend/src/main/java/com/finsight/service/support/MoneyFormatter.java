package com.finsight.service.support;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.Currency;
import java.util.Locale;

/** Formats amounts for display. INR uses Indian digit grouping (₹1,23,456.00). */
public final class MoneyFormatter {

    private static final Locale INDIA = Locale.forLanguageTag("en-IN");

    private MoneyFormatter() {
    }

    public static String format(BigDecimal amount, String currencyCode) {
        return formatter(currencyCode, 2).format(amount == null ? BigDecimal.ZERO : amount);
    }

    /** Whole units only (₹12,400) — for headline figures and chart labels. */
    public static String formatWhole(BigDecimal amount, String currencyCode) {
        BigDecimal value = amount == null ? BigDecimal.ZERO : amount.setScale(0, RoundingMode.HALF_UP);
        return formatter(currencyCode, 0).format(value);
    }

    /** Compact axis labels: 950, 12.4K, 3.2L (INR) or 3.2M. */
    public static String compact(BigDecimal amount, String currencyCode) {
        double value = amount == null ? 0 : amount.abs().doubleValue();
        String sign = amount != null && amount.signum() < 0 ? "-" : "";
        String symbol = symbol(currencyCode);
        boolean indian = "INR".equals(currencyCode);
        if (indian && value >= 10_000_000) {
            return sign + symbol + trim(value / 10_000_000) + "Cr";
        }
        if (indian && value >= 100_000) {
            return sign + symbol + trim(value / 100_000) + "L";
        }
        if (!indian && value >= 1_000_000) {
            return sign + symbol + trim(value / 1_000_000) + "M";
        }
        if (value >= 1_000) {
            return sign + symbol + trim(value / 1_000) + "K";
        }
        return sign + symbol + trim(value);
    }

    public static String symbol(String currencyCode) {
        try {
            Currency currency = Currency.getInstance(currencyCode);
            return currency.getSymbol(localeFor(currencyCode));
        } catch (IllegalArgumentException | NullPointerException e) {
            return currencyCode == null ? "" : currencyCode + " ";
        }
    }

    public static Locale localeFor(String currencyCode) {
        return "INR".equals(currencyCode) ? INDIA : Locale.US;
    }

    private static NumberFormat formatter(String currencyCode, int fractionDigits) {
        NumberFormat format = NumberFormat.getCurrencyInstance(localeFor(currencyCode));
        try {
            Currency currency = Currency.getInstance(currencyCode);
            format.setCurrency(currency);
            if (!"INR".equals(currencyCode) && !"USD".equals(currencyCode)) {
                // Show the ISO code for currencies whose symbol would be ambiguous in the US locale.
                String symbol = currency.getSymbol(Locale.US);
                if (symbol.equals(currencyCode) && format instanceof java.text.DecimalFormat decimal) {
                    var symbols = decimal.getDecimalFormatSymbols();
                    symbols.setCurrencySymbol(currencyCode + " ");
                    decimal.setDecimalFormatSymbols(symbols);
                }
            }
        } catch (IllegalArgumentException | NullPointerException ignored) {
            // Unknown code: fall back to the locale's default currency formatting.
        }
        format.setMinimumFractionDigits(fractionDigits);
        format.setMaximumFractionDigits(fractionDigits);
        return format;
    }

    private static String trim(double value) {
        if (value >= 100 || Math.abs(value - Math.round(value)) < 0.05) {
            return String.valueOf(Math.round(value));
        }
        return String.format(Locale.ROOT, "%.1f", value);
    }
}
