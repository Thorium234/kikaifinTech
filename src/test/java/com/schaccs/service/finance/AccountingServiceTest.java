package com.schaccs.service.finance;

import com.schaccs.accounting.AccountingEngine;
import com.schaccs.config.CurrencyConfig;
import com.schaccs.enums.AccountType;
import com.schaccs.model.finance.FinancialTransaction;
import com.schaccs.model.finance.LedgerEntry;
import com.schaccs.store.LedgerStore;
import com.schaccs.repository.PersistenceService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AccountingServiceTest {

    private AccountingService service;
    private LedgerStore ledgerStore;

    @BeforeEach
    void setUp() {
        PersistenceService.getInstance().clearAll();
        ledgerStore = LedgerStore.getInstance();
        ledgerStore.clear();
        service = new AccountingService(new AccountingEngine(), ledgerStore);
    }

    @AfterEach
    void tearDown() {
        PersistenceService.getInstance().clearAll();
        ledgerStore.clear();
    }

    private void seedEntry(AccountType type, BigDecimal debit, BigDecimal credit) {
        LedgerEntry entry = new LedgerEntry();
        entry.setAccountType(type);
        entry.setDebit(debit);
        entry.setCredit(credit);
        ledgerStore.addLedgerEntry(entry);
    }

    @Test
    @DisplayName("balance returns zero for empty ledger")
    void balance_empty() {
        assertEquals(0, service.balance(AccountType.CASH_AT_BANK).compareTo(BigDecimal.ZERO));
    }

    @Test
    @DisplayName("balance tracks debit-normal accounts correctly")
    void balance_debitNormal() {
        seedEntry(AccountType.CASH_AT_BANK, CurrencyConfig.money("10000"), CurrencyConfig.zero());
        seedEntry(AccountType.CASH_AT_BANK, CurrencyConfig.money("5000"), CurrencyConfig.money("2000"));

        assertEquals(0, service.balance(AccountType.CASH_AT_BANK).compareTo(CurrencyConfig.money("13000")));
    }

    @Test
    @DisplayName("balance tracks credit-normal accounts correctly")
    void balance_creditNormal() {
        seedEntry(AccountType.TUITION_FEES, CurrencyConfig.zero(), CurrencyConfig.money("25000"));

        assertEquals(0, service.balance(AccountType.TUITION_FEES).compareTo(CurrencyConfig.money("25000")));
    }

    @Test
    @DisplayName("allBalances returns balances for all account types")
    void allBalances() {
        seedEntry(AccountType.CASH_AT_BANK, CurrencyConfig.money("10000"), CurrencyConfig.zero());
        seedEntry(AccountType.TUITION_FEES, CurrencyConfig.zero(), CurrencyConfig.money("8000"));

        Map<AccountType, BigDecimal> balances = service.allBalances();
        assertNotNull(balances);
        assertEquals(0, balances.get(AccountType.CASH_AT_BANK).compareTo(CurrencyConfig.money("10000")));
        assertEquals(0, balances.get(AccountType.TUITION_FEES).compareTo(CurrencyConfig.money("8000")));
    }

    @Test
    @DisplayName("transactions returns ledger transaction list")
    void transactions() {
        assertTrue(service.transactions().isEmpty());

        seedEntry(AccountType.CASH_AT_BANK, CurrencyConfig.money("5000"), CurrencyConfig.zero());
        FinancialTransaction tx = new FinancialTransaction();
        tx.setAccountType(AccountType.CASH_AT_BANK);
        tx.setDebit(CurrencyConfig.money("5000"));
        ledgerStore.addTransaction(tx);
        assertEquals(1, service.transactions().size());
    }
}
