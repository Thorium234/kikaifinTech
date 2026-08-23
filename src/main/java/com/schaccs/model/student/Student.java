package com.schaccs.model.student;

import com.schaccs.enums.BoardingStatus;
import com.schaccs.enums.DurationUnit;
import com.schaccs.enums.StudentStatus;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

public class Student {

    private final String id;
    private final StringProperty admissionNumber = new SimpleStringProperty();
    private final StringProperty name = new SimpleStringProperty();
    private final StringProperty gender = new SimpleStringProperty();
    private final StringProperty formClass = new SimpleStringProperty();
    private final StringProperty stream = new SimpleStringProperty();
    private final ObjectProperty<BoardingStatus> boardingStatus = new SimpleObjectProperty<>(BoardingStatus.BOARDING);
    private final StringProperty parentName = new SimpleStringProperty();
    private final StringProperty phone = new SimpleStringProperty();
    private final ObjectProperty<Integer> yearOfAdmission = new SimpleObjectProperty<>(2026);
    private final ObjectProperty<Integer> academicYear = new SimpleObjectProperty<>(2026);
    private final ObjectProperty<StudentStatus> status = new SimpleObjectProperty<>(StudentStatus.ACTIVE);
    private final StringProperty avatarPath = new SimpleStringProperty();
    private final StringProperty courseCode = new SimpleStringProperty();
    private final ObjectProperty<Integer> durationValue = new SimpleObjectProperty<>();
    private final ObjectProperty<DurationUnit> durationUnit = new SimpleObjectProperty<>();
    private final ObjectProperty<LocalDate> enrollmentDate = new SimpleObjectProperty<>();
    private final ObjectProperty<LocalDate> expectedCompletionDate = new SimpleObjectProperty<>();
    private final StringProperty lifecycleStatus = new SimpleStringProperty();
    private final ObjectProperty<Boolean> deleted = new SimpleObjectProperty<>(false);
    private final ObjectProperty<LocalDateTime> deletedAt = new SimpleObjectProperty<>();
    private final StringProperty deletionReason = new SimpleStringProperty();
    private final ObjectProperty<Integer> courseDurationYears = new SimpleObjectProperty<>(4);

    public Student() {
        this.id = UUID.randomUUID().toString();
    }

    private Student(String id) {
        this.id = id != null ? id : UUID.randomUUID().toString();
    }

    /** Restore a student with a known id (persistence). */
    public static Student withId(String id) {
        return new Student(id);
    }

    public Student(String admissionNumber, String name, String formClass, String stream,
                   BoardingStatus boardingStatus, String phone) {
        this();
        this.admissionNumber.set(admissionNumber);
        this.name.set(name);
        this.formClass.set(formClass);
        this.stream.set(stream);
        this.boardingStatus.set(boardingStatus);
        this.phone.set(phone);
    }

    public String getId() {
        return id;
    }

    public String getAdmissionNumber() {
        return admissionNumber.get();
    }

    public void setAdmissionNumber(String value) {
        admissionNumber.set(value);
    }

    public StringProperty admissionNumberProperty() {
        return admissionNumber;
    }

    public String getName() {
        return name.get();
    }

    public void setName(String value) {
        name.set(value);
    }

    public StringProperty nameProperty() {
        return name;
    }

    public String getGender() {
        return gender.get();
    }

    public void setGender(String value) {
        gender.set(value);
    }

    public StringProperty genderProperty() {
        return gender;
    }

    public String getFormClass() {
        return formClass.get();
    }

    public void setFormClass(String value) {
        formClass.set(value);
    }

    public StringProperty formClassProperty() {
        return formClass;
    }

    public String getStream() {
        return stream.get();
    }

    public void setStream(String value) {
        stream.set(value);
    }

    public StringProperty streamProperty() {
        return stream;
    }

    public String getClassLabel() {
        String fc = getFormClass() == null ? "" : getFormClass();
        String st = getStream() == null ? "" : getStream();
        if (fc.isBlank() && st.isBlank()) return "";
        if (fc.isBlank()) return st;
        if (st.isBlank()) return fc;
        return fc + " " + st;
    }

    public BoardingStatus getBoardingStatus() {
        return boardingStatus.get();
    }

    public void setBoardingStatus(BoardingStatus value) {
        boardingStatus.set(value);
    }

    public ObjectProperty<BoardingStatus> boardingStatusProperty() {
        return boardingStatus;
    }

    public String getParentName() {
        return parentName.get();
    }

    public void setParentName(String value) {
        parentName.set(value);
    }

    public StringProperty parentNameProperty() {
        return parentName;
    }

    public String getPhone() {
        return phone.get();
    }

    public void setPhone(String value) {
        phone.set(value);
    }

    public StringProperty phoneProperty() {
        return phone;
    }

    public Integer getYearOfAdmission() {
        return yearOfAdmission.get();
    }

    public void setYearOfAdmission(Integer value) {
        yearOfAdmission.set(value);
    }

    public ObjectProperty<Integer> yearOfAdmissionProperty() {
        return yearOfAdmission;
    }

    public Integer getAcademicYear() {
        return academicYear.get();
    }

    public void setAcademicYear(Integer value) {
        academicYear.set(value);
    }

