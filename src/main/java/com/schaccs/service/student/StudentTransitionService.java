package com.schaccs.service.student;

import com.schaccs.config.CurrencyConfig;
import com.schaccs.enums.AcademicTerm;
import com.schaccs.enums.BoardingStatus;
import com.schaccs.model.student.Student;
import com.schaccs.model.student.StudentFeeLedger;
import com.schaccs.repository.PersistenceService;
import com.schaccs.service.audit.AuditService;
import com.schaccs.service.fee.FeeCalculationService;
import com.schaccs.store.FeeStructureStore;
import com.schaccs.store.StudentStore;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Handles a student's Day ↔ Boarding transition. Only the fee for the term in
 * which the transition happens is adjusted on the ledger (delta between the new
 * and old boarding structure for that single term). Past terms/years billed
 * while the student was a day scholar are left untouched, and future terms are
 * billed under the new status automatically. An audit trail entry is written for
 * every transition.
 */
public class StudentTransitionService {

    private final FeeCalculationService feeCalc;
    private final StudentStore studentStore;
    private final FeeStructureStore feeStore;
    private final AuditService auditService;

    public StudentTransitionService() {
        this(new FeeCalculationService(), StudentStore.getInstance(),
                FeeStructureStore.getInstance(), new AuditService());
    }

    public StudentTransitionService(FeeCalculationService feeCalc, StudentStore studentStore,
                                    FeeStructureStore feeStore, AuditService auditService) {
        this.feeCalc = feeCalc;
        this.studentStore = studentStore;
        this.feeStore = feeStore;
        this.auditService = auditService;
    }

    public Optional<Student> findById(String id) {
        return studentStore.findById(id);
    }

    /** Per-votehead fee difference between the student's current and target status for one term. */
    public List<TransitionDelta> preview(Student student, BoardingStatus newStatus, AcademicTerm term) {
        Map<String, BigDecimal> currentAmounts = feeCalc.termAmountsFor(student, student.getBoardingStatus(), term);
        Map<String, BigDecimal> targetAmounts = feeCalc.termAmountsFor(student, newStatus, term);

        Set<String> codes = new LinkedHashSet<>();
        codes.addAll(currentAmounts.keySet());
        codes.addAll(targetAmounts.keySet());

        List<TransitionDelta> deltas = new ArrayList<>();
        for (String code : codes) {
            BigDecimal current = currentAmounts.getOrDefault(code, CurrencyConfig.zero());
            BigDecimal target = targetAmounts.getOrDefault(code, CurrencyConfig.zero());
            BigDecimal delta = target.subtract(current);
            if (delta.compareTo(BigDecimal.ZERO) != 0) {
                deltas.add(new TransitionDelta(code, feeStore.voteheadName(code), current, target, delta));
            }
        }
        return deltas;
    }

    /** Net change for the billed term (positive = additional charge, negative = reduced charge). */
    public BigDecimal previewNetDelta(Student student, BoardingStatus newStatus, AcademicTerm term) {
        return preview(student, newStatus, term).stream()
                .map(TransitionDelta::delta)
                .reduce(CurrencyConfig.zero(), BigDecimal::add);
    }

    /**
     * Applies the transition: adjusts only the given term's charges on the ledger
     * and updates the student's boarding status. Rejects no-op transitions.
     */
    public TransitionResult apply(Student student, BoardingStatus newStatus, AcademicTerm term) {
        return apply(student, newStatus, term, BigDecimal.ONE);
    }

