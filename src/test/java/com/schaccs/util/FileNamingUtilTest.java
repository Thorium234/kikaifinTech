package com.schaccs.util;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FileNamingUtilTest {

    @Test
    void suggestedNamesAreDescriptiveWithDate() {
        assertEquals("Kikay_FinTech_Fee_Balances_" + LocalDate.now() + ".pdf",
                FileNamingUtil.suggest("fee-balances.pdf"));
    }

    @Test
    void preservesAcronymsAndNumbers() {
        assertEquals("Kikay_FinTech_FORM_1_2026_" + LocalDate.now() + ".xlsx",
                FileNamingUtil.suggest("FORM_1_2026.xlsx"));
        assertEquals("Kikay_FinTech_Receipt_21571_" + LocalDate.now() + ".pdf",
                FileNamingUtil.suggest("receipt-21571.pdf"));
    }

    @Test
    void keepsSingleWordDescriptor() {
        assertEquals("Kikay_FinTech_Defaulters_" + LocalDate.now() + ".csv",
                FileNamingUtil.suggest("defaulters.csv"));
    }

    @Test
    void nullAndBlankArePassedThrough() {
        assertEquals(null, FileNamingUtil.suggest(null));
        assertEquals("", FileNamingUtil.suggest(""));
    }
}
