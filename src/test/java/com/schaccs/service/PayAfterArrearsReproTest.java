package com.schaccs.service;

import com.schaccs.config.CurrencyConfig;
import com.schaccs.enums.AcademicTerm;
import com.schaccs.enums.AccountType;
import com.schaccs.enums.BoardingStatus;
import com.schaccs.enums.PaymentMode;
import com.schaccs.enums.StudentStatus;
import com.schaccs.model.fee.FeeStructure;
import com.schaccs.model.fee.FeeStructureItem;
import com.schaccs.model.finance.Votehead;
import com.schaccs.model.receipt.Receipt;
import com.schaccs.model.receipt.ReceiptLine;
import com.schaccs.model.student.Student;
import com.schaccs.model.student.StudentFeeLedger;
import com.schaccs.repository.PersistenceService;
import com.schaccs.service.fee.FeeCalculationService;
import com.schaccs.service.receipt.ReceiptService;
import com.schaccs.service.school.AcademicCalendarService;
import com.schaccs.service.student.PayPreviewService;
import com.schaccs.store.FeeStructureStore;
import com.schaccs.store.StudentStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * After a term rollover the new term's fees are charged from the fee structure,
 * so paying off arrears leaves a real billed balance and subsequent receipts
 * distribute across voteheads (not advance) — keeping Pay and receipting
 * transparent and wired to the fee structure.
 */
class PayAfterArrearsReproTest {

    @BeforeEach
    void setUp() {
        PersistenceService.getInstance().clearAll();
        setupStructure();
    }

    @AfterEach
    void tearDown() {
        PersistenceService.getInstance().clearAll();
    }

    private void setupStructure() {
        FeeStructureStore store = FeeStructureStore.getInstance();
        if (store.findVoteheadByCode("TUITION").isEmpty()) {
            store.addVotehead(new Votehead("TUITION", "Tuition", AccountType.SCHOOL_FUND, 1));
        }
        FeeStructure structure = store.findStructure(2026, BoardingStatus.DAY).orElse(null);
        if (structure == null) {
            structure = new FeeStructure(2026, "ALL", BoardingStatus.DAY, "Day Structure 2026");
            store.addStructure(structure);
        }
        structure.addItem(new FeeStructureItem("TUITION", "Tuition", AcademicTerm.TERM_1,
                BoardingStatus.DAY, CurrencyConfig.money("1000")));
        structure.addItem(new FeeStructureItem("TUITION", "Tuition", AcademicTerm.TERM_2,
                BoardingStatus.DAY, CurrencyConfig.money("1000")));
    }

    private Student createStudent() {
        Student s = new Student();
        s.setAdmissionNumber("ADM-ARR-" + System.nanoTime());
        s.setName("Arrears Student");
        s.setFormClass("Form 1");
        s.setStream("A");
        s.setBoardingStatus(BoardingStatus.DAY);
        s.setStatus(StudentStatus.ACTIVE);
        s.setAcademicYear(2026);
        StudentStore.getInstance().add(s);
        return s;
    }

    @Test
    @DisplayName("After rollover the new term is billed and receipts distribute to voteheads")
    void rolloverBillsNextTermAndReceiptsDistribute() {
        AcademicCalendarService calendar = new AcademicCalendarService();
        calendar.seedIfEmpty();
        Student student = createStudent();
        StudentFeeLedger ledger = StudentStore.getInstance().getLedger(student.getId());
        new FeeCalculationService().chargeTermFees(student, AcademicTerm.TERM_1);
        ledger.pay("TUITION", CurrencyConfig.money("400"));

        // End of Term 1: unpaid 600 rolls to arrears, Term 2 is billed 1000.
        calendar.rolloverIfDue(LocalDate.of(2026, 5, 5));
        assertEquals(AcademicTerm.TERM_2, ledger.getCurrentTerm());
        assertEquals(0, ledger.getArrears().compareTo(CurrencyConfig.money("600")));
        assertEquals(0, ledger.getTotalCharged().compareTo(CurrencyConfig.money("1000")),
                "The new term's fee must be charged at rollover");

        // Clearing arrears leaves the current term billed, not zero.
        ReceiptService.Result arrearsPayment = new ReceiptService().receivePayment(
                student, CurrencyConfig.money("600"), PaymentMode.CASH, "SLIP-ARR", LocalDate.now(), null);
        assertTrue(arrearsPayment.isSuccess(), () -> "receive failed: " + arrearsPayment.getErrors());
        assertEquals(0, ledger.getArrears().compareTo(BigDecimal.ZERO));
        assertEquals(0, ledger.getTotalCharged().compareTo(CurrencyConfig.money("1000")));
        assertEquals(0, ledger.getTotalPaid().compareTo(BigDecimal.ZERO));
        assertEquals(0, ledger.getBalance().compareTo(CurrencyConfig.money("1000")),
                "Pay must still show the term fee owed after arrears are cleared");
        assertEquals(1, arrearsPayment.getReceipt().getLines().size());
        assertEquals("ARREARS", arrearsPayment.getReceipt().getLines().get(0).getVoteheadCode());

        PayPreviewService.FeeStatus status = new PayPreviewService().feeStatus(student);
        assertEquals(0, status.expectedTerm().compareTo(CurrencyConfig.money("1000")));
        assertEquals(0, status.charged().compareTo(CurrencyConfig.money("1000")));
        assertEquals(0, status.paid().compareTo(BigDecimal.ZERO));
        assertEquals(0, status.balance().compareTo(CurrencyConfig.money("1000")));

        // Paying the full term fee now distributes to the TUITION votehead.
        ReceiptService.Result termPayment = new ReceiptService().receivePayment(
                student, CurrencyConfig.money("1000"), PaymentMode.CASH, "SLIP-TERM", LocalDate.now(), null);
        assertTrue(termPayment.isSuccess(), () -> "receive failed: " + termPayment.getErrors());
        List<ReceiptLine> lines = termPayment.getReceipt().getLines();
        assertFalse(lines.isEmpty());
        assertTrue(lines.stream().noneMatch(l -> StudentFeeLedger.ADVANCE_CODE.equals(l.getVoteheadCode())),
                "Term payment must not go to advance when a votehead is billed");
        assertTrue(lines.stream().anyMatch(l -> "TUITION".equals(l.getVoteheadCode())
                        && l.getAmount().compareTo(CurrencyConfig.money("1000")) == 0),
                "Receipt must show the TUITION votehead allocation");
        assertEquals(0, ledger.getTotalPaid().compareTo(CurrencyConfig.money("1000")));
        assertEquals(0, ledger.getBalance().compareTo(BigDecimal.ZERO));
    }
}
