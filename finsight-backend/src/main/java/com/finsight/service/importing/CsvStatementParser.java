package com.finsight.service.importing;

import com.finsight.api.dto.request.ValidTransactionDate;
import com.finsight.api.exception.IngestionException;
import com.finsight.domain.enums.ImportFormat;
import com.finsight.domain.enums.TransactionType;
import com.finsight.service.privacy.DescriptionRedactor;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Generic bank-statement CSV parser. It does not assume one bank's layout: the delimiter, encoding,
 * header row, column roles, date format and sign convention are all detected, and each can be overridden
 * through {@link ImportOptions}.
 */
@Component
public class CsvStatementParser implements StatementParser {

    public static final int MAX_DATA_ROWS = 10_000;
    private static final int HEADER_SCAN_ROWS = 40;

    private static final List<String> DATE_HEADERS = List.of("transaction date", "txn date", "trans date",
        "tran date", "date", "posting date", "post date", "booking date", "date of transaction", "value date",
        "value dt", "txn dt", "posted date");
    private static final List<String> DESCRIPTION_HEADERS = List.of("description", "narration", "particulars",
        "transaction details", "transaction description", "details", "remarks", "transaction remarks", "merchant",
        "merchant name", "payee", "memo", "name", "description narration");
    private static final List<String> AMOUNT_HEADERS = List.of("amount", "transaction amount", "txn amount", "amt",
        "value");
    private static final List<String> DEBIT_HEADERS = List.of("debit", "debits", "withdrawal", "withdrawals",
        "withdrawal amt", "withdrawal amount", "debit amount", "debit amt", "dr", "dr amount", "money out",
        "paid out", "spent", "outflow");
    private static final List<String> CREDIT_HEADERS = List.of("credit", "credits", "deposit", "deposits",
        "deposit amt", "deposit amount", "credit amount", "credit amt", "cr", "cr amount", "money in", "paid in",
        "received", "inflow");
    private static final List<String> DIRECTION_HEADERS = List.of("dr cr", "cr dr", "debit credit", "type",
        "transaction type", "txn type", "dr or cr", "cr or dr");
    private static final List<String> CURRENCY_HEADERS = List.of("currency", "ccy", "currency code");
    private static final Set<String> SUMMARY_WORDS = Set.of("total", "opening balance", "closing balance",
        "balance b f", "balance c f", "brought forward", "carried forward", "statement summary");

    @Override
    public ImportFormat format() {
        return ImportFormat.CSV;
    }

    @Override
    public boolean supports(String fileName, byte[] content) {
        String name = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        return (name.endsWith(".csv") || name.endsWith(".txt")) && !looksBinary(content);
    }

