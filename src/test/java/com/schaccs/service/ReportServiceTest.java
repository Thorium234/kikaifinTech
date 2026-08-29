package com.schaccs.service;

import com.schaccs.accounting.AccountingEngine;
import com.schaccs.config.AppConfig;
import com.schaccs.config.CurrencyConfig;
import com.schaccs.enums.AccountType;
import com.schaccs.enums.TransactionType;
import com.schaccs.model.finance.JournalEntry;
import com.schaccs.model.report.BalanceSheetRow;
import com.schaccs.model.report.CashbookRow;
import com.schaccs.model.report.IncomeExpenditureRow;
import com.schaccs.model.report.TrialBalanceRow;
import com.schaccs.repository.PersistenceService;
import com.schaccs.service.report.ReportService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * V2 financial statements: trial balance must balance, cashbook merges income
 * and expenditure with running balance and separates by funding source / bank
 * account, and Income &amp; Expenditure and Balance Sheet aggregate account
 * balances. All postings go through the AccountingEngine (double-entry), never
 * by mutating ledger stores directly.
 */
class ReportServiceTest {

    private final AccountingEngine engine = new AccountingEngine();
    private final ReportService report = new ReportService();

    @BeforeEach
    void setUp() {
        PersistenceService.getInstance().clearAll();
        AppConfig.getInstance().setCurrentUserRole("PRINCIPAL");
    }

    @AfterEach
    void tearDown() {
        PersistenceService.getInstance().clearAll();
    }

    private static BigDecimal amount(String v) {
        return CurrencyConfig.money(v);
    }

    private void post(LocalDate date, String ref, AccountType debitAccount, AccountType creditAccount,
                      BigDecimal amount) {
        JournalEntry journal = new JournalEntry();
        journal.setDate(date);
        journal.setReference(ref);
        journal.setNarration("Test posting — " + ref);
        journal.addLine(debitAccount, "X", amount, CurrencyConfig.zero(), "Debit leg");
        journal.addLine(creditAccount, "X", CurrencyConfig.zero(), amount, "Credit leg");
        engine.postTransaction(journal, TransactionType.FEE_RECEIPT, null, null, null);
    }

    /** Parent fees into boarding bank + government capitation into tuition bank. */
    private void seedTwoFundingSources() {
        // Parent-funded boarding fees received into the boarding/PTA bank account
        post(LocalDate.of(2026, 3, 5), "P-01",
                AccountType.BANK_BOARDING, AccountType.FEES_BOARDING_ACTIVITY,
                amount("10000"));
        // Government capitation received into the tuition bank account
        post(LocalDate.of(2026, 3, 10), "G-01",
                AccountType.BANK_TUITION, AccountType.GOVT_CAPITATION_TUITION,
                amount("25000"));
        // A payment out of the boarding bank account (parent-funded supply)
        post(LocalDate.of(2026, 3, 15), "P-02",
                AccountType.SUPPLIES, AccountType.BANK_BOARDING,
                amount("2000"));
    }

    @Test
    @DisplayName("Trial balance totals are equal (debits == credits)")
    void trialBalanceBalances() {
        seedTwoFundingSources();
        List<TrialBalanceRow> rows = report.trialBalance();
        BigDecimal totalDebit = rows.stream().map(TrialBalanceRow::getDebit)
                .reduce(CurrencyConfig.zero(), BigDecimal::add);
        BigDecimal totalCredit = rows.stream().map(TrialBalanceRow::getCredit)
                .reduce(CurrencyConfig.zero(), BigDecimal::add);
        assertEquals(0, totalDebit.compareTo(totalCredit),
                "Trial balance debit total must equal credit total");
        assertTrue(rows.stream().anyMatch(r -> r.getDebit().signum() != 0
                || r.getCredit().signum() != 0), "Ledger has activity");
        assertTrue(report.isLedgerBalanced());
        // 10,000 boarding + 25,000 tuition + 2,000 supplies = 37,000 each side
        assertEquals(0, amount("37000").compareTo(totalDebit));
    }

