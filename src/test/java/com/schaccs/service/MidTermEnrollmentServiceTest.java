package com.schaccs.service;

import com.schaccs.config.CurrencyConfig;
import com.schaccs.enums.AcademicTerm;
import com.schaccs.enums.BoardingStatus;
import com.schaccs.enums.StudentStatus;
import com.schaccs.model.fee.FeeStructure;
import com.schaccs.model.fee.FeeStructureItem;
import com.schaccs.model.student.MidTermStudent;
import com.schaccs.model.student.Student;
import com.schaccs.model.student.StudentFeeLedger;
import com.schaccs.repository.PersistenceService;
import com.schaccs.service.school.AcademicCalendarService;
import com.schaccs.service.student.MidTermEnrollmentService;
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
 * Mid-term enrollments: current-term custom fee billing via the fee ledger and
 * the automatic full-standard-tuition charge from the next term onward.
 */
class MidTermEnrollmentServiceTest {

    private MidTermEnrollmentService service;
    private AcademicCalendarService calendar;

    @BeforeEach
    void setUp() {
        PersistenceService.getInstance().clearAll();
        service = new MidTermEnrollmentService();
        calendar = new AcademicCalendarService();
        calendar.seedIfEmpty();
    }

    @AfterEach
    void tearDown() {
        PersistenceService.getInstance().clearAll();
    }

    private Student createStudent() {
        Student s = new Student();
        s.setAdmissionNumber("ADM-" + System.nanoTime());
        s.setName("Mid-Term Student");
        s.setFormClass("Form 1");
        s.setStream("A");
        s.setBoardingStatus(BoardingStatus.DAY);
        s.setStatus(StudentStatus.ACTIVE);
        s.setAcademicYear(2026);
        StudentStore.getInstance().add(s);
        return s;
    }

    private void seedFeeStructure(int year) {
        FeeStructure structure = new FeeStructure(year, "Form 1", BoardingStatus.DAY, "Form 1 Day");
        structure.addItem(new FeeStructureItem("TUITION", "Tuition", AcademicTerm.TERM_2,
                BoardingStatus.DAY, CurrencyConfig.money("5000")));
        FeeStructureStore.getInstance().addStructure(structure);
    }

    @Test
    @DisplayName("Enroll with toggle on charges the custom fee on the current term ledger")
    void enrollWithToggleOnChargesCurrentTerm() {
        Student student = createStudent();
        LocalDate joined = LocalDate.of(2026, 7, 10);

        List<String> errors = service.enrollStudent(student, joined, true, CurrencyConfig.money("2000"));

        assertTrue(errors.isEmpty());
        assertEquals(1, service.getEnrollments().size());
        MidTermStudent enrollment = service.getEnrollments().get(0);
        assertEquals(student.getAdmissionNumber(), enrollment.getAdmissionNumber());
        assertEquals(student.getName(), enrollment.getName());
        assertEquals(joined, enrollment.getDateJoined());
        assertTrue(enrollment.isChargeCurrentTerm());
        assertEquals(0, enrollment.getMidTermFee().compareTo(CurrencyConfig.money("2000")));
        assertEquals("Active", enrollment.getStatus());

        StudentFeeLedger ledger = StudentStore.getInstance().getLedger(student.getId());
        assertEquals(0, ledger.getCharged(MidTermStudent.MIDTERM_CODE).compareTo(CurrencyConfig.money("2000")));
        assertEquals(calendar.currentTerm(LocalDate.now()).orElse(AcademicTerm.TERM_1),
                ledger.getCurrentTerm(),
                "The student's term aligns with the school's current term");
    }

    @Test
    @DisplayName("Enroll with toggle off charges nothing for the current term")
    void enrollWithToggleOffChargesNothing() {
        Student student = createStudent();

        List<String> errors = service.enrollStudent(student, LocalDate.of(2026, 7, 10), false, null);

        assertTrue(errors.isEmpty());
        MidTermStudent enrollment = service.getEnrollments().get(0);
        assertFalse(enrollment.isChargeCurrentTerm());
        assertEquals(0, enrollment.getMidTermFee().compareTo(BigDecimal.ZERO));
        StudentFeeLedger ledger = StudentStore.getInstance().getLedger(student.getId());
        assertEquals(0, ledger.getCharged(MidTermStudent.MIDTERM_CODE).compareTo(BigDecimal.ZERO));
    }

