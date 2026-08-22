package com.schaccs.service;

import com.schaccs.config.AppConfig;
import com.schaccs.config.CurrencyConfig;
import com.schaccs.enums.AcademicTerm;
import com.schaccs.enums.BoardingStatus;
import com.schaccs.enums.StudentStatus;
import com.schaccs.model.student.Student;
import com.schaccs.model.student.StudentFeeLedger;
import com.schaccs.model.student.StudentTermBalance;
import com.schaccs.repository.PersistenceService;
import com.schaccs.service.school.AcademicCalendarService;
import com.schaccs.service.student.StudentService;
import com.schaccs.store.RecycleBinStore;
import com.schaccs.store.StudentStore;
import com.schaccs.store.StudentTermBalanceStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StudentCohortLifecycleTest {

    @BeforeEach
    void setUp() {
        PersistenceService.getInstance().clearAll();
        AppConfig.getInstance().setCurrentUserRole("PRINCIPAL");
    }

    @AfterEach
    void tearDown() {
        PersistenceService.getInstance().clearAll();
    }

    private Student createStudent(String adm, int admissionYear, int duration) {
        Student s = new Student();
        s.setAdmissionNumber(adm);
        s.setName("Student " + adm);
        s.setFormClass("Form 1");
        s.setStream("A");
        s.setBoardingStatus(BoardingStatus.BOARDING);
        s.setStatus(StudentStatus.ACTIVE);
        s.setAcademicYear(admissionYear);
        s.setYearOfAdmission(admissionYear);
        s.setCourseDurationYears(duration);
        s.setLifecycleStatus("ACTIVE");
        StudentStore.getInstance().add(s);
        return s;
    }

    @Nested
    @DisplayName("Student Model Lifecycle Fields")
    class ModelTests {

        @Test
        @DisplayName("computeExpectedCompletionYear uses courseDurationYears")
        void computeYear() {
            Student s = new Student();
            s.setYearOfAdmission(2024);
            s.setCourseDurationYears(4);
            assertEquals(2028, s.computeExpectedCompletionYear());
        }

        @Test
        @DisplayName("computeExpectedCompletionYear falls back to durationValue")
        void computeYearFallback() {
            Student s = new Student();
            s.setYearOfAdmission(2024);
            s.setCourseDurationYears(null);
            s.setDurationValue(3);
            assertEquals(2027, s.computeExpectedCompletionYear());
        }

        @Test
        @DisplayName("computeExpectedCompletionYear returns null when no admission year")
        void computeYearNull() {
            Student s = new Student();
            s.setYearOfAdmission(null);
            s.setCourseDurationYears(null);
            assertNull(s.computeExpectedCompletionYear());
        }

        @Test
        @DisplayName("markDeleted sets soft-delete flags and WITHDRAWN status")
        void markDeletedSetsFlags() {
            Student s = new Student();
            s.markDeleted("Transferred");
            assertTrue(s.isDeleted());
            assertNotNull(s.getDeletedAt());
            assertEquals("Transferred", s.getDeletionReason());
            assertEquals("WITHDRAWN", s.getLifecycleStatus());
        }

        @Test
        @DisplayName("clearDeleted resets all flags")
        void clearDeletedResetsFlags() {
            Student s = new Student();
            s.markDeleted("Temp leave");
            s.clearDeleted();
            assertFalse(s.isDeleted());
            assertNull(s.getDeletedAt());
            assertNull(s.getDeletionReason());
            assertEquals("ACTIVE", s.getLifecycleStatus());
        }
    }

    @Nested
    @DisplayName("Soft-Delete Financial Locking")
    class SoftDeleteTests {

        @Test
        @DisplayName("Zero-balance student can be deleted")
        void zeroBalanceDeleted() {
            Student s = createStudent("ADM-1", 2026, 4);
            StudentService svc = new StudentService();
            List<String> errors = svc.deleteToRecycleBin(List.of(s), "Done");
            assertTrue(errors.isEmpty());
            assertTrue(s.isDeleted());
            assertEquals("WITHDRAWN", s.getLifecycleStatus());
        }

        @Test
        @DisplayName("Outstanding balance blocks deletion")
        void outstandingBlocks() {
            Student s = createStudent("ADM-2", 2026, 4);
            StudentFeeLedger ledger = StudentStore.getInstance().getLedger(s.getId());
            ledger.charge("TUITION", CurrencyConfig.money("10000"));
            ledger.pay("TUITION", CurrencyConfig.money("5000"));
            StudentService svc = new StudentService();
            List<String> errors = svc.deleteToRecycleBin(List.of(s), "X");
            assertFalse(errors.isEmpty());
            assertFalse(s.isDeleted());
        }

        @Test
        @DisplayName("Advance credit blocks deletion")
        void advanceBlocks() {
            Student s = createStudent("ADM-3", 2026, 4);
            StudentFeeLedger ledger = StudentStore.getInstance().getLedger(s.getId());
            ledger.setAdvance(CurrencyConfig.money("2000"));
            StudentService svc = new StudentService();
            List<String> errors = svc.deleteToRecycleBin(List.of(s), "X");
            assertFalse(errors.isEmpty());
            assertFalse(s.isDeleted());
        }

        @Test
        @DisplayName("Restore clears deletion flags on existing student")
        void restoreClearsFlags() {
            Student s = createStudent("ADM-4", 2026, 4);
            StudentService svc = new StudentService();
            svc.deleteToRecycleBin(List.of(s), "Temp");
            var snap = RecycleBinStore.getInstance().findById(s.getId()).orElseThrow();
            List<String> errors = svc.restore(List.of(snap));
            assertTrue(errors.isEmpty());
            assertFalse(s.isDeleted());
            assertEquals("ACTIVE", s.getLifecycleStatus());
        }
    }

    @Nested
    @DisplayName("Lifecycle Auto-Stop")
    class AutoStopTests {

        @Test
        @DisplayName("checkCompletions marks students past expected year")
        void checkCompletionsByYear() {
            Student s = createStudent("ADM-Y1", 2020, 4);
            AcademicCalendarService cal = new AcademicCalendarService();
            int count = cal.checkCompletions(LocalDate.now());
            assertEquals(1, count);
            assertEquals(StudentStatus.COMPLETED, s.getStatus());
            assertEquals("COMPLETED", s.getLifecycleStatus());
        }

        @Test
        @DisplayName("Already completed students not counted")
        void alreadyCompletedNotCounted() {
            Student s = createStudent("ADM-Y2", 2020, 4);
            s.setStatus(StudentStatus.COMPLETED);
            s.setLifecycleStatus("COMPLETED");
            AcademicCalendarService cal = new AcademicCalendarService();
            assertEquals(0, cal.checkCompletions(LocalDate.now()));
        }

        @Test
        @DisplayName("Students still on course not auto-completed")
        void onCourseNotCompleted() {
            createStudent("ADM-Y3", LocalDate.now().getYear(), 4);
            AcademicCalendarService cal = new AcademicCalendarService();
            assertEquals(0, cal.checkCompletions(LocalDate.now()));
        }
    }

    @Nested
    @DisplayName("StudentTermBalance Store")
    class TermBalanceTests {

        @Test
        @DisplayName("Store CRUD and lookup by student")
        void storeCRUD() {
            var store = StudentTermBalanceStore.getInstance();
            var b1 = new StudentTermBalance("S1", 2026, AcademicTerm.TERM_1,
                    CurrencyConfig.money("5000"), CurrencyConfig.zero(),
                    CurrencyConfig.money("3000"), CurrencyConfig.money("2000"));
            var b2 = new StudentTermBalance("S1", 2026, AcademicTerm.TERM_2,
                    CurrencyConfig.money("5000"), CurrencyConfig.money("2000"),
                    CurrencyConfig.money("4000"), CurrencyConfig.money("3000"));
            store.add(b1);
            store.add(b2);

            assertEquals(2, store.findByStudent("S1").size());
            assertTrue(store.find("S1", 2026, AcademicTerm.TERM_1).isPresent());
            assertEquals(0, CurrencyConfig.money("2000").compareTo(
                    store.find("S1", 2026, AcademicTerm.TERM_1).get().getClosingBalance()),
                    "Closing balance should match the value passed to constructor");
        }

        @Test
        @DisplayName("Closing balance computation helper")
        void closingBalance() {
            java.math.BigDecimal bal = StudentTermBalance.computeClosingBalance(
                    CurrencyConfig.money("2000"), CurrencyConfig.money("10000"), CurrencyConfig.money("5000"));
            assertEquals(0, CurrencyConfig.money("7000").compareTo(bal));
        }

        @Test
        @DisplayName("findByYear returns all students for that year")
        void findByYear() {
            var store = StudentTermBalanceStore.getInstance();
            store.add(new StudentTermBalance("S1", 2026, AcademicTerm.TERM_1,
                    CurrencyConfig.money("1000"), CurrencyConfig.zero(),
                    CurrencyConfig.money("500"), CurrencyConfig.money("500")));
            store.add(new StudentTermBalance("S2", 2026, AcademicTerm.TERM_1,
                    CurrencyConfig.money("2000"), CurrencyConfig.zero(),
                    CurrencyConfig.money("1000"), CurrencyConfig.money("1000")));
            store.add(new StudentTermBalance("S3", 2025, AcademicTerm.TERM_1,
                    CurrencyConfig.money("1000"), CurrencyConfig.zero(),
                    CurrencyConfig.money("1000"), CurrencyConfig.zero()));
            assertEquals(2, store.findByYear(2026).size());
            assertEquals(1, store.findByYear(2025).size());
        }
    }

    @Nested
    @DisplayName("Alumni and Deleted Student Queries")
    class QueryTests {

        @Test
        @DisplayName("alumni() returns completed and graduated students")
        void alumniList() {
            StudentService svc = new StudentService();
            Student s1 = createStudent("ADM-A1", 2026, 4);
            s1.setStatus(StudentStatus.COMPLETED);
            s1.setLifecycleStatus("COMPLETED");
            Student s2 = createStudent("ADM-A2", 2026, 4);
            s2.setStatus(StudentStatus.GRADUATED);
            s2.setLifecycleStatus("GRADUATED");
            Student s3 = createStudent("ADM-A3", 2026, 4);

            List<Student> alumni = svc.alumni();
            assertEquals(2, alumni.size());
        }

        @Test
        @DisplayName("activeCount excludes soft-deleted students")
        void activeCountExcludesDeleted() {
            StudentService svc = new StudentService();
            Student s1 = createStudent("ADM-C1", 2026, 4);
            createStudent("ADM-C2", 2026, 4);
            svc.deleteToRecycleBin(List.of(s1), "Gone");
            assertEquals(1, svc.activeCount());
        }
    }
}
