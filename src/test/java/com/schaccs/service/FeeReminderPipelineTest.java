package com.schaccs.service;

import com.schaccs.config.AppConfig;
import com.schaccs.config.CurrencyConfig;
import com.schaccs.enums.AcademicTerm;
import com.schaccs.enums.BoardingStatus;
import com.schaccs.enums.StudentStatus;
import com.schaccs.model.fee.FeeStructure;
import com.schaccs.model.fee.FeeStructureItem;
import com.schaccs.model.finance.Votehead;
import com.schaccs.model.student.Student;
import com.schaccs.model.student.StudentBalance;
import com.schaccs.model.student.StudentFeeLedger;
import com.schaccs.repository.Database;
import com.schaccs.repository.PersistenceService;
import com.schaccs.service.fee.FeeCalculationService;
import com.schaccs.service.report.ReportService;
import com.schaccs.service.student.StudentService;
import com.schaccs.store.FeeStructureStore;
import com.schaccs.store.LedgerStore;
import com.schaccs.store.ReceiptStore;
import com.schaccs.store.StudentStore;
import com.schaccs.validation.StudentValidator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive end-to-end test of the fee reminder pipeline:
 * set up fee structures → add students → charge fees → save → load → verify defaulters.
 */
class FeeReminderPipelineTest {

    @BeforeEach
    void setUp() {
        PersistenceService.getInstance().clearAll();
        AppConfig.getInstance().getSchoolProfile().setAcademicYear(2026);
    }

    @AfterEach
    void tearDown() {
        PersistenceService.getInstance().clearAll();
        Database.getInstance().close();
    }

    private void setupFeeStructure(BoardingStatus boarding, AcademicTerm term, String voteheadCode, String voteheadName, BigDecimal amount) {
        FeeStructureStore store = FeeStructureStore.getInstance();
        if (store.findVoteheadByCode(voteheadCode).isEmpty()) {
            store.addVotehead(new Votehead(voteheadCode, voteheadName, com.schaccs.enums.AccountType.SCHOOL_FUND, 1));
        }
        FeeStructure structure = store.findStructure(2026, boarding).orElse(null);
        if (structure == null) {
            structure = new FeeStructure(2026, "ALL", boarding, boarding + " Structure 2026");
            store.addStructure(structure);
        }
        FeeStructureItem item = new FeeStructureItem(voteheadCode, voteheadName, term, boarding, amount);
        structure.addItem(item);
    }

    private Student createStudent(String adm, BoardingStatus boarding) {
        Student s = new Student();
        s.setAdmissionNumber(adm);
        s.setName("Test Student " + adm);
        s.setFormClass("Form 2");
        s.setStream("A");
        s.setBoardingStatus(boarding);
        s.setGender("M");
        s.setPhone("0700000000");
        s.setStatus(StudentStatus.ACTIVE);
        s.setAcademicYear(2026);
        return s;
    }

    private ReportService createReportService() {
        return new ReportService(StudentStore.getInstance(), ReceiptStore.getInstance(),
                FeeStructureStore.getInstance(), LedgerStore.getInstance());
    }

    private FeeCalculationService createFeeCalcService() {
        return new FeeCalculationService(FeeStructureStore.getInstance(), StudentStore.getInstance());
    }

    // ============================
    // Test 1: Full flow via StudentService + FeeCalculationService (no DB round-trip)
    // ============================
    @Test
    void fullFlow_chargeAndVerifyDefaultersInMemory() {
        setupFeeStructure(BoardingStatus.BOARDING, AcademicTerm.TERM_1, "TUITION", "Tuition", CurrencyConfig.money("15000"));
        setupFeeStructure(BoardingStatus.BOARDING, AcademicTerm.TERM_1, "BOARD", "Boarding", CurrencyConfig.money("25000"));

        Student student = createStudent("ADM-001", BoardingStatus.BOARDING);
        StudentService studentService = new StudentService(StudentStore.getInstance(), new StudentValidator());
        List<String> errors = studentService.addStudent(student);
        assertTrue(errors.isEmpty(), "addStudent should succeed: " + errors);

        FeeCalculationService feeService = createFeeCalcService();
        feeService.chargeTermFees(student, AcademicTerm.TERM_1);

        StudentFeeLedger ledger = StudentStore.getInstance().getLedger(student.getId());
        assertEquals(0, ledger.getTotalCharged().compareTo(CurrencyConfig.money("40000")),
                "Total charged should be 15000 + 25000 = 40000, got " + ledger.getTotalCharged());

        ReportService report = createReportService();
        List<StudentBalance> defaulters = report.defaulters(null);
        assertFalse(defaulters.isEmpty(), "Should have at least one defaulter");
        StudentBalance bal = defaulters.stream()
                .filter(b -> "ADM-001".equals(b.getAdmissionNumber()))
                .findFirst().orElse(null);
        assertNotNull(bal, "ADM-001 should appear as defaulter");
        assertEquals(0, bal.getBalance().compareTo(CurrencyConfig.money("40000")),
                "Balance should be 40000, got " + bal.getBalance());
    }

