package com.schaccs.util;

import com.schaccs.config.CurrencyConfig;

import java.math.BigDecimal;

public final class CurrencyUtil {

    private CurrencyUtil() {
    }

    public static String format(BigDecimal amount) {
        return CurrencyConfig.format(amount);
    }

    public static String formatPlain(BigDecimal amount) {
        return CurrencyConfig.formatPlain(amount);
    }

    public static BigDecimal parse(String text) {
        if (text == null || text.isBlank()) {
            return CurrencyConfig.zero();
        }
        String cleaned = text.replace("KSh", "")
                .replace("KES", "")
                .replace(",", "")
                .replace(" ", "")
                .trim();
        if (cleaned.isEmpty()) {
            return CurrencyConfig.zero();
        }
        return CurrencyConfig.money(cleaned);
    }

    /**
     * Simple English amount-in-words for Kenyan receipts (whole shillings).
     */
    public static String toWords(BigDecimal amount) {
        if (amount == null) {
            return "Zero only";
        }
        long value = amount.setScale(0, CurrencyConfig.ROUNDING).longValue();
        if (value == 0) {
            return "Zero only";
        }
        return capitalize(convert(value)) + " only";
    }

    private static String convert(long n) {
        if (n < 0) {
            return "minus " + convert(-n);
        }
        if (n < 20) {
            return ONES[(int) n];
        }
        if (n < 100) {
            return TENS[(int) (n / 10)] + (n % 10 == 0 ? "" : " " + ONES[(int) (n % 10)]);
        }
        if (n < 1000) {
            return ONES[(int) (n / 100)] + " hundred" + (n % 100 == 0 ? "" : " and " + convert(n % 100));
        }
        if (n < 1_000_000) {
            return convert(n / 1000) + " thousand" + (n % 1000 == 0 ? "" : " " + convert(n % 1000));
        }
        return convert(n / 1_000_000) + " million" + (n % 1_000_000 == 0 ? "" : " " + convert(n % 1_000_000));
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) {
            return s;
        }
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static final String[] ONES = {
            "zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine",
            "ten", "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen",
            "seventeen", "eighteen", "nineteen"
    };

    private static final String[] TENS = {
            "", "", "twenty", "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety"
    };
}
