package com.schaccs.service.fee;

import com.schaccs.config.AppConfig;
import com.schaccs.config.CurrencyConfig;
import com.schaccs.config.SchoolProfile;
import com.schaccs.enums.AcademicTerm;
import com.schaccs.enums.BoardingStatus;
import com.schaccs.model.fee.FeeStructure;
import com.schaccs.model.fee.FeeStructureItem;
import com.schaccs.model.student.Student;
import com.schaccs.model.student.StudentFeeLedger;
import com.schaccs.store.FeeStructureStore;
import com.schaccs.store.StudentStore;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public class FeeCalculationService {

    private final FeeStructureStore feeStore;
    private final StudentStore studentStore;

    public FeeCalculationService() {
        this(FeeStructureStore.getInstance(), StudentStore.getInstance());
    }

    public FeeCalculationService(FeeStructureStore feeStore, StudentStore studentStore) {
        this.feeStore = feeStore;
        this.studentStore = studentStore;
    }

    public Optional<FeeStructure> structureFor(Student student) {
        int year = student.getAcademicYear() != null
                ? student.getAcademicYear()
                : AppConfig.getInstance().getAcademicYear();
        return feeStore.findStructure(year, student.getBoardingStatus());
    }

    /**
     * Apply annual fee structure charges to the student ledger (all terms).
     */
    /**
     * Apply annual fee structure charges to the student ledger (all terms).
     * Idempotent: does nothing if the ledger already has charges (e.g. on re-edit),
     * to avoid double-charging the same voteheads.
     * Applies a sibling discount when enabled and this student is not the first
     * child sharing the same guardian key.
     */
    public void chargeAnnualFees(Student student) {
        StudentFeeLedger ledger = studentStore.getLedger(student.getId());
        if (!ledger.getChargedByVotehead().isEmpty()) {
            return;
        }
        structureFor(student).ifPresent(structure -> {
            BigDecimal factor = siblingDiscountFactor(student);
            for (FeeStructureItem item : structure.getItems()) {
                BigDecimal amount = CurrencyConfig.money(item.getAmount().multiply(factor));
                ledger.charge(item.getVoteheadCode(), amount);
            }
        });
    }

    /** Returns the multiplier applied to fees (1.0 normally, <1.0 for discounted siblings). */
    private BigDecimal siblingDiscountFactor(Student student) {
        SchoolProfile profile = AppConfig.getInstance().getSchoolProfile();
        if (!profile.isSiblingDiscountEnabled() || student.getGuardianKey() == null
                || student.getGuardianKey().trim().isEmpty()) {
            return CurrencyConfig.money("1.00");
        }
        boolean isFirstSibling = studentStore.getStudents().stream()
                .filter(s -> student.getGuardianKey().equalsIgnoreCase(s.getGuardianKey()))
                .findFirst()
                .map(first -> first.getId().equals(student.getId()))
                .orElse(true);
        if (isFirstSibling) {
            return CurrencyConfig.money("1.00");
        }
        BigDecimal rate = profile.getSiblingDiscountRate().max(CurrencyConfig.zero()).min(CurrencyConfig.money("0.50"));
        return CurrencyConfig.money(BigDecimal.ONE.subtract(rate));
    }

    public void chargeTermFees(Student student, AcademicTerm term) {
        StudentFeeLedger ledger = studentStore.getLedger(student.getId());
        if (term == ledger.getCurrentTerm() && !ledger.getChargedByVotehead().isEmpty()) {
            return;
        }
        structureFor(student).ifPresent(structure -> {
            boolean alreadyCharged = structure.itemsForTerm(term).stream()
                    .anyMatch(item -> ledger.getCharged(item.getVoteheadCode()).compareTo(BigDecimal.ZERO) > 0);
            if (alreadyCharged) {
                return;
            }
            BigDecimal factor = siblingDiscountFactor(student);
            for (FeeStructureItem item : structure.itemsForTerm(term)) {
                BigDecimal amount = CurrencyConfig.money(item.getAmount().multiply(factor));
                ledger.charge(item.getVoteheadCode(), amount);
            }
            ledger.setCurrentTerm(term);
        });
    }

    public BigDecimal expectedAnnualFee(BoardingStatus status) {
        return feeStore.findStructure(AppConfig.getInstance().getAcademicYear(), status)
                .map(FeeStructure::grandTotal)
                .orElse(CurrencyConfig.zero());
    }

    public BigDecimal expectedTermFee(BoardingStatus status, AcademicTerm term) {
        return feeStore.findStructure(AppConfig.getInstance().getAcademicYear(), status)
                .map(s -> s.totalForTerm(term))
                .orElse(CurrencyConfig.zero());
    }

    public List<FeeStructure> allStructures() {
        return feeStore.getStructures();
    }
}
