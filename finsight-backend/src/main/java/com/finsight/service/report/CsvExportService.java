package com.finsight.service.report;

import com.finsight.api.dto.response.TransactionResponse;
import com.finsight.service.analytics.CategoryComparison;
import com.finsight.service.analytics.PeriodSummary;
import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Writes CSV exports (UTF-8 with BOM so spreadsheet apps detect the encoding). Text cells that begin with a
 * formula trigger ({@code = + - @}, tab or carriage return) are prefixed with an apostrophe so opening the
 * file in a spreadsheet can never execute an injected formula.
 */
@Service
public class CsvExportService {

    private static final String[] TRANSACTION_HEADER = {
        "Date", "Description", "Type", "Category", "Amount", "Currency", "Review status", "In reports", "Source",
        "Possible duplicate", "Notes"
    };

    public byte[] transactions(List<TransactionResponse> transactions) {
        return write(out -> {
            row(out, TRANSACTION_HEADER);
            transactions.forEach(t -> transactionRow(out, t));
        });
    }

    public byte[] report(FinancialReport report) {
        return write(out -> {
            row(out, "FinSight report", report.period().type().label(), report.period().label());
            row(out, "Period start", report.period().start().toString());
            row(out, "Period end", report.period().end().toString());
            row(out, "Currency", report.currency());
            out.println();

            PeriodSummary s = report.summary();
            PeriodSummary p = report.previousSummary();
            row(out, "Summary", "This period", p == null ? "" : "Previous period");
            row(out, "Income", amount(s.income()), p == null ? "" : amount(p.income()));
            row(out, "Spending (net of refunds)", amount(s.spending()), p == null ? "" : amount(p.spending()));
            row(out, "Refunds", amount(s.refunds()), p == null ? "" : amount(p.refunds()));
            row(out, "Net savings", amount(s.net()), p == null ? "" : amount(p.net()));
            row(out, "Savings rate %", s.savingsRate() == null ? "" : s.savingsRate().toPlainString(),
                p == null || p.savingsRate() == null ? "" : p.savingsRate().toPlainString());
            row(out, "Transactions counted", String.valueOf(s.transactionCount()),
                p == null ? "" : String.valueOf(p.transactionCount()));
            row(out, "Not counted (pending / rejected / excluded)",
                report.excluded().pending() + " / " + report.excluded().rejected() + " / " + report.excluded().excludedApproved());
            out.println();

            row(out, "Category", "Spending this period", "Spending previous period", "Change");
            for (CategoryComparison c : report.categories()) {
                row(out, c.name(), amount(c.current()), amount(c.previous()), amount(c.change()));
            }
            out.println();

            row(out, TRANSACTION_HEADER);
            report.transactions().forEach(t -> transactionRow(out, t));
        });
    }

    private static void transactionRow(PrintWriter out, TransactionResponse t) {
        row(out,
            t.transactionDate() == null ? "" : t.transactionDate().toString(),
            t.description(),
            t.type() == null ? "" : t.type().name(),
            t.category() == null ? "" : t.category().name(),
            amount(t.amount()),
            t.currency(),
            t.reviewStatus().name(),
            t.includedInReports() ? "Yes" : "No",
            t.source() == null ? "" : t.source().name(),
            t.duplicate() ? "Yes" : "No",
            t.notes());
    }

    private static String amount(BigDecimal value) {
        return value == null ? "" : value.toPlainString();
    }

    private static void row(PrintWriter out, String... cells) {
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) {
                line.append(',');
            }
            line.append(escape(cells[i]));
        }
        out.print(line);
        out.print("\r\n");
    }

    static String escape(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        String safe = value;
        char first = safe.charAt(0);
        boolean numeric = safe.matches("-?\\d+(\\.\\d+)?");
        if (!numeric && (first == '=' || first == '+' || first == '-' || first == '@' || first == '\t' || first == '\r')) {
            safe = "'" + safe;
        }
        if (safe.contains(",") || safe.contains("\"") || safe.contains("\n") || safe.contains("\r")) {
            safe = "\"" + safe.replace("\"", "\"\"") + "\"";
        }
        return safe;
    }

    private static byte[] write(java.util.function.Consumer<PrintWriter> body) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        bytes.write(0xEF);
        bytes.write(0xBB);
        bytes.write(0xBF);
        try (PrintWriter out = new PrintWriter(new OutputStreamWriter(bytes, StandardCharsets.UTF_8))) {
            body.accept(out);
        }
        return bytes.toByteArray();
    }
}
