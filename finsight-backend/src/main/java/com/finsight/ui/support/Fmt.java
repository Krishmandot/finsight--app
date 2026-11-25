package com.finsight.ui.support;

import com.finsight.api.dto.response.TransactionResponse;
import com.finsight.domain.enums.IngestionSource;
import com.finsight.domain.enums.TransactionReviewStatus;
import com.finsight.domain.enums.TransactionType;
import com.finsight.service.support.MoneyFormatter;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Display formatting shared by all views. */
public final class Fmt {

    public static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);
    public static final DateTimeFormatter DATE_SHORT = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH);
    public static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.ENGLISH);
    public static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH);
    public static final DateTimeFormatter MONTH_SHORT = DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH);
    public static final DateTimeFormatter MONTH_SHORT_YEAR = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH);

    private Fmt() {
    }

    public static String money(BigDecimal amount, String currency) {
        return MoneyFormatter.format(amount, currency);
    }

    public static String moneyWhole(BigDecimal amount, String currency) {
        return MoneyFormatter.formatWhole(amount, currency);
    }

    /** "+₹45,000.00" for money in, "−₹450.00" for money out, plain for transfers. */
    public static String signed(TransactionResponse t) {
        String value = money(t.amount(), t.currency());
        if (t.type() == null) {
            return value;
        }
        return switch (t.type()) {
            case CREDIT, REFUND -> "+" + value;
            case DEBIT -> "−" + value;
            default -> value;
        };
    }

    public static boolean isMoneyIn(TransactionType type) {
        return type == TransactionType.CREDIT || type == TransactionType.REFUND;
    }

    public static String date(LocalDate date) {
        return date == null ? "" : date.format(DATE);
    }

    public static String dateShort(LocalDate date) {
        if (date == null) {
            return "";
        }
        return date.getYear() == LocalDate.now().getYear() ? date.format(DATE_SHORT) : date.format(DATE);
    }

    public static String dateTime(LocalDateTime dateTime) {
        return dateTime == null ? "" : dateTime.format(DATE_TIME);
    }

    public static String month(YearMonth month) {
        return month.format(MONTH);
    }

    public static String type(TransactionType type) {
        if (type == null) {
            return "Unknown";
        }
        return switch (type) {
            case DEBIT -> "Expense";
            case CREDIT -> "Income";
            case REFUND -> "Refund";
            case TRANSFER -> "Transfer";
            case UNKNOWN -> "Unknown";
        };
    }

    public static String source(IngestionSource source) {
        if (source == null) {
            return "Unknown";
        }
        return switch (source) {
            case MANUAL -> "Added manually";
            case STATEMENT_IMPORT -> "CSV statement";
            case PDF -> "PDF statement";
            case SMS_CSV, SMS_JSON -> "SMS export";
            case ANDROID_NOTIFICATION -> "Notification";
        };
    }

    public static String status(TransactionReviewStatus status, boolean included) {
        return switch (status) {
            case PENDING -> "Pending review";
            case REJECTED -> "Rejected";
            case APPROVED -> included ? "Counted" : "Excluded";
        };
    }

    public static String percent(double value) {
        return Math.round(value) + "%";
    }

    public static String percentChange(double value) {
        long rounded = Math.round(value);
        return (rounded > 0 ? "+" : rounded < 0 ? "−" : "") + Math.abs(rounded) + "%";
    }

    public static String initials(String name) {
        if (name == null || name.isBlank()) {
            return "?";
        }
        String[] parts = name.trim().split("\\s+");
        String first = parts[0].substring(0, 1);
        String second = parts.length > 1 ? parts[parts.length - 1].substring(0, 1) : "";
        return (first + second).toUpperCase(Locale.ROOT);
    }
}
