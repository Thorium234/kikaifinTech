package com.schaccs.service.report;

import com.schaccs.config.CurrencyConfig;
import com.schaccs.enums.AccountType;
import com.schaccs.enums.PaymentMode;
import com.schaccs.enums.StudentStatus;
import com.schaccs.model.finance.FinancialTransaction;
import com.schaccs.model.finance.Votehead;
import com.schaccs.model.receipt.Receipt;
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
                .map(Receipt::getAmount)
                .reduce(CurrencyConfig.zero(), BigDecimal::add);
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
        for (Votehead vh : feeStore.getVoteheads()) {
            BigDecimal c = charged.getOrDefault(vh.getCode(), CurrencyConfig.zero());
            BigDecimal p = collected.getOrDefault(vh.getCode(), CurrencyConfig.zero());
            list.add(new VoteheadSummary(vh.getCode(), vh.getName(), c, p));
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
}
