package com.schaccs.service;

import com.schaccs.config.CurrencyConfig;
import com.schaccs.enums.AcademicTerm;
import com.schaccs.enums.BoardingStatus;
import com.schaccs.enums.DurationUnit;
import com.schaccs.enums.StudentStatus;
import com.schaccs.enums.TermStatus;
import com.schaccs.model.school.TermPeriod;
import com.schaccs.model.student.Student;
import com.schaccs.model.student.StudentFeeLedger;
import com.schaccs.repository.PersistenceService;
import com.schaccs.service.fee.FeeCalculationService;
import com.schaccs.service.school.AcademicCalendarService;
import com.schaccs.store.StudentStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Academic Calendar: term-period CRUD validation, date awareness, and the
 * automatic end-of-term transition that rolls unpaid balances into arrears and
 * moves students to the next term/class.
 */
class AcademicCalendarServiceTest {

    private AcademicCalendarService service;

    @BeforeEach
    void setUp() {
        PersistenceService.getInstance().clearAll();
        service = new AcademicCalendarService();
    }

    @AfterEach
    void tearDown() {
        PersistenceService.getInstance().clearAll();
    }

    private Student createStudent(String form, Integer academicYear) {
        Student s = new Student();
        s.setAdmissionNumber("ADM-" + System.nanoTime());
        s.setName("Calendar Student");
        s.setFormClass(form);
        s.setStream("A");
        s.setBoardingStatus(BoardingStatus.DAY);
        s.setStatus(StudentStatus.ACTIVE);
        s.setAcademicYear(academicYear);
        StudentStore.getInstance().add(s);
        return s;
    }

    private void seed() {
        service.seedIfEmpty();
    }

    @Test
    @DisplayName("Seed inserts current-year sample periods and is idempotent")
    void seedSeedsSampleDataOnce() {
        assertTrue(service.seedIfEmpty());
        assertEquals(3, service.getPeriods().size());
        assertFalse(service.seedIfEmpty());

        int year = LocalDate.now().getYear();
        Optional<TermPeriod> t1 = service.periodForTerm(AcademicTerm.TERM_1, LocalDate.of(year, 6, 1));
        assertTrue(t1.isPresent());
        assertEquals(LocalDate.of(year, 1, 1), t1.get().getFrom());
        assertEquals(LocalDate.of(year, 4, 30), t1.get().getTo());
    }