    /**
     * Applies the transition with an optional proration ratio (0 &lt; ratio &lt;= 1)
     * that scales the per-votehead delta for the transition term — e.g. a mid-term
     * boarding transfer can pro-rate the boarding charge for the weeks remaining.
     * A ratio of 1.0 charges the full difference. Also converts any prepaid amount
     * held on a votehead that is being dropped (e.g. a day-scholar's prepaid lunch
     * fee) into a carry-forward credit that offsets the new boarding obligation.
     */
    public TransitionResult apply(Student student, BoardingStatus newStatus, AcademicTerm term,
                                  BigDecimal prorateRatio) {
        if (student == null || newStatus == null || term == null) {
            return TransitionResult.failure(List.of("Student, target status, and term are required."));
        }
        if (newStatus == student.getBoardingStatus()) {
            return TransitionResult.failure(List.of(
                    student.getName() + " is already " + newStatus.getDisplayName() + "."));
        }
        if (prorateRatio == null || prorateRatio.compareTo(BigDecimal.ZERO) <= 0
                || prorateRatio.compareTo(BigDecimal.ONE) > 0) {
            return TransitionResult.failure(List.of("Proration ratio must be between 0 (exclusive) and 1."));
        }

        BoardingStatus oldStatus = student.getBoardingStatus();
        List<TransitionDelta> deltas = preview(student, newStatus, term);
        StudentFeeLedger ledger = studentStore.getLedger(student.getId());
        java.util.List<FeeConversion> conversions = new java.util.ArrayList<>();
        for (TransitionDelta d : deltas) {
            if (d.delta().compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal prorated = CurrencyConfig.money(d.delta().multiply(prorateRatio));
                ledger.charge(d.code(), prorated);
            } else {
                BigDecimal reduction = d.delta().negate();
                // A negative delta means this votehead is being dropped/shrunk. If the
                // student had already prepaid into it (e.g. prepaid lunch), convert that
                // prepaid amount into a carry-forward credit so it offsets the new
                // boarding obligation rather than being stranded. The charge itself is
                // reduced by the full delta regardless, so any prepaid that stays within
                // the new (lower) charge remains allocated to that votehead.
                BigDecimal prepaid = ledger.getPaid(d.code());
                if (prepaid.compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal convert = prepaid.min(reduction);
                    if (convert.compareTo(BigDecimal.ZERO) > 0) {
                        ledger.addAdvance(convert);
                        ledger.reversePayment(d.code(), convert);
                        conversions.add(new FeeConversion(d.code(), convert));
                    }
                }
                ledger.reduceCharge(d.code(), reduction);
            }
        }

        student.setBoardingStatus(newStatus);
        PersistenceService.getInstance().saveAll();
        String conversionJson = conversions.stream()
                .map(c -> "{\"code\":\"" + safe(c.code()) + "\",\"converted\":"
                        + c.amount() + "}")
                .collect(java.util.stream.Collectors.joining(",", "[", "]"));
        auditService.log("STUDENT_TRANSITION", "Student", student.getId(),
                "{\"admissionNumber\":\"" + safe(student.getAdmissionNumber())
                        + "\",\"from\":\"" + oldStatus.getDisplayName()
                        + "\",\"to\":\"" + newStatus.getDisplayName()
                        + "\",\"term\":\"" + term.getDisplayName()
                        + "\",\"proration\":" + prorateRatio
                        + ",\"prepaidConversions\":" + conversionJson
                        + ",\"netDelta\":" + deltas.stream()
                        .map(TransitionDelta::delta).reduce(CurrencyConfig.zero(), BigDecimal::add) + "}");
        return TransitionResult.success(student, deltas, conversions);
    }

    /** Records a prepaid amount converted into a carry-forward credit during a transition. */
    public record FeeConversion(String code, BigDecimal amount) {
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    public record TransitionDelta(String code, String name, BigDecimal from, BigDecimal to, BigDecimal delta) {
    }

    public record TransitionResult(boolean success, Student student,
                                   List<TransitionDelta> deltas, List<FeeConversion> conversions,
                                   List<String> errors) {

        public static TransitionResult success(Student student, List<TransitionDelta> deltas,
                                               List<FeeConversion> conversions) {
            return new TransitionResult(true, student, deltas, conversions, List.of());
        }

        public static TransitionResult failure(List<String> errors) {
            return new TransitionResult(false, null, List.of(), List.of(), errors);
        }
    }
}
