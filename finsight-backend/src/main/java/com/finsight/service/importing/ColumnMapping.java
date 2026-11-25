package com.finsight.service.importing;

/**
 * Zero-based CSV column indices; {@link #NONE} when a column is absent. A statement needs a date and
 * either a single amount column or separate debit/credit columns.
 *
 * @param headerRow zero-based index of the header row, or {@link #NONE} when the file has no header
 */
public record ColumnMapping(
    int headerRow,
    int date,
    int description,
    int amount,
    int debit,
    int credit,
    int direction,
    int currency
) {
    public static final int NONE = -1;

    public boolean hasSplitAmounts() {
        return debit != NONE || credit != NONE;
    }

    public boolean isUsable() {
        return date != NONE && (amount != NONE || debit != NONE || credit != NONE);
    }

    public ColumnMapping withDate(int value) {
        return new ColumnMapping(headerRow, value, description, amount, debit, credit, direction, currency);
    }

    public ColumnMapping withDescription(int value) {
        return new ColumnMapping(headerRow, date, value, amount, debit, credit, direction, currency);
    }

    public ColumnMapping withAmount(int value) {
        return new ColumnMapping(headerRow, date, description, value, debit, credit, direction, currency);
    }

    public ColumnMapping withDebit(int value) {
        return new ColumnMapping(headerRow, date, description, amount, value, credit, direction, currency);
    }

    public ColumnMapping withCredit(int value) {
        return new ColumnMapping(headerRow, date, description, amount, debit, value, direction, currency);
    }

    public ColumnMapping withDirection(int value) {
        return new ColumnMapping(headerRow, date, description, amount, debit, credit, value, currency);
    }
}
