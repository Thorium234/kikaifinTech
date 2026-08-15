package com.schaccs.service.student;

import com.schaccs.enums.AcademicTerm;
import com.schaccs.model.student.Student;
import com.schaccs.model.student.StudentFeeLedger;
import com.schaccs.service.fee.FeeCalculationService;
import com.schaccs.store.StudentStore;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Builds the fee-status snapshot shown by the Pay workspace. Expected amounts
 * come from the fee structure (per votehead, current term, sibling discount
 * applied) so the cashier can see what the learner SHOULD pay even before the
 * term charges have been posted to the ledger.
 */
public class PayPreviewService {

    private final FeeCalculationService feeCalc;
    private final StudentStore studentStore;

    public PayPreviewService() {
        this(new FeeCalculationService(), StudentStore.getInstance());
    }

    public PayPreviewService(FeeCalculationService feeCalc, StudentStore studentStore) {
        this.feeCalc = feeCalc;
        this.studentStore = studentStore;
    }

    public boolean hasStructure(Student student) {
        return student != null && feeCalc.structureFor(student).isPresent();
    }

    /**
     * Votehead-code → display-name map from the student's fee structure for the
     * current term (falling back to any term's item when the current term has no
     * entry for that code). Display-only; does not affect charging or distribution.
     */
    public Map<String, String> structureNames(Student student) {
        Map<String, String> names = new java.util.LinkedHashMap<>();
        if (student == null) {
            return names;
        }
        StudentFeeLedger ledger = studentStore.getLedger(student.getId());
        feeCalc.structureFor(student).ifPresent(structure -> {
            for (var item : structure.getItems()) {
                names.putIfAbsent(item.getVoteheadCode(), item.getVoteheadName());
            }
        });
        AcademicTerm term = ledger.getCurrentTerm();
        if (term != null) {
            Map<String, String> termNames = new java.util.LinkedHashMap<>();
            feeCalc.structureFor(student).ifPresent(structure -> {
                for (var item : structure.itemsForTerm(term)) {
                    termNames.put(item.getVoteheadCode(), item.getVoteheadName());
                }
            });
            names.putAll(termNames);
        }
        return names;
    }

    public FeeStatus feeStatus(Student student) {
        StudentFeeLedger ledger = studentStore.getLedger(student.getId());
        Map<String, BigDecimal> expectedByVotehead = feeCalc.termAmountsFor(
                student, student.getBoardingStatus(), ledger.getCurrentTerm());
        BigDecimal expectedTerm = expectedByVotehead.values().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new FeeStatus(expectedTerm, expectedByVotehead,
                ledger.getTotalCharged(), ledger.getTotalPaid(),
                ledger.getBalance(), ledger.getArrears(), ledger.getAdvance());
    }

    public record FeeStatus(BigDecimal expectedTerm,
                            Map<String, BigDecimal> expectedByVotehead,
                            BigDecimal charged,
                            BigDecimal paid,
                            BigDecimal balance,
                            BigDecimal arrears,
                            BigDecimal advance) {
    }
}
