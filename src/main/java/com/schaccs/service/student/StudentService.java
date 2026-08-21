package com.schaccs.service.student;

import com.schaccs.model.student.Student;
import com.schaccs.model.student.DeletedStudent;
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
        student.setStatus(com.schaccs.enums.StudentStatus.INACTIVE);
        PersistenceService.getInstance().saveAll();
    }

    /**
     * Move students to the recycle bin and deallocate them from the financial
     * records: the fee ledger (charges, payments, arrears, advance) is removed so
     * they no longer appear in student fee/arrears reports. Historical receipts
     * and ledger transactions are kept as financial records.
     */
    public void deleteToRecycleBin(List<Student> students) {
        RoleGuard.requireFullAccess();
        for (Student s : students) {
            RecycleBinStore.getInstance().add(DeletedStudent.from(s));
            store.remove(s);
        }
        PersistenceService.getInstance().saveAll();
    }

    /**
     * Restore deleted students back into the registry (same ids, fresh fee
     * ledger). Returns errors for records that could not be restored, e.g. when
     * the admission number is already in use again.
     */
    public List<String> restore(List<DeletedStudent> deleted) {
        List<String> errors = new ArrayList<>();
        for (DeletedStudent d : deleted) {
            try {
                store.add(d.toStudent());
                RecycleBinStore.getInstance().remove(d);
            } catch (IllegalArgumentException e) {
                errors.add(d.getAdmissionNumber() + ": " + e.getMessage());
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
                .filter(s -> s.getStatus() == com.schaccs.enums.StudentStatus.ACTIVE)
                .count();
    }
}
