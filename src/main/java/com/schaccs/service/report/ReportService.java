package com.schaccs.service.report;

import com.schaccs.config.AppConfig;
import com.schaccs.config.CurrencyConfig;
import com.schaccs.enums.AccountType;
import com.schaccs.enums.PaymentMode;
import com.schaccs.enums.StudentStatus;
import com.schaccs.model.finance.FinancialTransaction;
import com.schaccs.model.finance.Votehead;
import com.schaccs.model.receipt.Receipt;
import com.schaccs.model.report.AgeingBucket;
import com.schaccs.model.report.CollectionSummary;
import com.schaccs.model.report.TrialBalanceRow;
import com.schaccs.model.report.VoteheadSummary;
import com.schaccs.model.student.Student;
import com.schaccs.model.student.StudentBalance;
import com.schaccs.model.student.StudentFeeLedger;
import com.schaccs.store.FeeStructureStore;
import com.schaccs.store.LedgerStore;
import com.schaccs.store.ReceiptStore;
import com.schaccs.store.StudentStore;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ReportService {

    private final StudentStore studentStore;
    private final ReceiptStore receiptStore;
    private final FeeStructureStore feeStore;
    private final LedgerStore ledgerStore;

    public ReportService() {
        this(StudentStore.getInstance(), ReceiptStore.getInstance(),
                FeeStructureStore.getInstance(), LedgerStore.getInstance());
    }

    public ReportService(StudentStore studentStore, ReceiptStore receiptStore,
                         FeeStructureStore feeStore, LedgerStore ledgerStore) {
        this.studentStore = studentStore;
        this.receiptStore = receiptStore;
        this.feeStore = feeStore;
        this.ledgerStore = ledgerStore;
    }

    public List<StudentBalance> feeBalances() {
        List<StudentBalance> list = new ArrayList<>();
        for (Student s : studentStore.getStudents()) {
            if (s.getStatus() != StudentStatus.ACTIVE) {
                continue;
            }
            StudentFeeLedger ledger = studentStore.getLedger(s.getId());
            BigDecimal balance = CurrencyConfig.money(
                    ledger.getTotalCharged().add(ledger.getArrears()).subtract(ledger.getTotalPaid()).subtract(ledger.getAdvance()));
            list.add(new StudentBalance(s, ledger.getTotalCharged(), ledger.getTotalPaid(), ledger.getArrears(), balance));
        }
        list.sort(Comparator.comparing(StudentBalance::getBalance).reversed());
        return list;
    }

    public List<StudentBalance> defaulters(BigDecimal threshold) {
        BigDecimal min = threshold != null ? threshold : CurrencyConfig.zero();
        return feeBalances().stream()
                .filter(b -> b.getBalance().compareTo(min) > 0)
                .collect(Collectors.toList());
    }

    /**
     * Term-scoped balances: expected fee for the given term (from the fee structure)
     * minus total paid to date. When term is null, falls back to full-year balances.
     */
    public List<StudentBalance> feeBalances(com.schaccs.enums.AcademicTerm term) {
        if (term == null) {
            return feeBalances();
        }
        List<StudentBalance> list = new ArrayList<>();
        for (Student s : studentStore.getStudents()) {
            if (s.getStatus() != StudentStatus.ACTIVE) {
                continue;
            }
            StudentFeeLedger ledger = studentStore.getLedger(s.getId());
            BigDecimal expected = expectedTermFee(s, term);
            BigDecimal paid = ledger.getTotalPaid().min(expected);
            BigDecimal balance = CurrencyConfig.money(expected.subtract(paid).max(CurrencyConfig.zero()));
            list.add(new StudentBalance(s, expected, paid, CurrencyConfig.zero(), balance));
        }
        list.sort(Comparator.comparing(StudentBalance::getBalance).reversed());
        return list;
    }

    public List<StudentBalance> defaulters(com.schaccs.enums.AcademicTerm term, BigDecimal threshold) {
        BigDecimal min = threshold != null ? threshold : CurrencyConfig.zero();
        return feeBalances(term).stream()
                .filter(b -> b.getBalance().compareTo(min) > 0)
                .collect(Collectors.toList());
    }

    /**
     * Defaulter report with UI filters: term, minimum outstanding threshold,
     * form class and stream. Filters are applied independently and a null/blank
     * value means "all". Sorting by descending balance is preserved.
     */
    public List<StudentBalance> defaulters(com.schaccs.enums.AcademicTerm term, BigDecimal threshold,
                                           String formClass, String stream) {
        BigDecimal min = threshold != null ? threshold : CurrencyConfig.zero();
        return feeBalances(term).stream()
                .filter(b -> b.getBalance().compareTo(min) > 0)
                .filter(b -> isBlank(formClass) || b.getFormClass() == null
                        || b.getFormClass().equalsIgnoreCase(formClass))
                .filter(b -> isBlank(stream) || b.getStream() == null
                        || b.getStream().equalsIgnoreCase(stream))
                .collect(Collectors.toList());
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    public BigDecimal expectedTermFee(Student student, com.schaccs.enums.AcademicTerm term) {
        int year = student.getAcademicYear() != null
                ? student.getAcademicYear() : com.schaccs.config.AppConfig.getInstance().getAcademicYear();
        return feeStore.findStructure(year, student.getBoardingStatus())
                .map(s -> s.totalForTerm(term))
                .orElse(CurrencyConfig.zero());
    }

    /**
     * Coarse ageing of outstanding balances based on the student's year of
     * admission / academic year as the oldest known billing baseline. This is
     * not invoice-level receivables ageing, but provides a useful operational
     * view until dated charge lines are introduced.
     */
    public List<AgeingBucket> ageing() {
        long[] floors = {0, 31, 61, 91};
        String[] labels = {"Current (0-30)", "31-60 days", "61-90 days", "90+ days"};
        BigDecimal[] totals = {CurrencyConfig.zero(), CurrencyConfig.zero(),
                CurrencyConfig.zero(), CurrencyConfig.zero()};
        long[] counts = {0, 0, 0, 0};

        for (StudentBalance b : feeBalances()) {
            if (b.getBalance().compareTo(CurrencyConfig.zero()) <= 0) {
                continue;
            }
            Student student = studentStore.findById(b.getStudentId()).orElse(null);
            int year = student != null && student.getYearOfAdmission() != null
                    ? student.getYearOfAdmission()
                    : AppConfig.getInstance().getAcademicYear();
            LocalDate baseline = LocalDate.of(year, 1, 1);
            long daysElapsed = java.time.temporal.ChronoUnit.DAYS.between(baseline, LocalDate.now());
            int idx = daysElapsed <= 30 ? 0 : daysElapsed <= 60 ? 1 : daysElapsed <= 90 ? 2 : 3;
            totals[idx] = CurrencyConfig.money(totals[idx].add(b.getBalance()));
            counts[idx]++;
        }
        List<AgeingBucket> result = new java.util.ArrayList<>();
        for (int i = 0; i < labels.length; i++) {
            result.add(new AgeingBucket(labels[i], totals[i], counts[i]));
        }
        return result;
    }


    public StudentBalance studentStatement(Student student) {
        StudentFeeLedger ledger = studentStore.getLedger(student.getId());
        return new StudentBalance(student, ledger.getTotalCharged(), ledger.getTotalPaid(), ledger.getArrears());
    }

    public List<Receipt> studentReceipts(Student student) {
        return receiptStore.forStudent(student.getId());
    }

    public List<CollectionSummary> dailyCollection(LocalDate date) {
        LocalDate d = date != null ? date : LocalDate.now();
        Map<PaymentMode, List<Receipt>> grouped = receiptStore.forDate(d).stream()
                .filter(r -> !r.isReversed())
                .collect(Collectors.groupingBy(Receipt::getPaymentMode));
        List<CollectionSummary> result = new ArrayList<>();
        for (Map.Entry<PaymentMode, List<Receipt>> e : grouped.entrySet()) {
            BigDecimal total = e.getValue().stream()
                    .map(Receipt::getAmount)
                    .reduce(CurrencyConfig.zero(), BigDecimal::add);
            result.add(new CollectionSummary(d, e.getKey(), e.getValue().size(), total));
        }
        return result;
    }

    public BigDecimal totalCollectionOn(LocalDate date) {
        return receiptStore.forDate(date).stream()
                .filter(r -> !r.isReversed())
                .map(Receipt::getAmount)
                .reduce(CurrencyConfig.zero(), BigDecimal::add);
    }

    public BigDecimal totalCollectionAll() {
        return receiptStore.getReceipts().stream()
                .filter(r -> !r.isReversed())
                .map(Receipt::getAmount)
                .reduce(CurrencyConfig.zero(), BigDecimal::add);
    }

    public java.util.List<java.util.Map.Entry<LocalDate, BigDecimal>> dailyCollectionTrend(int days) {
        Map<LocalDate, BigDecimal> trend = new java.util.LinkedHashMap<>();
        LocalDate today = LocalDate.now();
        for (int i = days - 1; i >= 0; i--) {
            trend.put(today.minusDays(i), CurrencyConfig.zero());
        }
        for (Receipt r : receiptStore.getReceipts()) {
            if (r.getDate() != null && trend.containsKey(r.getDate()) && !r.isReversed()) {
                trend.put(r.getDate(), trend.get(r.getDate()).add(r.getAmount()));
            }
        }
        return new ArrayList<>(trend.entrySet());
    }

    public BigDecimal totalOutstanding() {
        return feeBalances().stream()
                .map(StudentBalance::getBalance)
                .reduce(CurrencyConfig.zero(), BigDecimal::add);
    }

    public List<VoteheadSummary> voteheadSummaries() {
        Map<String, BigDecimal> charged = new LinkedHashMap<>();
        Map<String, BigDecimal> collected = new LinkedHashMap<>();

        for (Student s : studentStore.getStudents()) {
            StudentFeeLedger ledger = studentStore.getLedger(s.getId());
            ledger.getChargedByVotehead().forEach((code, amt) ->
                    charged.merge(code, amt, BigDecimal::add));
            ledger.getPaidByVotehead().forEach((code, amt) ->
                    collected.merge(code, amt, BigDecimal::add));
        }

        List<VoteheadSummary> list = new ArrayList<>();
        BigDecimal advanceCollected = CurrencyConfig.zero();
        for (Student s : studentStore.getStudents()) {
            advanceCollected = advanceCollected.add(studentStore.getLedger(s.getId()).getAdvance());
        }
        for (Votehead vh : feeStore.getVoteheads()) {
            BigDecimal c = charged.getOrDefault(vh.getCode(), CurrencyConfig.zero());
            BigDecimal p = collected.getOrDefault(vh.getCode(), CurrencyConfig.zero());
            list.add(new VoteheadSummary(vh.getCode(), vh.getName(), c, p));
        }
        if (advanceCollected.compareTo(CurrencyConfig.zero()) > 0) {
            list.add(new VoteheadSummary(StudentFeeLedger.ADVANCE_CODE, "Advance / Credit",
                    CurrencyConfig.zero(), advanceCollected));
        }
        return list;
    }

    public List<TrialBalanceRow> trialBalance() {
        return trialBalance(null, null);
    }

    /**
     * Year-isolated trial balance: scopes the report to a single academic year's
     * calendar span (1 Jan–31 Dec), so uncleared arrears and revenue from other
     * years never bleed into this year's report.
     */
    public List<TrialBalanceRow> trialBalanceForYear(int academicYear) {
        return trialBalance(LocalDate.of(academicYear, 1, 1),
                LocalDate.of(academicYear, 12, 31));
    }

    public List<TrialBalanceRow> trialBalance(LocalDate from, LocalDate to) {
        Map<AccountType, BigDecimal> debits = new EnumMap<>(AccountType.class);
        Map<AccountType, BigDecimal> credits = new EnumMap<>(AccountType.class);
        for (AccountType t : AccountType.values()) {
            debits.put(t, CurrencyConfig.zero());
            credits.put(t, CurrencyConfig.zero());
        }
        java.util.stream.Stream<FinancialTransaction> stream = ledgerStore.getTransactions().stream();
        if (from != null) stream = stream.filter(t -> !t.getDate().isBefore(from));
        if (to != null) stream = stream.filter(t -> !t.getDate().isAfter(to));
        stream.forEach(tx -> {
            if (tx.getAccountType() == null) return;
            debits.merge(tx.getAccountType(), tx.getDebit(), BigDecimal::add);
            credits.merge(tx.getAccountType(), tx.getCredit(), BigDecimal::add);
        });
        List<TrialBalanceRow> rows = new ArrayList<>();
        for (AccountType t : AccountType.values()) {
            rows.add(new TrialBalanceRow(t, debits.get(t), credits.get(t)));
        }
        return rows;
    }

    /** True when total debits equal total credits across the whole ledger. */
    public boolean isLedgerBalanced() {
        BigDecimal totalDebit = ledgerStore.getTransactions().stream()
                .map(FinancialTransaction::getDebit)
                .reduce(CurrencyConfig.zero(), BigDecimal::add);
        BigDecimal totalCredit = ledgerStore.getTransactions().stream()
                .map(FinancialTransaction::getCredit)
                .reduce(CurrencyConfig.zero(), BigDecimal::add);
        return totalDebit.compareTo(totalCredit) == 0;
    }

    /**
     * Audit view for Trial Balance resolution: scans all journal lines grouped by
     * their shared reference and returns the groups whose debits do not equal their
     * credits (i.e. an orphaned/unbalanced entry). Each entry exposes the offending
     * lines and the net imbalance so an auditor can identify and post a corrective
     * journal. An empty list means every journal in the ledger is balanced.
     */
    public List<UnbalancedEntry> findUnbalancedEntries() {
        Map<String, List<FinancialTransaction>> byRef = ledgerStore.getTransactions().stream()
                .filter(t -> t.getReference() != null)
                .collect(Collectors.groupingBy(com.schaccs.model.finance.FinancialTransaction::getReference));
        List<UnbalancedEntry> result = new ArrayList<>();
        for (Map.Entry<String, List<FinancialTransaction>> e : byRef.entrySet()) {
            BigDecimal db = e.getValue().stream().map(FinancialTransaction::getDebit)
                    .reduce(CurrencyConfig.zero(), BigDecimal::add);
            BigDecimal cr = e.getValue().stream().map(FinancialTransaction::getCredit)
                    .reduce(CurrencyConfig.zero(), BigDecimal::add);
            if (db.compareTo(cr) != 0) {
                result.add(new UnbalancedEntry(e.getKey(), e.getValue(), db, cr,
                        CurrencyConfig.money(db.subtract(cr))));
            }
        }
        result.sort(java.util.Comparator.comparing(UnbalancedEntry::getReference));
        return result;
    }

    /** A journal reference group whose debits and credits do not balance. */
    public static class UnbalancedEntry {
        private final String reference;
        private final List<FinancialTransaction> lines;
        private final BigDecimal totalDebit;
        private final BigDecimal totalCredit;
        private final BigDecimal imbalance;

        UnbalancedEntry(String reference, List<FinancialTransaction> lines,
                        BigDecimal totalDebit, BigDecimal totalCredit, BigDecimal imbalance) {
            this.reference = reference;
            this.lines = lines;
            this.totalDebit = totalDebit;
            this.totalCredit = totalCredit;
            this.imbalance = imbalance;
        }

        public String getReference() { return reference; }
        public List<FinancialTransaction> getLines() { return lines; }
        public BigDecimal getTotalDebit() { return totalDebit; }
        public BigDecimal getTotalCredit() { return totalCredit; }
        public BigDecimal getImbalance() { return imbalance; }
    }

    public List<com.schaccs.model.report.CashbookRow> cashbook(java.time.LocalDate from, java.time.LocalDate to) {
        return cashbook(from, to, null, null);
    }

    /**
     * V2 multi-column cashbook over the bank accounts. Merges cash movements from
     * receipts (income) and payment vouchers (expenses) directly from the ledger
     * stores — no independent tracking file.
     *
     * <p>The scope is constrained by two optional filters:
     * <ul>
     *   <li><b>bankAccount</b> — restrict to one ring-fenced bank account
     *       (e.g. {@link AccountType#BANK_TUITION}, {@link AccountType#BANK_BOARDING},
     *       {@link AccountType#BANK_INFRASTRUCTURE}); {@code null} includes all bank accounts.</li>
     *   <li><b>fundingSource</b> — separate by funding source
     *       (e.g. {@code "GOVT"} Ministry funding vs {@code "PARENT"} parent fees,
     *       via {@link AccountType#getRestrictedGroup()}); {@code null} includes all.</li>
     * </ul>
     *
     * A transaction on a bank account contributes its debit as a receipt and its
     * credit as a payment. The running balance accumulates within the selected scope.
     */
    public List<com.schaccs.model.report.CashbookRow> cashbook(java.time.LocalDate from, java.time.LocalDate to,
                                                                AccountType bankAccount, String fundingSource) {
        List<com.schaccs.model.report.CashbookRow> rows = new java.util.ArrayList<>();
        BigDecimal runningBalance = CurrencyConfig.zero();
        java.util.function.Predicate<FinancialTransaction> bankFilter = bankAccount == null
                ? t -> isBankAccount(t.getAccountType())
                : t -> t.getAccountType() == bankAccount;
        java.util.function.Predicate<FinancialTransaction> sourceFilter = fundingSource == null
                        || fundingSource.isBlank()
                ? t -> true
                : t -> t.getAccountType() != null
                        && fundingSource.equalsIgnoreCase(t.getAccountType().getRestrictedGroup());
        List<FinancialTransaction> filtered = ledgerStore.getTransactions().stream()
                .filter(t -> !t.getDate().isBefore(from) && !t.getDate().isAfter(to))
                .filter(bankFilter)
                .filter(sourceFilter)
                .sorted(java.util.Comparator.comparing(FinancialTransaction::getDate))
                .toList();
        for (FinancialTransaction tx : filtered) {
            BigDecimal receipts = tx.getDebit();
            BigDecimal payments = tx.getCredit();
            runningBalance = CurrencyConfig.money(runningBalance.add(receipts).subtract(payments));
            rows.add(new com.schaccs.model.report.CashbookRow(
                    tx.getDate(), tx.getReference(), tx.getDescription(),
                    receipts, payments, runningBalance, tx.getAccountType()));
        }
        return rows;
    }

    public List<com.schaccs.model.report.IncomeExpenditureRow> incomeExpenditure() {
        List<com.schaccs.model.report.IncomeExpenditureRow> rows = new java.util.ArrayList<>();
        Map<AccountType, BigDecimal> balances = ledgerStore.getAccountBalances();
        for (Map.Entry<AccountType, BigDecimal> e : balances.entrySet()) {
            AccountType at = e.getKey();
            BigDecimal bal = e.getValue();
            if (bal.compareTo(CurrencyConfig.zero()) == 0) continue;
            if (at.getStatementCategory() == com.schaccs.enums.StatementCategory.INCOME_EXPENDITURE) {
                String category = at.getNormalBalance() == com.schaccs.enums.NormalBalance.CREDIT ? "Income" : "Expenditure";
                rows.add(new com.schaccs.model.report.IncomeExpenditureRow(category, at.getDisplayName(), bal));
            }
        }
        return rows;
    }

    public List<com.schaccs.model.report.BalanceSheetRow> balanceSheet() {
        List<com.schaccs.model.report.BalanceSheetRow> rows = new java.util.ArrayList<>();
        Map<AccountType, BigDecimal> balances = ledgerStore.getAccountBalances();
        for (Map.Entry<AccountType, BigDecimal> e : balances.entrySet()) {
            AccountType at = e.getKey();
            String section;
            if (at == AccountType.SCHOOL_FUND) {
                section = "Fund Balance";
            } else if (at.name().startsWith("FSE")) {
                section = "Restricted Funds";
            } else if (at.getNormalBalance() == com.schaccs.enums.NormalBalance.CREDIT
                    && at.getStatementCategory() == com.schaccs.enums.StatementCategory.BALANCE_SHEET) {
                section = "Liabilities";
            } else {
                section = "Assets";
            }
            rows.add(new com.schaccs.model.report.BalanceSheetRow(section,
                    e.getKey().getDisplayName(), e.getValue()));
        }
        BigDecimal totalIncome = CurrencyConfig.zero();
        BigDecimal totalExpense = CurrencyConfig.zero();
        for (var ie : incomeExpenditure()) {
            if ("Income".equals(ie.getCategory())) {
                totalIncome = totalIncome.add(ie.getAmount());
            } else {
                totalExpense = totalExpense.add(ie.getAmount());
            }
        }
        rows.add(new com.schaccs.model.report.BalanceSheetRow("Fund Balance",
                "Accumulated Surplus/(Deficit)",
                CurrencyConfig.money(totalIncome.subtract(totalExpense))));
        return rows;
    }

    public static class CashFlowRow {
        private final String category;
        private final String item;
        private final BigDecimal amount;

        public CashFlowRow(String category, String item, BigDecimal amount) {
            this.category = category;
            this.item = item;
            this.amount = amount;
        }

        public String getCategory() { return category; }
        public String getItem() { return item; }
        public BigDecimal getAmount() { return amount; }

        @Override
        public String toString() {
            return category + " | " + item + " | " + CurrencyConfig.format(amount);
        }
    }

    public List<CashFlowRow> cashFlowStatement(LocalDate from, LocalDate to) {
        List<FinancialTransaction> filtered = ledgerStore.getTransactions().stream()
                .filter(t -> !t.getDate().isBefore(from) && !t.getDate().isAfter(to))
                .toList();

        List<CashFlowRow> rows = new ArrayList<>();

        // Cash Flow from Operating Activities
        BigDecimal receipts = filtered.stream()
                .filter(t -> isBankAccount(t.getAccountType()))
                .map(FinancialTransaction::getDebit)
                .reduce(CurrencyConfig.zero(), BigDecimal::add);
        BigDecimal payments = filtered.stream()
                .filter(t -> isBankAccount(t.getAccountType()))
                .map(FinancialTransaction::getCredit)
                .reduce(CurrencyConfig.zero(), BigDecimal::add);

        rows.add(new CashFlowRow("Operating", "Cash Receipts", receipts));
        rows.add(new CashFlowRow("Operating", "Cash Payments", payments.negate()));
        BigDecimal netOperating = CurrencyConfig.money(receipts.subtract(payments));
        rows.add(new CashFlowRow("Operating", "Net Cash from Operating Activities", netOperating));

        // Cash Flow from Investing Activities
        BigDecimal fixedAssetPurchases = filtered.stream()
                .filter(t -> t.getAccountType() == AccountType.FIXED_ASSETS)
                .map(FinancialTransaction::getDebit)
                .reduce(CurrencyConfig.zero(), BigDecimal::add);
        rows.add(new CashFlowRow("Investing", "Purchase of Fixed Assets", fixedAssetPurchases.negate()));
        BigDecimal netInvesting = CurrencyConfig.money(fixedAssetPurchases.negate());
        rows.add(new CashFlowRow("Investing", "Net Cash from Investing Activities", netInvesting));

        // Cash Flow from Financing Activities
        BigDecimal financing = filtered.stream()
                .filter(t -> t.getAccountType() == AccountType.RETAINED_EARNINGS)
                .map(FinancialTransaction::getCredit)
                .reduce(CurrencyConfig.zero(), BigDecimal::add);
        rows.add(new CashFlowRow("Financing", "Capital Contributions", financing));
        BigDecimal netFinancing = CurrencyConfig.money(financing);
        rows.add(new CashFlowRow("Financing", "Net Cash from Financing Activities", netFinancing));

        // Summary
        BigDecimal netChange = CurrencyConfig.money(netOperating.add(netInvesting).add(netFinancing));
        rows.add(new CashFlowRow("Summary", "Net Increase/(Decrease) in Cash", netChange));

        // Opening cash balance
        BigDecimal openingBalance = CurrencyConfig.zero();
        for (FinancialTransaction tx : ledgerStore.getTransactions()) {
            if (tx.getDate().isBefore(from) && isBankAccount(tx.getAccountType())) {
                openingBalance = CurrencyConfig.money(openingBalance.add(tx.getDebit()).subtract(tx.getCredit()));
            }
        }
        rows.add(new CashFlowRow("Summary", "Opening Cash Balance", openingBalance));

        BigDecimal closingBalance = CurrencyConfig.money(openingBalance.add(netChange));
        rows.add(new CashFlowRow("Summary", "Closing Cash Balance", closingBalance));

        return rows;
    }

    private static boolean isBankAccount(AccountType type) {
        return type == AccountType.CASH_AT_BANK
                || type == AccountType.BANK_TUITION
                || type == AccountType.BANK_INFRASTRUCTURE
                || type == AccountType.BANK_BOARDING;
    }
}
