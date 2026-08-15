package com.schaccs.service;

import com.schaccs.accounting.ReceiptAllocationEngine;
import com.schaccs.config.AppConfig;
import com.schaccs.config.CurrencyConfig;
import com.schaccs.enums.AccountType;
import com.schaccs.enums.AcademicTerm;
import com.schaccs.enums.BoardingStatus;
import com.schaccs.enums.PaymentMode;
import com.schaccs.model.fee.FeeAllocation;
import com.schaccs.model.fee.FeeStructure;
import com.schaccs.model.fee.FeeStructureItem;
import com.schaccs.model.finance.Votehead;
import com.schaccs.model.student.Student;
import com.schaccs.model.student.StudentFeeLedger;
import com.schaccs.service.receipt.ReceiptService;
import com.schaccs.store.FeeStructureStore;
import com.schaccs.store.LedgerStore;
import com.schaccs.store.ReceiptStore;
import com.schaccs.store.StudentStore;
import com.schaccs.repository.PersistenceService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ReceiptCoreTest {

    @BeforeEach
    void setUp() {
        PersistenceService.getInstance().clearAll();
        FeeStructureStore.getInstance().addVotehead(new Votehead("BOARD", "Boarding", AccountType.SCHOOL_FUND, 1));
        FeeStructureStore.getInstance().addVotehead(new Votehead("ACT", "Activity", AccountType.FSE_OPERATIONS, 2));
        AppConfig.getInstance().getSchoolProfile().setNextReceiptNumber(1000);
    }

    @AfterEach
    void tearDown() {
        PersistenceService.getInstance().clearAll();
    }

    @Test
    @DisplayName("allocation respects priority order and overpayment goes to advance")
    void allocationRespectsPriorityAndOverpaymentAdvance() {
        StudentFeeLedger ledger = new StudentFeeLedger("S1");
        ledger.charge("ACT", CurrencyConfig.money("1000"));
        ledger.charge("BOARD", CurrencyConfig.money("5000"));

        ReceiptAllocationEngine engine = new ReceiptAllocationEngine(FeeStructureStore.getInstance());
        List<FeeAllocation> allocations = engine.allocate(ledger, CurrencyConfig.money("7000"));

        assertEquals("BOARD", allocations.get(0).getVoteheadCode());
        assertEquals(CurrencyConfig.money("5000.00"), allocations.get(0).getAllocated());
        assertEquals("ACT", allocations.get(1).getVoteheadCode());
        assertEquals(CurrencyConfig.money("1000.00"), allocations.get(1).getAllocated());
        assertEquals(StudentFeeLedger.ADVANCE_CODE, allocations.get(2).getVoteheadCode());
        assertEquals(CurrencyConfig.money("1000.00"), allocations.get(2).getAllocated());
    }

    @Test
    @DisplayName("receive and reverse receipt restores ledger and posts contra entry")
    void receiveAndReverseReceiptRestoresLedgerAndPostsContra() {
        String uniqueAdmission = "TEST-RCPT-" + UUID.randomUUID();
        Student student = new Student();
        student.setAdmissionNumber(uniqueAdmission);
        student.setName("Alice");
        student.setFormClass("Form 1");
        student.setBoardingStatus(BoardingStatus.BOARDING);
        StudentStore.getInstance().add(student);
        StudentFeeLedger ledger = StudentStore.getInstance().getLedger(student.getId());
        ledger.charge("BOARD", CurrencyConfig.money("4000"));

        ReceiptService service = new ReceiptService();
        ReceiptService.Result result = service.receivePayment(student, CurrencyConfig.money("2500"),
                PaymentMode.BANK_SLIP, "SLIP123", LocalDate.now(), null);

        assertTrue(result.isSuccess());
        assertEquals(CurrencyConfig.money("2500.00"), ledger.getPaid("BOARD"));
        assertEquals(2, LedgerStore.getInstance().getTransactions().size());

        ReceiptService.Result reversed = service.reverseReceipt(result.getReceipt(), "test");
        assertTrue(reversed.isSuccess());
        assertEquals(CurrencyConfig.zero(), ledger.getPaid("BOARD"));
        assertTrue(result.getReceipt().isReversed());
        assertEquals(4, LedgerStore.getInstance().getTransactions().size());
        assertTrue(LedgerStore.getInstance().getTransactions().stream()
                .anyMatch(t -> "RCPT-RV-1000".equals(t.getReference())));
    }

    @Test
    @DisplayName("allocation uses fee structure vote head name when votehead store has no match")
    void allocationUsesStructureVoteHeadName() {
        FeeStructureStore.getInstance().clear();

        FeeStructure fs = new FeeStructure(2026, "ALL", BoardingStatus.BOARDING, "Boarding Fee Structure 2026");
        fs.addItem(new FeeStructureItem("8", "LUNCH", AcademicTerm.TERM_1,
                BoardingStatus.BOARDING, CurrencyConfig.money("5500")));
        FeeStructureStore.getInstance().addStructure(fs);

        StudentFeeLedger ledger = new StudentFeeLedger("S-LUNCH");
        ledger.charge("8", CurrencyConfig.money("5500"));
        ledger.setCurrentTerm(AcademicTerm.TERM_1);

        ReceiptAllocationEngine engine = new ReceiptAllocationEngine(FeeStructureStore.getInstance());
        List<FeeAllocation> allocations = engine.allocate(ledger, CurrencyConfig.money("5500"), fs, AcademicTerm.TERM_1);

        assertEquals(1, allocations.size());
        assertEquals("8", allocations.get(0).getVoteheadCode());
        assertEquals("LUNCH", allocations.get(0).getVoteheadName());
        assertEquals(CurrencyConfig.money("5500.00"), allocations.get(0).getAllocated());
    }
}