    @Override
    public ParsedStatement parse(byte[] content, ImportOptions options) {
        String text = decode(content);
        List<String> warnings = new ArrayList<>();
        char delimiter = detectDelimiter(text);
        List<List<String>> records = tokenize(text, delimiter);
        if (records.isEmpty()) {
            throw new IngestionException("The file is empty");
        }

        ColumnMapping mapping = options.mapping() != null ? options.mapping() : detectMapping(records);
        if (mapping == null || !mapping.isUsable()) {
            throw new IngestionException("Couldn't find date and amount columns. Make sure the file has a header "
                + "row (for example Date, Description, Amount or Debit/Credit), or choose the columns manually.");
        }
        List<String> headers = headerLabels(records, mapping);
        int firstDataRow = mapping.headerRow() + 1;
        if (records.size() - firstDataRow > MAX_DATA_ROWS) {
            throw new IngestionException("The file has more than " + MAX_DATA_ROWS + " rows. Split it into smaller files.");
        }

        // Date format: explicit, or detected from every value in the date column.
        String datePattern = options.dateFormat();
        if (datePattern == null || !DateDetector.PATTERNS.contains(datePattern)) {
            List<String> samples = new ArrayList<>();
            for (int i = firstDataRow; i < records.size(); i++) {
                samples.add(cell(records.get(i), mapping.date()));
            }
            DateDetector.Detection detection = DateDetector.detect(samples)
                .orElseThrow(() -> new IngestionException("Couldn't recognize the dates in the selected date column"));
            datePattern = detection.pattern();
            if (detection.ambiguous()) {
                warnings.add("Dates could be day/month or month/day. They were read as day/month ("
                    + DateDetector.example(datePattern) + " is 26 November). Change the date format if that's wrong.");
            }
        }

        ImportOptions.AmountConvention convention = options.amountConventionOrDefault();
        boolean usesSignedAmount = !mapping.hasSplitAmounts() && mapping.direction() == ColumnMapping.NONE;
        if (usesSignedAmount) {
            warnings.add(convention == ImportOptions.AmountConvention.NEGATIVE_IS_EXPENSE
                ? "Negative amounts were read as money out and positive amounts as money in."
                : "Positive amounts were read as money out and negative amounts as money in.");
        }

        List<ParsedStatement.Row> rows = new ArrayList<>();
        List<ParsedStatement.SkippedRow> skipped = new ArrayList<>();
        String defaultCurrency = options.defaultCurrency() == null ? "INR" : options.defaultCurrency();
        for (int i = firstDataRow; i < records.size(); i++) {
            List<String> record = records.get(i);
            int line = i + 1;
            if (record.stream().allMatch(String::isBlank)) {
                continue;
            }
            String rawDate = cell(record, mapping.date());
            Optional<LocalDate> date = DateDetector.parse(rawDate, datePattern);
            if (date.isEmpty()) {
                String joined = normalizeHeader(String.join(" ", record));
                boolean summary = SUMMARY_WORDS.stream().anyMatch(joined::contains);
                skipped.add(new ParsedStatement.SkippedRow(line, summary ? "Summary or balance row"
                    : rawDate.isBlank() ? "No date" : "Date not in the expected format"));
                continue;
            }
            if (!ValidTransactionDate.Rules.isPlausible(date.get())) {
                skipped.add(new ParsedStatement.SkippedRow(line, "Date is outside the supported range"));
                continue;
            }

            Resolved resolved = resolveAmount(record, mapping, convention);
            if (resolved.error != null) {
                skipped.add(new ParsedStatement.SkippedRow(line, resolved.error));
                continue;
            }

            String description = DescriptionRedactor.clean(cell(record, mapping.description()));
            if (description.isEmpty()) {
                description = "Statement entry";
            }
            if (description.length() > 2000) {
                description = description.substring(0, 2000);
            }
            String currency = defaultCurrency;
            String rawCurrency = cell(record, mapping.currency()).trim().toUpperCase(Locale.ROOT);
            if (rawCurrency.matches("[A-Z]{3}")) {
                currency = rawCurrency;
            }
            rows.add(new ParsedStatement.Row(line, date.get(), description, resolved.amount, resolved.type, currency));
        }

        if (rows.isEmpty() && !skipped.isEmpty()) {
            warnings.add("No rows could be imported. Check the column mapping and date format.");
        }
        return new ParsedStatement(ImportFormat.CSV, headers, mapping, datePattern, rows, skipped, warnings);
    }

    // ------------------------------------------------------------------------------------------------------

    private record Resolved(BigDecimal amount, TransactionType type, String error) {
        static Resolved error(String message) {
            return new Resolved(null, null, message);
        }
    }

