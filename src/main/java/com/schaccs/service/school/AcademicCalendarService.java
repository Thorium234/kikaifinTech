package com.schaccs.service.school;

import com.schaccs.config.CurrencyConfig;
import com.schaccs.enums.AcademicTerm;
import com.schaccs.model.school.TermPeriod;
import com.schaccs.model.student.Student;
import com.schaccs.model.student.StudentFeeLedger;
import com.schaccs.repository.PersistenceService;
import com.schaccs.service.audit.AuditService;
import com.schaccs.store.AcademicCalendarStore;
import com.schaccs.store.StudentStore;
import javafx.collections.ObservableList;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Academic calendar: a fully customizable list of term periods (Term | From |
 * To) plus the automatic end-of-term logic. When a term's end date passes, the
 * service knows the term has ended and can roll each student's unpaid balance
 * into arrears, move them to the next term, and (after term 3) promote their
 * class into the next academic year.
 */
public class AcademicCalendarService {

    private static final Pattern FORM_PATTERN = Pattern.compile("(?i)(form\\s*)(\\d+)");
    private static final int MAX_FORM = 6;

    private final AcademicCalendarStore store;
    private final StudentStore studentStore;
    private final AuditService auditService;

    private LocalDate lastRolloverDate;

    public AcademicCalendarService() {
        this(AcademicCalendarStore.getInstance(), StudentStore.getInstance(), new AuditService());
    }

    public AcademicCalendarService(AcademicCalendarStore store, StudentStore studentStore,
                                   AuditService auditService) {
        this.store = store;
        this.studentStore = studentStore;
        this.auditService = auditService;
    }

    public ObservableList<TermPeriod> getPeriods() {
        return store.getPeriods();
    }

    // ------------------------------------------------------------------
    // CRUD (validated, persisted)
    // ------------------------------------------------------------------

    public List<String> addPeriod(AcademicTerm term, LocalDate from, LocalDate to) {
        List<String> errors = validate(term, from, to, null);
        if (!errors.isEmpty()) {
            return errors;
        }
        store.add(new TermPeriod(term, from, to));
        PersistenceService.getInstance().saveAll();
        return errors;
    }

    public List<String> updatePeriod(TermPeriod period, AcademicTerm term, LocalDate from, LocalDate to) {
        if (period == null) {
            return List.of("No period selected.");
        }
        List<String> errors = validate(term, from, to, period);
        if (!errors.isEmpty()) {
            return errors;
        }
        period.setTerm(term);
        period.setFrom(from);
        period.setTo(to);
        PersistenceService.getInstance().saveAll();
        return errors;
    }

    public void removePeriod(TermPeriod period) {
        store.remove(period);
        PersistenceService.getInstance().saveAll();
    }

    private List<String> validate(AcademicTerm term, LocalDate from, LocalDate to, TermPeriod editing) {
        List<String> errors = new ArrayList<>();
        if (term == null) {
            errors.add("Select a term.");
        }
        if (from == null) {
            errors.add("Pick a start date.");
        }
        if (to == null) {
            errors.add("Pick an end date.");
        }
        if (from != null && to != null && !from.isBefore(to)) {
            errors.add("The end date must be after the start date.");
        }
        if (errors.isEmpty()) {
            for (TermPeriod p : store.getPeriods()) {
                if (editing != null && p.getId().equals(editing.getId())) {
                    continue;
                }
                if (p.getYear() == from.getYear() && p.getTerm() == term) {
                    errors.add(term.getDisplayName() + " for " + from.getYear() + " already exists.");
                    break;
                }
                if (rangesOverlap(from, to, p.getFrom(), p.getTo())) {
                    errors.add(term.getDisplayName() + " overlaps " + p.getTerm().getDisplayName()
                            + " (" + p.getFrom() + " to " + p.getTo() + ").");
                    break;
                }
            }
        }
        return errors;
    }

    private boolean rangesOverlap(LocalDate aFrom, LocalDate aTo, LocalDate bFrom, LocalDate bTo) {
        return !aTo.isBefore(bFrom) && !bTo.isBefore(aFrom);
    }

    // ------------------------------------------------------------------
    // Automatic awareness of where the school is in the calendar
    // ------------------------------------------------------------------

    /** The period whose dates contain the given date, if any. */
    public Optional<TermPeriod> periodFor(LocalDate date) {
        if (date == null) {
            return Optional.empty();
        }
        return store.getPeriods().stream()
                .filter(p -> p.getFrom() != null && p.getTo() != null
                        && !date.isBefore(p.getFrom()) && !date.isAfter(p.getTo()))
                .findFirst();
    }

