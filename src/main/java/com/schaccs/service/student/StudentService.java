package com.schaccs.service.student;

import com.schaccs.config.CurrencyConfig;
import com.schaccs.enums.StudentStatus;
import com.schaccs.model.student.Student;
import com.schaccs.model.student.DeletedStudent;
import com.schaccs.model.student.StudentFeeLedger;
import com.schaccs.repository.PersistenceService;
import com.schaccs.store.RecycleBinStore;
import com.schaccs.store.StudentStore;
import com.schaccs.util.RoleGuard;
import com.schaccs.validation.StudentValidator;
import javafx.collections.ObservableList;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class StudentService {

    private final StudentStore store;
    private final StudentValidator validator;

    public StudentService() {
        this(StudentStore.getInstance(), new StudentValidator());
    }

    public StudentService(StudentStore store, StudentValidator validator) {
        this.store = store;
        this.validator = validator;
    }

    public ObservableList<Student> getAll() {
        return store.getStudents();
    }

    public ObservableList<Student> search(String query) {
        return store.search(query);
    }

    public Optional<Student> findByAdmission(String admissionNumber) {
        return store.findByAdmissionNumber(admissionNumber);
    }

    public List<String> addStudent(Student student) {
        RoleGuard.requireDataEntry();
        List<String> errors = validator.validate(student, true);
        if (errors.isEmpty()) {
            try {
                store.add(student);
                PersistenceService.getInstance().saveAll();
            } catch (IllegalArgumentException e) {
                errors.add(e.getMessage());
            }
        }
        return errors;
    }

    public List<String> updateStudent(Student student) {
        RoleGuard.requireDataEntry();
        List<String> errors = validator.validate(student, false);
        if (errors.isEmpty()) {
            store.findByAdmissionNumber(student.getAdmissionNumber()).ifPresent(other -> {
                if (!other.getId().equals(student.getId())) {
                    errors.add("Admission number already used by another student: " + student.getAdmissionNumber());
                }
            });
        }
        if (errors.isEmpty()) {
            PersistenceService.getInstance().saveAll();
        }
        return errors;
    }

    public void markInactive(Student student) {
        student.setStatus(StudentStatus.INACTIVE);
        PersistenceService.getInstance().saveAll();
    }

    /**
     * Soft-delete students: mark them as deleted with a reason, snapshot into the
     * recycle bin for audit traceability, and keep all financial records intact.
     * Students with outstanding balances cannot be deleted.
     */
    public List<String> deleteToRecycleBin(List<Student> students, String reason) {
        RoleGuard.requireFullAccess();
        List<String> errors = new ArrayList<>();
        for (Student s : students) {
            if (hasOutstandingBalance(s)) {
                errors.add(s.getAdmissionNumber() + ": Cannot delete \u2014 outstanding balance exists. "
                        + "Clear all fees before removing this student.");
                continue;
            }
            s.markDeleted(reason);
            RecycleBinStore.getInstance().add(DeletedStudent.from(s));
        }
        PersistenceService.getInstance().saveAll();
        return errors;
    }

    /** Backward-compatible overload without explicit reason. */
    public void deleteToRecycleBin(List<Student> students) {
        deleteToRecycleBin(students, null);
    }

    /**
     * Restore soft-deleted students back into the active registry.
     * Clears deletion flags on the existing student records and removes
     * the recycle bin snapshots.
     */
    public List<String> restore(List<DeletedStudent> deleted) {
        List<String> errors = new ArrayList<>();
        for (DeletedStudent d : deleted) {
            Optional<Student> existing = store.findById(d.getId());
            if (existing.isEmpty()) {
                // Fallback: re-add from snapshot (handles edge case where student was purged from store)
                try {
                    Student restored = d.toStudent();
                    restored.clearDeleted();
                    store.add(restored);
                    RecycleBinStore.getInstance().remove(d);
                } catch (IllegalArgumentException e) {
                    errors.add(d.getAdmissionNumber() + ": " + e.getMessage());
                }
            } else {
                // Normal path: clear deleted flags on the existing student
                Student s = existing.get();
                s.clearDeleted();
                RecycleBinStore.getInstance().remove(d);
            }
        }
        PersistenceService.getInstance().saveAll();
        return errors;
    }

    /** Permanently remove deleted-student snapshots from the recycle bin. */
    public void purge(List<DeletedStudent> deleted) {
        for (DeletedStudent d : deleted) {
            RecycleBinStore.getInstance().remove(d);
        }
        PersistenceService.getInstance().saveAll();
    }

    public long activeCount() {
        return store.getStudents().stream()
                .filter(s -> s.getStatus() == StudentStatus.ACTIVE && !s.isDeleted())
                .count();
    }

    /**
     * Students whose lifecycle status indicates graduation or completion.
     */
    public List<Student> alumni() {
        return store.getStudents().stream()
                .filter(s -> !s.isDeleted()
                        && (s.getStatus() == StudentStatus.COMPLETED
                            || s.getStatus() == StudentStatus.GRADUATED))
                .toList();
    }

    /**
     * Students marked as soft-deleted (visible only in recycle bin views).
     */
    public List<Student> deletedStudents() {
        return store.getStudents().stream()
                .filter(Student::isDeleted)
                .toList();
    }

    /**
     * Check if a student has any outstanding financial balance that would prevent
     * deletion. A student with non-zero ledger balance, arrears, or advance cannot
     * be soft-deleted.
     */
    private boolean hasOutstandingBalance(Student student) {
        StudentFeeLedger ledger = store.getLedger(student.getId());
        return ledger.getBalance().compareTo(CurrencyConfig.zero()) != 0
                || ledger.getArrears().compareTo(CurrencyConfig.zero()) > 0
                || ledger.getAdvance().compareTo(CurrencyConfig.zero()) > 0;
    }
}
