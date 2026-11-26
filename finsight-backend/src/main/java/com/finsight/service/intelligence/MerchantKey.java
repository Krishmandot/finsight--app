package com.finsight.service.intelligence;

import java.util.Locale;
import java.util.Set;
import java.util.StringJoiner;

/**
 * Reduces a bank description to a stable merchant key so that "UPI/NETFLIX.COM/8812" and
 * "NETFLIX.COM 4471 MUMBAI" are recognised as the same payee. Digits, punctuation and generic banking words
 * are dropped and the first two remaining words are kept.
 */
public final class MerchantKey {

    private static final Set<String> NOISE = Set.of(
        "upi", "imps", "neft", "rtgs", "pos", "txn", "ref", "payment", "pay", "paid", "to", "from", "via",
        "debit", "card", "purchase", "online", "ecom", "pvt", "ltd", "limited", "india", "autopay", "auto",
        "debited", "vpa", "the", "for", "bank", "transfer", "mandate", "nach", "ach", "mumbai", "delhi",
        "bangalore", "bengaluru", "www");

    private MerchantKey() {
    }

    /** Returns the merchant key, or an empty string when nothing meaningful remains. */
    public static String of(String description) {
        if (description == null) {
            return "";
        }
        String[] tokens = description.toLowerCase(Locale.ROOT).replaceAll("[^a-z]+", " ").trim().split("\\s+");
        StringJoiner key = new StringJoiner(" ");
        int kept = 0;
        for (String token : tokens) {
            if (token.length() < 3 || NOISE.contains(token)) {
                continue;
            }
            key.add(token);
            if (++kept == 2) {
                break;
            }
        }
        return key.toString();
    }

    /** "netflix com" becomes "Netflix Com" for display. */
    public static String display(String key) {
        StringJoiner out = new StringJoiner(" ");
        for (String word : key.split(" ")) {
            if (!word.isEmpty()) {
                out.add(Character.toUpperCase(word.charAt(0)) + word.substring(1));
            }
        }
        return out.toString();
    }
}
