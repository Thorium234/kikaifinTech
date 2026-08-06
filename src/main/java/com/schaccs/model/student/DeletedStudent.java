package com.schaccs.model.student;

import com.schaccs.enums.BoardingStatus;
import com.schaccs.enums.StudentStatus;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Immutable snapshot of a student moved to the recycle bin. Keeps the original
 * id so a restore re-links any remaining references (receipts, ledger history),
 * and can be purged permanently.
 */
public class DeletedStudent {

    private final String id;
    private final String admissionNumber;
    private final String name;
    private final String gender;
    private final String formClass;
    private final String stream;
    private final BoardingStatus boardingStatus;
    private final String parentName;
    private final String phone;
    private final String avatarPath;
    private final Integer yearOfAdmission;
    private final Integer academicYear;
    private final StudentStatus status;
    private final LocalDateTime deletedAt;

    private DeletedStudent(String id, String admissionNumber, String name, String gender, String formClass,
                           String stream, BoardingStatus boardingStatus, String parentName, String phone,
                           String avatarPath, Integer yearOfAdmission, Integer academicYear,
                           StudentStatus status, LocalDateTime deletedAt) {
        this.id = id;
        this.admissionNumber = admissionNumber;
        this.name = name;
        this.gender = gender;
        this.formClass = formClass;
        this.stream = stream;
        this.boardingStatus = boardingStatus;
        this.parentName = parentName;
        this.phone = phone;
        this.avatarPath = avatarPath;
        this.yearOfAdmission = yearOfAdmission;
        this.academicYear = academicYear;
        this.status = status;
        this.deletedAt = deletedAt;
    }

    public static DeletedStudent from(Student s) {
        return new DeletedStudent(
                s.getId(),
                s.getAdmissionNumber(),
                s.getName(),
                s.getGender(),
                s.getFormClass(),
                s.getStream(),
                s.getBoardingStatus(),
                s.getParentName(),
                s.getPhone(),
                s.getAvatarPath(),
                s.getYearOfAdmission(),
                s.getAcademicYear(),
                s.getStatus(),
                LocalDateTime.now());
    }

    /** Reconstruct a snapshot from the database. */
    public static DeletedStudent restore(String id, String admissionNumber, String name, String gender,
                                         String formClass, String stream, BoardingStatus boardingStatus,
                                         String parentName, String phone, String avatarPath,
                                         Integer yearOfAdmission, Integer academicYear,
                                         StudentStatus status, LocalDateTime deletedAt) {
        return new DeletedStudent(id, admissionNumber, name, gender, formClass, stream,
                boardingStatus, parentName, phone, avatarPath, yearOfAdmission, academicYear,
                status, deletedAt);
    }

    public Student toStudent() {
        Student s = Student.withId(id);
        s.setAdmissionNumber(admissionNumber);
        s.setName(name);
        s.setGender(gender);
        s.setFormClass(formClass);
        s.setStream(stream);
        s.setBoardingStatus(boardingStatus);
        s.setParentName(parentName);
        s.setPhone(phone);
        s.setAvatarPath(avatarPath);
        if (yearOfAdmission != null) {
            s.setYearOfAdmission(yearOfAdmission);
        }
        if (academicYear != null) {
            s.setAcademicYear(academicYear);
        }
        if (status != null) {
            s.setStatus(status);
        }
        return s;
    }

    public String getId() {
        return id;
    }

    public String getAdmissionNumber() {
        return admissionNumber;
    }

    public String getName() {
        return name;
    }

    public String getGender() {
        return gender;
    }

    public String getFormClass() {
        return formClass;
    }

    public String getStream() {
        return stream;
    }

    public BoardingStatus getBoardingStatus() {
        return boardingStatus;
    }

    public String getParentName() {
        return parentName;
    }

    public String getPhone() {
        return phone;
    }

    public String getAvatarPath() {
        return avatarPath;
    }

    public Integer getYearOfAdmission() {
        return yearOfAdmission;
    }

    public Integer getAcademicYear() {
        return academicYear;
    }

    public StudentStatus getStatus() {
        return status;
    }

    public LocalDateTime getDeletedAt() {
        return deletedAt;
    }

    public String getClassLabel() {
        String fc = formClass == null ? "" : formClass;
        String st = stream == null ? "" : stream;
        if (fc.isBlank() && st.isBlank()) return "";
        if (fc.isBlank()) return st;
        if (st.isBlank()) return fc;
        return fc + " " + st;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DeletedStudent that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return admissionNumber + " — " + name;
    }
}
