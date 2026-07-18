package com.schaccs.model.school;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import java.util.Objects;
import java.util.UUID;

public class SchoolFormClass {

    private final String id;
    private final StringProperty name = new SimpleStringProperty();

    public SchoolFormClass() {
        this.id = UUID.randomUUID().toString();
    }

    public SchoolFormClass(String name) {
        this();
        this.name.set(name);
    }

    private SchoolFormClass(String id, String name) {
        this.id = id;
        this.name.set(name);
    }

    public static SchoolFormClass withId(String id, String name) {
        return new SchoolFormClass(id, name);
    }

    public String getId() {
        return id;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SchoolFormClass that)) return false;
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
