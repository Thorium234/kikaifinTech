package com.schaccs.model.school;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import java.util.Objects;
import java.util.UUID;

public class SchoolStream {

    private final String id;
    private final StringProperty name = new SimpleStringProperty();

    public SchoolStream() {
        this.id = UUID.randomUUID().toString();
    }

    public SchoolStream(String name) {
        this();
        this.name.set(name);
    }

    private SchoolStream(String id, String name) {
        this.id = id;
        this.name.set(name);
    }

    public static SchoolStream withId(String id, String name) {
        return new SchoolStream(id, name);
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
        if (!(o instanceof SchoolStream that)) return false;
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
