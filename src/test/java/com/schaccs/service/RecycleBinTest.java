package com.schaccs.service;

import com.schaccs.config.CurrencyConfig;
import com.schaccs.enums.BoardingStatus;
import com.schaccs.enums.StudentStatus;
import com.schaccs.model.student.DeletedStudent;
import com.schaccs.model.student.Student;
import com.schaccs.model.student.StudentFeeLedger;
import com.schaccs.repository.PersistenceService;
import com.schaccs.service.student.StudentService;
import com.schaccs.store.RecycleBinStore;
import com.schaccs.store.StudentStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Recycle bin: deleting students moves them out of the registry (deallocating
 * their fee ledger from the school financial records) and into the recycle bin,
 * from where they can be restored or purged permanently.
 */
class RecycleBinTest {

    private StudentService service;

    @BeforeEach
    void setUp() {
        PersistenceService.getInstance().clearAll();
        service = new StudentService();
    }

    @AfterEach
    void tearDown() {
        PersistenceService.getInstance().clearAll();
    }

    private Student createStudent(String adm) {
        Student s = new Student();
        s.setAdmissionNumber(adm);
        s.setName("Student " + adm);
        s.setFormClass("Form 1");
        s.setStream("A");
        s.setBoardingStatus(BoardingStatus.BOARDING);
        s.setStatus(StudentStatus.ACTIVE);
        s.setAcademicYear(2026);
        StudentStore.getInstance().add(s);
        return s;
    }

    @Test
    @DisplayName("Delete moves the student to the recycle bin and removes them from the registry")
    void deleteMovesStudentToRecycleBinAndRemovesFromRegistry() {
        Student s = createStudent("ADM-1");
        assertTrue(StudentStore.getInstance().findById(s.getId()).isPresent());

        service.deleteToRecycleBin(List.of(s));

        assertTrue(StudentStore.getInstance().findById(s.getId()).isEmpty());
        assertTrue(RecycleBinStore.getInstance().findById(s.getId()).isPresent(),
                "The deleted student lands in the recycle bin");
    }

    @Test
    @DisplayName("A student with a paid fee ledger can be deleted (financials deallocated)")
    void deleteDeallocatesFinancialRecords() {
        Student s = createStudent("ADM-2");
        StudentFeeLedger ledger = StudentStore.getInstance().getLedger(s.getId());
        ledger.charge("TUITION", CurrencyConfig.money("5000"));
        ledger.pay("TUITION", CurrencyConfig.money("2000"));

        service.deleteToRecycleBin(List.of(s));

        assertTrue(StudentStore.getInstance().findById(s.getId()).isEmpty(),
                "Students with financial records are not blocked from deletion");
        assertTrue(RecycleBinStore.getInstance().findById(s.getId()).isPresent());
    }

    @Test
    @DisplayName("Deleting several students at once moves them all to the recycle bin")
    void deleteBatchMovesAll() {
        Student a = createStudent("ADM-B1");
        Student b = createStudent("ADM-B2");

        service.deleteToRecycleBin(List.of(a, b));

        assertEquals(2, RecycleBinStore.getInstance().getItems().size());
        assertTrue(StudentStore.getInstance().getStudents().isEmpty());
    }

    @Test
    @DisplayName("Restore re-adds the student with the same id and clears the recycle bin")
    void restoreReAddsStudentWithSameId() {
        Student s = createStudent("ADM-3");
        String id = s.getId();
        service.deleteToRecycleBin(List.of(s));
        DeletedStudent deleted = RecycleBinStore.getInstance().findById(id).orElseThrow();

        List<String> errors = service.restore(List.of(deleted));

        assertTrue(errors.isEmpty());
        assertTrue(RecycleBinStore.getInstance().findById(id).isEmpty());
        Student restored = StudentStore.getInstance().findById(id).orElseThrow();
        assertEquals("ADM-3", restored.getAdmissionNumber());
        assertEquals("Student ADM-3", restored.getName());
        assertEquals(id, restored.getId(), "The original id is preserved so references relink");
    }

    @Test
    @DisplayName("Restore reports a conflict when the admission number is already in use")
    void restoreReportsConflictWhenAdmissionNumberReused() {
        Student s = createStudent("ADM-4");
        service.deleteToRecycleBin(List.of(s));
        DeletedStudent deleted = RecycleBinStore.getInstance().findById(s.getId()).orElseThrow();
        createStudent("ADM-4");

        List<String> errors = service.restore(List.of(deleted));

        assertFalse(errors.isEmpty());
        assertTrue(RecycleBinStore.getInstance().findById(s.getId()).isPresent(),
                "The blocked record stays in the recycle bin");
    }

    @Test
    @DisplayName("Purge permanently removes the record from the recycle bin")
    void purgeRemovesPermanently() {
        Student s = createStudent("ADM-5");
        service.deleteToRecycleBin(List.of(s));
        DeletedStudent deleted = RecycleBinStore.getInstance().findById(s.getId()).orElseThrow();

        service.purge(List.of(deleted));

        assertTrue(RecycleBinStore.getInstance().findById(s.getId()).isEmpty());
    }
}