    public ObjectProperty<Integer> academicYearProperty() {
        return academicYear;
    }

    public StudentStatus getStatus() {
        return status.get();
    }

    public void setStatus(StudentStatus value) {
        status.set(value);
    }

    public ObjectProperty<StudentStatus> statusProperty() {
        return status;
    }

    public String getAvatarPath() {
        return avatarPath.get();
    }

    public void setAvatarPath(String value) {
        avatarPath.set(value);
    }

    public StringProperty avatarPathProperty() {
        return avatarPath;
    }

    public String getCourseCode() {
        return courseCode.get();
    }

    public void setCourseCode(String value) {
        courseCode.set(value);
    }

    public StringProperty courseCodeProperty() {
        return courseCode;
    }

    public Integer getDurationValue() {
        return durationValue.get();
    }

    public void setDurationValue(Integer value) {
        durationValue.set(value);
    }

    public ObjectProperty<Integer> durationValueProperty() {
        return durationValue;
    }

    public DurationUnit getDurationUnit() {
        return durationUnit.get();
    }

    public void setDurationUnit(DurationUnit value) {
        durationUnit.set(value);
    }

    public ObjectProperty<DurationUnit> durationUnitProperty() {
        return durationUnit;
    }

    public LocalDate getEnrollmentDate() {
        return enrollmentDate.get();
    }

    public void setEnrollmentDate(LocalDate value) {
        enrollmentDate.set(value);
    }

    public ObjectProperty<LocalDate> enrollmentDateProperty() {
        return enrollmentDate;
    }

    public LocalDate getExpectedCompletionDate() {
        return expectedCompletionDate.get();
    }

    public void setExpectedCompletionDate(LocalDate value) {
        expectedCompletionDate.set(value);
    }

    public ObjectProperty<LocalDate> expectedCompletionDateProperty() {
        return expectedCompletionDate;
    }

    public String getLifecycleStatus() {
        return lifecycleStatus.get();
    }

    public void setLifecycleStatus(String value) {
        lifecycleStatus.set(value);
    }

    public StringProperty lifecycleStatusProperty() {
        return lifecycleStatus;
    }

    public boolean isDeleted() {
        Boolean val = deleted.get();
        return val != null && val;
    }

    public void setDeleted(boolean value) {
        deleted.set(value);
    }

    public ObjectProperty<Boolean> deletedProperty() {
        return deleted;
    }

    public LocalDateTime getDeletedAt() {
        return deletedAt.get();
    }

    public void setDeletedAt(LocalDateTime value) {
        deletedAt.set(value);
    }

    public ObjectProperty<LocalDateTime> deletedAtProperty() {
        return deletedAt;
    }

    public String getDeletionReason() {
        return deletionReason.get();
    }

    public void setDeletionReason(String value) {
        deletionReason.set(value);
    }

    public StringProperty deletionReasonProperty() {
        return deletionReason;
    }

    public Integer getCourseDurationYears() {
        return courseDurationYears.get();
    }

    public void setCourseDurationYears(Integer value) {
        courseDurationYears.set(value);
    }

    public ObjectProperty<Integer> courseDurationYearsProperty() {
        return courseDurationYears;
    }

    /**
     * Compute the expected completion year: Y_admit + D − 1 (the last calendar
     * year of the course timeline). Falls back to duration_value when
     * courseDurationYears is null/zero.
     */
    public Integer computeExpectedCompletionYear() {
        Integer admission = getYearOfAdmission();
        Integer duration = getCourseDurationYears();
        if (duration == null || duration <= 0) {
            duration = getDurationValue();
        }
        if (admission == null || duration == null || duration <= 0) {
            return null;
        }
        return admission + duration - 1;
    }

    /** First year of the cohort timeline (Y_admit), or null when unknown. */
    public Integer computeTimelineStartYear() {
        return getYearOfAdmission();
    }

    /** Mark this student as soft-deleted. */
    public void markDeleted(String reason) {
        setDeleted(true);
        setDeletedAt(LocalDateTime.now());
        if (reason != null) {
            setDeletionReason(reason);
        }
        setLifecycleStatus("WITHDRAWN");
    }

    /** Clear soft-delete flags (restore). */
    public void clearDeleted() {
        setDeleted(false);
        deletedAt.set(null);
        deletionReason.set(null);
        setLifecycleStatus("ACTIVE");
    }

    /** True when the course clock has passed the expected completion date. */
    public boolean isCourseCompleted(LocalDate today) {
        LocalDate expected = expectedCompletionDate.get();
        return expected != null && today != null && today.isAfter(expected);
    }

    public boolean matchesSearch(String query) {
        if (query == null || query.isBlank()) {
            return true;
        }
        String q = query.trim().toLowerCase();
        return contains(getAdmissionNumber(), q)
                || contains(getName(), q)
                || contains(getClassLabel(), q)
                || contains(getPhone(), q)
                || contains(getParentName(), q);
    }

    private static boolean contains(String value, String q) {
        return value != null && value.toLowerCase().contains(q);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Student student)) return false;
        return Objects.equals(id, student.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return getAdmissionNumber() + " — " + getName();
    }
}
