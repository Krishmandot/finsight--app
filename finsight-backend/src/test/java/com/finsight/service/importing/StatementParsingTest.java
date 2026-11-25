package com.finsight.service.importing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.finsight.domain.entity.Transaction;
import com.finsight.domain.enums.IngestionSource;
import com.finsight.domain.enums.TransactionType;
import com.finsight.service.privacy.DescriptionRedactor;
import com.finsight.service.report.ReportPeriod;
import com.finsight.service.report.ReportPeriodType;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StatementParsingTest {

    private final CsvStatementParser parser = new CsvStatementParser();

    private ParsedStatement parse(String csv) {
        return parser.parse(csv.getBytes(StandardCharsets.UTF_8), ImportOptions.defaults("INR"));
    }

    @Test
    void parsesDebitCreditColumnsWithPreambleAndIndianGrouping() {
        ParsedStatement s = parse("""
            Statement for account 001234567890
            Date,Narration,Withdrawal Amt.,Deposit Amt.,Closing Balance
            01/11/2026,UPI-SWIGGY-ORDER,450.00,,"1,23,456.00"
            02/11/2026,SALARY NOV,,"1,20,000.00","2,43,006.00"
            ,Total,450.00,120000.00,
            """);
        assertEquals(2, s.rows().size());
        assertEquals(TransactionType.DEBIT, s.rows().get(0).type());
        assertEquals(new BigDecimal("450.00"), s.rows().get(0).amount());
        assertEquals(LocalDate.of(2026, 11, 2), s.rows().get(1).date());
        assertEquals(new BigDecimal("120000.00"), s.rows().get(1).amount());
        assertEquals(TransactionType.CREDIT, s.rows().get(1).type());
        assertEquals(1, s.skipped().size());
    }

    @Test
    void signedAmountColumnAndMonthFirstDatesAreDetected() {
        ParsedStatement s = parse("Date;Description;Amount\n11/25/2026;Coffee;-4,50\n11/26/2026;Refund;12,00\n");
        assertEquals("M/d/uuuu", s.dateFormat());
        assertEquals(TransactionType.DEBIT, s.rows().get(0).type());
        assertEquals(new BigDecimal("4.50"), s.rows().get(0).amount());
        assertEquals(TransactionType.CREDIT, s.rows().get(1).type());
    }

    @Test
    void directionColumnIsNotMistakenForAmount() {
        ParsedStatement s = parse("Txn Date,Details,Amount,Dr/Cr\n2026-11-03,ATM CASH,2000,DR\n2026-11-04,Interest,15.20,CR\n");
        assertEquals(TransactionType.DEBIT, s.rows().get(0).type());
        assertEquals(TransactionType.CREDIT, s.rows().get(1).type());
    }

    @Test
    void duplicateDetectionIsOneToOneSoRepeatedPurchasesSurvive() {
        Transaction existing = Transaction.builder().id(UUID.randomUUID()).amount(new BigDecimal("120.00"))
            .transactionDate(LocalDate.of(2026, 11, 5)).type(TransactionType.DEBIT)
            .redactedDescription("CAFE COFFEE DAY").ingestionSource(IngestionSource.STATEMENT_IMPORT).build();
        ParsedStatement.Row row = new ParsedStatement.Row(1, LocalDate.of(2026, 11, 5), "Cafe Coffee Day",
            new BigDecimal("120.00"), TransactionType.DEBIT, "INR");
        List<DuplicateDetector.Match> matches = new DuplicateDetector(List.of(existing)).matchAll(List.of(row, row));
        assertEquals(DuplicateDetector.Kind.EXACT, matches.get(0).kind());
        assertEquals(DuplicateDetector.Kind.NONE, matches.get(1).kind());
    }

    @Test
    void redactorMasksLongNumbersOnly() {
        assertEquals("NEFT ••••7890 ref 2026", DescriptionRedactor.redact("NEFT 001234567890 ref 2026"));
        assertTrue(DescriptionRedactor.redact("card 4111 1111 1111 1111").endsWith("••••1111"));
    }

    @Test
    void reportPeriodsAreContiguous() {
        ReportPeriod f = ReportPeriod.containing(ReportPeriodType.FORTNIGHTLY, LocalDate.of(2026, 11, 26));
        assertEquals(15, f.days());
        assertEquals(f.start().minusDays(1), f.previous().end());
        ReportPeriod w = ReportPeriod.containing(ReportPeriodType.WEEKLY, LocalDate.of(2026, 11, 26));
        assertEquals(java.time.DayOfWeek.MONDAY, w.start().getDayOfWeek());
    }
}