    // ============================
    // Test 2: Full flow with save/load round-trip via PersistenceService
    // ============================
    @Test
    void fullFlow_saveAndLoad_preservesChargesAndShowsDefaulters() {
        setupFeeStructure(BoardingStatus.BOARDING, AcademicTerm.TERM_1, "TUITION", "Tuition", CurrencyConfig.money("15000"));
        setupFeeStructure(BoardingStatus.BOARDING, AcademicTerm.TERM_1, "BOARD", "Boarding", CurrencyConfig.money("25000"));

        Student student = createStudent("ADM-002", BoardingStatus.BOARDING);
        StudentService studentService = new StudentService(StudentStore.getInstance(), new StudentValidator());
        studentService.addStudent(student);

        FeeCalculationService feeService = createFeeCalcService();
        feeService.chargeTermFees(student, AcademicTerm.TERM_1);

        PersistenceService.getInstance().saveAll();

        StudentStore.getInstance().clear();
        FeeStructureStore.getInstance().clear();

        PersistenceService.getInstance().loadAll();

        Student reloaded = StudentStore.getInstance().findByAdmissionNumber("ADM-002").orElse(null);
        assertNotNull(reloaded, "Student should survive round-trip");
        assertEquals(StudentStatus.ACTIVE, reloaded.getStatus(), "Student should be ACTIVE");

        StudentFeeLedger ledger = StudentStore.getInstance().getLedger(reloaded.getId());
        assertEquals(0, ledger.getTotalCharged().compareTo(CurrencyConfig.money("40000")),
                "Charges should survive round-trip: expected 40000, got " + ledger.getTotalCharged());

        ReportService report = createReportService();
        List<StudentBalance> defaulters = report.defaulters(null);
        StudentBalance bal = defaulters.stream()
                .filter(b -> "ADM-002".equals(b.getAdmissionNumber()))
                .findFirst().orElse(null);
        assertNotNull(bal, "Student should appear as defaulter after round-trip");
        assertEquals(0, bal.getBalance().compareTo(CurrencyConfig.money("40000")),
                "Balance should be 40000 after round-trip, got " + bal.getBalance());
    }

    // ============================
    // Test 3: Partial payment reduces defaulter balance after round-trip
    // ============================
    @Test
    void fullFlow_partialPayment_reducesBalance() {
        setupFeeStructure(BoardingStatus.BOARDING, AcademicTerm.TERM_1, "TUITION", "Tuition", CurrencyConfig.money("20000"));

        Student student = createStudent("ADM-003", BoardingStatus.BOARDING);
        StudentService studentService = new StudentService(StudentStore.getInstance(), new StudentValidator());
        studentService.addStudent(student);

        FeeCalculationService feeService = createFeeCalcService();
        feeService.chargeTermFees(student, AcademicTerm.TERM_1);

        StudentFeeLedger ledger = StudentStore.getInstance().getLedger(student.getId());
        ledger.pay("TUITION", CurrencyConfig.money("8000"));

        PersistenceService.getInstance().saveAll();
        StudentStore.getInstance().clear();
        FeeStructureStore.getInstance().clear();
        PersistenceService.getInstance().loadAll();

        StudentFeeLedger reloadedLedger = StudentStore.getInstance().getLedger(
                StudentStore.getInstance().findByAdmissionNumber("ADM-003").get().getId());
        assertEquals(0, reloadedLedger.getTotalCharged().compareTo(CurrencyConfig.money("20000")),
                "Charged should be 20000");
        assertEquals(0, reloadedLedger.getTotalPaid().compareTo(CurrencyConfig.money("8000")),
                "Paid should be 8000");

        ReportService report = createReportService();
        List<StudentBalance> defaulters = report.defaulters(null);
        StudentBalance bal = defaulters.stream()
                .filter(b -> "ADM-003".equals(b.getAdmissionNumber()))
                .findFirst().orElse(null);
        assertNotNull(bal, "Partially paid student should still be a defaulter");
        assertEquals(0, bal.getBalance().compareTo(CurrencyConfig.money("12000")),
                "Balance should be 20000 - 8000 = 12000, got " + bal.getBalance());
    }

