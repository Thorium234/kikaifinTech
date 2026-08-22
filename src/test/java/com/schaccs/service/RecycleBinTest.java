package com.schaccs.service;

import com.schaccs.config.AppConfig;
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
 * Recycle bin: soft-deleting students marks them as deleted with a reason,
 * snapshots them into the recycle bin for audit traceability, and keeps all
 * financial records intact. Students with outstanding balances cannot be
 * soft-deleted.
 */
class RecycleBinTest {

    private StudentService service;

    @BeforeEach
    void setUp() {
        PersistenceService.getInstance().clearAll();
        AppConfig.getInstance().setCurrentUserRole("PRINCIPAL");
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
    @DisplayName("Delete marks student as soft-deleted and creates recycle bin snapshot")
    void deleteMarksStudentAsSoftDeletedAndCreatesSnapshot() {
        Student s = createStudent("ADM-1");
        assertTrue(StudentStore.getInstance().findById(s.getId()).isPresent());

        List<String> errors = service.deleteToRecycleBin(List.of(s), "Transferred to another school");

        assertTrue(errors.isEmpty());
        assertTrue(RecycleBinStore.getInstance().findById(s.getId()).isPresent(),
                "The deleted student lands in the recycle bin");
        Student fromStore = StudentStore.getInstance().findById(s.getId()).orElseThrow();
        assertTrue(fromStore.isDeleted(), "Student should be marked as soft-deleted");
        assertEquals("WITHDRAWN", fromStore.getLifecycleStatus());
        assertEquals("Transferred to another school", fromStore.getDeletionReason());
    }

    @Test
    @DisplayName("A student with a fully-paid fee ledger can be soft-deleted")
    void deleteDeallocatesFinancialRecords() {
        Student s = createStudent("ADM-2");
        StudentFeeLedger ledger = StudentStore.getInstance().getLedger(s.getId());
        ledger.charge("TUITION", CurrencyConfig.money("5000"));
        ledger.pay("TUITION", CurrencyConfig.money("5000"));

        List<String> errors = service.deleteToRecycleBin(List.of(s), "Graduated");

        assertTrue(errors.isEmpty(),
                "Students with zero balance can be soft-deleted");
        assertTrue(RecycleBinStore.getInstance().findById(s.getId()).isPresent());
        assertTrue(s.isDeleted());
    }

    @Test
    @DisplayName("A student with outstanding balance cannot be soft-deleted")
    void deleteBlockedForOutstandingBalance() {
        Student s = createStudent("ADM-2B");
        StudentFeeLedger ledger = StudentStore.getInstance().getLedger(s.getId());
        ledger.charge("TUITION", CurrencyConfig.money("5000"));
        ledger.pay("TUITION", CurrencyConfig.money("2000"));

        List<String> errors = service.deleteToRecycleBin(List.of(s), "Withdrawing");

        assertFalse(errors.isEmpty(), "Should report error for outstanding balance");
        assertFalse(s.isDeleted(), "Student should NOT be marked as deleted");
        assertTrue(RecycleBinStore.getInstance().findById(s.getId()).isEmpty(),
                "Student should NOT be in the recycle bin");
    }

    @Test
    @DisplayName("Deleting several students at once marks them all as deleted")
    void deleteBatchMarksAll() {
        Student a = createStudent("ADM-B1");
        Student b = createStudent("ADM-B2");

        List<String> errors = service.deleteToRecycleBin(List.of(a, b), "Batch removal");

        assertTrue(errors.isEmpty());
        assertEquals(2, RecycleBinStore.getInstance().getItems().size());
        assertTrue(a.isDeleted());
        assertTrue(b.isDeleted());
    }

    @Test
    @DisplayName("Restore re-adds the student with the same id and clears the recycle bin")
    void restoreReAddsStudentWithSameId() {
        Student s = createStudent("ADM-3");
        String id = s.getId();
        service.deleteToRecycleBin(List.of(s), "Temp leave");
        DeletedStudent deleted = RecycleBinStore.getInstance().findById(id).orElseThrow();

        List<String> errors = service.restore(List.of(deleted));

        assertTrue(errors.isEmpty());
        assertTrue(RecycleBinStore.getInstance().findById(id).isEmpty());
        Student restored = StudentStore.getInstance().findById(id).orElseThrow();
        assertEquals("ADM-3", restored.getAdmissionNumber());
        assertEquals("Student ADM-3", restored.getName());
        assertEquals(id, restored.getId(), "The original id is preserved so references relink");
        assertFalse(restored.isDeleted(), "Restored student should not be marked as deleted");
        assertEquals("ACTIVE", restored.getLifecycleStatus());
    }

    @Test
    @DisplayName("Restore reports a conflict when the admission number is already in use by an active student")
    void restoreReportsConflictWhenAdmissionNumberReused() {
        Student s = createStudent("ADM-4");
        String originalId = s.getId();
        service.deleteToRecycleBin(List.of(s), "Leaving");
        DeletedStudent deleted = RecycleBinStore.getInstance().findById(originalId).orElseThrow();

        // Simulate a new student getting the same admission number after the original was removed
        // by removing the soft-deleted student from the store first, then adding a new one
        StudentStore.getInstance().remove(s);
        Student newStudent = createStudent("ADM-4");
        assertNotEquals(originalId, newStudent.getId(), "Different student, different id");

        List<String> errors = service.restore(List.of(deleted));

        assertFalse(errors.isEmpty());
        assertTrue(RecycleBinStore.getInstance().findById(originalId).isPresent(),
                "The blocked record stays in the recycle bin");
    }

    @Test
    @DisplayName("Purge permanently removes the record from the recycle bin")
    void purgeRemovesPermanently() {
        Student s = createStudent("ADM-5");
        service.deleteToRecycleBin(List.of(s), "Purging");
        DeletedStudent deleted = RecycleBinStore.getInstance().findById(s.getId()).orElseThrow();

        service.purge(List.of(deleted));

        assertTrue(RecycleBinStore.getInstance().findById(s.getId()).isEmpty());
    }
}
