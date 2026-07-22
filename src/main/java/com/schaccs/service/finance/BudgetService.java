package com.schaccs.service.finance;

import com.schaccs.config.CurrencyConfig;
import com.schaccs.model.finance.Budget;
import com.schaccs.model.finance.BudgetLine;
import com.schaccs.store.AccountStore;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class BudgetService {

    private final AccountStore accountStore;

    public BudgetService() {
        this(AccountStore.getInstance());
    }

    public BudgetService(AccountStore accountStore) {
        this.accountStore = accountStore;
    }

    public Budget createBudget(String name, String fiscalYearId, List<BudgetLine> lines) {
        Budget budget = new Budget();
        budget.setName(name);
        budget.setFiscalYearId(fiscalYearId);
        accountStore.getBudgets().add(budget);
        for (BudgetLine line : lines) {
            line.setBudgetId(budget.getId());
            accountStore.getBudgetLines().add(line);
        }
        return budget;
    }

    public void approveBudget(Budget budget) {
        budget.setApproved(true);
        budget.setApprovedAt(LocalDateTime.now());
    }

    public boolean checkBudget(String voteheadCode, BigDecimal amount) {
        List<BudgetLine> lines = accountStore.findBudgetLinesByVoteheadCode(voteheadCode);
        if (lines.isEmpty()) return true;
        BigDecimal totalAvailable = CurrencyConfig.zero();
        for (BudgetLine line : lines) {
            totalAvailable = totalAvailable.add(line.getAvailableAmount());
        }
        return totalAvailable.compareTo(amount) >= 0;
    }

    public void recordSpend(String voteheadCode, BigDecimal amount) {
        for (BudgetLine line : accountStore.findBudgetLinesByVoteheadCode(voteheadCode)) {
            BigDecimal current = line.getSpentAmount();
            line.setSpentAmount(current.add(amount));
        }
    }

    public void recordCommitment(String voteheadCode, BigDecimal amount) {
        for (BudgetLine line : accountStore.findBudgetLinesByVoteheadCode(voteheadCode)) {
            BigDecimal current = line.getCommittedAmount();
            line.setCommittedAmount(current.add(amount));
        }
    }

    public List<BudgetVarianceRow> getVarianceReport(String budgetId) {
        List<BudgetVarianceRow> rows = new ArrayList<>();
        for (BudgetLine line : accountStore.findBudgetLinesByBudgetId(budgetId)) {
            rows.add(new BudgetVarianceRow(
                    line.getVoteheadCode(),
                    line.getAllocatedAmount(),
                    line.getSpentAmount(),
                    line.getCommittedAmount(),
                    line.getAvailableAmount(),
                    line.getUtilizationPercent()
            ));
        }
        return rows;
    }

    public double getOverallUtilization(String fiscalYearId) {
        BigDecimal totalAllocated = CurrencyConfig.zero();
        BigDecimal totalUsed = CurrencyConfig.zero();
        for (Budget budget : accountStore.getBudgets()) {
            if (!fiscalYearId.equals(budget.getFiscalYearId())) continue;
            for (BudgetLine line : accountStore.findBudgetLinesByBudgetId(budget.getId())) {
                totalAllocated = totalAllocated.add(line.getAllocatedAmount());
                totalUsed = totalUsed.add(line.getSpentAmount()).add(line.getCommittedAmount());
            }
        }
        if (totalAllocated.compareTo(BigDecimal.ZERO) <= 0) return 0.0;
        return totalUsed.multiply(BigDecimal.valueOf(100))
                .divide(totalAllocated, 2, java.math.RoundingMode.HALF_UP)
                .doubleValue();
    }

    public static class BudgetVarianceRow {
        private final String voteheadCode;
        private final BigDecimal allocated;
        private final BigDecimal spent;
        private final BigDecimal committed;
        private final BigDecimal available;
        private final double utilizationPercent;

        public BudgetVarianceRow(String voteheadCode, BigDecimal allocated, BigDecimal spent,
                                 BigDecimal committed, BigDecimal available, double utilizationPercent) {
            this.voteheadCode = voteheadCode;
            this.allocated = CurrencyConfig.money(allocated);
            this.spent = CurrencyConfig.money(spent);
            this.committed = CurrencyConfig.money(committed);
            this.available = CurrencyConfig.money(available);
            this.utilizationPercent = utilizationPercent;
        }

        public String getVoteheadCode() { return voteheadCode; }
        public BigDecimal getAllocated() { return allocated; }
        public BigDecimal getSpent() { return spent; }
        public BigDecimal getCommitted() { return committed; }
        public BigDecimal getAvailable() { return available; }
        public double getUtilizationPercent() { return utilizationPercent; }
    }
}