    @Test
    @DisplayName("Cashbook merges income and expenditure and computes a running balance")
    void cashbookRunningBalance() {
        seedTwoFundingSources();
        List<CashbookRow> rows = report.cashbook(
                LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31));
        // All bank legs: P-01 (credit feat no) — but only bank accounts appear
        assertFalse(rows.isEmpty());
        // Opening 0 → +10000 boarding → +25000 tuition → -2000 supplies = 33000
        assertEquals(0, amount("33000").compareTo(rows.get(rows.size() - 1).getBalance()),
                "Running balance accumulates receipts minus payments");
    }

    @Test
    @DisplayName("Cashbook separates by funding source: parent vs government")
    void cashbookSeparatesByFundingSource() {
        seedTwoFundingSources();
        List<CashbookRow> govt = report.cashbook(LocalDate.of(2026, 3, 1),
                LocalDate.of(2026, 3, 31), null, "GOVT");
        assertEquals(0, amount("25000").compareTo(
                govt.get(govt.size() - 1).getBalance()),
                "Government cashbook only sees capitation");
        assertTrue(govt.stream().allMatch(r -> r.getAccountType() == AccountType.BANK_TUITION));

        List<CashbookRow> parent = report.cashbook(LocalDate.of(2026, 3, 1),
                LocalDate.of(2026, 3, 31), null, "PARENT");
        assertEquals(0, amount("8000").compareTo(parent.get(parent.size() - 1).getBalance()),
                "Parent cashbook: +10000 boarding, -2000 supplies, closes at 8000");
        assertTrue(parent.stream().allMatch(r -> r.getAccountType() == AccountType.BANK_BOARDING));
    }

    @Test
    @DisplayName("Cashbook filters by a single bank account")
    void cashbookFiltersByBankAccount() {
        seedTwoFundingSources();
        List<CashbookRow> boarding = report.cashbook(LocalDate.of(2026, 3, 1),
                LocalDate.of(2026, 3, 31), AccountType.BANK_BOARDING, null);
        assertEquals(0, amount("8000").compareTo(
                boarding.get(boarding.size() - 1).getBalance()),
                "Boarding bank closes at 10000 - 2000 = 8000");
        assertTrue(boarding.stream()
                .allMatch(r -> r.getAccountType() == AccountType.BANK_BOARDING));
    }

    @Test
    @DisplayName("Income & Expenditure rows categorize income and expenses")
    void incomeExpenditureCategorizes() {
        seedTwoFundingSources();
        List<IncomeExpenditureRow> rows = report.incomeExpenditure();
        assertTrue(rows.stream().anyMatch(r -> "Income".equals(r.getCategory())
                && r.getItem().contains("Boarding/Activity")
                && r.getAmount().compareTo(amount("10000")) == 0));
        assertTrue(rows.stream().anyMatch(r -> "Income".equals(r.getCategory())
                && r.getItem().contains("Capitation")
                && r.getAmount().compareTo(amount("25000")) == 0));
        assertTrue(rows.stream().anyMatch(r -> "Expenditure".equals(r.getCategory())
                && r.getItem().contains("Supplies")
                && r.getAmount().compareTo(amount("2000")) == 0));
    }

    @Test
    @DisplayName("Balance sheet includes assets, liabilities and fund balance")
    void balanceSheetHasSections() {
        seedTwoFundingSources();
        List<BalanceSheetRow> rows = report.balanceSheet();
        assertTrue(rows.stream().anyMatch(r -> "Assets".equals(r.getSection())
                && r.getItem().contains("Boarding / PTA")));
        assertTrue(rows.stream().anyMatch(r -> "Assets".equals(r.getSection())
                && r.getItem().contains("Subsidized Tuition")));
        assertTrue(rows.stream().anyMatch(r -> "Fund Balance".equals(r.getSection())));
    }

    @Test
    @DisplayName("Trial balance for a year isolates prior-year balances")
    void trialBalanceYearIsolation() {
        // Prior year (2025) transaction must be excluded from the 2026 report.
        post(LocalDate.of(2025, 11, 20), "P-2025",
                AccountType.BANK_BOARDING, AccountType.FEES_BOARDING_ACTIVITY, amount("50000"));
        // Current year (2026) transaction must be included.
        post(LocalDate.of(2026, 4, 2), "P-2026",
                AccountType.BANK_BOARDING, AccountType.FEES_BOARDING_ACTIVITY, amount("12000"));

        List<TrialBalanceRow> currentYear = report.trialBalanceForYear(2026);
        for (TrialBalanceRow row : currentYear) {
            if (row.getAccountType() == AccountType.BANK_BOARDING) {
                assertEquals(0, row.getDebit().compareTo(amount("12000")),
                        "Only the 2026 boarding receipt appears in the 2026 trial balance");
            }
        }
        // Global trial balance still sees both years.
        List<TrialBalanceRow> all = report.trialBalance();
        BigDecimal totalDebit = all.stream().map(TrialBalanceRow::getDebit)
                .reduce(CurrencyConfig.zero(), BigDecimal::add);
        assertEquals(0, totalDebit.compareTo(amount("62000")),
                "Unscoped trial balance includes both years' activity");
    }
}
