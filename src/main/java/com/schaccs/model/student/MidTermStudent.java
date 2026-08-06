package com.schaccs.model.student;

import com.schaccs.config.CurrencyConfig;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * A mid-term enrollment record: a student admitted part-way through the current
 * term. The toggle decides whether the remaining part of the current term is
 * billed (custom mid-term fee, posted to the student fee ledger under the
 * MIDTERM votehead); from the next term onward the full standard tuition
 * applies automatically through the end-of-term transition.
 */
public class MidTermStudent {

    public static final String MIDTERM_CODE = "MIDTERM";

    private final String id;
    private final StringProperty studentId = new SimpleStringProperty();
    private final StringProperty admissionNumber = new SimpleStringProperty();
    private final StringProperty name = new SimpleStringProperty();
    private final ObjectProperty<LocalDate> dateJoined = new SimpleObjectProperty<>();
    private final BooleanProperty chargeCurrentTerm = new SimpleBooleanProperty(false);
    private final ObjectProperty<BigDecimal> midTermFee = new SimpleObjectProperty<>(CurrencyConfig.zero());
    private final StringProperty status = new SimpleStringProperty("Active");

    public MidTermStudent() {
        this.id = UUID.randomUUID().toString();
    }

    private MidTermStudent(String id) {
        this.id = id != null ? id : UUID.randomUUID().toString();
    }

    public static MidTermStudent forStudent(Student student, LocalDate dateJoined,
                                            boolean chargeCurrentTerm, BigDecimal midTermFee) {
        MidTermStudent e = new MidTermStudent();
        e.studentId.set(student.getId());
        e.admissionNumber.set(student.getAdmissionNumber());
        e.name.set(student.getName());
        e.dateJoined.set(dateJoined);
        e.chargeCurrentTerm.set(chargeCurrentTerm);
        e.midTermFee.set(chargeCurrentTerm ? CurrencyConfig.money(midTermFee) : CurrencyConfig.zero());
        e.status.set(student.getStatus() != null ? student.getStatus().getDisplayName() : "Active");
        return e;
    }

    /** Restore a mid-term enrollment with a known id (persistence). */
    public static MidTermStudent withId(String id) {
        return new MidTermStudent(id);
    }

    public String getId() {
        return id;
    }

    public String getStudentId() {
        return studentId.get();
    }

    public void setStudentId(String value) {
        studentId.set(value);
    }

    public StringProperty studentIdProperty() {
        return studentId;
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

    public LocalDate getDateJoined() {
        return dateJoined.get();
    }

    public void setDateJoined(LocalDate value) {
        dateJoined.set(value);
    }

    public ObjectProperty<LocalDate> dateJoinedProperty() {
        return dateJoined;
    }

    public boolean isChargeCurrentTerm() {
        return chargeCurrentTerm.get();
    }

    public void setChargeCurrentTerm(boolean value) {
        chargeCurrentTerm.set(value);
    }

    public BooleanProperty chargeCurrentTermProperty() {
        return chargeCurrentTerm;
    }

    public BigDecimal getMidTermFee() {
        return midTermFee.get();
    }

    public void setMidTermFee(BigDecimal value) {
        midTermFee.set(CurrencyConfig.money(value));
    }

    public ObjectProperty<BigDecimal> midTermFeeProperty() {
        return midTermFee;
    }

    public String getStatus() {
        return status.get();
    }

    public void setStatus(String value) {
        status.set(value);
    }

    public StringProperty statusProperty() {
        return status;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MidTermStudent that)) return false;
        return Objects.equals(id, that.id);
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
