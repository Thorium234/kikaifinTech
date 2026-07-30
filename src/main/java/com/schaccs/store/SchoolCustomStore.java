package com.schaccs.store;

import com.schaccs.model.school.SchoolFormClass;
import com.schaccs.model.school.SchoolStream;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.util.Optional;

public final class SchoolCustomStore {

    private static final SchoolCustomStore INSTANCE = new SchoolCustomStore();

    private final ObservableList<SchoolFormClass> formClasses = FXCollections.observableArrayList();
    private final ObservableList<SchoolStream> streams = FXCollections.observableArrayList();

    private SchoolCustomStore() {
    }

    public static SchoolCustomStore getInstance() {
        return INSTANCE;
    }

    public ObservableList<SchoolFormClass> getFormClasses() {
        return formClasses;
    }

    public ObservableList<SchoolStream> getStreams() {
        return streams;
    }

    public synchronized void addFormClass(SchoolFormClass fc) {
        formClasses.add(fc);
    }

    public synchronized void removeFormClass(SchoolFormClass fc) {
        formClasses.remove(fc);
    }

    public synchronized void addStream(SchoolStream s) {
        streams.add(s);
    }

    public synchronized void removeStream(SchoolStream s) {
        streams.remove(s);
    }

    public Optional<SchoolFormClass> findFormClassById(String id) {
        return formClasses.stream().filter(fc -> fc.getId().equals(id)).findFirst();
    }

    public Optional<SchoolStream> findStreamById(String id) {
        return streams.stream().filter(s -> s.getId().equals(id)).findFirst();
    }

    public boolean formClassNameExists(String name) {
        return formClasses.stream().anyMatch(fc -> fc.getName().equalsIgnoreCase(name.trim()));
    }

    public boolean streamNameExists(String name) {
        return streams.stream().anyMatch(s -> s.getName().equalsIgnoreCase(name.trim()));
    }

    public synchronized void clear() {
        formClasses.clear();
        streams.clear();
    }
}
