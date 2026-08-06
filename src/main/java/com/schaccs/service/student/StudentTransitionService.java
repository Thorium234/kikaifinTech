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
        if (student == null || newStatus == null || term == null) {
            return TransitionResult.failure(List.of("Student, target status, and term are required."));
        }
        if (newStatus == student.getBoardingStatus()) {
            return TransitionResult.failure(List.of(
                    student.getName() + " is already " + newStatus.getDisplayName() + "."));
        }

        BoardingStatus oldStatus = student.getBoardingStatus();
        List<TransitionDelta> deltas = preview(student, newStatus, term);
        StudentFeeLedger ledger = studentStore.getLedger(student.getId());
        for (TransitionDelta d : deltas) {
            if (d.delta().compareTo(BigDecimal.ZERO) > 0) {
                ledger.charge(d.code(), d.delta());
            } else {
                ledger.reduceCharge(d.code(), d.delta().negate());
            }
        }

        student.setBoardingStatus(newStatus);
        PersistenceService.getInstance().saveAll();
        auditService.log("STUDENT_TRANSITION", "Student", student.getId(),
                "{\"admissionNumber\":\"" + safe(student.getAdmissionNumber())
                        + "\",\"from\":\"" + oldStatus.getDisplayName()
                        + "\",\"to\":\"" + newStatus.getDisplayName()
                        + "\",\"term\":\"" + term.getDisplayName()
                        + "\",\"netDelta\":" + deltas.stream()
                        .map(TransitionDelta::delta).reduce(CurrencyConfig.zero(), BigDecimal::add) + "}");
        return TransitionResult.success(student, deltas);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    public record TransitionDelta(String code, String name, BigDecimal from, BigDecimal to, BigDecimal delta) {
    }

    public record TransitionResult(boolean success, Student student,
                                   List<TransitionDelta> deltas, List<String> errors) {

        public static TransitionResult success(Student student, List<TransitionDelta> deltas) {
            return new TransitionResult(true, student, deltas, List.of());
        }

        public static TransitionResult failure(List<String> errors) {
            return new TransitionResult(false, null, List.of(), errors);
        }
    }
}
