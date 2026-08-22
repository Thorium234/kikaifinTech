package com.schaccs.store;

import com.schaccs.enums.AcademicTerm;
import com.schaccs.model.student.StudentTermBalance;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.util.List;
import java.util.Optional;

/**
 * In-memory store for student term balance snapshots. Populated at startup
 * from the {@code student_term_balances} table and persisted on save.
 */
public final class StudentTermBalanceStore {

    private static final StudentTermBalanceStore INSTANCE = new StudentTermBalanceStore();

    private final ObservableList<StudentTermBalance> items = FXCollections.observableArrayList();

    private StudentTermBalanceStore() {
    }

    public static StudentTermBalanceStore getInstance() {
        return INSTANCE;
    }

    public ObservableList<StudentTermBalance> getItems() {
        return items;
    }

    public synchronized void add(StudentTermBalance item) {
        items.add(item);
    }

    public synchronized List<StudentTermBalance> findByStudent(String studentId) {
        return items.stream()
                .filter(b -> b.getStudentId().equals(studentId))
                .sorted((a, b) -> {
                    int yearCmp = Integer.compare(a.getAcademicYear(), b.getAcademicYear());
                    return yearCmp != 0 ? yearCmp : a.getTerm().compareTo(b.getTerm());
                })
                .toList();
    }

    public synchronized Optional<StudentTermBalance> find(String studentId, int academicYear, AcademicTerm term) {
        return items.stream()
                .filter(b -> b.getStudentId().equals(studentId)
                        && b.getAcademicYear() == academicYear
                        && b.getTerm() == term)
                .findFirst();
    }

    public synchronized List<StudentTermBalance> findByYear(int academicYear) {
        return items.stream()
                .filter(b -> b.getAcademicYear() == academicYear)
                .toList();
    }

    public synchronized void removeByStudent(String studentId) {
        items.removeIf(b -> b.getStudentId().equals(studentId));
    }

    public synchronized void clear() {
        items.clear();
    }
}
