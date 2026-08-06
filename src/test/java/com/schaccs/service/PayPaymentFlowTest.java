package com.schaccs.service;

import com.schaccs.config.AppConfig;
import com.schaccs.config.CurrencyConfig;
import com.schaccs.enums.AcademicTerm;
import com.schaccs.enums.AccountType;
import com.schaccs.enums.BoardingStatus;
import com.schaccs.enums.PaymentMode;
import com.schaccs.enums.StudentStatus;
import com.schaccs.model.fee.FeeStructure;
import com.schaccs.model.fee.FeeStructureItem;
import com.schaccs.model.finance.Votehead;
import com.schaccs.model.student.Student;
import com.schaccs.repository.PersistenceService;
import com.schaccs.service.receipt.ReceiptService;
import com.schaccs.service.student.PayPreviewService;
import com.schaccs.store.FeeStructureStore;
import com.schaccs.store.StudentStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies the Pay receive-and-print flow end to end at the service layer: the
 * amount a cashier captures on the Pay workspace is posted through the same
 * ReceiptService pipeline the Receipting view uses, and the Pay fee-status
 * summary (PayPreviewService.feeStatus) immediately reflects the payment.
 */
class PayPaymentFlowTest {

    @BeforeEach
    void setUp() {
        PersistenceService.getInstance().clearAll();
        AppConfig.getInstance().getSchoolProfile().setAcademicYear(2026);
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
    }

    private Student createStudent() {
        Student s = new Student();
        s.setAdmissionNumber("ADM-FLOW-" + System.nanoTime());
        s.setName("Pay Flow Student");
        s.setFormClass("Form 1");
        s.setStream("A");
        s.setBoardingStatus(BoardingStatus.DAY);
        s.setStatus(StudentStatus.ACTIVE);
        s.setAcademicYear(2026);
        StudentStore.getInstance().add(s);
        return s;
    }

    @Test
    @DisplayName("Pay capture posts via ReceiptService and Pay fee-status reflects it")
    void payFlowUpdatesPreviewStatus() {
        setupStructure();
        Student student = createStudent();
        StudentStore.getInstance().getLedger(student.getId()).charge("TUITION", CurrencyConfig.money("1000"));

        PayPreviewService payPreview = new PayPreviewService();
        PayPreviewService.FeeStatus before = payPreview.feeStatus(student);
        assertEquals(0, before.paid().compareTo(BigDecimal.ZERO));
        assertEquals(0, before.balance().compareTo(CurrencyConfig.money("1000")));

        ReceiptService.Result result = new ReceiptService().receivePayment(
                student, CurrencyConfig.money("600"), PaymentMode.BANK_SLIP,
                "SLIP-9988", LocalDate.now(), null);

        assertTrue(result.isSuccess(), () -> "receivePayment failed: " + result.getErrors());
        assertNotNull(result.getReceipt());
        assertEquals(0, result.getReceipt().getAmount().compareTo(CurrencyConfig.money("600")));

        PayPreviewService.FeeStatus after = payPreview.feeStatus(student);
        assertEquals(0, after.paid().compareTo(CurrencyConfig.money("600")));
        assertEquals(0, after.balance().compareTo(CurrencyConfig.money("400")));
    }

    @Test
    @DisplayName("Pay capture rejects invalid amounts before reaching the ledger")
    void payCaptureValidatesAmount() {
        setupStructure();
        Student student = createStudent();

        ReceiptService.Result result = new ReceiptService().receivePayment(
                student, CurrencyConfig.money("0"), PaymentMode.CASH, "SLIP-1", LocalDate.now(), null);

        assertFalse(result.isSuccess());
        assertEquals(0, new PayPreviewService().feeStatus(student).paid().compareTo(BigDecimal.ZERO));
    }
}
