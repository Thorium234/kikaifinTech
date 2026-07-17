package com.schaccs.model.finance;

import com.schaccs.enums.AccountType;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

public class Votehead {

    private final String id;
    private final StringProperty code = new SimpleStringProperty();
    private final StringProperty name = new SimpleStringProperty();
    private final ObjectProperty<AccountType> accountType = new SimpleObjectProperty<>(AccountType.SCHOOL_FUND);
    private final IntegerProperty priority = new SimpleIntegerProperty(100);
    private final BooleanProperty active = new SimpleBooleanProperty(true);
    private final ObjectProperty<BigDecimal> annualBudget = new SimpleObjectProperty<>(BigDecimal.ZERO);
    private final ObjectProperty<BigDecimal> termlyBudget = new SimpleObjectProperty<>(BigDecimal.ZERO);

    public Votehead() {
        this.id = UUID.randomUUID().toString();
    }

    public Votehead(String code, String name, AccountType accountType, int priority) {
        this();
        this.code.set(code);
        this.name.set(name);
        this.accountType.set(accountType);
        this.priority.set(priority);
        this.annualBudget.set(BigDecimal.ZERO);
        this.termlyBudget.set(BigDecimal.ZERO);
    }

    public String getId() {
        return id;
    }

    public String getCode() {
        return code.get();
    }

    public void setCode(String v) {
        code.set(v);
    }

    public StringProperty codeProperty() {
        return code;
    }

    public String getName() {
        return name.get();
    }

    public void setName(String v) {
        name.set(v);
    }

    public StringProperty nameProperty() {
        return name;
    }

    public AccountType getAccountType() {
        return accountType.get();
    }

    public void setAccountType(AccountType v) {
        accountType.set(v);
    }

    public ObjectProperty<AccountType> accountTypeProperty() {
        return accountType;
    }

    public int getPriority() {
        return priority.get();
    }

    public void setPriority(int v) {
        priority.set(v);
    }

    public IntegerProperty priorityProperty() {
        return priority;
    }

    public boolean isActive() {
        return active.get();
    }

    public void setActive(boolean v) {
        active.set(v);
    }

    public BooleanProperty activeProperty() {
        return active;
    }

    public BigDecimal getAnnualBudget() {
        return annualBudget.get();
    }

    public void setAnnualBudget(BigDecimal v) {
        annualBudget.set(v);
    }

    public ObjectProperty<BigDecimal> annualBudgetProperty() {
        return annualBudget;
    }

    public BigDecimal getTermlyBudget() {
        return termlyBudget.get();
    }

    public void setTermlyBudget(BigDecimal v) {
        termlyBudget.set(v);
    }

    public ObjectProperty<BigDecimal> termlyBudgetProperty() {
        return termlyBudget;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Votehead that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return getName();
    }
}
