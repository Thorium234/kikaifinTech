package com.schaccs.service.student;

import com.schaccs.config.CurrencyConfig;
import com.schaccs.enums.AcademicTerm;
import com.schaccs.model.fee.FeeStructure;
import com.schaccs.model.student.Student;
import com.schaccs.model.student.StudentFeeLedger;
import com.schaccs.model.student.StudentTermBalance;
import com.schaccs.repository.PersistenceService;
import com.schaccs.service.audit.AuditService;
import com.schaccs.service.school.AcademicCalendarService;
import com.schaccs.store.FeeStructureStore;
import com.schaccs.store.StudentStore;
import com.schaccs.store.StudentTermBalanceStore;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Cohort-Temporal Ledger Replay engine.
 * <p>
 * When a student is imported with a Year of Admission earlier than their
 * arrival year, this service reconstructs the historical timeline
 * {@code [Y_admit .. min(Y_arrival - 1, Y_complete)]} term by term: for every
 * historical term it bills that year's fee structure, matches historical
 * payments against the period boundaries (waterfall allocation, earliest term
 * first), and rolls the unpaid balance forward as the next term's brought-
 * forward arrears. Each term is recorded as an immutable
 * {@link StudentTermBalance} snapshot, and the accumulated net position lands
 * on the student's live ledger as opening arrears (or advance credit when
 * overpaid) — mirroring the convention used by the fees-balance import.
 * <p>
 * Terms from the arrival year onwards are intentionally left to live billing
 * ({@code FeeCalculationService.chargeTermFees} + the end-of-term rollover),
 * which writes its own snapshots, so no term is ever double-billed. Replay is
 * idempotent: if a snapshot already exists for the student's first timeline
 * term the replay is skipped.
 */
public class CohortReplayService {

    private final FeeStructureStore feeStore;
    private final AcademicCalendarService calendarService;
    private final StudentTermBalanceStore balanceStore;
    private final StudentStore studentStore;
    private final AuditService auditService;

    public CohortReplayService() {
        this(FeeStructureStore.getInstance(), new AcademicCalendarService(),
                StudentTermBalanceStore.getInstance(), StudentStore.getInstance(), new AuditService());
    }

    public CohortReplayService(FeeStructureStore feeStore, AcademicCalendarService calendarService,
                               StudentTermBalanceStore balanceStore, StudentStore studentStore,
                               AuditService auditService) {
        this.feeStore = feeStore;
        this.calendarService = calendarService;
        this.balanceStore = balanceStore;
        this.studentStore = studentStore;
        this.auditService = auditService;
    }

    /**
     * Reconstruct the student's pre-arrival financial history with no
     * historical payments (every term fully outstanding unless a balance
     * import already settled it).
     */
    public ReplayResult replay(Student student) {
        return replay(student, null);
    }

