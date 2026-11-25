package com.finsight.service.importing;

import com.finsight.domain.entity.Transaction;
import com.finsight.domain.enums.IngestionSource;
import com.finsight.service.categorization.TextNormalizer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Matches imported rows against the user's existing (non-rejected, non-deleted) transactions.
 *
 * <ul>
 *   <li><b>Exact</b>: same date, amount, direction and description (ignoring case, punctuation and digits).
 *       Typically the same statement imported twice.</li>
 *   <li><b>Possible</b>: same date, amount and direction as a transaction the user entered manually, with a
 *       different description. Typically the bank's version of a hand-entered expense.</li>
 * </ul>
 *
 * Matching is one-to-one: each existing transaction can absorb at most one imported row. So two identical
 * coffees on the same day in the statement, with one already recorded, yield one duplicate and one new
 * row. Recurring payments on different dates never match each other.
 */
public final class DuplicateDetector {

    public enum Kind {
        NONE,
        EXACT,
        POSSIBLE
    }

    public record Match(Kind kind, UUID existingId) {
        static final Match NONE = new Match(Kind.NONE, null);
    }

    private final Map<String, Deque<Transaction>> byExactKey = new HashMap<>();
    private final Map<String, Deque<Transaction>> manualByLooseKey = new HashMap<>();
    private final Set<UUID> consumed = new HashSet<>();

    public DuplicateDetector(List<Transaction> existing) {
        for (Transaction transaction : existing) {
            if (transaction.getAmount() == null || transaction.getTransactionDate() == null) {
                continue;
            }
            byExactKey.computeIfAbsent(exactKey(transaction), k -> new ArrayDeque<>()).add(transaction);
            if (transaction.getIngestionSource() == IngestionSource.MANUAL) {
                manualByLooseKey.computeIfAbsent(looseKey(transaction), k -> new ArrayDeque<>()).add(transaction);
            }
        }
    }

    public List<Match> matchAll(List<ParsedStatement.Row> rows) {
        List<Match> matches = new ArrayList<>(rows.size());
        rows.forEach(row -> matches.add(match(row)));
        return matches;
    }

    Match match(ParsedStatement.Row row) {
        Transaction exact = take(byExactKey.get(exactKey(row)));
        if (exact != null) {
            return new Match(Kind.EXACT, exact.getId());
        }
        Transaction possible = take(manualByLooseKey.get(looseKey(row)));
        if (possible != null) {
            return new Match(Kind.POSSIBLE, possible.getId());
        }
        return Match.NONE;
    }

    private Transaction take(Deque<Transaction> candidates) {
        if (candidates == null) {
            return null;
        }
        while (!candidates.isEmpty()) {
            Transaction candidate = candidates.poll();
            if (consumed.add(candidate.getId())) {
                return candidate;
            }
        }
        return null;
    }

    private static String looseKey(Transaction t) {
        return t.getTransactionDate() + "|" + t.getAmount().stripTrailingZeros().toPlainString() + "|" + t.getType();
    }

    private static String exactKey(Transaction t) {
        return looseKey(t) + "|" + TextNormalizer.normalizeWords(t.getRedactedDescription());
    }

    private static String looseKey(ParsedStatement.Row row) {
        return row.date() + "|" + row.amount().stripTrailingZeros().toPlainString() + "|" + row.type();
    }

    private static String exactKey(ParsedStatement.Row row) {
        return looseKey(row) + "|" + TextNormalizer.normalizeWords(row.description());
    }
}