    /** Current period (if in session) or the next upcoming one; fallback to the most recent ended period. */
    public Optional<TermPeriod> currentOrNextPeriod(LocalDate date) {
        Optional<TermPeriod> inSession = periodFor(date);
        if (inSession.isPresent()) {
            return inSession;
        }
        Optional<TermPeriod> upcoming = store.getPeriods().stream()
                .filter(p -> p.getFrom() != null && date.isBefore(p.getFrom()))
                .min(Comparator.comparing(TermPeriod::getFrom));
        if (upcoming.isPresent()) {
            return upcoming;
        }
        return store.getPeriods().stream()
                .filter(p -> p.getTo() != null)
                .max(Comparator.comparing(TermPeriod::getTo));
    }

    /** The term the school is currently in (or about to start). */
    public Optional<AcademicTerm> currentTerm(LocalDate date) {
        return currentOrNextPeriod(date).map(TermPeriod::getTerm);
    }

    /** Start date of the next term period after the given date, if one is scheduled. */
    public Optional<LocalDate> nextTermStart(LocalDate date) {
        return store.getPeriods().stream()
                .map(TermPeriod::getFrom)
                .filter(f -> f != null && f.isAfter(date))
                .min(Comparator.naturalOrder());
    }

    /** Days left in the current/next period (0 on its last day, negative when none applies). */
    public long daysRemaining(LocalDate date) {
        Optional<TermPeriod> period = periodFor(date);
        if (period.isPresent()) {
            return Math.max(0, ChronoUnit.DAYS.between(date, period.get().getTo()));
        }
        return currentOrNextPeriod(date)
                .map(p -> ChronoUnit.DAYS.between(date, p.getFrom()))
                .orElse(-1L);
    }

    /** Most relevant period for a given term relative to today. */
    public Optional<TermPeriod> periodForTerm(AcademicTerm term, LocalDate today) {
        List<TermPeriod> candidates = store.getPeriods().stream()
                .filter(p -> p.getTerm() == term && p.getFrom() != null && p.getTo() != null)
                .toList();
        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        return candidates.stream().filter(p -> !today.isBefore(p.getFrom()) && !today.isAfter(p.getTo()))
                .findFirst()
                .or(() -> candidates.stream().filter(p -> today.isAfter(p.getTo()))
                        .max(Comparator.comparing(TermPeriod::getTo)))
                .or(() -> candidates.stream().min(Comparator.comparing(TermPeriod::getFrom)));
    }

    public boolean isTermEnded(AcademicTerm term, LocalDate today) {
        return periodForTerm(term, today)
                .map(p -> today.isAfter(p.getTo()))
                .orElse(false);
    }

    /** Unpaid balance (charged − paid) for a student's current term that would roll to arrears. */
    public BigDecimal unpaidAtCurrentTermEnd(Student student) {
        StudentFeeLedger ledger = studentStore.getLedger(student.getId());
        return unpaid(ledger);
    }

    /**
     * Sum of students currently past their term's end date together with the
     * total unpaid that will roll into arrears when the rollover runs.
     */
    public RolloverPreview overduePreview(LocalDate today) {
        int studentsOverdue = 0;
        BigDecimal totalUnpaid = CurrencyConfig.zero();
        for (Student s : studentStore.getStudents()) {
            StudentFeeLedger ledger = studentStore.getLedger(s.getId());
            if (periodForTerm(ledger.getCurrentTerm(), today)
                    .filter(p -> today.isAfter(p.getTo()) && p.getYear() == effectiveYear(s))
                    .isPresent()) {
                studentsOverdue++;
                totalUnpaid = totalUnpaid.add(unpaid(ledger));
            }
        }
        return new RolloverPreview(studentsOverdue, totalUnpaid);
    }

    // ------------------------------------------------------------------
    // Automatic end-of-term transition
    // ------------------------------------------------------------------

