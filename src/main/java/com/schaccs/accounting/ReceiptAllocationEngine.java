package com.schaccs.accounting;

import com.schaccs.config.CurrencyConfig;
import com.schaccs.enums.AcademicTerm;
import com.schaccs.model.fee.FeeAllocation;
import com.schaccs.model.fee.FeeStructure;
import com.schaccs.model.fee.FeeStructureItem;
import com.schaccs.model.finance.Votehead;
import com.schaccs.model.student.StudentFeeLedger;
import com.schaccs.store.FeeStructureStore;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class ReceiptAllocationEngine {

    private final FeeStructureStore feeStore;

    public ReceiptAllocationEngine() {
        this(FeeStructureStore.getInstance());
    }

    public ReceiptAllocationEngine(FeeStructureStore feeStore) {
        this.feeStore = feeStore;
    }

    /**
     * Equal-distribution allocation across current-term voteheads.
     * Order: advance credit → arrears → equal share among current-term voteheads → overflow to advance.
     */
    public List<FeeAllocation> allocate(StudentFeeLedger ledger, BigDecimal paymentAmount,
                                         FeeStructure feeStructure, AcademicTerm term) {
        List<FeeAllocation> allocations = new ArrayList<>();
        if (paymentAmount == null || paymentAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return allocations;
        }

        Set<String> termCodes = feeStructure.itemsForTerm(term).stream()
                .map(FeeStructureItem::getVoteheadCode)
                .collect(Collectors.toSet());

        BigDecimal remaining = CurrencyConfig.money(paymentAmount);

        // 1. Apply existing advance/credit balance first
        if (ledger.getAdvance().compareTo(BigDecimal.ZERO) > 0 && remaining.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal available = ledger.getAdvance();
            BigDecimal take = available.min(remaining);
            if (take.compareTo(BigDecimal.ZERO) > 0) {
                allocations.add(new FeeAllocation(StudentFeeLedger.ADVANCE_CODE, "Advance / Credit", available, take));
                remaining = remaining.subtract(take);
            }
        }

        // 2. Clear historical arrears
        if (ledger.getArrears().compareTo(BigDecimal.ZERO) > 0 && remaining.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal arrears = ledger.getArrears();
            BigDecimal take = arrears.min(remaining);
            if (take.compareTo(BigDecimal.ZERO) > 0) {
                allocations.add(new FeeAllocation("ARREARS", "Outstanding / Arrears", arrears, take));
                remaining = remaining.subtract(take);
            }
        }

        // 3. Equal distribution across current-term outstanding voteheads
        Map<String, BigDecimal> outstanding = ledger.getOutstandingByVotehead().entrySet().stream()
                .filter(e -> termCodes.contains(e.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                        (a, b) -> a, LinkedHashMap::new));

        if (!outstanding.isEmpty() && remaining.compareTo(BigDecimal.ZERO) > 0) {
            remaining = distributeEqually(allocations, outstanding, remaining);
        }

        // 4. Overflow to advance credit
        if (remaining.compareTo(BigDecimal.ZERO) > 0) {
            allocations.add(new FeeAllocation(StudentFeeLedger.ADVANCE_CODE, "Advance / Credit",
                    CurrencyConfig.zero(), remaining));
        }

        return allocations;
    }

    private BigDecimal distributeEqually(List<FeeAllocation> allocations,
                                          Map<String, BigDecimal> outstanding,
                                          BigDecimal remaining) {
        while (!outstanding.isEmpty() && remaining.compareTo(BigDecimal.ZERO) > 0) {
            int count = outstanding.size();
            BigDecimal equalShare = remaining.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_DOWN);
            BigDecimal expectedTotal = equalShare.multiply(BigDecimal.valueOf(count));
            BigDecimal remainderCents = remaining.subtract(expectedTotal);

            BigDecimal roundDistributed = CurrencyConfig.zero();
            List<String> settled = new ArrayList<>();

            for (Map.Entry<String, BigDecimal> entry : outstanding.entrySet()) {
                BigDecimal due = entry.getValue();
                BigDecimal alloc = equalShare;

                // Distribute remainder cents one-by-one
                if (remainderCents.compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal cent = CurrencyConfig.money("0.01");
                    alloc = alloc.add(cent);
                    remainderCents = remainderCents.subtract(cent);
                }

                alloc = alloc.min(due).min(remaining.subtract(roundDistributed));
                if (alloc.compareTo(BigDecimal.ZERO) <= 0) continue;

                String name = feeStore.voteheadName(entry.getKey());
                allocations.add(new FeeAllocation(entry.getKey(), name, due, alloc));
                roundDistributed = roundDistributed.add(alloc);

                BigDecimal remainingDue = due.subtract(alloc);
                if (remainingDue.compareTo(BigDecimal.ZERO) <= 0) {
                    settled.add(entry.getKey());
                } else {
                    entry.setValue(remainingDue);
                }
            }

            remaining = remaining.subtract(roundDistributed);
            settled.forEach(outstanding::remove);
        }
        return remaining;
    }

    /**
     * Legacy priority-based allocation — only used when term structure is unavailable.
     * Prefer allocate(ledger, amount, feeStructure, term) for equal distribution.
     */
    public List<FeeAllocation> allocate(StudentFeeLedger ledger, BigDecimal paymentAmount) {
        List<FeeAllocation> allocations = new ArrayList<>();
        if (paymentAmount == null || paymentAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return allocations;
        }
        BigDecimal remaining = CurrencyConfig.money(paymentAmount);
        Map<String, BigDecimal> outstanding = ledger.getOutstandingByVotehead();
        List<String> orderedCodes = new ArrayList<>(outstanding.keySet());
        orderedCodes.sort(Comparator.comparingInt(this::priorityOf));

        if (ledger.getAdvance().compareTo(BigDecimal.ZERO) > 0 && remaining.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal available = ledger.getAdvance();
            BigDecimal take = available.min(remaining);
            if (take.compareTo(BigDecimal.ZERO) > 0) {
                allocations.add(new FeeAllocation(StudentFeeLedger.ADVANCE_CODE, "Advance / Credit", available, take));
                remaining = remaining.subtract(take);
            }
        }
        if (ledger.getArrears().compareTo(BigDecimal.ZERO) > 0 && remaining.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal arrears = ledger.getArrears();
            BigDecimal take = arrears.min(remaining);
            if (take.compareTo(BigDecimal.ZERO) > 0) {
                allocations.add(new FeeAllocation("ARREARS", "Outstanding / Arrears", arrears, take));
                remaining = remaining.subtract(take);
            }
        }
        for (String code : orderedCodes) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) break;
            BigDecimal due = outstanding.get(code);
            if (due == null || due.compareTo(BigDecimal.ZERO) <= 0) continue;
            BigDecimal take = due.min(remaining);
            allocations.add(new FeeAllocation(code, feeStore.voteheadName(code), due, take));
            remaining = remaining.subtract(take);
        }
        if (remaining.compareTo(BigDecimal.ZERO) > 0) {
            allocations.add(new FeeAllocation(StudentFeeLedger.ADVANCE_CODE, "Advance / Credit",
                    CurrencyConfig.zero(), remaining));
        }
        return allocations;
    }

    private int priorityOf(String code) {
        return feeStore.findVoteheadByCode(code)
                .map(Votehead::getPriority)
                .orElse(999);
    }
}
