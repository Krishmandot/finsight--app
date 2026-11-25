package com.finsight.service.importing;

/**
 * User-adjustable parsing options. Every field is optional; null means "detect automatically".
 *
 * @param dateFormat          a {@link java.time.format.DateTimeFormatter} pattern from {@link DateDetector#PATTERNS}
 * @param amountConvention    how to read a single signed amount column
 * @param mapping             explicit CSV column mapping overriding header detection
 * @param defaultCurrency     currency for rows without a currency column (normally the user's preference)
 * @param pdfPassword         password for an encrypted PDF statement; used only in memory, never stored
 * @param skipExactDuplicates when true, rows that exactly match an existing transaction are not imported
 */
public record ImportOptions(
    String dateFormat,
    AmountConvention amountConvention,
    ColumnMapping mapping,
    String defaultCurrency,
    String pdfPassword,
    boolean skipExactDuplicates
) {
    public enum AmountConvention {
        /** Negative amounts are money out (most bank exports). */
        NEGATIVE_IS_EXPENSE,
        /** Positive amounts are money out (common in credit-card exports). */
        POSITIVE_IS_EXPENSE
    }

    public static ImportOptions defaults(String currency) {
        return new ImportOptions(null, null, null, currency, null, true);
    }

    public ImportOptions withDateFormat(String value) {
        return new ImportOptions(value, amountConvention, mapping, defaultCurrency, pdfPassword, skipExactDuplicates);
    }

    public ImportOptions withAmountConvention(AmountConvention value) {
        return new ImportOptions(dateFormat, value, mapping, defaultCurrency, pdfPassword, skipExactDuplicates);
    }

    public ImportOptions withMapping(ColumnMapping value) {
        return new ImportOptions(dateFormat, amountConvention, value, defaultCurrency, pdfPassword, skipExactDuplicates);
    }

    public ImportOptions withPdfPassword(String value) {
        return new ImportOptions(dateFormat, amountConvention, mapping, defaultCurrency, value, skipExactDuplicates);
    }

    public ImportOptions withSkipExactDuplicates(boolean value) {
        return new ImportOptions(dateFormat, amountConvention, mapping, defaultCurrency, pdfPassword, value);
    }

    public AmountConvention amountConventionOrDefault() {
        return amountConvention == null ? AmountConvention.NEGATIVE_IS_EXPENSE : amountConvention;
    }
}
