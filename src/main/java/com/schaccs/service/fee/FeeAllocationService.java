package com.schaccs.service.fee;

import com.schaccs.accounting.ReceiptAllocationEngine;
import com.schaccs.enums.AcademicTerm;
import com.schaccs.model.fee.FeeAllocation;
import com.schaccs.model.fee.FeeStructure;
import com.schaccs.model.student.StudentFeeLedger;

import java.math.BigDecimal;
import java.util.List;

public class FeeAllocationService {

    private final ReceiptAllocationEngine engine;

    public FeeAllocationService() {
        this(new ReceiptAllocationEngine());
    }

    public FeeAllocationService(ReceiptAllocationEngine engine) {
        this.engine = engine;
    }

    public List<FeeAllocation> preview(StudentFeeLedger ledger, BigDecimal amount,
                                        FeeStructure feeStructure, AcademicTerm term) {
        return engine.allocate(ledger, amount, feeStructure, term);
    }

    public List<FeeAllocation> preview(StudentFeeLedger ledger, BigDecimal amount) {
        return engine.allocate(ledger, amount);
    }
}