    /**
     * Moves every student whose current term has ended into the next term:
     * unpaid balance is added to arrears, the term ledger cycle is closed, the
     * current term advances, and after Term 3 the class is promoted into the
     * next academic year. Safe to run repeatedly — it only advances students who
     * are still sitting on an ended term.
     */
    public RolloverResult rolloverIfDue(LocalDate today) {
        int studentsRolled = 0;
        int classPromotions = 0;
        BigDecimal arrearsRolled = CurrencyConfig.zero();

        for (Student s : studentStore.getStudents()) {
            StudentFeeLedger ledger = studentStore.getLedger(s.getId());
            boolean movedThisStudent = false;
            int guard = 0;
            while (guard++ < 8) {
                AcademicTerm current = ledger.getCurrentTerm();
                Optional<TermPeriod> period = periodForTerm(current, today);
                if (period.isEmpty() || !today.isAfter(period.get().getTo())
                        || period.get().getYear() != effectiveYear(s)) {
                    break;
                }
                BigDecimal unpaid = unpaid(ledger);
                ledger.setArrears(ledger.getArrears().add(unpaid));
                boolean promoted = false;
                if (current == AcademicTerm.TERM_3) {
                    promoted = promoteClass(s);
                    int nextYear = period.get().getTo().getYear() + 1;
                    Optional<TermPeriod> nextYearT1 = store.getPeriods().stream()
                            .filter(p -> p.getTerm() == AcademicTerm.TERM_1
                                    && p.getFrom() != null && p.getFrom().isAfter(period.get().getTo())
                                    && p.getFrom().getYear() == nextYear)
                            .min(Comparator.comparing(TermPeriod::getFrom));
                    s.setAcademicYear(nextYearT1.map(p -> p.getFrom().getYear()).orElse(nextYear));
                    if (promoted) {
                        classPromotions++;
                    }
                }
                ledger.clearCurrentCycle();
                ledger.setCurrentTerm(nextTerm(current));
                if (!movedThisStudent) {
                    studentsRolled++;
                    movedThisStudent = true;
                }
                arrearsRolled = arrearsRolled.add(unpaid);
            }
        }

        if (studentsRolled > 0) {
            PersistenceService.getInstance().saveAll();
            lastRolloverDate = today;
            auditService.log("TERM_ROLLOVER", "Academic Calendar", "-",
                    "{\"asOf\":\"" + today + "\",\"students\":\"" + studentsRolled
                            + "\",\"arrears\":\"" + arrearsRolled
                            + "\",\"classPromotions\":\"" + classPromotions + "\"}");
        }
        return new RolloverResult(studentsRolled, classPromotions, arrearsRolled, today);
    }

    private BigDecimal unpaid(StudentFeeLedger ledger) {
        return ledger.getTotalCharged().subtract(ledger.getTotalPaid()).max(CurrencyConfig.zero());
    }

    private int effectiveYear(Student s) {
        return s.getAcademicYear() != null
                ? s.getAcademicYear()
                : com.schaccs.config.AppConfig.getInstance().getAcademicYear();
    }

    private AcademicTerm nextTerm(AcademicTerm term) {
        return switch (term) {
            case TERM_1 -> AcademicTerm.TERM_2;
            case TERM_2 -> AcademicTerm.TERM_3;
            case TERM_3 -> AcademicTerm.TERM_1;
        };
    }

    /** Promotes "Form N" → "Form N+1". Returns false when the class has no promotable form number. */
    private boolean promoteClass(Student s) {
        String form = s.getFormClass();
        if (form == null) {
            return false;
        }
        Matcher m = FORM_PATTERN.matcher(form);
        if (!m.find()) {
            return false;
        }
        int current = Integer.parseInt(m.group(2));
        if (current < 1 || current >= MAX_FORM) {
            return false;
        }
        s.setFormClass(m.replaceFirst(m.group(1) + (current + 1)));
        return true;
    }

    public LocalDate getLastRolloverDate() {
        return lastRolloverDate;
    }

    // ------------------------------------------------------------------
    // Sample data
    // ------------------------------------------------------------------

    /**
     * Seeds the 2026 sample periods (Term 1 20/01/2026–19/04/2026, Term 2
     * 05/05/2026–28/07/2026, Term 3 24/08/2026–25/10/2026) when the calendar is
     * empty. Never overwrites existing (customized) periods. Returns true when
     * the sample data was inserted.
     */
    public boolean seedIfEmpty() {
        if (!store.getPeriods().isEmpty()) {
            return false;
        }
        store.add(new TermPeriod(AcademicTerm.TERM_1, LocalDate.of(2026, 1, 20), LocalDate.of(2026, 4, 19)));
        store.add(new TermPeriod(AcademicTerm.TERM_2, LocalDate.of(2026, 5, 5), LocalDate.of(2026, 7, 28)));
        store.add(new TermPeriod(AcademicTerm.TERM_3, LocalDate.of(2026, 8, 24), LocalDate.of(2026, 10, 25)));
        PersistenceService.getInstance().saveAll();
        return true;
    }

    public record RolloverPreview(int studentsOverdue, BigDecimal totalUnpaid) {
    }

    public record RolloverResult(int studentsRolled, int classPromotions,
                                 BigDecimal arrearsRolled, LocalDate asOf) {
    }
}