    // ============================
    // Test 4: Fully paid student should NOT appear as defaulter
    // ============================
    @Test
    void fullFlow_fullyPaid_notDefaulter() {
        setupFeeStructure(BoardingStatus.BOARDING, AcademicTerm.TERM_1, "TUITION", "Tuition", CurrencyConfig.money("10000"));

        Student student = createStudent("ADM-004", BoardingStatus.BOARDING);
        StudentService studentService = new StudentService(StudentStore.getInstance(), new StudentValidator());
        studentService.addStudent(student);

        FeeCalculationService feeService = createFeeCalcService();
        feeService.chargeTermFees(student, AcademicTerm.TERM_1);

        StudentFeeLedger ledger = StudentStore.getInstance().getLedger(student.getId());
        ledger.pay("TUITION", CurrencyConfig.money("10000"));

        ReportService report = createReportService();
        List<StudentBalance> defaulters = report.defaulters(null);
        boolean found = defaulters.stream().anyMatch(b -> "ADM-004".equals(b.getAdmissionNumber()));
        assertFalse(found, "Fully paid student should NOT appear as defaulter");
    }

    // ============================
    // Test 5: INACTIVE student should NOT appear as defaulter
    // ============================
    @Test
    void inactiveStudent_notShownAsDefaulter() {
        setupFeeStructure(BoardingStatus.BOARDING, AcademicTerm.TERM_1, "TUITION", "Tuition", CurrencyConfig.money("10000"));

        Student student = createStudent("ADM-005", BoardingStatus.BOARDING);
        student.setStatus(StudentStatus.INACTIVE);
        StudentService studentService = new StudentService(StudentStore.getInstance(), new StudentValidator());
        studentService.addStudent(student);

        FeeCalculationService feeService = createFeeCalcService();
        feeService.chargeTermFees(student, AcademicTerm.TERM_1);

        ReportService report = createReportService();
        List<StudentBalance> defaulters = report.defaulters(null);
        boolean found = defaulters.stream().anyMatch(b -> "ADM-005".equals(b.getAdmissionNumber()));
        assertFalse(found, "INACTIVE student should NOT appear as defaulter");
    }

    // ============================
    // Test 6: DAY scholar should use DAY fee structure, not BOARDING
    // ============================
    @Test
    void dayScholar_usesDayStructure() {
        setupFeeStructure(BoardingStatus.BOARDING, AcademicTerm.TERM_1, "TUITION", "Tuition", CurrencyConfig.money("20000"));
        setupFeeStructure(BoardingStatus.DAY, AcademicTerm.TERM_1, "TUITION", "Tuition", CurrencyConfig.money("10000"));

        Student dayStudent = createStudent("ADM-DAY", BoardingStatus.DAY);
        StudentService studentService = new StudentService(StudentStore.getInstance(), new StudentValidator());
        studentService.addStudent(dayStudent);

        FeeCalculationService feeService = createFeeCalcService();
        feeService.chargeTermFees(dayStudent, AcademicTerm.TERM_1);

        StudentFeeLedger ledger = StudentStore.getInstance().getLedger(dayStudent.getId());
        assertEquals(0, ledger.getTotalCharged().compareTo(CurrencyConfig.money("10000")),
                "Day scholar should be charged 10000, got " + ledger.getTotalCharged());
    }

