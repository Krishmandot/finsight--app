package com.finsight.service.categorization;

import com.finsight.domain.enums.TransactionType;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Keyword rules mapped to the seeded system categories (by name). Rules are evaluated in order and the
 * first match wins, so more specific merchants (food delivery) come before generic ones (ride hailing).
 * Keywords are matched as whole words against normalized descriptions.
 */
final class BuiltInRules {

    record Rule(String categoryName, Set<TransactionType> types, List<String> keywords) {
    }

    private static final Set<TransactionType> SPENDING = EnumSet.of(TransactionType.DEBIT, TransactionType.REFUND);
    private static final Set<TransactionType> INCOME = EnumSet.of(TransactionType.CREDIT);
    private static final Set<TransactionType> ANY = EnumSet.allOf(TransactionType.class);

    static final List<Rule> RULES = List.of(
        new Rule("Salary", INCOME, List.of("salary", "payroll", "sal", "wages", "stipend")),
        new Rule("Other Income", INCOME, List.of("interest", "int pd", "dividend", "cashback", "reward", "refund")),
        new Rule("Restaurants", SPENDING, List.of(
            "swiggy", "zomato", "uber eats", "ubereats", "doordash", "grubhub", "deliveroo", "dominos", "domino",
            "pizza hut", "mcdonalds", "mcdonald", "kfc", "burger king", "starbucks", "subway", "cafe", "coffee",
            "restaurant", "dining", "eatsure", "faasos", "box8", "chaayos", "dunkin", "barbeque nation")),
        new Rule("Groceries", SPENDING, List.of(
            "bigbasket", "big basket", "blinkit", "grofers", "zepto", "instamart", "dmart", "d mart", "jiomart",
            "reliance fresh", "reliance smart", "more retail", "spencers", "nature s basket", "grocery", "grocer",
            "supermarket", "hypermarket", "kirana", "walmart", "tesco", "aldi", "lidl", "whole foods", "safeway",
            "kroger", "costco", "trader joe")),
        new Rule("Fuel", SPENDING, List.of(
            "petrol", "diesel", "fuel", "hpcl", "bpcl", "iocl", "indian oil", "bharat petroleum",
            "hindustan petroleum", "shell", "nayara", "filling station", "gas station", "chevron", "exxon")),
        new Rule("Public Transport", SPENDING, List.of(
            "metro", "irctc", "railway", "rail", "bus", "redbus", "ksrtc", "msrtc", "transit", "mta",
            "tfl", "oyster")),
        new Rule("Transportation", SPENDING, List.of(
            "uber", "ola", "rapido", "lyft", "taxi", "cab", "parking", "fastag", "toll", "blusmart")),
        new Rule("Subscriptions", SPENDING, List.of(
            "netflix", "spotify", "prime video", "amazon prime", "hotstar", "disney", "youtube premium",
            "youtube", "apple com bill", "itunes", "google play", "sonyliv", "zee5", "jiocinema", "audible",
            "icloud", "dropbox", "adobe", "microsoft 365", "office 365", "chatgpt", "openai", "notion", "canva",
            "linkedin premium", "subscription")),
        new Rule("Electricity", SPENDING, List.of(
            "electricity", "bescom", "msedcl", "mseb", "tata power", "adani electricity", "bses", "tneb",
            "tangedco", "cesc", "power bill")),
        new Rule("Internet", SPENDING, List.of(
            "broadband", "internet", "fibernet", "act fibernet", "airtel", "jio", "vodafone", "bsnl",
            "hathway", "excitel", "comcast", "verizon", "mobile recharge", "recharge", "postpaid", "prepaid")),
        new Rule("Utilities", SPENDING, List.of("water bill", "gas bill", "piped gas", "mahanagar gas", "igl", "utility")),
        new Rule("Healthcare", SPENDING, List.of(
            "pharmacy", "pharma", "apollo", "medplus", "netmeds", "pharmeasy", "1mg", "tata 1mg", "hospital",
            "clinic", "medical", "diagnostic", "dental", "doctor", "practo", "chemist")),
        new Rule("Education", SPENDING, List.of(
            "school", "college", "university", "tuition", "coursera", "udemy", "byju", "unacademy", "edx",
            "exam fee", "education")),
        new Rule("Travel", SPENDING, List.of(
            "makemytrip", "goibibo", "cleartrip", "yatra", "ixigo", "airbnb", "booking com", "oyo", "hotel",
            "airlines", "airline", "indigo", "air india", "vistara", "spicejet", "akasa", "expedia", "flight")),
        new Rule("Entertainment", SPENDING, List.of(
            "bookmyshow", "pvr", "inox", "cinema", "movie", "concert", "gaming", "steam", "playstation", "xbox")),
        new Rule("Shopping", SPENDING, List.of(
            "amazon", "flipkart", "myntra", "ajio", "nykaa", "meesho", "tata cliq", "snapdeal", "ikea", "decathlon",
            "croma", "reliance digital", "lifestyle", "westside", "pantaloons", "h m", "zara", "uniqlo", "ebay",
            "etsy")),
        new Rule("ATM Withdrawal", EnumSet.of(TransactionType.DEBIT), List.of(
            "atm", "cash withdrawal", "atw", "nfs cash", "cash wdl")),
        new Rule("Financial Services", SPENDING, List.of(
            "emi", "loan", "insurance", "lic", "premium", "credit card bill", "cc bill", "bank charges",
            "service charge", "annual fee", "late fee", "gst on charges", "mutual fund", "sip", "zerodha",
            "groww", "upstox")),
        new Rule("Transfer", ANY, List.of("self transfer", "own account", "sweep", "fd booking"))
    );

    private BuiltInRules() {
    }
}
