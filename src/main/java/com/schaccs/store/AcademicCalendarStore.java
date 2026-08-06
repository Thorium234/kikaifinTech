package com.schaccs.store;

import com.schaccs.model.school.TermPeriod;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.util.Optional;

/**
 * In-memory store for the academic calendar periods (Term | From | To).
 */
public final class AcademicCalendarStore {

    private static final AcademicCalendarStore INSTANCE = new AcademicCalendarStore();

    private final ObservableList<TermPeriod> periods = FXCollections.observableArrayList();

    private AcademicCalendarStore() {
    }

    public static AcademicCalendarStore getInstance() {
        return INSTANCE;
    }

    public ObservableList<TermPeriod> getPeriods() {
        return periods;
    }

    public synchronized void add(TermPeriod period) {
        periods.add(period);
    }

    public synchronized void remove(TermPeriod period) {
        periods.remove(period);
    }

    public Optional<TermPeriod> findById(String id) {
        return periods.stream().filter(p -> p.getId().equals(id)).findFirst();
    }

    public synchronized void clear() {
        periods.clear();
    }
}
