package com.schaccs.model.student;

import com.schaccs.config.CurrencyConfig;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Snapshot of a student's fee position for UI tables and reports.
 */
public class StudentBalance {

    private final StringProperty studentId = new SimpleStringProperty();
    private final StringProperty admissionNumber = new SimpleStringProperty();
    private final StringProperty studentName = new SimpleStringProperty();
    private final StringProperty classLabel = new SimpleStringProperty();
    private final StringProperty formClass = new SimpleStringProperty();
    private final StringProperty stream = new SimpleStringProperty();
    private final ObjectProperty<BigDecimal> totalCharged = new SimpleObjectProperty<>(CurrencyConfig.zero());
    private final ObjectProperty<BigDecimal> totalPaid = new SimpleObjectProperty<>(CurrencyConfig.zero());
    private final ObjectProperty<BigDecimal> arrears = new SimpleObjectProperty<>(CurrencyConfig.zero());
    private final ObjectProperty<BigDecimal> balance = new SimpleObjectProperty<>(CurrencyConfig.zero());

    public StudentBalance() {
    }

    public StudentBalance(Student student, BigDecimal charged, BigDecimal paid, BigDecimal arrears) {
        this(student, charged, paid, arrears, charged.add(arrears).subtract(paid));
    }

    public StudentBalance(Student student, BigDecimal charged, BigDecimal paid, BigDecimal arrears, BigDecimal balance) {
        this.studentId.set(student.getId());
        this.admissionNumber.set(student.getAdmissionNumber());
        this.studentName.set(student.getName());
        this.classLabel.set(student.getClassLabel());
        this.formClass.set(student.getFormClass());
        this.stream.set(student.getStream());
        this.totalCharged.set(CurrencyConfig.money(charged));
        this.totalPaid.set(CurrencyConfig.money(paid));
        this.arrears.set(CurrencyConfig.money(arrears));
        this.balance.set(CurrencyConfig.money(balance));
    }

    public String getStudentId() {
        return studentId.get();
    }

    public void setStudentId(String v) {
        studentId.set(v);
    }

    public StringProperty studentIdProperty() {
        return studentId;
    }

    public String getAdmissionNumber() {
        return admissionNumber.get();
    }

    public void setAdmissionNumber(String v) {
        admissionNumber.set(v);
    }

    public StringProperty admissionNumberProperty() {
        return admissionNumber;
    }

    public String getStudentName() {
        return studentName.get();
    }

    public void setStudentName(String v) {
        studentName.set(v);
    }

    public StringProperty studentNameProperty() {
        return studentName;
    }

    public String getClassLabel() {
        return classLabel.get();
    }

    public void setClassLabel(String v) {
        classLabel.set(v);
    }

    public StringProperty classLabelProperty() {
        return classLabel;
    }

    public String getFormClass() {
        return formClass.get();
    }

    public void setFormClass(String v) {
        formClass.set(v);
    }

    public StringProperty formClassProperty() {
        return formClass;
    }

    public String getStream() {
        return stream.get();
    }

    public void setStream(String v) {
        stream.set(v);
    }

    public StringProperty streamProperty() {
        return stream;
    }

    public BigDecimal getTotalCharged() {
        return totalCharged.get();
    }

    public void setTotalCharged(BigDecimal v) {
        totalCharged.set(CurrencyConfig.money(v));
    }

    public ObjectProperty<BigDecimal> totalChargedProperty() {
        return totalCharged;
    }

    public BigDecimal getTotalPaid() {
        return totalPaid.get();
    }

    public void setTotalPaid(BigDecimal v) {
        totalPaid.set(CurrencyConfig.money(v));
    }

    public ObjectProperty<BigDecimal> totalPaidProperty() {
        return totalPaid;
    }

    public BigDecimal getArrears() {
        return arrears.get();
    }

    public void setArrears(BigDecimal v) {
        arrears.set(CurrencyConfig.money(v));
    }

    public ObjectProperty<BigDecimal> arrearsProperty() {
        return arrears;
    }

    public BigDecimal getBalance() {
        return balance.get();
    }

    public void setBalance(BigDecimal v) {
        balance.set(CurrencyConfig.money(v));
    }

    public ObjectProperty<BigDecimal> balanceProperty() {
        return balance;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof StudentBalance that)) return false;
        return Objects.equals(studentId.get(), that.studentId.get());
    }

    @Override
    public int hashCode() {
        return Objects.hash(studentId.get());
    }
}
