package com.schaccs.config;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.Currency;
import java.util.Locale;

public final class CurrencyConfig {

    public static final String CURRENCY_CODE = "KES";
    public static final int SCALE = 2;
    public static final RoundingMode ROUNDING = RoundingMode.HALF_UP;
    public static final Locale LOCALE = Locale.forLanguageTag("en-KE");

    private static final NumberFormat FORMATTER = NumberFormat.getCurrencyInstance(LOCALE);

    static {
        FORMATTER.setCurrency(Currency.getInstance(CURRENCY_CODE));
        FORMATTER.setMinimumFractionDigits(SCALE);
        FORMATTER.setMaximumFractionDigits(SCALE);
    }

    private CurrencyConfig() {
    }

    public static BigDecimal money(double value) {
        return BigDecimal.valueOf(value).setScale(SCALE, ROUNDING);
    }

    public static BigDecimal money(String value) {
        return new BigDecimal(value).setScale(SCALE, ROUNDING);
    }

    public static BigDecimal money(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(SCALE, ROUNDING);
        }
        return value.setScale(SCALE, ROUNDING);
    }

    public static BigDecimal zero() {
        return BigDecimal.ZERO.setScale(SCALE, ROUNDING);
    }

    public static String format(BigDecimal amount) {
        if (amount == null) {
            amount = zero();
        }
        return "KSh " + String.format("%,.2f", amount);
    }

    public static String formatPlain(BigDecimal amount) {
        if (amount == null) {
            amount = zero();
        }
        return String.format("%,.2f", amount);
    }
}