    private static Resolved resolveAmount(List<String> record, ColumnMapping mapping,
                                          ImportOptions.AmountConvention convention) {
        if (mapping.hasSplitAmounts()) {
            Optional<AmountParser.ParsedAmount> debit = AmountParser.parse(cell(record, mapping.debit()));
            Optional<AmountParser.ParsedAmount> credit = AmountParser.parse(cell(record, mapping.credit()));
            boolean hasDebit = debit.isPresent() && debit.get().value().signum() != 0;
            boolean hasCredit = credit.isPresent() && credit.get().value().signum() != 0;
            if (hasDebit && hasCredit) {
                return Resolved.error("Both a debit and a credit amount are present");
            }
            if (hasDebit) {
                return new Resolved(debit.get().value().abs(), TransactionType.DEBIT, null);
            }
            if (hasCredit) {
                return new Resolved(credit.get().value().abs(), TransactionType.CREDIT, null);
            }
            if (mapping.amount() == ColumnMapping.NONE) {
                return Resolved.error(blankOrInvalid(cell(record, mapping.debit()), cell(record, mapping.credit())));
            }
        }

        String rawAmount = cell(record, mapping.amount());
        Optional<AmountParser.ParsedAmount> parsed = AmountParser.parse(rawAmount);
        if (parsed.isEmpty()) {
            return Resolved.error(rawAmount.isBlank() ? "No amount" : "Amount not recognized");
        }
        BigDecimal value = parsed.get().value();
        if (value.signum() == 0) {
            return Resolved.error("Amount is zero");
        }

        TransactionType type = directionFromCell(cell(record, mapping.direction()));
        if (type == null) {
            type = parsed.get().directionHint();
        }
        if (type == null) {
            boolean negative = value.signum() < 0;
            boolean expense = convention == ImportOptions.AmountConvention.NEGATIVE_IS_EXPENSE ? negative : !negative;
            type = expense ? TransactionType.DEBIT : TransactionType.CREDIT;
        }
        return new Resolved(value.abs(), type, null);
    }

    private static String blankOrInvalid(String debit, String credit) {
        return debit.isBlank() && credit.isBlank() ? "No amount" : "Amount not recognized";
    }

    static TransactionType directionFromCell(String raw) {
        String value = normalizeHeader(raw);
        if (value.isEmpty()) {
            return null;
        }
        return switch (value) {
            case "dr", "d", "debit", "withdrawal", "expense", "out", "purchase", "payment", "sale" -> TransactionType.DEBIT;
            case "cr", "c", "credit", "deposit", "income", "in" -> TransactionType.CREDIT;
            case "refund", "reversal" -> TransactionType.REFUND;
            default -> null;
        };
    }

    static ColumnMapping detectMapping(List<List<String>> records) {
        int limit = Math.min(records.size(), HEADER_SCAN_ROWS);
        for (int row = 0; row < limit; row++) {
            List<String> cells = records.get(row).stream().map(CsvStatementParser::normalizeHeader).toList();
            // Direction columns ("Dr/Cr", "Debit/Credit") are matched exactly and first, so they are never
            // mistaken for a debit or credit amount column.
            int direction = findExact(cells, DIRECTION_HEADERS);
            int date = find(cells, DATE_HEADERS, taken(direction));
            int amount = find(cells, AMOUNT_HEADERS, taken(direction, date));
            int debit = find(cells, DEBIT_HEADERS, taken(direction, date, amount));
            int credit = find(cells, CREDIT_HEADERS, taken(direction, date, amount, debit));
            if (date == ColumnMapping.NONE || (amount == ColumnMapping.NONE && debit == ColumnMapping.NONE
                && credit == ColumnMapping.NONE)) {
                continue;
            }
            int description = find(cells, DESCRIPTION_HEADERS, taken(direction, date, amount, debit, credit));
            int currency = find(cells, CURRENCY_HEADERS, taken(date, amount, debit, credit, description, direction));
            return new ColumnMapping(row, date, description, amount, debit, credit, direction, currency);
        }
        return null;
    }

    /** Finds the first column matching the synonyms in priority order, skipping already-assigned columns. */
    private static int find(List<String> cells, List<String> synonyms, Set<Integer> taken) {
        for (String synonym : synonyms) {
            for (int i = 0; i < cells.size(); i++) {
                String cell = cells.get(i);
                if (taken.contains(i) || cell.contains("balance")) {
                    continue;
                }
                if (cell.equals(synonym) || cell.startsWith(synonym + " ")) {
                    return i;
                }
            }
        }
        return ColumnMapping.NONE;
    }

    private static Set<Integer> taken(int... columns) {
        Set<Integer> set = new java.util.HashSet<>();
        for (int column : columns) {
            set.add(column);
        }
        return set;
    }

    private static int findExact(List<String> cells, List<String> synonyms) {
        for (String synonym : synonyms) {
            int index = cells.indexOf(synonym);
            if (index >= 0) {
                return index;
            }
        }
        return ColumnMapping.NONE;
    }

