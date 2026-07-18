package com.schaccs.service.school;

import com.schaccs.model.school.SchoolFormClass;
import com.schaccs.model.school.SchoolStream;
import com.schaccs.repository.PersistenceService;
import com.schaccs.store.SchoolCustomStore;
import javafx.collections.ObservableList;

import java.util.ArrayList;
import java.util.List;

public class SchoolCustomService {

    private final SchoolCustomStore store;

    public SchoolCustomService() {
        this(SchoolCustomStore.getInstance());
    }

    public SchoolCustomService(SchoolCustomStore store) {
        this.store = store;
    }

    public ObservableList<SchoolFormClass> getFormClasses() {
        return store.getFormClasses();
    }

    public ObservableList<SchoolStream> getStreams() {
        return store.getStreams();
    }

    public List<String> addFormClass(String name) {
        List<String> errors = new ArrayList<>();
        if (name == null || name.trim().isEmpty()) {
            errors.add("Form class name is required.");
            return errors;
        }
        if (store.formClassNameExists(name.trim())) {
            errors.add("Form class \"" + name.trim() + "\" already exists.");
            return errors;
        }
        store.addFormClass(new SchoolFormClass(name.trim()));
        PersistenceService.getInstance().saveAll();
        return errors;
    }

    public List<String> updateFormClass(SchoolFormClass fc, String newName) {
        List<String> errors = new ArrayList<>();
        if (newName == null || newName.trim().isEmpty()) {
            errors.add("Form class name is required.");
            return errors;
        }
        String trimmed = newName.trim();
        if (!fc.getName().equalsIgnoreCase(trimmed) && store.formClassNameExists(trimmed)) {
            errors.add("Form class \"" + trimmed + "\" already exists.");
            return errors;
        }
        fc.setName(trimmed);
        PersistenceService.getInstance().saveAll();
        return errors;
    }

    public void removeFormClass(SchoolFormClass fc) {
        store.removeFormClass(fc);
        PersistenceService.getInstance().saveAll();
    }

    public List<String> addStream(String name) {
        List<String> errors = new ArrayList<>();
        if (name == null || name.trim().isEmpty()) {
            errors.add("Stream name is required.");
            return errors;
        }
        if (store.streamNameExists(name.trim())) {
            errors.add("Stream \"" + name.trim() + "\" already exists.");
            return errors;
        }
        store.addStream(new SchoolStream(name.trim()));
        PersistenceService.getInstance().saveAll();
        return errors;
    }

    public List<String> updateStream(SchoolStream stream, String newName) {
        List<String> errors = new ArrayList<>();
        if (newName == null || newName.trim().isEmpty()) {
            errors.add("Stream name is required.");
            return errors;
        }
        String trimmed = newName.trim();
        if (!stream.getName().equalsIgnoreCase(trimmed) && store.streamNameExists(trimmed)) {
            errors.add("Stream \"" + trimmed + "\" already exists.");
            return errors;
        }
        stream.setName(trimmed);
        PersistenceService.getInstance().saveAll();
        return errors;
    }

    public void removeStream(SchoolStream stream) {
        store.removeStream(stream);
        PersistenceService.getInstance().saveAll();
    }
}
