package com.schaccs.validation;

import com.schaccs.model.student.Student;
import com.schaccs.store.StudentStore;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class StudentValidator {

    private static final Pattern KENYAN_PHONE = Pattern.compile("^(\\+254|254|0)(7\\d{8}|1\\d{8})$");

    /** Letters (incl. accents), spaces, dots, apostrophes and hyphens only. */
    private static final Pattern PERS_NAME = Pattern.compile("^[\\p{L} .'-]+$");

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
        } else {
            student.setAdmissionNumber(student.getAdmissionNumber().trim());
            if (isNew) {
                studentStore.findByAdmissionNumber(student.getAdmissionNumber()).ifPresent(s ->
                        errors.add("Admission number already exists: " + student.getAdmissionNumber()));
            }
        }
        if (student.getName() == null || student.getName().isBlank()) {
            errors.add("Student name is required.");
        } else if (!PERS_NAME.matcher(student.getName().trim()).matches()) {
            errors.add("Student name contains invalid characters. Use letters only "
                    + "(spaces, dots, apostrophes and hyphens are allowed).");
        }
        if (student.getFormClass() == null || student.getFormClass().isBlank()) {
            errors.add("Class / Form is required.");
        }
        if (student.getBoardingStatus() == null) {
            errors.add("Boarding status is required.");
        }
        if (student.getPhone() != null && !student.getPhone().isBlank()
                && !KENYAN_PHONE.matcher(student.getPhone().replaceAll("\\s+", "")).matches()) {
            errors.add("Phone number must be Kenyan format (+254, 254, 07, or 01...).");
        }
        return errors;
    }
}
