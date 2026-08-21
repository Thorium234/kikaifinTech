package com.schaccs.service.finance;

import com.schaccs.accounting.DoubleEntryEngine;
import com.schaccs.config.AppConfig;
import com.schaccs.config.CurrencyConfig;
import com.schaccs.enums.AccountType;
import com.schaccs.enums.TransactionType;
import com.schaccs.model.finance.FinancialTransaction;
import com.schaccs.model.finance.FiscalYear;
import com.schaccs.store.AccountStore;
import com.schaccs.store.LedgerStore;
import com.schaccs.repository.PersistenceService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class FiscalYearServiceTest {

    private FiscalYearService service;
    private AccountStore accountStore;
    private LedgerStore ledgerStore;

    @BeforeEach
    void setUp() {
        PersistenceService.getInstance().clearAll();
        accountStore = AccountStore.getInstance();
        accountStore.clear();
        ledgerStore = LedgerStore.getInstance();
        ledgerStore.clear();
        service = new FiscalYearService(accountStore, ledgerStore, new DoubleEntryEngine());
        AppConfig.getInstance().setCurrentUserRole("ADMIN");
    }

    @AfterEach
    void tearDown() {
        PersistenceService.getInstance().clearAll();
        accountStore.clear();
        ledgerStore.clear();
    }

    @Test
    @DisplayName("openFiscalYear creates a new fiscal year with correct dates")
    void openFiscalYear() {
        FiscalYear fy = service.openFiscalYear(2026);

        assertEquals(2026, fy.getYear());
        assertEquals(LocalDate.of(2026, 1, 1), fy.getStartDate());
        assertEquals(LocalDate.of(2026, 12, 31), fy.getEndDate());
        assertTrue(fy.isOpen());
        assertFalse(fy.isClosed());
        assertEquals(1, accountStore.getFiscalYears().size());
    }

    @Test
    @DisplayName("openFiscalYear throws if year already exists")
    void openFiscalYear_duplicate() {
        service.openFiscalYear(2026);
        assertThrows(IllegalStateException.class, () -> service.openFiscalYear(2026));
    }

    @Test
    @DisplayName("getCurrentFiscalYear returns the open fiscal year")
    void getCurrentFiscalYear() {
        FiscalYear fy = service.openFiscalYear(2026);
        assertEquals(fy, service.getCurrentFiscalYear());
    }

    @Test
    @DisplayName("getCurrentFiscalYear throws if no fiscal year is open")
    void getCurrentFiscalYear_none() {
        assertThrows(IllegalStateException.class, () -> service.getCurrentFiscalYear());
    }

    @Test
    @DisplayName("isTransactionAllowed returns true for null date")
    void isTransactionAllowed_null() {
        assertTrue(service.isTransactionAllowed(null));
    }

    @Test
    @DisplayName("isTransactionAllowed returns true when no fiscal year open")
    void isTransactionAllowed_noFy() {
        assertTrue(service.isTransactionAllowed(LocalDate.of(2026, 6, 15)));
    }

    @Test
    @DisplayName("isTransactionAllowed returns true for date within fiscal year")
    void isTransactionAllowed_within() {
        service.openFiscalYear(2026);
        assertTrue(service.isTransactionAllowed(LocalDate.of(2026, 6, 15)));
    }

    @Test
    @DisplayName("isTransactionAllowed returns false for date outside fiscal year")
    void isTransactionAllowed_outside() {
        service.openFiscalYear(2026);
        assertFalse(service.isTransactionAllowed(LocalDate.of(2025, 12, 31)));
        assertFalse(service.isTransactionAllowed(LocalDate.of(2027, 1, 1)));
    }

    @Test
    @DisplayName("closeFiscalYear closes current and opens next year")
    void closeFiscalYear() {
        service.openFiscalYear(2026);
        FiscalYear fy26 = service.getCurrentFiscalYear();

        service.closeFiscalYear(fy26);

        assertFalse(fy26.isOpen());
        assertTrue(fy26.isClosed());
        assertNotNull(fy26.getClosedAt());
        assertNotNull(fy26.getClosedBy());

        FiscalYear fy27 = service.getCurrentFiscalYear();
        assertEquals(2027, fy27.getYear());
        assertTrue(fy27.isOpen());
    }

    @Test
    @DisplayName("closeFiscalYear throws if fiscal year is not open")
    void closeFiscalYear_notOpen() {
        FiscalYear fy = new FiscalYear();
        fy.setYear(2025);
        fy.setOpen(false);
        fy.setClosed(true);
        assertThrows(IllegalStateException.class, () -> service.closeFiscalYear(fy));
    }

    @Test
    @DisplayName("closeFiscalYear throws if transactions exist after FY end")
    void closeFiscalYear_lateTransactions() {
        service.openFiscalYear(2026);
        FiscalYear fy = service.getCurrentFiscalYear();

        FinancialTransaction lateTx = new FinancialTransaction();
        lateTx.setAccountType(AccountType.TUITION_FEES);
        lateTx.setDate(LocalDate.of(2027, 1, 5));
        lateTx.setDebit(BigDecimal.ZERO);
        lateTx.setCredit(CurrencyConfig.money("5000"));
        ledgerStore.addTransaction(lateTx);

        assertThrows(IllegalStateException.class, () -> service.closeFiscalYear(fy));
    }
}
