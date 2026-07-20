package com.schaccs.validation;

import com.schaccs.model.student.Student;
import com.schaccs.store.StudentStore;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class StudentValidator {

    private static final Pattern KENYAN_PHONE = Pattern.compile("^(\\+254|254|0)(7\\d{8}|1\\d{8})$");
    private static final Pattern UPI = Pattern.compile("^[A-Z0-9]{8,20}$", Pattern.CASE_INSENSITIVE);

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
        if (student.getUpi() != null && !student.getUpi().isBlank()
                && !UPI.matcher(student.getUpi().trim()).matches()) {
            errors.add("UPI must be 8-20 alphanumeric characters.");
        }
        if (student.getGuardianPhone() != null && !student.getGuardianPhone().isBlank()
                && !KENYAN_PHONE.matcher(student.getGuardianPhone().replaceAll("\\s+", "")).matches()) {
            errors.add("Guardian phone must be Kenyan format (+254, 254, 07, or 01...).");
        }
        return errors;
    }
}
