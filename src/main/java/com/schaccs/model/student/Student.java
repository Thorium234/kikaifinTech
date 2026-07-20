package com.schaccs.model.student;

import com.schaccs.enums.BoardingStatus;
import com.schaccs.enums.StudentStatus;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import java.util.Objects;
import java.util.UUID;

public class Student {

    private final String id;
    private final StringProperty admissionNumber = new SimpleStringProperty();
    private final StringProperty upi = new SimpleStringProperty();
    private final StringProperty name = new SimpleStringProperty();
    private final StringProperty gender = new SimpleStringProperty();
    private final StringProperty formClass = new SimpleStringProperty();
    private final StringProperty stream = new SimpleStringProperty();
    private final ObjectProperty<BoardingStatus> boardingStatus = new SimpleObjectProperty<>(BoardingStatus.BOARDING);
    private final StringProperty parentName = new SimpleStringProperty();
    private final StringProperty guardianPhone = new SimpleStringProperty();
    private final StringProperty guardianId = new SimpleStringProperty();
    private final StringProperty guardianKey = new SimpleStringProperty();
    private final StringProperty phone = new SimpleStringProperty();
    private final ObjectProperty<Integer> yearOfAdmission = new SimpleObjectProperty<>(2026);
    private final ObjectProperty<Integer> academicYear = new SimpleObjectProperty<>(2026);
    private final ObjectProperty<StudentStatus> status = new SimpleObjectProperty<>(StudentStatus.ACTIVE);
    private final StringProperty avatarPath = new SimpleStringProperty();

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

    public String getUpi() {
        return upi.get();
    }

    public void setUpi(String value) {
        upi.set(value);
    }

    public StringProperty upiProperty() {
        return upi;
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

    public String getGuardianPhone() {
        return guardianPhone.get();
    }

    public void setGuardianPhone(String value) {
        guardianPhone.set(value);
    }

    public StringProperty guardianPhoneProperty() {
        return guardianPhone;
    }

    public String getGuardianId() {
        return guardianId.get();
    }

    public void setGuardianId(String value) {
        guardianId.set(value);
    }

    public StringProperty guardianIdProperty() {
        return guardianId;
    }

    /** Shared key linking siblings/children of the same guardian for multi-child discounts. */
    public String getGuardianKey() {
        return guardianKey.get();
    }

    public void setGuardianKey(String value) {
        guardianKey.set(value);
    }

    public StringProperty guardianKeyProperty() {
        return guardianKey;
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

    public boolean matchesSearch(String query) {
        if (query == null || query.isBlank()) {
            return true;
        }
        String q = query.trim().toLowerCase();
        return contains(getAdmissionNumber(), q)
                || contains(getName(), q)
                || contains(getClassLabel(), q)
                || contains(getPhone(), q)
                || contains(getParentName(), q)
                || contains(getGuardianPhone(), q)
                || contains(getGuardianId(), q);
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