    private static List<String> headerLabels(List<List<String>> records, ColumnMapping mapping) {
        int width = records.stream().limit(HEADER_SCAN_ROWS + 50L).mapToInt(List::size).max().orElse(0);
        List<String> labels = new ArrayList<>();
        List<String> header = mapping.headerRow() >= 0 && mapping.headerRow() < records.size()
            ? records.get(mapping.headerRow()) : List.of();
        for (int i = 0; i < width; i++) {
            String label = i < header.size() ? header.get(i).trim() : "";
            labels.add(label.isEmpty() ? "Column " + (i + 1) : label);
        }
        return labels;
    }

    static String normalizeHeader(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
    }

    private static String cell(List<String> record, int index) {
        return index >= 0 && index < record.size() && record.get(index) != null ? record.get(index) : "";
    }

    static String decode(byte[] content) {
        int offset = content.length >= 3 && (content[0] & 0xFF) == 0xEF && (content[1] & 0xFF) == 0xBB
            && (content[2] & 0xFF) == 0xBF ? 3 : 0;
        ByteBuffer buffer = ByteBuffer.wrap(content, offset, content.length - offset);
        try {
            return StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(buffer).toString();
        } catch (CharacterCodingException e) {
            // Older bank exports are often Windows-1252.
            return new String(content, offset, content.length - offset, Charset.forName("windows-1252"));
        }
    }

    static char detectDelimiter(String text) {
        String[] lines = text.split("\\r?\\n", 60);
        char best = ',';
        double bestScore = -1;
        for (char candidate : new char[]{',', ';', '\t', '|'}) {
            Map<Integer, Integer> frequency = new HashMap<>();
            int counted = 0;
            for (String line : lines) {
                if (line.isBlank()) {
                    continue;
                }
                int fields = tokenize(line, candidate).stream().findFirst().map(List::size).orElse(1);
                if (fields > 1) {
                    frequency.merge(fields, 1, Integer::sum);
                }
                counted++;
            }
            if (counted == 0) {
                continue;
            }
            // Score: how many lines share the most common field count, weighted by that count.
            Map.Entry<Integer, Integer> mode = frequency.entrySet().stream()
                .max(Map.Entry.<Integer, Integer>comparingByValue().thenComparing(Map.Entry.comparingByKey()))
                .orElse(null);
            double score = mode == null ? 0 : mode.getValue() * Math.log(mode.getKey() + 1.0);
            if (score > bestScore) {
                bestScore = score;
                best = candidate;
            }
        }
        return best;
    }

    /** RFC 4180 tokenizer: quoted fields may contain delimiters, doubled quotes and line breaks. */
    static List<List<String>> tokenize(String text, char delimiter) {
        List<List<String>> records = new ArrayList<>();
        List<String> current = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;
        boolean fieldStarted = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '"') {
                        field.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    field.append(c);
                }
            } else if (c == '"' && field.toString().isBlank()) {
                inQuotes = true;
                field.setLength(0);
                fieldStarted = true;
            } else if (c == delimiter) {
                current.add(field.toString().trim());
                field.setLength(0);
                fieldStarted = true;
            } else if (c == '\n' || c == '\r') {
                if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') {
                    i++;
                }
                if (fieldStarted || !field.isEmpty() || !current.isEmpty()) {
                    current.add(field.toString().trim());
                    records.add(current);
                }
                current = new ArrayList<>();
                field.setLength(0);
                fieldStarted = false;
            } else {
                field.append(c);
                fieldStarted = true;
            }
        }
        if (fieldStarted || !field.isEmpty() || !current.isEmpty()) {
            current.add(field.toString().trim());
            records.add(current);
        }
        return records;
    }

    private static boolean looksBinary(byte[] content) {
        int limit = Math.min(content.length, 4096);
        for (int i = 0; i < limit; i++) {
            if (content[i] == 0) {
                return true;
            }
        }
        return content.length >= 5 && content[0] == '%' && content[1] == 'P' && content[2] == 'D' && content[3] == 'F';
    }
}
