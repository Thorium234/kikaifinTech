package com.schaccs.model.fee;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.util.Objects;
import java.util.UUID;

public class FeeStructureTemplate {

    private final String id;
    private String name;
    private final ObservableList<FeeStructureTemplateItem> items = FXCollections.observableArrayList();

    public FeeStructureTemplate() {
        this.id = UUID.randomUUID().toString();
    }

    public FeeStructureTemplate(String name) {
        this();
        this.name = name;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public ObservableList<FeeStructureTemplateItem> getItems() { return items; }
    public void addItem(FeeStructureTemplateItem item) { items.add(item); }

    @Override
    public String toString() {
        return name != null ? name : "(unnamed template)";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FeeStructureTemplate that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