    @Test
    @DisplayName("CRUD rejects bad dates, overlaps, and duplicate term years")
    void crudValidation() {
        seed();
        // end before start
        assertFalse(service.addPeriod(AcademicTerm.TERM_1,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 1)).isEmpty());
        // overlapping range
        assertFalse(service.addPeriod(AcademicTerm.TERM_2,
                LocalDate.of(2026, 4, 1), LocalDate.of(2026, 6, 1)).isEmpty());
        // duplicate term for same year
        assertFalse(service.addPeriod(AcademicTerm.TERM_1,
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 1)).isEmpty());
        // same term in a different year is fine
        assertTrue(service.addPeriod(AcademicTerm.TERM_1,
                LocalDate.of(2027, 1, 10), LocalDate.of(2027, 4, 10)).isEmpty());
        assertEquals(4, service.getPeriods().size());
    }

    @Test
    @DisplayName("Service knows the current term, next term start and days remaining")
    void dateAwareness() {
        seed();
        int year = LocalDate.now().getYear();
        assertEquals(AcademicTerm.TERM_2,
                service.currentTerm(LocalDate.of(year, 6, 1)).orElse(null));
        assertEquals(AcademicTerm.TERM_3,
                service.currentOrNextPeriod(LocalDate.of(year, 9, 1)).orElseThrow().getTerm());
        assertEquals(LocalDate.of(year, 9, 1),
                service.nextTermStart(LocalDate.of(year, 6, 1)).orElse(null));
        assertEquals(1, service.daysRemaining(LocalDate.of(year, 4, 29)));
        assertEquals(AcademicTerm.TERM_1,
                service.periodFor(LocalDate.of(year, 3, 1)).orElseThrow().getTerm());
    }

    @Test
    @DisplayName("End of Term 1: unpaid balance rolls to arrears and the student moves to Term 2")
    void rolloverEndOfTerm1() {
        seed();
        Student student = createStudent("Form 1", 2026);
        StudentFeeLedger ledger = StudentStore.getInstance().getLedger(student.getId());
        ledger.charge("TUITION", CurrencyConfig.money("1000"));
        ledger.pay("TUITION", CurrencyConfig.money("400"));

        AcademicCalendarService.RolloverResult result =
                service.rolloverIfDue(LocalDate.of(2026, 5, 5));

        assertEquals(1, result.studentsRolled());
        assertEquals(0, result.classPromotions());
        assertEquals(0, result.arrearsRolled().compareTo(CurrencyConfig.money("600")));
        assertEquals(0, ledger.getArrears().compareTo(CurrencyConfig.money("600")));
        assertEquals(AcademicTerm.TERM_2, ledger.getCurrentTerm());
        assertEquals(0, ledger.getTotalCharged().compareTo(BigDecimal.ZERO),
                "The ended term's cycle is closed");
    }

    @Test
    @DisplayName("Rollover is idempotent — running it again does not double-count arrears")
    void rolloverIsIdempotent() {
        seed();
        Student student = createStudent("Form 1", 2026);
        StudentFeeLedger ledger = StudentStore.getInstance().getLedger(student.getId());
        ledger.charge("TUITION", CurrencyConfig.money("1000"));
        ledger.pay("TUITION", CurrencyConfig.money("400"));

        service.rolloverIfDue(LocalDate.of(2026, 5, 5));
        AcademicCalendarService.RolloverResult second =
                service.rolloverIfDue(LocalDate.of(2026, 5, 5));

        assertEquals(0, second.studentsRolled());
        assertEquals(0, ledger.getArrears().compareTo(CurrencyConfig.money("600")));
        assertEquals(AcademicTerm.TERM_2, ledger.getCurrentTerm());
    }

    @Test
    @DisplayName("Catch-up moves a student past every term that has already ended")
    void rolloverCatchesUpMultipleEndedTerms() {
        seed();
        Student student = createStudent("Form 1", 2026);
        StudentFeeLedger ledger = StudentStore.getInstance().getLedger(student.getId());
        ledger.charge("TUITION", CurrencyConfig.money("1000"));
        ledger.pay("TUITION", CurrencyConfig.money("400"));

        AcademicCalendarService.RolloverResult result =
                service.rolloverIfDue(LocalDate.of(2026, 9, 5));

        assertEquals(1, result.studentsRolled(), "One student, counted once despite two terms caught up");
        assertEquals(0, result.arrearsRolled().compareTo(CurrencyConfig.money("600")));
        assertEquals(AcademicTerm.TERM_3, ledger.getCurrentTerm());
    }

    @Test
    @DisplayName("After Term 3 the class is promoted and the student moves to Term 1 of the next year")
    void rolloverTerm3PromotesClass() {
        seed();
        Student student = createStudent("Form 1", 2026);
        StudentFeeLedger ledger = StudentStore.getInstance().getLedger(student.getId());
        ledger.setCurrentTerm(AcademicTerm.TERM_3);
        ledger.charge("TUITION", CurrencyConfig.money("800"));
        ledger.pay("TUITION", CurrencyConfig.money("200"));

        AcademicCalendarService.RolloverResult result =
                service.rolloverIfDue(LocalDate.of(2027, 1, 5));

        assertEquals(1, result.studentsRolled());
        assertEquals(1, result.classPromotions());
        assertEquals("Form 2", student.getFormClass());
        assertEquals(2027, student.getAcademicYear());
        assertEquals(AcademicTerm.TERM_1, ledger.getCurrentTerm());
        assertEquals(0, ledger.getArrears().compareTo(CurrencyConfig.money("600")));
    }

    @Test
    @DisplayName("Overdue preview reports how many students and how much will roll")
    void overduePreview() {
        seed();
        Student student = createStudent("Form 1", 2026);
        StudentFeeLedger ledger = StudentStore.getInstance().getLedger(student.getId());
        ledger.charge("TUITION", CurrencyConfig.money("1000"));
        ledger.pay("TUITION", CurrencyConfig.money("300"));

        AcademicCalendarService.RolloverPreview ended =
                service.overduePreview(LocalDate.of(2026, 5, 5));
        assertEquals(1, ended.studentsOverdue());
        assertEquals(0, ended.totalUnpaid().compareTo(CurrencyConfig.money("700")));

        AcademicCalendarService.RolloverPreview inSession =
                service.overduePreview(LocalDate.of(2026, 4, 15));
        assertEquals(0, inSession.studentsOverdue());
    }

    @Test
    @DisplayName("UpdatePeriod edits a period in place")
    void updatePeriodEditsInPlace() {
        seed();
        TermPeriod t1 = service.periodForTerm(AcademicTerm.TERM_1, LocalDate.of(2026, 3, 1)).orElseThrow();
        List<String> errors = service.updatePeriod(t1, AcademicTerm.TERM_1,
                LocalDate.of(2026, 1, 27), LocalDate.of(2026, 4, 25));
        assertTrue(errors.isEmpty());
        assertEquals(LocalDate.of(2026, 1, 27), t1.getFrom());
        assertEquals(LocalDate.of(2026, 4, 25), t1.getTo());
    }

    @Test
    @DisplayName("reconcileStatuses marks exactly one ACTIVE, past ENDED and future PLANNED")
    void reconcileStatusesMarksTermLifecycle() {
        seed();
        service.reconcileStatuses(LocalDate.of(2026, 6, 1));
        assertEquals(TermStatus.ENDED, periodOf(AcademicTerm.TERM_1).getStatus());
        assertEquals(TermStatus.ACTIVE, periodOf(AcademicTerm.TERM_2).getStatus());
        assertEquals(TermStatus.PLANNED, periodOf(AcademicTerm.TERM_3).getStatus());
        long activeCount = service.getPeriods().stream()
                .filter(p -> p.getStatus() == TermStatus.ACTIVE).count();
        assertEquals(1, activeCount, "Only one term may be ACTIVE at a time");
        assertEquals(AcademicTerm.TERM_2, service.activeTerm(LocalDate.of(2026, 6, 1)).orElse(null));
    }

    @Test
    @DisplayName("reconcileStatuses is idempotent on the same day")
    void reconcileStatusesIdempotent() {
        seed();
        service.reconcileStatuses(LocalDate.of(2026, 6, 1));
        int changed = service.reconcileStatuses(LocalDate.of(2026, 6, 1));
        assertEquals(0, changed);
    }

    @Test
    @DisplayName("ensureYearCalendar scaffolds the three standard ended terms for a missing year")
    void ensureYearCalendarScaffoldsMissingYear() {
        seed();
        assertTrue(service.ensureYearCalendar(2020));
        assertEquals(6, service.getPeriods().size());
        assertEquals(AcademicTerm.TERM_1,
                service.periodForTerm(AcademicTerm.TERM_1, LocalDate.of(2020, 2, 1)).orElseThrow().getTerm());
        assertEquals(AcademicTerm.TERM_3,
                service.periodForTerm(AcademicTerm.TERM_3, LocalDate.of(2020, 10, 1)).orElseThrow().getTerm());
        for (TermPeriod p : service.getPeriods()) {
            if (p.getYear() == 2020) {
                assertEquals(TermStatus.ENDED, p.getStatus(),
                        "Scaffolded historical terms are ENDED by default");
            }
        }
        assertFalse(service.ensureYearCalendar(2020), "Existing year is never regenerated");
        assertFalse(service.ensureYearCalendar(2026), "Sample year already present");
    }

    @Test
    @DisplayName("expectedCompletionDate computes from enrollment plus YEARS or TERMS duration")
    void expectedCompletionDateComputesDuration() {
        seed();
        Student years = createStudent("Form 1", 2026);
        years.setEnrollmentDate(LocalDate.of(2024, 1, 10));
        years.setDurationValue(4);
        years.setDurationUnit(DurationUnit.YEARS);
        assertEquals(LocalDate.of(2028, 1, 10), service.expectedCompletionDate(years));

        Student terms = createStudent("Form 1", 2026);
        terms.setEnrollmentDate(LocalDate.of(2024, 1, 10));
        terms.setDurationValue(9);
        terms.setDurationUnit(DurationUnit.TERMS);
        assertEquals(LocalDate.of(2027, 1, 10), service.expectedCompletionDate(terms),
                "9 terms ≈ 3 years of 4 months each");

        Student incomplete = createStudent("Form 1", 2026);
        assertNull(service.expectedCompletionDate(incomplete));
    }

    @Test
    @DisplayName("checkCompletions marks a student COMPLETED once the expected completion date passes")
    void checkCompletionsMarksCompletedStudent() {
        seed();
        Student student = createStudent("Form 4", 2026);
        student.setEnrollmentDate(LocalDate.of(2022, 1, 10));
        student.setDurationValue(4);
        student.setDurationUnit(DurationUnit.YEARS);
        student.setExpectedCompletionDate(service.expectedCompletionDate(student));

        assertEquals(0, service.checkCompletions(LocalDate.of(2025, 1, 1)));
        assertEquals(StudentStatus.ACTIVE, student.getStatus());

        assertEquals(1, service.checkCompletions(LocalDate.of(2027, 1, 1)));
        assertEquals(StudentStatus.COMPLETED, student.getStatus());
    }

    @Test
    @DisplayName("Rollover skips non-ACTIVE students (completed courses are not advanced)")
    void rolloverSkipsCompletedStudents() {
        seed();
        Student student = createStudent("Form 4", 2026);
        student.setStatus(StudentStatus.COMPLETED);
        StudentFeeLedger ledger = StudentStore.getInstance().getLedger(student.getId());
        ledger.setCurrentTerm(AcademicTerm.TERM_3);
        ledger.charge("TUITION", CurrencyConfig.money("800"));

        AcademicCalendarService.RolloverResult result =
                service.rolloverIfDue(LocalDate.of(2027, 1, 5));

        assertEquals(0, result.studentsRolled());
        assertEquals(AcademicTerm.TERM_3, ledger.getCurrentTerm(),
                "Completed student's term is not advanced");
    }

    @Test
    @DisplayName("Rollover freezes a student whose course clock passed even if the term ended")
    void rolloverCompletesCourseInsteadOfAdvancing() {
        seed();
        Student student = createStudent("Form 4", 2026);
        student.setEnrollmentDate(LocalDate.of(2022, 1, 10));
        student.setDurationValue(4);
        student.setDurationUnit(DurationUnit.YEARS);
        student.setExpectedCompletionDate(service.expectedCompletionDate(student));
        StudentFeeLedger ledger = StudentStore.getInstance().getLedger(student.getId());
        ledger.setCurrentTerm(AcademicTerm.TERM_3);
        ledger.charge("TUITION", CurrencyConfig.money("800"));

        AcademicCalendarService.RolloverResult result =
                service.rolloverIfDue(LocalDate.of(2027, 1, 15));

        assertEquals(0, result.studentsRolled());
        assertEquals(StudentStatus.COMPLETED, student.getStatus());
        assertEquals(AcademicTerm.TERM_3, ledger.getCurrentTerm());
    }

    @Test
    @DisplayName("Fee generation freezes for COMPLETED / GRADUATED students")
    void feeGenerationFreezesForCompletedStudents() {
        seed();
        Student completed = createStudent("Form 4", 2026);
        completed.setStatus(StudentStatus.COMPLETED);
        new FeeCalculationService().chargeTermFees(completed, AcademicTerm.TERM_1);
        assertEquals(0, StudentStore.getInstance().getLedger(completed.getId())
                .getTotalCharged().compareTo(BigDecimal.ZERO));

        Student graduated = createStudent("Form 4", 2026);
        graduated.setStatus(StudentStatus.GRADUATED);
        new FeeCalculationService().chargeAnnualFees(graduated);
        assertEquals(0, StudentStore.getInstance().getLedger(graduated.getId())
                .getTotalCharged().compareTo(BigDecimal.ZERO));
    }

    private TermPeriod periodOf(AcademicTerm term) {
        return service.periodForTerm(term, LocalDate.of(2026, 6, 1)).orElseThrow();
    }
}
