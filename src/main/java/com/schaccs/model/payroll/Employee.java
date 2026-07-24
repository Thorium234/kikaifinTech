package com.schaccs.model.payroll;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

public class Employee {

    public enum EmploymentStatus {
        ACTIVE("Active"),
        ON_LEAVE("On Leave"),
        TERMINATED("Terminated"),
        RETIRED("Retired"),
        SUSPENDED("Suspended");

        private final String displayName;
        EmploymentStatus(String displayName) { this.displayName = displayName; }
        public String getDisplayName() { return displayName; }
        @Override public String toString() { return displayName; }
    }

    private final String id;
    private final StringProperty employeeNumber = new SimpleStringProperty();
    private final StringProperty firstName = new SimpleStringProperty();
    private final StringProperty lastName = new SimpleStringProperty();
    private final StringProperty nationalId = new SimpleStringProperty();
    private final StringProperty department = new SimpleStringProperty();
    private final StringProperty position = new SimpleStringProperty();
    private final ObjectProperty<LocalDate> employmentDate = new SimpleObjectProperty<>();
    private final ObjectProperty<EmploymentStatus> employmentStatus = new SimpleObjectProperty<>(EmploymentStatus.ACTIVE);
    private final StringProperty bankName = new SimpleStringProperty();
    private final StringProperty bankBranch = new SimpleStringProperty();
    private final StringProperty bankAccountNumber = new SimpleStringProperty();
    private final StringProperty kraPin = new SimpleStringProperty();
    private final StringProperty nssfNumber = new SimpleStringProperty();
    private final StringProperty shifNumber = new SimpleStringProperty();
    private final StringProperty phone = new SimpleStringProperty();
    private final StringProperty email = new SimpleStringProperty();
    private final StringProperty address = new SimpleStringProperty();

    public Employee() {
        this.id = UUID.randomUUID().toString();
    }

    private Employee(String id) {
        this.id = id != null ? id : UUID.randomUUID().toString();
    }

    public static Employee withId(String id) {
        return new Employee(id);
    }

    public String getId() { return id; }

    public String getEmployeeNumber() { return employeeNumber.get(); }
    public void setEmployeeNumber(String value) { employeeNumber.set(value); }
    public StringProperty employeeNumberProperty() { return employeeNumber; }

    public String getFirstName() { return firstName.get(); }
    public void setFirstName(String value) { firstName.set(value); }
    public StringProperty firstNameProperty() { return firstName; }

    public String getLastName() { return lastName.get(); }
    public void setLastName(String value) { lastName.set(value); }
    public StringProperty lastNameProperty() { return lastName; }

    public String getFullName() {
        String f = getFirstName() == null ? "" : getFirstName();
        String l = getLastName() == null ? "" : getLastName();
        return (f + " " + l).trim();
    }

    public String getNationalId() { return nationalId.get(); }
    public void setNationalId(String value) { nationalId.set(value); }
    public StringProperty nationalIdProperty() { return nationalId; }

    public String getDepartment() { return department.get(); }
    public void setDepartment(String value) { department.set(value); }
    public StringProperty departmentProperty() { return department; }

    public String getPosition() { return position.get(); }
    public void setPosition(String value) { position.set(value); }
    public StringProperty positionProperty() { return position; }

    public LocalDate getEmploymentDate() { return employmentDate.get(); }
    public void setEmploymentDate(LocalDate value) { employmentDate.set(value); }
    public ObjectProperty<LocalDate> employmentDateProperty() { return employmentDate; }

    public EmploymentStatus getEmploymentStatus() { return employmentStatus.get(); }
    public void setEmploymentStatus(EmploymentStatus value) { employmentStatus.set(value); }
    public ObjectProperty<EmploymentStatus> employmentStatusProperty() { return employmentStatus; }

    public String getBankName() { return bankName.get(); }
    public void setBankName(String value) { bankName.set(value); }
    public StringProperty bankNameProperty() { return bankName; }

    public String getBankBranch() { return bankBranch.get(); }
    public void setBankBranch(String value) { bankBranch.set(value); }
    public StringProperty bankBranchProperty() { return bankBranch; }

    public String getBankAccountNumber() { return bankAccountNumber.get(); }
    public void setBankAccountNumber(String value) { bankAccountNumber.set(value); }
    public StringProperty bankAccountNumberProperty() { return bankAccountNumber; }

    public String getKraPin() { return kraPin.get(); }
    public void setKraPin(String value) { kraPin.set(value); }
    public StringProperty kraPinProperty() { return kraPin; }

    public String getNssfNumber() { return nssfNumber.get(); }
    public void setNssfNumber(String value) { nssfNumber.set(value); }
    public StringProperty nssfNumberProperty() { return nssfNumber; }

    public String getShifNumber() { return shifNumber.get(); }
    public void setShifNumber(String value) { shifNumber.set(value); }
    public StringProperty shifNumberProperty() { return shifNumber; }

    public String getPhone() { return phone.get(); }
    public void setPhone(String value) { phone.set(value); }
    public StringProperty phoneProperty() { return phone; }

    public String getEmail() { return email.get(); }
    public void setEmail(String value) { email.set(value); }
    public StringProperty emailProperty() { return email; }

    public String getAddress() { return address.get(); }
    public void setAddress(String value) { address.set(value); }
    public StringProperty addressProperty() { return address; }

    public boolean matchesSearch(String query) {
        if (query == null || query.isBlank()) return true;
        String q = query.trim().toLowerCase();
        return contains(getEmployeeNumber(), q) || contains(getFullName(), q)
                || contains(getNationalId(), q) || contains(getDepartment(), q)
                || contains(getPosition(), q) || contains(getPhone(), q);
    }

    private static boolean contains(String value, String q) {
        return value != null && value.toLowerCase().contains(q);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Employee e)) return false;
        return Objects.equals(id, e.id);
    }

    @Override
    public int hashCode() { return Objects.hash(id); }

    @Override
    public String toString() { return getEmployeeNumber() + " — " + getFullName(); }
}
