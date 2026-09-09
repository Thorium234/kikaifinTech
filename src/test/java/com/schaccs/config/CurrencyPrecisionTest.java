package com.schaccs.config;

import com.schaccs.util.CurrencyUtil;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Guards the financial-math contract: BigDecimal everywhere, scale always 2,
 * and money fields rejecting alphabetic characters, negative values, symbols
 * and malformed numbers before they ever reach a journal entry.
 */
class CurrencyPrecisionTest {

    @Test
    void addsExactWithoutBinaryFloatDrift() {
        assertEquals(0, new BigDecimal("0.30").compareTo(CurrencyConfig.money(0.1).add(CurrencyConfig.money(0.2))));
        assertEquals(0, new BigDecimal("0.30").compareTo(CurrencyConfig.money(0.1 + 0.2)));
    }

    @Test
    void alwaysScaleTwo() {
        assertEquals(2, CurrencyConfig.money("10.5").scale());
        assertEquals(2, CurrencyConfig.money(7).scale());
        assertEquals(2, CurrencyConfig.money(new BigDecimal("99.9")).scale());
        assertEquals(2, CurrencyConfig.zero().scale());
        assertEquals(2, CurrencyConfig.money((BigDecimal) null).scale());
    }

    @Test
    void moneyRejectsInvalidInputByReturningZero() {
        assertZero(CurrencyUtil.parse("abc"));    // alphabetic characters
        assertZero(CurrencyUtil.parse("-500"));   // negative values
        assertZero(CurrencyUtil.parse("5#"));     // symbols
        assertZero(CurrencyUtil.parse("1.2.3"));  // malformed (multiple decimal points)
        assertZero(CurrencyUtil.parse(""));
        assertZero(CurrencyUtil.parse(null));
    }

    @Test
    void moneyAcceptsValidFormats() {
        assertEquals(0, new BigDecimal("1200.50").compareTo(CurrencyUtil.parse("1,200.50")));
        assertEquals(0, new BigDecimal("500.00").compareTo(CurrencyUtil.parse("KSh 500")));
        assertEquals(0, new BigDecimal("0.50").compareTo(CurrencyUtil.parse("0.5")));
        assertEquals(0, new BigDecimal("999999.99").compareTo(CurrencyUtil.parse("999999.99")));
    }

    @Test
    void formattedValuesMatchStoredPrecision() {
        assertEquals("1,250.40", CurrencyConfig.formatPlain(new BigDecimal("1250.4")));
        String formatted = CurrencyConfig.format(new BigDecimal("1250.4"));
        assertTrue(formatted.contains("1,250.40"), formatted);
        assertTrue(formatted.toLowerCase().contains("ksh"), formatted);
    }

    @Test
    void centsAreSpeltExactlyFromTheStoredAmount() {
        String words = CurrencyUtil.toWords(new BigDecimal("1250.40"));
        assertTrue(words.toLowerCase().contains("one thousand two hundred and fifty"), words);
        assertTrue(words.contains("Forty cents"), words);
        assertTrue(words.endsWith(" only"), words);
    }

    private static void assertZero(BigDecimal value) {
        assertEquals(0, CurrencyConfig.zero().compareTo(value), "Expected rejected money input to yield zero, got: " + value);
    }
}