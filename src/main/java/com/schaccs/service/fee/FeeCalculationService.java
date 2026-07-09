package com.schaccs.service.fee;

import com.schaccs.config.AppConfig;
import com.schaccs.config.CurrencyConfig;
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
    public void chargeAnnualFees(Student student) {
        structureFor(student).ifPresent(structure -> {
            StudentFeeLedger ledger = studentStore.getLedger(student.getId());
            for (FeeStructureItem item : structure.getItems()) {
                // only charge once per votehead total across terms — accumulate by code
                ledger.charge(item.getVoteheadCode(), item.getAmount());
            }
        });
    }

    public void chargeTermFees(Student student, AcademicTerm term) {
        structureFor(student).ifPresent(structure -> {
            StudentFeeLedger ledger = studentStore.getLedger(student.getId());
            for (FeeStructureItem item : structure.itemsForTerm(term)) {
                ledger.charge(item.getVoteheadCode(), item.getAmount());
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