    // ============================
    // Test 7: Multiple students survive save/load round-trip
    // ============================
    @Test
    void multipleStudents_allAppearAsDefaulters() {
        setupFeeStructure(BoardingStatus.BOARDING, AcademicTerm.TERM_1, "TUITION", "Tuition", CurrencyConfig.money("15000"));

        StudentService studentService = new StudentService(StudentStore.getInstance(), new StudentValidator());
        FeeCalculationService feeService = createFeeCalcService();

        for (int i = 1; i <= 5; i++) {
            Student s = createStudent("ADM-M" + i, BoardingStatus.BOARDING);
            studentService.addStudent(s);
            feeService.chargeTermFees(s, AcademicTerm.TERM_1);
        }

        PersistenceService.getInstance().saveAll();
        StudentStore.getInstance().clear();
        FeeStructureStore.getInstance().clear();
        PersistenceService.getInstance().loadAll();

        ReportService report = createReportService();
        List<StudentBalance> defaulters = report.defaulters(null);
        assertEquals(5, defaulters.size(), "All 5 students should appear as defaulters");
        for (StudentBalance b : defaulters) {
            assertEquals(0, b.getBalance().compareTo(CurrencyConfig.money("15000")),
                    b.getAdmissionNumber() + " should have balance 15000, got " + b.getBalance());
        }
    }

    // ============================
    // Test 8: chargeTermFees is idempotent — calling twice does not double-charge
    // ============================
    @Test
    void chargeTermFees_idempotent_noDoubleCharging() {
        setupFeeStructure(BoardingStatus.BOARDING, AcademicTerm.TERM_1, "TUITION", "Tuition", CurrencyConfig.money("10000"));

        Student student = createStudent("ADM-IDX", BoardingStatus.BOARDING);
        StudentService studentService = new StudentService(StudentStore.getInstance(), new StudentValidator());
        studentService.addStudent(student);

        FeeCalculationService feeService = createFeeCalcService();
        feeService.chargeTermFees(student, AcademicTerm.TERM_1);
        feeService.chargeTermFees(student, AcademicTerm.TERM_1);
        feeService.chargeTermFees(student, AcademicTerm.TERM_1);

        StudentFeeLedger ledger = StudentStore.getInstance().getLedger(student.getId());
        assertEquals(0, ledger.getTotalCharged().compareTo(CurrencyConfig.money("10000")),
                "Should NOT be double/triple charged: expected 10000, got " + ledger.getTotalCharged());
    }

    // ============================
    // Test 9: Student with no matching fee structure has zero balance
    // ============================
    @Test
    void studentWithNoMatchingStructure_zeroBalance() {
        setupFeeStructure(BoardingStatus.BOARDING, AcademicTerm.TERM_1, "TUITION", "Tuition", CurrencyConfig.money("10000"));

        Student dayStudent = createStudent("ADM-NOMATCH", BoardingStatus.DAY);
        StudentService studentService = new StudentService(StudentStore.getInstance(), new StudentValidator());
        studentService.addStudent(dayStudent);

        FeeCalculationService feeService = createFeeCalcService();
        feeService.chargeTermFees(dayStudent, AcademicTerm.TERM_1);

        StudentFeeLedger ledger = StudentStore.getInstance().getLedger(dayStudent.getId());
        assertEquals(0, ledger.getTotalCharged().compareTo(BigDecimal.ZERO),
                "Student with no matching structure should have zero charges");
    }

    // ============================
    // Test 10: Arrears + charges both contribute to defaulter balance
    // ============================
    @Test
    void arrearsContributToDefaulterBalance() {
        setupFeeStructure(BoardingStatus.BOARDING, AcademicTerm.TERM_1, "TUITION", "Tuition", CurrencyConfig.money("10000"));

        Student student = createStudent("ADM-ARR", BoardingStatus.BOARDING);
        StudentService studentService = new StudentService(StudentStore.getInstance(), new StudentValidator());
        studentService.addStudent(student);

        FeeCalculationService feeService = createFeeCalcService();
        feeService.chargeTermFees(student, AcademicTerm.TERM_1);

        StudentFeeLedger ledger = StudentStore.getInstance().getLedger(student.getId());
        ledger.setArrears(CurrencyConfig.money("5000"));

        ReportService report = createReportService();
        List<StudentBalance> defaulters = report.defaulters(null);
        StudentBalance bal = defaulters.stream()
                .filter(b -> "ADM-ARR".equals(b.getAdmissionNumber()))
                .findFirst().orElse(null);
        assertNotNull(bal, "Student with arrears should be a defaulter");
        assertEquals(0, bal.getBalance().compareTo(CurrencyConfig.money("15000")),
                "Balance should be 10000 (charged) + 5000 (arrears) = 15000, got " + bal.getBalance());
    }
}
