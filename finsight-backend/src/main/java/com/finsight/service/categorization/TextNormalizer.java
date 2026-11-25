package com.finsight.service.categorization;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;

/** Normalization shared by categorization rules and duplicate detection. */
public final class TextNormalizer {

    /** Tokens that carry no information about the merchant in bank narrations. */
    private static final Set<String> NOISE = Set.of(
        "upi", "pos", "neft", "imps", "rtgs", "ach", "nach", "ecs", "txn", "trf", "transfer", "payment", "paid",
        "to", "from", "ref", "refno", "the", "and", "for", "via", "debit", "credit", "card", "purchase", "online",
        "bil", "bill", "inb", "mb", "ib", "pay", "dr", "cr", "by", "at", "on", "in", "of", "www", "com", "ltd",
        "pvt", "private", "limited", "india", "mumbai", "delhi", "bangalore", "bengaluru", "chennai", "pune",
        "hyderabad", "kolkata", "payu", "razorpay", "paytm", "phonepe", "gpay", "bhim", "okaxis", "oksbi",
        "okicici", "okhdfcbank", "ybl", "ibl", "axl"
    );

    private TextNormalizer() {
    }

    /** Lower-case, accents stripped, everything except letters and digits replaced by single spaces. */
    public static String normalize(String text) {
        if (text == null) {
            return "";
        }
        String decomposed = Normalizer.normalize(text, Normalizer.Form.NFKD).replaceAll("\\p{M}", "");
        return decomposed.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
    }

    /** Like {@link #normalize} but also drops digits, so reference numbers do not affect comparisons. */
    public static String normalizeWords(String text) {
        return normalize(text).replaceAll("[0-9]+", " ").replaceAll("\\s+", " ").trim();
    }

    /**
     * Best-effort merchant keyword for suggesting a personal rule: the first word of at least three letters
     * that is not banking noise. Returns an empty string when nothing suitable exists.
     */
    public static String suggestKeyword(String description) {
        for (String token : normalizeWords(description).split(" ")) {
            if (token.length() >= 3 && !NOISE.contains(token)) {
                return token;
            }
        }
        return "";
    }

    /** True when {@code keyword} occurs in {@code normalizedText} as whole word(s). */
    public static boolean containsWord(String normalizedText, String keyword) {
        if (keyword.isEmpty() || normalizedText.isEmpty()) {
            return false;
        }
        String haystack = " " + normalizedText + " ";
        return haystack.contains(" " + keyword + " ");
    }
}
