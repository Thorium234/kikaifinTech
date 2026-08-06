package com.schaccs.service;

import com.schaccs.config.CurrencyConfig;
import com.schaccs.enums.AcademicTerm;
import com.schaccs.enums.BoardingStatus;
import com.schaccs.enums.StudentStatus;
import com.schaccs.model.school.TermPeriod;
import com.schaccs.model.student.Student;
import com.schaccs.model.student.StudentFeeLedger;
import com.schaccs.repository.PersistenceService;
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
    @DisplayName("Seed inserts the 2026 sample periods and is idempotent")
    void seedSeedsSampleDataOnce() {
        assertTrue(service.seedIfEmpty());
        assertEquals(3, service.getPeriods().size());
        assertFalse(service.seedIfEmpty());

        Optional<TermPeriod> t1 = service.periodForTerm(AcademicTerm.TERM_1, LocalDate.of(2026, 6, 1));
        assertTrue(t1.isPresent());
        assertEquals(LocalDate.of(2026, 1, 20), t1.get().getFrom());
        assertEquals(LocalDate.of(2026, 4, 19), t1.get().getTo());
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
        assertEquals(AcademicTerm.TERM_2,
                service.currentTerm(LocalDate.of(2026, 6, 1)).orElse(null));
        assertEquals(AcademicTerm.TERM_3,
                service.currentOrNextPeriod(LocalDate.of(2026, 8, 1)).orElseThrow().getTerm());
        assertEquals(LocalDate.of(2026, 8, 24),
                service.nextTermStart(LocalDate.of(2026, 6, 1)).orElse(null));
        assertEquals(1, service.daysRemaining(LocalDate.of(2026, 4, 18)));
        assertEquals(AcademicTerm.TERM_1,
                service.periodFor(LocalDate.of(2026, 3, 1)).orElseThrow().getTerm());
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
                service.rolloverIfDue(LocalDate.of(2026, 4, 25));

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

        service.rolloverIfDue(LocalDate.of(2026, 4, 25));
        AcademicCalendarService.RolloverResult second =
                service.rolloverIfDue(LocalDate.of(2026, 4, 25));

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
                service.rolloverIfDue(LocalDate.of(2026, 8, 1));

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
                service.rolloverIfDue(LocalDate.of(2026, 10, 30));

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
                service.overduePreview(LocalDate.of(2026, 4, 25));
        assertEquals(1, ended.studentsOverdue());
        assertEquals(0, ended.totalUnpaid().compareTo(CurrencyConfig.money("700")));

        AcademicCalendarService.RolloverPreview inSession =
                service.overduePreview(LocalDate.of(2026, 4, 1));
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
}
