package com.finsight.service.importing;

import com.finsight.api.dto.request.ValidTransactionDate;
import com.finsight.api.exception.IngestionException;
import com.finsight.domain.enums.ImportFormat;
import com.finsight.domain.enums.TransactionType;
import com.finsight.service.privacy.DescriptionRedactor;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

/**
 * Best-effort parser for text-based PDF bank statements.
 *
 * <p>PDF statements have no machine-readable structure, so this parser only accepts lines it can interpret
 * with confidence: a line must start with a date and end with one or more amounts. Whether money went in
 * or out is taken from an explicit Dr/Cr marker, or from the change in the running balance column. Rows
 * where the direction cannot be established are skipped and reported, never guessed. Scanned (image-only)
 * PDFs are rejected because they contain no text.</p>
 */
@Component
public class PdfStatementParser implements StatementParser {

    public static final int MAX_PAGES = 200;

    private static final String DATE_TOKEN = "(\\d{1,2}[/.-]\\d{1,2}[/.-]\\d{2,4}|\\d{4}-\\d{2}-\\d{2}"
        + "|\\d{1,2}[ -](?:jan|feb|mar|apr|may|jun|jul|aug|sep|sept|oct|nov|dec)[a-z]*[ -,]*\\d{2,4})";
    private static final Pattern LEADING_DATE = Pattern.compile("^\\s*" + DATE_TOKEN + "\\s+(.*)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern TRAILING_DATE = Pattern.compile("\\s+" + DATE_TOKEN + "\\s*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern AMOUNT_TOKEN = Pattern.compile(
        "\\(?-?(?:[₹$€£]|rs\\.?|inr)?\\s?\\d{1,3}(?:,\\d{2,3})*(?:\\.\\d{2})\\)?(?:\\s?(?:cr|dr)\\.?)?", Pattern.CASE_INSENSITIVE);
    private static final Pattern TRAILING_AMOUNTS = Pattern.compile("((?:\\s+" + AMOUNT_TOKEN.pattern() + "){1,3})\\s*$",
        Pattern.CASE_INSENSITIVE);
    private static final Pattern OPENING_BALANCE = Pattern.compile(
        "(?i)(opening balance|balance b/?f|brought forward|balance forward)[^\\d]*(" + AMOUNT_TOKEN.pattern() + ")");
    private static final Pattern NOISE_LINE = Pattern.compile(
        "(?i).*(page \\d+|statement of account|closing balance|total|generated on|this is a computer).*");

    @Override
    public ImportFormat format() {
        return ImportFormat.PDF;
    }

    @Override
    public boolean supports(String fileName, byte[] content) {
        return content.length >= 5 && content[0] == '%' && content[1] == 'P' && content[2] == 'D'
            && content[3] == 'F' && content[4] == '-';
    }

    @Override
    public ParsedStatement parse(byte[] content, ImportOptions options) {
        String text = extractText(content, options.pdfPassword());
        if (text.isBlank()) {
            throw new IngestionException("No text could be read from this PDF. It may be a scanned image; "
                + "download a CSV or a text-based PDF statement from your bank instead.");
        }
        return parseText(text, options);
    }

    ParsedStatement parseText(String text, ImportOptions options) {
        List<String> warnings = new ArrayList<>();
        warnings.add("PDF statements are read on a best-effort basis. Check every row before approving it.");
        String[] lines = text.split("\\r?\\n");

        // Pass 1: find candidate transaction lines and their date tokens.
        List<Candidate> candidates = new ArrayList<>();
        BigDecimal openingBalance = null;
        Candidate last = null;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].replace(' ', ' ').trim();
            if (line.isEmpty()) {
                continue;
            }
            if (openingBalance == null) {
                Matcher opening = OPENING_BALANCE.matcher(line);
                if (opening.find()) {
                    openingBalance = AmountParser.parse(opening.group(2)).map(AmountParser.ParsedAmount::value).orElse(null);
                    continue;
                }
            }
            Matcher dateMatcher = LEADING_DATE.matcher(line);
            Matcher amounts = TRAILING_AMOUNTS.matcher(line);
            if (dateMatcher.matches() && amounts.find()) {
                String rest = line.substring(dateMatcher.start(2), amounts.start(1)).trim();
                rest = TRAILING_DATE.matcher(rest).replaceFirst("").trim(); // value-date column
                List<AmountParser.ParsedAmount> parsedAmounts = new ArrayList<>();
                Matcher token = AMOUNT_TOKEN.matcher(amounts.group(1));
                while (token.find()) {
                    AmountParser.parse(token.group()).ifPresent(parsedAmounts::add);
                }
                last = new Candidate(i + 1, dateMatcher.group(1), new StringBuilder(rest), parsedAmounts);
                candidates.add(last);
            } else if (last != null && !dateMatcher.matches() && !AMOUNT_TOKEN.matcher(line).find()
                && !NOISE_LINE.matcher(line).matches() && last.continuations < 2 && last.description.length() < 200) {
                // Narrations often wrap onto the next line.
                last.description.append(' ').append(line);
                last.continuations++;
            } else {
                last = null;
            }
        }
        if (candidates.isEmpty()) {
            throw new IngestionException("No transactions were recognized in this PDF layout. "
                + "Download a CSV statement from your bank and import that instead.");
        }

