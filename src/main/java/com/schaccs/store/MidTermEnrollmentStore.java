package com.schaccs.store;

import com.schaccs.model.student.MidTermStudent;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.util.Optional;

/**
 * In-memory store for mid-term enrollment records.
 */
public final class MidTermEnrollmentStore {

    private static final MidTermEnrollmentStore INSTANCE = new MidTermEnrollmentStore();

    private final ObservableList<MidTermStudent> enrollments = FXCollections.observableArrayList();

    private MidTermEnrollmentStore() {
    }

    public static MidTermEnrollmentStore getInstance() {
        return INSTANCE;
    }

    public ObservableList<MidTermStudent> getEnrollments() {
        return enrollments;
    }

    public synchronized void add(MidTermStudent enrollment) {
        enrollments.add(enrollment);
    }

    public synchronized void remove(MidTermStudent enrollment) {
        enrollments.remove(enrollment);
    }

    public Optional<MidTermStudent> findById(String id) {
        return enrollments.stream().filter(e -> e.getId().equals(id)).findFirst();
    }

    public Optional<MidTermStudent> findByStudentId(String studentId) {
        return enrollments.stream()
                .filter(e -> e.getStudentId() != null && e.getStudentId().equals(studentId))
                .findFirst();
    }

    public synchronized void clear() {
        enrollments.clear();
    }
}