    /**
     * Reconstruct the student's pre-arrival financial history, allocating the
     * given total of historical payments across the timeline waterfall-style
     * (earliest term first). Any excess above total historical billing becomes
     * advance credit on the live ledger.
     *
     * @param historicalPaymentsTotal total payments made before arrival
     *                                (nullable / zero for none)
     */
    public ReplayResult replay(Student student, BigDecimal historicalPaymentsTotal) {
        if (student == null || student.isDeleted()) {
            return ReplayResult.skipped("Student is missing or archived.");
        }
        Integer admit = resolveAdmissionYear(student);
        Integer duration = resolveDurationYears(student);
        if (admit == null || duration == null || duration <= 0) {
            return ReplayResult.skipped("Admission year or course duration unknown — nothing to replay.");
        }
        int arrivalYear = resolveArrivalYear(student);
        int lastTimelineYear = admit + duration - 1;
        int endYear = Math.min(arrivalYear - 1, lastTimelineYear);
        if (endYear < admit) {
            return ReplayResult.skipped("No pre-arrival history (cohort starts in the arrival year).");
        }
        if (!balanceStore.find(student.getId(), admit, AcademicTerm.TERM_1).isEmpty()) {
            return ReplayResult.skipped("Ledger replay already performed for " + admit + ".");
        }

        for (int year = admit; year <= endYear; year++) {
            calendarService.ensureYearCalendar(year);
        }

        List<String> warnings = new ArrayList<>();
        BigDecimal paymentTotal = CurrencyConfig.money(
                historicalPaymentsTotal == null ? BigDecimal.ZERO : historicalPaymentsTotal);
        if (paymentTotal.compareTo(BigDecimal.ZERO) < 0) {
            warnings.add("Negative historical payment total treated as zero.");
            paymentTotal = CurrencyConfig.zero();
        }
        BigDecimal paymentsLeft = paymentTotal;
        BigDecimal runningBalance = CurrencyConfig.zero();
        int snapshotted = 0;

        for (int year = admit; year <= endYear; year++) {
            FeeStructure structure = feeStore.findStructure(year, student.getBoardingStatus()).orElse(null);
            if (structure == null) {
                warnings.add("No fee structure for " + year + " — balance carried forward unchanged.");
            }
            for (AcademicTerm term : List.of(AcademicTerm.TERM_1, AcademicTerm.TERM_2, AcademicTerm.TERM_3)) {
                BigDecimal arrearsBf = runningBalance;
                BigDecimal billed = structure != null ? structure.totalForTerm(term) : CurrencyConfig.zero();
                BigDecimal paid = paymentsLeft.min(billed).max(CurrencyConfig.zero());
                paymentsLeft = CurrencyConfig.money(paymentsLeft.subtract(paid));
                runningBalance = StudentTermBalance.computeClosingBalance(arrearsBf, billed, paid);
                upsertSnapshot(new StudentTermBalance(student.getId(), year, term,
                        billed, arrearsBf, paid, runningBalance));
                snapshotted++;
            }
        }

        StudentFeeLedger ledger = studentStore.getLedger(student.getId());
        BigDecimal openingArrears = CurrencyConfig.zero();
        BigDecimal advanceCredit = paymentsLeft.max(CurrencyConfig.zero());
        if (runningBalance.compareTo(BigDecimal.ZERO) > 0) {
            openingArrears = runningBalance;
            ledger.setArrears(ledger.getArrears().add(openingArrears));
        } else if (runningBalance.compareTo(BigDecimal.ZERO) < 0) {
            // Defensive: per-term clamping keeps closing balances >= 0, but a
            // negative net position would still represent credit.
            advanceCredit = advanceCredit.add(runningBalance.negate());
        }
        if (advanceCredit.compareTo(BigDecimal.ZERO) > 0) {
            ledger.addAdvance(advanceCredit);
        }

        PersistenceService.getInstance().saveAll();
        auditService.log("COHORT_REPLAY", "Student", student.getId(),
                "{\"admissionNumber\":\"" + student.getAdmissionNumber()
                        + "\",\"timeline\":\"" + admit + "-" + endYear
                        + "\",\"termsSnapshotted\":" + snapshotted
                        + ",\"openingArrears\":" + openingArrears
                        + ",\"advanceCredit\":" + advanceCredit + "}");
        return new ReplayResult(true, snapshotted, openingArrears, advanceCredit, warnings, null);
    }

    /**
     * Insert or replace the snapshot for its (student, year, term) key. Shared
     * with the end-of-term rollover so both writers maintain one coherent
     * waterfall ledger.
     */
    public static synchronized void upsertSnapshot(StudentTermBalance snapshot) {
        StudentTermBalanceStore store = StudentTermBalanceStore.getInstance();
        store.find(snapshot.getStudentId(), snapshot.getAcademicYear(), snapshot.getTerm())
                .ifPresent(existing -> store.getItems().remove(existing));
        store.add(snapshot);
    }

    private Integer resolveAdmissionYear(Student student) {
        if (student.getYearOfAdmission() != null) {
            return student.getYearOfAdmission();
        }
        return student.getAcademicYear();
    }

    private Integer resolveDurationYears(Student student) {
        Integer duration = student.getCourseDurationYears();
        if (duration == null || duration <= 0) {
            duration = student.getDurationValue();
        }
        return duration;
    }

    /** The year the student enters live billing: explicit academic year, else the operating year. */
    private int resolveArrivalYear(Student student) {
        if (student.getAcademicYear() != null) {
            return student.getAcademicYear();
        }
        return LocalDate.now().getYear();
    }

    public record ReplayResult(boolean replayed, int termsSnapshotted,
                               BigDecimal openingArrears, BigDecimal advanceCredit,
                               List<String> warnings, String skipReason) {

        static ReplayResult skipped(String reason) {
            return new ReplayResult(false, 0, CurrencyConfig.zero(), CurrencyConfig.zero(),
                    List.of(), reason);
        }
    }
}
