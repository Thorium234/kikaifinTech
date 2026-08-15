package com.schaccs.service;

import com.schaccs.config.AppConfig;
import com.schaccs.config.CurrencyConfig;
import com.schaccs.enums.AcademicTerm;
import com.schaccs.enums.AccountType;
import com.schaccs.enums.BoardingStatus;
import com.schaccs.enums.StudentStatus;
import com.schaccs.model.fee.FeeStructure;
import com.schaccs.model.fee.FeeStructureItem;
import com.schaccs.model.finance.Votehead;
import com.schaccs.model.student.Student;
import com.schaccs.repository.PersistenceService;
import com.schaccs.service.fee.FeeCalculationService;
import com.schaccs.service.student.PayPreviewService;
import com.schaccs.store.FeeStructureStore;
import com.schaccs.store.StudentStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PayPreviewServiceTest {

    @BeforeEach
    void setUp() {
        PersistenceService.getInstance().clearAll();
        AppConfig.getInstance().getSchoolProfile().setAcademicYear(2026);
    }

    @AfterEach
    void tearDown() {
        PersistenceService.getInstance().clearAll();
    }

    private void setupStructure(BoardingStatus status, AcademicTerm term,
                                String code, String name, String amount) {
        FeeStructureStore store = FeeStructureStore.getInstance();
        if (store.findVoteheadByCode(code).isEmpty()) {
            store.addVotehead(new Votehead(code, name, AccountType.SCHOOL_FUND, 1));
        }
        FeeStructure structure = store.findStructure(2026, status).orElse(null);
        if (structure == null) {
            structure = new FeeStructure(2026, "ALL", status, status + " Structure 2026");
            store.addStructure(structure);
        }
        structure.addItem(new FeeStructureItem(code, name, term, status, CurrencyConfig.money(amount)));
    }

    private Student createStudent(BoardingStatus boarding, String parentName) {
        Student s = new Student();
        s.setAdmissionNumber("ADM-" + boarding + "-" + System.nanoTime());
        s.setName("Student");
        s.setFormClass("Form 1");
        s.setStream("A");
        s.setBoardingStatus(boarding);
        s.setParentName(parentName);
        s.setStatus(StudentStatus.ACTIVE);
        s.setAcademicYear(2026);
        StudentStore.getInstance().add(s);
        return s;
    }

    @Test
    void expectedTermFeeComesFromFeeStructure() {
        setupStructure(BoardingStatus.BOARDING, AcademicTerm.TERM_1, "TUITION", "Tuition", "1000");
        setupStructure(BoardingStatus.BOARDING, AcademicTerm.TERM_1, "BOARD", "Boarding", "5000");
        Student student = createStudent(BoardingStatus.BOARDING, "Guardian A");

        PayPreviewService service = new PayPreviewService();
        assertTrue(service.hasStructure(student));
        PayPreviewService.FeeStatus status = service.feeStatus(student);

        assertEquals(0, status.expectedTerm().compareTo(CurrencyConfig.money("6000")),
                "Expected term fee must equal the fee structure total for the current term");
        assertEquals(2, status.expectedByVotehead().size());
        assertEquals(0, status.expectedByVotehead().get("BOARD").compareTo(CurrencyConfig.money("5000")));
    }

    @Test
    void expectedFeeAppliesSiblingDiscount() {
        setupStructure(BoardingStatus.DAY, AcademicTerm.TERM_1, "TUITION", "Tuition", "1000");
        AppConfig.getInstance().getSchoolProfile().setSiblingDiscountEnabled(true);
        AppConfig.getInstance().getSchoolProfile().setSiblingDiscountRate(CurrencyConfig.money("0.20"));
        Student first = createStudent(BoardingStatus.DAY, "Guardian Same");
        Student second = createStudent(BoardingStatus.DAY, "Guardian Same");

        PayPreviewService service = new PayPreviewService();
        PayPreviewService.FeeStatus statusFirst = service.feeStatus(first);
        PayPreviewService.FeeStatus statusSecond = service.feeStatus(second);

        assertEquals(0, statusFirst.expectedTerm().compareTo(CurrencyConfig.money("1000")));
        assertEquals(0, statusSecond.expectedTerm().compareTo(CurrencyConfig.money("800")),
                "Second sibling should be billed at 80%");
    }

    @Test
    void feeStatusReflectsLedgerChargesAndPayments() {
        setupStructure(BoardingStatus.DAY, AcademicTerm.TERM_1, "TUITION", "Tuition", "1000");
        Student student = createStudent(BoardingStatus.DAY, "Guardian A");
        StudentStore.getInstance().getLedger(student.getId()).charge("TUITION", CurrencyConfig.money("1000"));
        StudentStore.getInstance().getLedger(student.getId()).pay("TUITION", CurrencyConfig.money("400"));
        StudentStore.getInstance().getLedger(student.getId()).setArrears(CurrencyConfig.money("250"));

        PayPreviewService.FeeStatus status = new PayPreviewService().feeStatus(student);

        assertEquals(0, status.charged().compareTo(CurrencyConfig.money("1000")));
        assertEquals(0, status.paid().compareTo(CurrencyConfig.money("400")));
        assertEquals(0, status.arrears().compareTo(CurrencyConfig.money("250")));
        assertEquals(0, status.balance().compareTo(CurrencyConfig.money("850")),
                "Balance = charged 1000 + arrears 250 - paid 400");
    }

    @Test
    void missingStructureFlagsPreviewWithoutCrashing() {
        Student student = createStudent(BoardingStatus.BOARDING, "Guardian A");
        PayPreviewService service = new PayPreviewService();

        assertFalse(service.hasStructure(student));
        PayPreviewService.FeeStatus status = service.feeStatus(student);
        assertEquals(0, status.expectedTerm().compareTo(BigDecimal.ZERO));
        assertTrue(status.expectedByVotehead().isEmpty());
    }

    @Test
    void structureNamesSurfaceImportedVoteHeadNames() {
        FeeStructureStore store = FeeStructureStore.getInstance();
        FeeStructure structure = new FeeStructure(2026, "ALL", BoardingStatus.BOARDING, "Boarding 2026");
        structure.addItem(new FeeStructureItem("8", "LUNCH", AcademicTerm.TERM_1,
                BoardingStatus.BOARDING, CurrencyConfig.money("5500")));
        structure.addItem(new FeeStructureItem("1", "BOARDING", AcademicTerm.TERM_1,
                BoardingStatus.BOARDING, CurrencyConfig.money("14000")));
        store.addStructure(structure);
        Student student = createStudent(BoardingStatus.BOARDING, "Guardian A");
        StudentStore.getInstance().getLedger(student.getId()).setCurrentTerm(AcademicTerm.TERM_1);

        Map<String, String> names = new PayPreviewService().structureNames(student);

        assertEquals("LUNCH", names.get("8"));
        assertEquals("BOARDING", names.get("1"));
    }
}
