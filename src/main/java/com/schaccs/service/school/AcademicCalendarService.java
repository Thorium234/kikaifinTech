package com.schaccs.service.school;

import com.schaccs.config.CurrencyConfig;
import com.schaccs.enums.AcademicTerm;
import com.schaccs.enums.DurationUnit;
import com.schaccs.enums.StudentStatus;
import com.schaccs.enums.TermStatus;
import com.schaccs.model.school.TermPeriod;
import com.schaccs.model.student.Student;
import com.schaccs.model.student.StudentFeeLedger;
import com.schaccs.repository.PersistenceService;
import com.schaccs.service.audit.AuditService;
import com.schaccs.service.fee.FeeCalculationService;
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
    private final FeeCalculationService feeCalculationService;

    private LocalDate lastRolloverDate;

    public AcademicCalendarService() {
        this(AcademicCalendarStore.getInstance(), StudentStore.getInstance(), new AuditService(),
                new FeeCalculationService());
    }

    public AcademicCalendarService(AcademicCalendarStore store, StudentStore studentStore,
                                   AuditService auditService) {
        this(store, studentStore, auditService, new FeeCalculationService());
    }

    public AcademicCalendarService(AcademicCalendarStore store, StudentStore studentStore,
                                   AuditService auditService, FeeCalculationService feeCalculationService) {
        this.store = store;
        this.studentStore = studentStore;
        this.auditService = auditService;
        this.feeCalculationService = feeCalculationService;
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

    // ------------------------------------------------------------------
    // Term lifecycle status engine
    // ------------------------------------------------------------------

    /**
     * Recompute the lifecycle status of every term period from the given date:
     * a period whose end date has passed becomes ENDED, the period containing
     * the date becomes ACTIVE, and everything still in the future stays PLANNED.
     * Guarantees at most one ACTIVE term at any timestamp. Returns the number of
     * periods whose status actually changed, so callers know whether a
     * term-boundary event (e.g. arrears rollover) just happened.
     */
    public int reconcileStatuses(LocalDate today) {
        int changed = 0;
        for (TermPeriod p : store.getPeriods()) {
            TermStatus next = TermStatus.PLANNED;
            if (p.getFrom() != null && p.getTo() != null) {
                if (today.isAfter(p.getTo())) {
                    next = TermStatus.ENDED;
                } else if (!today.isBefore(p.getFrom())) {
                    next = TermStatus.ACTIVE;
                }
            }
            if (p.getStatus() != next) {
                p.setStatus(next);
                changed++;
            }
        }
        if (changed > 0) {
            PersistenceService.getInstance().saveAll();
        }
        return changed;
    }

    /** The term period currently ACTIVE for the given date, if any. */
    public Optional<TermPeriod> activePeriod(LocalDate today) {
        return store.getPeriods().stream()
                .filter(p -> p.getStatus() == TermStatus.ACTIVE)
                .findFirst()
                .or(() -> periodFor(today));
    }

    /** The term currently ACTIVE for the given date, if any. */
    public Optional<AcademicTerm> activeTerm(LocalDate today) {
        return activePeriod(today).map(TermPeriod::getTerm);
    }

    /**
     * Ensure the calendar has the three standard terms for the given year,
     * generating them when none exist yet: Term 1 (Jan 1 – Apr 30), Term 2
     * (May 1 – Aug 31), Term 3 (Sep 1 – Dec 31). Statuses are derived from
     * today's date (ENDED for past terms, ACTIVE for the in-session term,
     * PLANNED for future ones), so scaffolding a historical year yields all-
     * ENDED terms while scaffolding the current year preserves live statuses.
     * Returns true when the scaffold was created.
     */
    public boolean ensureYearCalendar(int year) {
        boolean hasYear = store.getPeriods().stream()
                .anyMatch(p -> p.getYear() == year);
        if (hasYear) {
            return false;
        }
        LocalDate today = LocalDate.now();
        store.add(scaffoldTerm(AcademicTerm.TERM_1, year, 1, 5, today));
        store.add(scaffoldTerm(AcademicTerm.TERM_2, year, 5, 9, today));
        store.add(scaffoldTerm(AcademicTerm.TERM_3, year, 9, 13, today));
        PersistenceService.getInstance().saveAll();
        return true;
    }

    private TermPeriod scaffoldTerm(AcademicTerm term, int year, int startMonth, int endMonthExclusive, LocalDate today) {
        LocalDate from = LocalDate.of(year, startMonth, 1);
        LocalDate lastDay = endMonthExclusive > 12
                ? LocalDate.of(year, 12, 31)
                : LocalDate.of(year, endMonthExclusive, 1).minusDays(1);
        TermStatus status;
        if (today.isAfter(lastDay)) {
            status = TermStatus.ENDED;
        } else if (!today.isBefore(from)) {
            status = TermStatus.ACTIVE;
        } else {
            status = TermStatus.PLANNED;
        }
        return TermPeriod.withId(java.util.UUID.randomUUID().toString(), term, from, lastDay, status);
    }

    /**
     * Mark students whose expected completion date or year has passed as COMPLETED
     * (GRADUATED when the class was already promoted past the top form). Also
     * updates the lifecycle_status field. Returns the number of students whose
     * status changed.
     */
    public int checkCompletions(LocalDate today) {
        int completed = 0;
        int currentYear = today.getYear();
        for (Student s : studentStore.getStudents()) {
            if (s.getStatus() != StudentStatus.ACTIVE) {
                continue;
            }
            boolean shouldComplete = s.isCourseCompleted(today);
            // Also check year-based completion for cohort lifecycle
            if (!shouldComplete) {
                Integer expectedYear = s.computeExpectedCompletionYear();
                if (expectedYear != null && currentYear > expectedYear) {
                    shouldComplete = true;
                }
            }
            if (shouldComplete) {
                // Graduate if already past Form 6, otherwise mark completed
                String formClass = s.getFormClass();
                boolean pastForm6 = formClass != null && formClass.matches("(?i).*form\\s*6.*");
                if (pastForm6) {
                    s.setStatus(StudentStatus.GRADUATED);
                    s.setLifecycleStatus("GRADUATED");
                } else {
                    s.setStatus(StudentStatus.COMPLETED);
                    s.setLifecycleStatus("COMPLETED");
                }
                completed++;
            }
        }
        if (completed > 0) {
            PersistenceService.getInstance().saveAll();
            auditService.log("COURSE_COMPLETION", "Course Duration", "-",
                    "{\"asOf\":\"" + today + "\",\"students\":\"" + completed + "\"}");
        }
        return completed;
    }

    /**
     * Compute the expected completion date from the enrollment date and the
     * course duration. YEARS adds the value to the enrollment year; TERMS adds
     * 4 months per term (3 terms per year). Returns null when there is not
     * enough information.
     */
    public LocalDate expectedCompletionDate(Student student) {
        LocalDate enrolled = student.getEnrollmentDate();
        Integer value = student.getDurationValue();
        DurationUnit unit = student.getDurationUnit();
        if (enrolled == null || value == null || value <= 0 || unit == null) {
            return null;
        }
        return switch (unit) {
            case YEARS -> enrolled.plusYears(value);
            case TERMS -> enrolled.plusMonths(value * 4L);
        };
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

        reconcileStatuses(today);
        checkCompletions(today);

        for (Student s : studentStore.getStudents()) {
            if (s.getStatus() != StudentStatus.ACTIVE) {
                continue;
            }
            StudentFeeLedger ledger = studentStore.getLedger(s.getId());
            boolean movedThisStudent = false;
            int guard = 0;
            while (guard++ < 8) {
                AcademicTerm current = ledger.getCurrentTerm();
                Optional<TermPeriod> period = periodForTerm(current, today);
                if (period.isEmpty() || period.get().getStatus() != TermStatus.ENDED
                        || period.get().getYear() != effectiveYear(s)) {
                    break;
                }
                if (s.isCourseCompleted(today) || isYearComplete(s, today)) {
                    s.setStatus(StudentStatus.COMPLETED);
                    s.setLifecycleStatus("COMPLETED");
                    break;
                }
                BigDecimal arrearsBefore = ledger.getArrears();
                BigDecimal unpaid = unpaid(ledger);
                // Waterfall continuity: freeze this term's position into the
                // term-balance ledger before the cycle resets.
                com.schaccs.service.student.CohortReplayService.upsertSnapshot(
                        new com.schaccs.model.student.StudentTermBalance(
                                s.getId(), period.get().getYear(), current,
                                ledger.getTotalCharged(), arrearsBefore,
                                ledger.getTotalPaid(), arrearsBefore.add(unpaid)));
                ledger.setArrears(arrearsBefore.add(unpaid));
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
                feeCalculationService.chargeTermFees(s, nextTerm(current));
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

    /** Year-based completion check using cohort lifecycle data. */
    private boolean isYearComplete(Student s, LocalDate today) {
        Integer expectedYear = s.computeExpectedCompletionYear();
        return expectedYear != null && today.getYear() > expectedYear;
    }

    public LocalDate getLastRolloverDate() {
        return lastRolloverDate;
    }

    // ------------------------------------------------------------------
    // Sample data
    // ------------------------------------------------------------------

    /**
     * Seeds standard term periods for the current year when the calendar is
     * empty: Term 1 (Jan 1 – Apr 30), Term 2 (May 1 – Aug 31), Term 3
     * (Sep 1 – Dec 31). Never overwrites existing (customized) periods.
     * Returns true when the sample data was inserted.
     */
    public boolean seedIfEmpty() {
        if (!store.getPeriods().isEmpty()) {
            return false;
        }
        int year = LocalDate.now().getYear();
        store.add(new TermPeriod(AcademicTerm.TERM_1,
                LocalDate.of(year, 1, 1), LocalDate.of(year, 4, 30)));
        store.add(new TermPeriod(AcademicTerm.TERM_2,
                LocalDate.of(year, 5, 1), LocalDate.of(year, 8, 31)));
        store.add(new TermPeriod(AcademicTerm.TERM_3,
                LocalDate.of(year, 9, 1), LocalDate.of(year, 12, 31)));
        PersistenceService.getInstance().saveAll();
        return true;
    }

    public record RolloverPreview(int studentsOverdue, BigDecimal totalUnpaid) {
    }

    public record RolloverResult(int studentsRolled, int classPromotions,
                                 BigDecimal arrearsRolled, LocalDate asOf) {
    }
}