    @Test
    @DisplayName("Enroll validates the student, the date, and rejects duplicates")
    void enrollValidation() {
        Student registered = createStudent();
        Student unknown = new Student();
        unknown.setAdmissionNumber("ADM-UNKNOWN");
        unknown.setName("Unknown");
        unknown.setStatus(StudentStatus.ACTIVE);
        unknown.setAcademicYear(2026);

        assertFalse(service.enrollStudent(registered, null, true, CurrencyConfig.money("500"))
                .isEmpty(), "A joining date is required");
        assertFalse(service.enrollStudent(unknown, LocalDate.of(2026, 7, 10), true, CurrencyConfig.money("500"))
                .isEmpty(), "The student must exist in the registry");
        assertTrue(service.enrollStudent(registered, LocalDate.of(2026, 7, 10), true, CurrencyConfig.money("500"))
                .isEmpty());
        assertFalse(service.enrollStudent(registered, LocalDate.of(2026, 8, 1), true, CurrencyConfig.money("700"))
                .isEmpty(), "A student can only have one mid-term enrollment");
    }

    @Test
    @DisplayName("Charging the current term requires a positive fee")
    void enrollRequiresPositiveFeeWhenCharging() {
        Student student = createStudent();
        List<String> errors = service.enrollStudent(student, LocalDate.of(2026, 7, 10), true,
                CurrencyConfig.money("0"));
        assertFalse(errors.isEmpty());
        assertTrue(service.getEnrollments().isEmpty());
    }

    @Test
    @DisplayName("Update reconciles the ledger when the toggle or the fee changes")
    void updateAdjustsLedgerCharge() {
        Student student = createStudent();
        service.enrollStudent(student, LocalDate.of(2026, 7, 10), true, CurrencyConfig.money("1000"));
        MidTermStudent enrollment = service.getEnrollments().get(0);
        StudentFeeLedger ledger = StudentStore.getInstance().getLedger(student.getId());

        service.updateEnrollment(enrollment, LocalDate.of(2026, 7, 12), false, null);
        assertEquals(0, ledger.getCharged(MidTermStudent.MIDTERM_CODE).compareTo(BigDecimal.ZERO),
                "Turning the toggle off reverses the current-term charge");
        assertFalse(enrollment.isChargeCurrentTerm());
        assertEquals(0, enrollment.getMidTermFee().compareTo(BigDecimal.ZERO));

        service.updateEnrollment(enrollment, LocalDate.of(2026, 7, 12), true, CurrencyConfig.money("500"));
        assertEquals(0, ledger.getCharged(MidTermStudent.MIDTERM_CODE).compareTo(CurrencyConfig.money("500")),
                "Editing the amount replaces the previous charge");
        assertTrue(enrollment.isChargeCurrentTerm());
    }

    @Test
    @DisplayName("At the end of the current term the full standard fee is charged from the next term")
    void rolloverChargesFullFeesFromNextTerm() {
        seedFeeStructure(2026);
        Student student = createStudent();
        service.enrollStudent(student, LocalDate.of(2026, 7, 10), true, CurrencyConfig.money("2000"));
        StudentFeeLedger ledger = StudentStore.getInstance().getLedger(student.getId());
        ledger.setCurrentTerm(AcademicTerm.TERM_1);

        AcademicCalendarService.RolloverResult result =
                calendar.rolloverIfDue(LocalDate.of(2026, 4, 25));

        assertEquals(1, result.studentsRolled());
        assertEquals(AcademicTerm.TERM_2, ledger.getCurrentTerm());
        assertEquals(0, ledger.getCharged(MidTermStudent.MIDTERM_CODE).compareTo(BigDecimal.ZERO),
                "The partial current-term charge rolled into arrears with the closed cycle");
        assertEquals(0, ledger.getArrears().compareTo(CurrencyConfig.money("2000")));
        assertEquals(0, ledger.getCharged("TUITION").compareTo(CurrencyConfig.money("5000")),
                "Full standard tuition is auto-charged for the next term");
    }

    @Test
    @DisplayName("Students without a mid-term enrollment are charged the new term at rollover")
    void rolloverChargesNonEnrolledStudentsNextTerm() {
        seedFeeStructure(2026);
        Student student = createStudent();
        StudentFeeLedger ledger = StudentStore.getInstance().getLedger(student.getId());
        ledger.charge("TUITION", CurrencyConfig.money("1000"));
        ledger.pay("TUITION", CurrencyConfig.money("400"));

        calendar.rolloverIfDue(LocalDate.of(2026, 4, 25));

        assertEquals(AcademicTerm.TERM_2, ledger.getCurrentTerm());
        assertEquals(0, ledger.getArrears().compareTo(CurrencyConfig.money("600")));
        assertEquals(0, ledger.getCharged("TUITION").compareTo(CurrencyConfig.money("5000")),
                "The new term's standard fee is charged for the next term");
    }

    @Test
    @DisplayName("Delete removes the enrollment record")
    void deleteRemovesEnrollment() {
        Student student = createStudent();
        service.enrollStudent(student, LocalDate.of(2026, 7, 10), true, CurrencyConfig.money("800"));
        assertEquals(1, service.getEnrollments().size());

        service.deleteEnrollment(service.getEnrollments().get(0));

        assertTrue(service.getEnrollments().isEmpty());
    }
}
