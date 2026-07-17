package com.schaccs.service;

import com.schaccs.accounting.ReceiptAllocationEngine;
import com.schaccs.config.AppConfig;
import com.schaccs.config.CurrencyConfig;
import com.schaccs.enums.AccountType;
import com.schaccs.enums.BoardingStatus;
import com.schaccs.enums.PaymentMode;
import com.schaccs.model.fee.FeeAllocation;
import com.schaccs.model.finance.Votehead;
import com.schaccs.model.student.Student;
import com.schaccs.model.student.StudentFeeLedger;
import com.schaccs.service.receipt.ReceiptService;
import com.schaccs.store.FeeStructureStore;
import com.schaccs.store.LedgerStore;
import com.schaccs.store.ReceiptStore;
import com.schaccs.store.StudentStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ReceiptCoreTest {

    @BeforeEach
    void setUp() {
        StudentStore.getInstance().clear();
        ReceiptStore.getInstance().clear();
        LedgerStore.getInstance().clear();
        FeeStructureStore.getInstance().clear();
        FeeStructureStore.getInstance().addVotehead(new Votehead("BOARD", "Boarding", AccountType.SCHOOL_FUND, 1));
        FeeStructureStore.getInstance().addVotehead(new Votehead("ACT", "Activity", AccountType.FSE_OPERATIONS, 2));
        AppConfig.getInstance().getSchoolProfile().setNextReceiptNumber(1000);
    }

    @AfterEach
    void tearDown() {
        StudentStore.getInstance().clear();
        ReceiptStore.getInstance().clear();
        LedgerStore.getInstance().clear();
        FeeStructureStore.getInstance().clear();
    }

    @Test
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
}
