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
            list.add(new StudentBalance(s, ledger.getTotalCharged(), ledger.getTotalPaid(), ledger.getArrears()));
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
            BigDecimal paid = ledger.getTotalPaid();
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
        Map<AccountType, BigDecimal> debits = new EnumMap<>(AccountType.class);
        Map<AccountType, BigDecimal> credits = new EnumMap<>(AccountType.class);
        for (AccountType t : AccountType.values()) {
            debits.put(t, CurrencyConfig.zero());
            credits.put(t, CurrencyConfig.zero());
        }
        for (FinancialTransaction tx : ledgerStore.getTransactions()) {
            if (tx.getAccountType() == null) {
                continue;
            }
            debits.merge(tx.getAccountType(), tx.getDebit(), BigDecimal::add);
            credits.merge(tx.getAccountType(), tx.getCredit(), BigDecimal::add);
        }
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

    public List<com.schaccs.model.report.CashbookRow> cashbook(java.time.LocalDate from, java.time.LocalDate to) {
        List<com.schaccs.model.report.CashbookRow> rows = new java.util.ArrayList<>();
        BigDecimal runningBalance = CurrencyConfig.zero();
        List<FinancialTransaction> filtered = ledgerStore.getTransactions().stream()
                .filter(t -> !t.getDate().isBefore(from) && !t.getDate().isAfter(to))
                .sorted(java.util.Comparator.comparing(FinancialTransaction::getDate))
                .toList();
        for (FinancialTransaction tx : filtered) {
            BigDecimal receipts = tx.getDebit();
            BigDecimal payments = tx.getCredit();
            runningBalance = CurrencyConfig.money(runningBalance.add(receipts).subtract(payments));
            rows.add(new com.schaccs.model.report.CashbookRow(
                    tx.getDate(), tx.getReference(), tx.getDescription(),
                    receipts, payments, runningBalance));
        }
        return rows;
    }

    public List<com.schaccs.model.report.IncomeExpenditureRow> incomeExpenditure() {
        List<com.schaccs.model.report.IncomeExpenditureRow> rows = new java.util.ArrayList<>();
        for (Votehead vh : feeStore.getVoteheads()) {
            BigDecimal charged = CurrencyConfig.zero();
            BigDecimal collected = CurrencyConfig.zero();
            for (Student s : studentStore.getStudents()) {
                StudentFeeLedger l = studentStore.getLedger(s.getId());
                charged = charged.add(l.getCharged(vh.getCode()));
                collected = collected.add(l.getPaid(vh.getCode()));
            }
            rows.add(new com.schaccs.model.report.IncomeExpenditureRow("Income", vh.getName(), collected));
        }
        for (var voucher : com.schaccs.store.VoucherStore.getInstance().getVouchers()) {
            rows.add(new com.schaccs.model.report.IncomeExpenditureRow(
                    "Expenditure", voucher.getVoteheadName(), voucher.getAmount()));
        }
        return rows;
    }

    public List<com.schaccs.model.report.BalanceSheetRow> balanceSheet() {
        List<com.schaccs.model.report.BalanceSheetRow> rows = new java.util.ArrayList<>();
        Map<AccountType, BigDecimal> balances = ledgerStore.getAccountBalances();
        for (Map.Entry<AccountType, BigDecimal> e : balances.entrySet()) {
            String section = "Assets";
            if (e.getKey() == AccountType.SCHOOL_FUND) {
                section = "Fund Balance";
            } else if (e.getKey().name().startsWith("FSE")) {
                section = "Restricted Funds";
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
}
