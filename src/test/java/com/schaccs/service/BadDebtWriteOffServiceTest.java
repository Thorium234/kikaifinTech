package com.schaccs.service;

import com.schaccs.accounting.DoubleEntryEngine;
import com.schaccs.config.CurrencyConfig;
import com.schaccs.enums.AcademicTerm;
import com.schaccs.enums.AccountType;
import com.schaccs.enums.BoardingStatus;
import com.schaccs.enums.StudentStatus;
import com.schaccs.enums.TransactionType;
import com.schaccs.model.finance.FinancialTransaction;
import com.schaccs.model.finance.Votehead;
import com.schaccs.model.student.Student;
import com.schaccs.model.student.StudentFeeLedger;
import com.schaccs.repository.PersistenceService;
import com.schaccs.service.fee.FeeCalculationService;
import com.schaccs.service.finance.BadDebtWriteOffService;
import com.schaccs.store.FeeStructureStore;
import com.schaccs.store.LedgerStore;
import com.schaccs.store.StudentStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.schaccs.model.fee.FeeStructure;
import com.schaccs.model.fee.FeeStructureItem;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BadDebtWriteOffServiceTest {

    @BeforeEach
    void setUp() {
        PersistenceService.getInstance().clearAll();
    }

    @AfterEach
    void tearDown() {
        PersistenceService.getInstance().clearAll();
    }

    private void structure(String code, String name, BigDecimal amount) {
        FeeStructureStore store = FeeStructureStore.getInstance();
        if (store.findVoteheadByCode(code).isEmpty()) {
            store.addVotehead(new Votehead(code, name, AccountType.SCHOOL_FUND, 1));
        }
        FeeStructure structure = store.findStructure(2026, BoardingStatus.DAY).orElse(null);
        if (structure == null) {
            structure = new FeeStructure(2026, "ALL", BoardingStatus.DAY, "2026 Day");
            store.addStructure(structure);
        }
        structure.addItem(new FeeStructureItem(code, name, AcademicTerm.TERM_1, BoardingStatus.DAY, amount));
    }

    private Student createStudent(String adm) {
        Student s = new Student();
        s.setAdmissionNumber(adm);
        s.setName("Bad Debt " + adm);
        s.setFormClass("Form 3");
        s.setStream("A");
        s.setBoardingStatus(BoardingStatus.DAY);
        s.setGender("M");
        s.setPhone("0700000000");
        s.setStatus(StudentStatus.ACTIVE);
        s.setAcademicYear(2026);
        StudentStore.getInstance().add(s);
        return s;
    }

    private BadDebtWriteOffService service() {
        return new BadDebtWriteOffService(new DoubleEngine(), StudentStore.getInstance(),
                FeeStructureStore.getInstance(), new com.schaccs.service.audit.AuditService());
    }

    static final class DoubleEngine extends DoubleEntryEngine {
        DoubleEngine() {
            super(LedgerStore.getInstance());
        }
    }

    @Test
    void writeOffPostsBalancedJournalClearsLedgerAndKeepsHistory() {
        structure("TUITION", "Tuition", CurrencyConfig.money("10000"));
        structure("BOARD", "Boarding", CurrencyConfig.money("8000"));
        Student student = createStudent("ADM-BW");
        new FeeCalculationService(FeeStructureStore.getInstance(), StudentStore.getInstance())
                .chargeTermFees(student, AcademicTerm.TERM_1);

        StudentFeeLedger ledger = StudentStore.getInstance().getLedger(student.getId());
        assertEquals(0, ledger.getTotalCharged().compareTo(CurrencyConfig.money("18000")));
        ledger.pay("TUITION", CurrencyConfig.money("3000"));
        assertEquals(0, ledger.getOutstanding("TUITION").compareTo(CurrencyConfig.money("7000")));

        BadDebtWriteOffService.WriteOffResult result =
                service().writeOff(student, "Dropped out mid-term", "tester", LocalDate.of(2026, 3, 1));

        assertTrue(result.success(), "Write-off should succeed: " + result.errors());
        assertEquals(0, result.total().compareTo(CurrencyConfig.money("15000")),
                "Write off = 7000 tuition + 8000 boarding");
        assertTrue(result.reference().startsWith("WO-"), "Reference should be a write-off number");

        assertEquals(0, ledger.getTotalCharged().compareTo(CurrencyConfig.money("3000")),
                "Only the already-paid tuition portion remains charged; unpaid 15000 written off");
        assertEquals(0, ledger.getBalance().compareTo(BigDecimal.ZERO),
                "No balance outstanding after write-off");
        assertEquals(0, ledger.getOutstanding("TUITION").compareTo(BigDecimal.ZERO));
        assertEquals(0, ledger.getOutstanding("BOARD").compareTo(BigDecimal.ZERO));

        List<FinancialTransaction> txs = LedgerStore.getInstance().getTransactions();
        long writeOffTx = txs.stream().filter(t -> t.getType() == TransactionType.WRITE_OFF).count();
        assertEquals(4, writeOffTx, "2 voteheads x 2 lines (Expense debit + AR credit)");

        BigDecimal badDebtDebit = txs.stream()
                .filter(t -> t.getAccountType() == AccountType.BAD_DEBTS_EXPENSE)
                .map(FinancialTransaction::getDebit)
                .reduce(CurrencyConfig.zero(), BigDecimal::add);
        BigDecimal arCredit = txs.stream()
                .filter(t -> t.getAccountType() == AccountType.ACCOUNTS_RECEIVABLE)
                .map(FinancialTransaction::getCredit)
                .reduce(CurrencyConfig.zero(), BigDecimal::add);
        assertEquals(0, badDebtDebit.compareTo(CurrencyConfig.money("15000")));
        assertEquals(0, arCredit.compareTo(CurrencyConfig.money("15000")));

        assertEquals(StudentStatus.DROPPED, student.getStatus(),
                "Dropped-out student should be marked Dropped Out");
        assertTrue(StudentStore.getInstance().findById(student.getId()).isPresent(),
                "Student record must not be deleted");
    }

    @Test
    void writeOffRejectsZeroOutstanding() {
        structure("TUITION", "Tuition", CurrencyConfig.money("10000"));
        Student student = createStudent("ADM-NOOWE");
        StudentFeeLedger ledger = StudentStore.getInstance().getLedger(student.getId());
        ledger.pay("TUITION", CurrencyConfig.money("10000"));

        BadDebtWriteOffService.WriteOffResult result =
                service().writeOff(student, "Transfer out", "tester", LocalDate.of(2026, 3, 1));
        assertFalse(result.success());
        assertEquals(StudentStatus.ACTIVE, student.getStatus(), "No status change when nothing to write off");
    }

    @Test
    void outstandingByVoteheadReportsOnlyOpenBalances() {
        structure("TUITION", "Tuition", CurrencyConfig.money("10000"));
        structure("BOARD", "Boarding", CurrencyConfig.money("8000"));
        Student student = createStudent("ADM-OB");
        new FeeCalculationService(FeeStructureStore.getInstance(), StudentStore.getInstance())
                .chargeTermFees(student, AcademicTerm.TERM_1);
        StudentFeeLedger ledger = StudentStore.getInstance().getLedger(student.getId());
        ledger.pay("TUITION", CurrencyConfig.money("10000"));

        Map<String, BigDecimal> outstanding = service().outstandingByVotehead(student.getId());
        assertEquals(1, outstanding.size(), "Only boarding remains open");
        assertEquals(0, outstanding.get("BOARD").compareTo(CurrencyConfig.money("8000")));
    }
}
