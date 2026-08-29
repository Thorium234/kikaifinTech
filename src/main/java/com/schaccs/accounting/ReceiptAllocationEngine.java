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

        List<FeeStructureItem> termItems = feeStructure.itemsForTerm(term);
        Set<String> termCodes = termItems.stream()
                .map(FeeStructureItem::getVoteheadCode)
                .collect(Collectors.toSet());

        Map<String, String> structureNames = termItems.stream()
                .collect(Collectors.toMap(FeeStructureItem::getVoteheadCode,
                        FeeStructureItem::getVoteheadName,
                        (a, b) -> a, LinkedHashMap::new));

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
                .filter(e -> e.getValue().compareTo(BigDecimal.ZERO) > 0)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                        (a, b) -> a, LinkedHashMap::new));

        if (!outstanding.isEmpty() && remaining.compareTo(BigDecimal.ZERO) > 0) {
            remaining = distributeEqually(allocations, outstanding, remaining, structureNames);
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
                                          BigDecimal remaining,
                                          Map<String, String> structureNames) {
        int maxIterations = outstanding.size() * 10 + 10;
        int iterations = 0;
        while (!outstanding.isEmpty() && remaining.compareTo(BigDecimal.ZERO) > 0) {
            if (++iterations > maxIterations) break;
            int count = outstanding.size();
            BigDecimal equalShare = remaining.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
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

                String name = structureNames.getOrDefault(entry.getKey(),
                        feeStore.voteheadName(entry.getKey()));
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
     * Cascade allocation across the full academic year. Satisfies the current
     * term first, then pushes any overflow into the subsequent terms' voteheads
     * in chronological order (e.g. an overpayment on Term 1 flows into Term 2,
     * then Term 3), preserving the spec'd "push the remainder to the next term"
     * behaviour. Only if every term for the year is fully covered does the
     * remainder become an unallocated advance credit.
     *
     * <p>Step order per term: advance credit consumed first, then arrears, then
     * equal distribution across that term's outstanding voteheads — with any
     * residual cascading to the next term rather than to Advance immediately.
     */
    public List<FeeAllocation> allocateCascading(StudentFeeLedger ledger, BigDecimal paymentAmount,
                                                  FeeStructure feeStructure, AcademicTerm fromTerm) {
        List<FeeAllocation> allocations = new ArrayList<>();
        if (paymentAmount == null || paymentAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return allocations;
        }
        if (fromTerm == null) {
            return allocate(ledger, paymentAmount, feeStructure, fromTerm);
        }

        BigDecimal remaining = CurrencyConfig.money(paymentAmount);

        // Priority: existing advance credit, then confessional arrears
        if (ledger.getAdvance().compareTo(BigDecimal.ZERO) > 0 && remaining.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal take = ledger.getAdvance().min(remaining);
            allocations.add(new FeeAllocation(StudentFeeLedger.ADVANCE_CODE, "Advance / Credit",
                    ledger.getAdvance(), take));
            remaining = remaining.subtract(take);
        }
        if (ledger.getArrears().compareTo(BigDecimal.ZERO) > 0 && remaining.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal take = ledger.getArrears().min(remaining);
            allocations.add(new FeeAllocation("ARREARS", "Outstanding / Arrears",
                    ledger.getArrears(), take));
            remaining = remaining.subtract(take);
        }

        // Cascade through remaining terms of the academic year, in order.
        List<AcademicTerm> terms = new ArrayList<>();
        for (AcademicTerm t : AcademicTerm.values()) {
            if (t != null && t.ordinal() >= fromTerm.ordinal()) {
                terms.add(t);
            }
        }
        for (AcademicTerm term : terms) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) break;
            List<FeeStructureItem> termItems = feeStructure.itemsForTerm(term);
            if (termItems.isEmpty()) continue;
            Map<String, String> names = new LinkedHashMap<>();
            for (FeeStructureItem item : termItems) {
                names.put(item.getVoteheadCode(), item.getVoteheadName());
            }

            if (term == fromTerm) {
                // Current term: allocate only against voteheads the student is
                // ALREADY charged for (equal distribution), so a payment for an
                // existing balance is not diluted across unrelated planned fee
                // items. Only genuine overflow cascades to later terms below.
                Map<String, BigDecimal> outstanding = ledger.getOutstandingByVotehead().entrySet().stream()
                        .filter(e -> termItems.stream().anyMatch(i -> i.getVoteheadCode().equals(e.getKey())))
                        .filter(e -> e.getValue().compareTo(BigDecimal.ZERO) > 0)
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                                (a, b) -> a, LinkedHashMap::new));
                if (!outstanding.isEmpty()) {
                    remaining = distributeEqually(allocations, outstanding, remaining, names);
                }
            } else {
                // Later terms: treat each planned term fee as a cascade target so an
                // overpayment flows forward into future billing.
                Map<String, BigDecimal> outstanding = new LinkedHashMap<>();
                for (FeeStructureItem item : termItems) {
                    BigDecimal due = ledger.getOutstanding(item.getVoteheadCode());
                    BigDecimal planned = item.getAmount();
                    BigDecimal target = due;
                    if (due == null) {
                        target = planned;
                    } else if (planned != null && planned.compareTo(due) > 0) {
                        BigDecimal charged = ledger.getCharged(item.getVoteheadCode());
                        if (charged != null && charged.signum() == 0) {
                            target = planned;
                        }
                    }
                    if (target != null && target.compareTo(BigDecimal.ZERO) > 0) {
                        outstanding.put(item.getVoteheadCode(), target);
                    }
                }
                if (!outstanding.isEmpty()) {
                    remaining = distributeEqually(allocations, outstanding, remaining, names);
                }
            }
        }

        // Only after every term is satisfied does any remainder become Advance.
        if (remaining.compareTo(BigDecimal.ZERO) > 0) {
            allocations.add(new FeeAllocation(StudentFeeLedger.ADVANCE_CODE, "Advance / Credit",
                    CurrencyConfig.zero(), remaining));
        }
        return allocations;
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
