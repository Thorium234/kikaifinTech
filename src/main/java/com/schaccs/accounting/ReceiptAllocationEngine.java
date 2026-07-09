package com.schaccs.accounting;

import com.schaccs.config.CurrencyConfig;
import com.schaccs.model.fee.FeeAllocation;
import com.schaccs.model.finance.Votehead;
import com.schaccs.model.student.StudentFeeLedger;
import com.schaccs.store.FeeStructureStore;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Distributes a payment across outstanding voteheads by priority order.
 * Example: pay 15,000 against Boarding 12,000 + Activity 1,500 + RMI 2,000
 * → Boarding 12,000, Activity 1,500, RMI 1,500.
 */
public class ReceiptAllocationEngine {

    private final FeeStructureStore feeStore;

    public ReceiptAllocationEngine() {
        this(FeeStructureStore.getInstance());
    }

    public ReceiptAllocationEngine(FeeStructureStore feeStore) {
        this.feeStore = feeStore;
    }

    public List<FeeAllocation> allocate(StudentFeeLedger ledger, BigDecimal paymentAmount) {
        List<FeeAllocation> allocations = new ArrayList<>();
        if (paymentAmount == null || paymentAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return allocations;
        }

        BigDecimal remaining = CurrencyConfig.money(paymentAmount);
        Map<String, BigDecimal> outstanding = ledger.getOutstandingByVotehead();

        List<String> orderedCodes = new ArrayList<>(outstanding.keySet());
        orderedCodes.sort(Comparator.comparingInt(this::priorityOf));

        // Arrears first if present (virtual votehead)
        if (ledger.getArrears().compareTo(BigDecimal.ZERO) > 0 && remaining.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal arrears = ledger.getArrears();
            BigDecimal take = arrears.min(remaining);
            if (take.compareTo(BigDecimal.ZERO) > 0) {
                allocations.add(new FeeAllocation("ARREARS", "Outstanding / Arrears", arrears, take));
                remaining = remaining.subtract(take);
            }
        }

        for (String code : orderedCodes) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }
            BigDecimal due = outstanding.get(code);
            if (due == null || due.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            BigDecimal take = due.min(remaining);
            String name = feeStore.voteheadName(code);
            allocations.add(new FeeAllocation(code, name, due, take));
            remaining = remaining.subtract(take);
        }

        // Overpayment as advance / unallocated credit
        if (remaining.compareTo(BigDecimal.ZERO) > 0) {
            allocations.add(new FeeAllocation("ADVANCE", "Advance / Credit", CurrencyConfig.zero(), remaining));
        }

        return allocations;
    }

    private int priorityOf(String code) {
        return feeStore.findVoteheadByCode(code)
                .map(Votehead::getPriority)
                .orElse(999);
    }
}
