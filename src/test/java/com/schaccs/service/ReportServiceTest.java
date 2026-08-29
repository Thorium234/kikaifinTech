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
import com.schaccs.model.student.Student;
import com.schaccs.model.student.StudentFeeLedger;
import com.schaccs.repository.PersistenceService;
import com.schaccs.service.report.ReportService;
import com.schaccs.store.StudentStore;
import com.schaccs.enums.BoardingStatus;
import com.schaccs.enums.StudentStatus;
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

    private Student defaulter(String adm, String form, String stream, BigDecimal charge, BigDecimal paid) {
        Student s = new Student();
        s.setAdmissionNumber(adm);
        s.setName("Defaulter " + adm);
        s.setFormClass(form);
        s.setStream(stream);
        s.setBoardingStatus(BoardingStatus.DAY);
        s.setGender("M");
        s.setPhone("0700000000");
        s.setStatus(StudentStatus.ACTIVE);
        s.setAcademicYear(2026);
        StudentStore.getInstance().add(s);
        StudentFeeLedger ledger = StudentStore.getInstance().getLedger(s.getId());
        ledger.charge("TUITION", charge);
        ledger.pay("TUITION", paid);
        return s;
    }

    @Test
    @DisplayName("Defaulters filter by term, form, stream and minimum balance")
    void defaultersFilterByFormStreamAndThreshold() {
        defaulter("D-1A", "Form 1", "A", amount("10000"), amount("2000"));
        defaulter("D-1B", "Form 1", "B", amount("8000"), amount("3000"));
        defaulter("D-2A", "Form 2", "A", amount("9000"), amount("8000"));

        // All defaulters (balance > 0)
        assertEquals(3, report.defaulters(null, null).size());

        // Filter by form only
        List<com.schaccs.model.student.StudentBalance> form1 =
                report.defaulters(null, null, "Form 1", null);
        assertEquals(2, form1.size());
        assertTrue(form1.stream().allMatch(b -> "Form 1".equals(b.getFormClass())));

        // Filter by form + stream
        List<com.schaccs.model.student.StudentBalance> form1A =
                report.defaulters(null, null, "Form 1", "A");
        assertEquals(1, form1A.size());
        assertEquals("D-1A", form1A.get(0).getAdmissionNumber());

        // Filter by minimum balance threshold
        List<com.schaccs.model.student.StudentBalance> big =
                report.defaulters(null, amount("3000"), null, null);
        assertEquals(2, big.size(), "Only balances > 3000 remain (D-1A 8000, D-2A 1000 excluded)");
    }

    @Test
    @DisplayName("Ageing buckets include a chronic over-one-year tail")
    void ageingIncludesOverOneYearBucket() {
        Student s = defaulter("D-OLD", "Form 4", "A", amount("20000"), amount("5000"));
        s.setYearOfAdmission(2024);
        java.util.List<com.schaccs.model.report.AgeingBucket> buckets = report.ageing();
        com.schaccs.model.report.AgeingBucket overYear =
                buckets.stream().filter(b -> b.getLabel().contains("Over 1 year"))
                        .findFirst().orElseThrow();
        assertEquals(0, overYear.getAmount().compareTo(amount("15000")),
                "Balance admitted in 2024 falls into the over-one-year bucket");
        assertEquals(1, overYear.getStudents());
        assertTrue(buckets.size() >= 5, "Ageing schedule exposes at least the 5 AR buckets");
    }

    @Test
    @DisplayName("Votehead summary cross-references ring-fenced bank cash and flags overdraft")
    void voteheadSummaryFlagsOverdraftAgainstBankCash() {
        com.schaccs.store.FeeStructureStore.getInstance().addVotehead(
                new com.schaccs.model.finance.Votehead("BOARD", "Boarding",
                        AccountType.FEES_BOARDING_ACTIVITY, 1));
        Student v1 = defaulter("V-1", "Form 1", "A", amount("10000"), amount("0"));
        StudentFeeLedger v1Ledger = StudentStore.getInstance().getLedger(v1.getId());
        v1Ledger.charge("BOARD", amount("10000"));
        v1Ledger.pay("BOARD", amount("6000"));
        // Bank holds only 6000 for boarding; votehead collected 6000 -> no overdraft.
        post(LocalDate.of(2026, 4, 1), "V-BANK",
                AccountType.BANK_BOARDING, AccountType.FEES_BOARDING_ACTIVITY, amount("6000"));

        com.schaccs.model.report.VoteheadSummary boarding = report.voteheadSummaries().stream()
                .filter(v -> "BOARD".equals(v.getVoteheadCode())).findFirst().orElseThrow();
        assertEquals(0, boarding.getCollected().compareTo(amount("6000")));
        assertEquals(0, boarding.getBankBalance().compareTo(amount("6000")),
                "Bank cash column shows the ring-fenced boarding bank balance");
        assertFalse(boarding.isOverdraft(), "Collected not greater than bank cash");

        // Now add a second parent's payment so collected exceeds bank cash -> overdraft.
        Student v2 = defaulter("V-2", "Form 1", "A", amount("10000"), amount("0"));
        StudentFeeLedger v2Ledger = StudentStore.getInstance().getLedger(v2.getId());
        v2Ledger.charge("BOARD", amount("10000"));
        v2Ledger.pay("BOARD", amount("7000"));
        com.schaccs.model.report.VoteheadSummary boarding2 = report.voteheadSummaries().stream()
                .filter(v -> "BOARD".equals(v.getVoteheadCode())).findFirst().orElseThrow();
        assertEquals(0, boarding2.getCollected().compareTo(amount("13000")));
        assertEquals(0, boarding2.getBankBalance().compareTo(amount("6000")));
        assertTrue(boarding2.isOverdraft(), "Collected exceeds available bank cash -> overdraft");
    }
}
