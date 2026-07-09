package com.schaccs.validation;

import com.schaccs.model.student.Student;
import com.schaccs.store.StudentStore;

import java.util.ArrayList;
import java.util.List;

public class StudentValidator {

    private final StudentStore studentStore;

    public StudentValidator() {
        this(StudentStore.getInstance());
    }

    public StudentValidator(StudentStore studentStore) {
        this.studentStore = studentStore;
    }

    public List<String> validate(Student student, boolean isNew) {
        List<String> errors = new ArrayList<>();
        if (student.getAdmissionNumber() == null || student.getAdmissionNumber().isBlank()) {
            errors.add("Admission number is required.");
        } else if (isNew) {
            studentStore.findByAdmissionNumber(student.getAdmissionNumber()).ifPresent(s ->
                    errors.add("Admission number already exists: " + student.getAdmissionNumber()));
        }
        if (student.getName() == null || student.getName().isBlank()) {
            errors.add("Student name is required.");
        }
        if (student.getFormClass() == null || student.getFormClass().isBlank()) {
            errors.add("Class / Form is required.");
        }
        if (student.getBoardingStatus() == null) {
            errors.add("Boarding status is required.");
        }
        return errors;
    }
}
