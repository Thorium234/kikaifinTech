package com.schaccs.model.school;

import com.schaccs.enums.AcademicTerm;
import com.schaccs.enums.TermStatus;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * One period of the academic calendar: the term and its start/end dates.
 * Dates are fully customizable through the Calendar workspace. The lifecycle
 * status (PLANNED/ACTIVE/ENDED) is maintained by the calendar service: only one
 * term may be ACTIVE at a time, and once a term's end date passes the service
 * flips it to ENDED, which is the trigger for unpaid balances to roll to arrears.
 */
public class TermPeriod {

    private final String id;
    private final ObjectProperty<AcademicTerm> term = new SimpleObjectProperty<>();
    private final ObjectProperty<LocalDate> from = new SimpleObjectProperty<>();
    private final ObjectProperty<LocalDate> to = new SimpleObjectProperty<>();
    private final ObjectProperty<TermStatus> status = new SimpleObjectProperty<>(TermStatus.PLANNED);

    public TermPeriod(AcademicTerm term, LocalDate from, LocalDate to) {
        this(UUID.randomUUID().toString(), term, from, to, TermStatus.PLANNED);
    }

    private TermPeriod(String id, AcademicTerm term, LocalDate from, LocalDate to, TermStatus status) {
        this.id = id;
        this.term.set(term);
        this.from.set(from);
        this.to.set(to);
        this.status.set(status);
    }

    public static TermPeriod withId(String id, AcademicTerm term, LocalDate from, LocalDate to) {
        return new TermPeriod(id, term, from, to, TermStatus.PLANNED);
    }

    public static TermPeriod withId(String id, AcademicTerm term, LocalDate from, LocalDate to, TermStatus status) {
        return new TermPeriod(id, term, from, to, status);
    }

    public String getId() {
        return id;
    }

    public AcademicTerm getTerm() {
        return term.get();
    }

    public void setTerm(AcademicTerm term) {
        this.term.set(term);
    }

    public ObjectProperty<AcademicTerm> termProperty() {
        return term;
    }

    public LocalDate getFrom() {
        return from.get();
    }

    public void setFrom(LocalDate from) {
        this.from.set(from);
    }

    public ObjectProperty<LocalDate> fromProperty() {
        return from;
    }

    public LocalDate getTo() {
        return to.get();
    }

    public void setTo(LocalDate to) {
        this.to.set(to);
    }

    public ObjectProperty<LocalDate> toProperty() {
        return to;
    }

    public TermStatus getStatus() {
        return status.get();
    }

    public void setStatus(TermStatus status) {
        this.status.set(status);
    }

    public ObjectProperty<TermStatus> statusProperty() {
        return status;
    }

    /** Calendar year this period belongs to (derived from the start date). */
    public int getYear() {
        return from.get() != null ? from.get().getYear() : 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TermPeriod that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return getTerm() != null ? getTerm().getDisplayName() : "";
    }
}
