package com.schaccs.store;

import com.schaccs.model.student.Student;
import com.schaccs.model.student.StudentFeeLedger;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public final class StudentStore {

    private static final StudentStore INSTANCE = new StudentStore();

    private final ObservableList<Student> students = FXCollections.observableArrayList();
    private final Map<String, StudentFeeLedger> ledgers = new HashMap<>();

    private StudentStore() {
    }

    public static StudentStore getInstance() {
        return INSTANCE;
    }

    public ObservableList<Student> getStudents() {
        return students;
    }

    public synchronized void add(Student student) {
        if (student.getAdmissionNumber() != null && !student.getAdmissionNumber().isBlank()) {
            findByAdmissionNumber(student.getAdmissionNumber()).ifPresent(existing -> {
                if (!existing.getId().equals(student.getId())) {
                    throw new IllegalArgumentException("Admission number already exists: " + student.getAdmissionNumber());
                }
            });
        }
        students.add(student);
        ledgers.putIfAbsent(student.getId(), new StudentFeeLedger(student.getId()));
    }

    public synchronized void remove(Student student) {
        students.remove(student);
        ledgers.remove(student.getId());
    }

    public Optional<Student> findById(String id) {
        return students.stream().filter(s -> s.getId().equals(id)).findFirst();
    }

    public Optional<Student> findByAdmissionNumber(String admissionNumber) {
        if (admissionNumber == null) {
            return Optional.empty();
        }
        String key = admissionNumber.trim();
        return students.stream()
                .filter(s -> s.getAdmissionNumber() != null && s.getAdmissionNumber().equalsIgnoreCase(key))
                .findFirst();
    }

    public ObservableList<Student> search(String query) {
        if (query == null || query.isBlank()) {
            return students;
        }
        return students.filtered(s -> s.matchesSearch(query));
    }

    public StudentFeeLedger getLedger(String studentId) {
        return ledgers.computeIfAbsent(studentId, StudentFeeLedger::new);
    }

    public Map<String, StudentFeeLedger> getLedgers() {
        return ledgers;
    }

    public synchronized void clear() {
        students.clear();
        ledgers.clear();
    }
}