        String pattern = options.dateFormat();
        if (pattern == null || !DateDetector.PATTERNS.contains(pattern)) {
            DateDetector.Detection detection = DateDetector.detect(candidates.stream().map(c -> c.rawDate).toList())
                .orElseThrow(() -> new IngestionException("The dates in this PDF could not be recognized"));
            pattern = detection.pattern();
            if (detection.ambiguous()) {
                warnings.add("Dates could be day/month or month/day. They were read as day/month. "
                    + "Change the date format if that's wrong.");
            }
        }

        // Pass 2: resolve amount and direction.
        List<ParsedStatement.Row> rows = new ArrayList<>();
        List<ParsedStatement.SkippedRow> skipped = new ArrayList<>();
        BigDecimal previousBalance = openingBalance;
        String currency = options.defaultCurrency() == null ? "INR" : options.defaultCurrency();
        for (Candidate candidate : candidates) {
            Optional<LocalDate> date = DateDetector.parse(candidate.rawDate, pattern);
            if (date.isEmpty() || !ValidTransactionDate.Rules.isPlausible(date.get())) {
                skipped.add(new ParsedStatement.SkippedRow(candidate.line, "Date not recognized"));
                continue;
            }
            List<AmountParser.ParsedAmount> amounts = candidate.amounts;
            BigDecimal balance = amounts.size() >= 2 ? amounts.get(amounts.size() - 1).value() : null;
            AmountParser.ParsedAmount txAmount = amounts.size() >= 2 ? amounts.get(amounts.size() - 2) : amounts.get(0);
            BigDecimal amount = txAmount.value().abs();
            TransactionType type = txAmount.directionHint();

            if (type == null && balance != null && previousBalance != null) {
                BigDecimal delta = balance.subtract(previousBalance);
                if (delta.abs().compareTo(amount) == 0) {
                    type = delta.signum() < 0 ? TransactionType.DEBIT : TransactionType.CREDIT;
                }
            }
            if (balance != null) {
                previousBalance = balance;
            }
            if (amount.signum() == 0) {
                skipped.add(new ParsedStatement.SkippedRow(candidate.line, "Amount is zero"));
                continue;
            }
            if (type == null) {
                skipped.add(new ParsedStatement.SkippedRow(candidate.line,
                    "Couldn't tell whether this was money in or out"));
                continue;
            }
            String description = DescriptionRedactor.clean(candidate.description.toString());
            rows.add(new ParsedStatement.Row(candidate.line, date.get(),
                description.isEmpty() ? "Statement entry" : truncate(description), amount, type, currency));
        }
        if (!skipped.isEmpty()) {
            warnings.add(skipped.size() + " line(s) looked like transactions but couldn't be read reliably and were left out.");
        }
        return new ParsedStatement(ImportFormat.PDF, List.of(), null, pattern, rows, skipped, warnings);
    }

    private static String extractText(byte[] content, String password) {
        boolean supplied = password != null && !password.isEmpty();
        try (PDDocument document = supplied ? Loader.loadPDF(content, password) : Loader.loadPDF(content)) {
            if (document.getNumberOfPages() > MAX_PAGES) {
                throw new IngestionException("The PDF has more than " + MAX_PAGES + " pages");
            }
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            return stripper.getText(document);
        } catch (InvalidPasswordException e) {
            throw new PasswordRequiredException(supplied);
        } catch (IOException e) {
            throw new IngestionException("The PDF could not be read. It may be damaged or not a statement.");
        }
    }

    private static String truncate(String value) {
        return value.length() > 2000 ? value.substring(0, 2000) : value;
    }

    private static final class Candidate {
        final int line;
        final String rawDate;
        final StringBuilder description;
        final List<AmountParser.ParsedAmount> amounts;
        int continuations;

        Candidate(int line, String rawDate, StringBuilder description, List<AmountParser.ParsedAmount> amounts) {
            this.line = line;
            this.rawDate = rawDate.toLowerCase(Locale.ROOT).replaceAll("[ ,]+", " ").trim();
            this.description = description;
            this.amounts = amounts;
        }
    }
}
