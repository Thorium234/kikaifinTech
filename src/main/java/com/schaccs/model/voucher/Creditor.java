package com.schaccs.model.voucher;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import java.util.UUID;

public class Creditor {

    private final String id;
    private final StringProperty name = new SimpleStringProperty();
    private final StringProperty phone = new SimpleStringProperty();
    private final StringProperty description = new SimpleStringProperty();

    public Creditor() {
        this.id = UUID.randomUUID().toString();
    }

    private Creditor(String id) {
        this.id = id != null ? id : UUID.randomUUID().toString();
    }

    public static Creditor withId(String id) {
        return new Creditor(id);
    }

    public Creditor(String name, String phone) {
        this();
        this.name.set(name);
        this.phone.set(phone);
    }

    public String getId() {
        return id;
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

    public String getPhone() {
        return phone.get();
    }

    public void setPhone(String v) {
        phone.set(v);
    }

    public StringProperty phoneProperty() {
        return phone;
    }

    public String getDescription() {
        return description.get();
    }

    public void setDescription(String v) {
        description.set(v);
    }

    public StringProperty descriptionProperty() {
        return description;
    }

    @Override
    public String toString() {
        return getName() != null ? getName() : id;
    }
}
