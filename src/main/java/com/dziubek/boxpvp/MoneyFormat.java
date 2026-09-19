package com.dziubek.boxpvp;

/**
 * Wspólne formatowanie kwot ze skrótami (k/mil/bil/t) dla dużych liczb - używane w Kantorze i
 * na Rynku, żeby duże salda/ceny nie zajmowały pół ekranu cyfr.
 */
public final class MoneyFormat {

    private static final String[] SUFFIXES = {"", "k", "mil", "bil", "t"};

    private MoneyFormat() {
    }

    public static String format(double value) {
        double abs = Math.abs(value);
        if (abs < 1000) {
            return trim(value);
        }
        int tier = Math.min((int) (Math.log10(abs) / 3), SUFFIXES.length - 1);
        double scaled = value / Math.pow(1000, tier);
        return trim(scaled) + SUFFIXES[tier];
    }

    private static String trim(double value) {
        if (value == Math.floor(value)) {
            return String.valueOf((long) value);
        }
        return String.format("%.2f", value);
    }
}
