package com.schaccs.service.student;

import com.schaccs.model.student.Student;
import com.schaccs.store.StudentStore;
import com.schaccs.validation.StudentValidator;
import javafx.collections.ObservableList;

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
        List<String> errors = validator.validate(student, true);
        if (errors.isEmpty()) {
            store.add(student);
        }
        return errors;
    }

    public List<String> updateStudent(Student student) {
        List<String> errors = validator.validate(student, false);
        // ensure unique admission if changed
        if (errors.isEmpty()) {
            store.findByAdmissionNumber(student.getAdmissionNumber()).ifPresent(other -> {
                if (!other.getId().equals(student.getId())) {
                    errors.add("Admission number already used by another student.");
                }
            });
        }
        return errors;
    }

    public void markInactive(Student student) {
        student.setStatus(com.schaccs.enums.StudentStatus.INACTIVE);
    }

    public long activeCount() {
        return store.getStudents().stream()
                .filter(s -> s.getStatus() == com.schaccs.enums.StudentStatus.ACTIVE)
                .count();
    }
}
