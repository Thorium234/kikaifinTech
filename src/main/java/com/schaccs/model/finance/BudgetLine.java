package com.schaccs.model.finance;

import com.schaccs.config.CurrencyConfig;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.Objects;

public class BudgetLine {

    private final String id;
    private String budgetId;
    private String accountId;
    private String voteheadCode;
    private BigDecimal allocatedAmount = CurrencyConfig.zero();
    private BigDecimal spentAmount = CurrencyConfig.zero();
    private BigDecimal committedAmount = CurrencyConfig.zero();

    public BudgetLine() {
        this.id = UUID.randomUUID().toString();
    }

    private BudgetLine(String id) {
        this.id = id != null ? id : UUID.randomUUID().toString();
    }

    public static BudgetLine withId(String id) {
        return new BudgetLine(id);
    }

    public String getId() { return id; }
    public String getBudgetId() { return budgetId; }
    public void setBudgetId(String budgetId) { this.budgetId = budgetId; }
    public String getAccountId() { return accountId; }
    public void setAccountId(String accountId) { this.accountId = accountId; }
    public String getVoteheadCode() { return voteheadCode; }
    public void setVoteheadCode(String voteheadCode) { this.voteheadCode = voteheadCode; }
    public BigDecimal getAllocatedAmount() { return allocatedAmount; }
    public void setAllocatedAmount(BigDecimal allocatedAmount) { this.allocatedAmount = CurrencyConfig.money(allocatedAmount); }
    public BigDecimal getSpentAmount() { return spentAmount; }
    public void setSpentAmount(BigDecimal spentAmount) { this.spentAmount = CurrencyConfig.money(spentAmount); }
    public BigDecimal getCommittedAmount() { return committedAmount; }
    public void setCommittedAmount(BigDecimal committedAmount) { this.committedAmount = CurrencyConfig.money(committedAmount); }

    public BigDecimal getAvailableAmount() {
        return CurrencyConfig.money(allocatedAmount.subtract(spentAmount).subtract(committedAmount));
    }

    public double getUtilizationPercent() {
        if (allocatedAmount.compareTo(BigDecimal.ZERO) <= 0) return 0.0;
        BigDecimal used = spentAmount.add(committedAmount);
        return used.multiply(BigDecimal.valueOf(100))
                .divide(allocatedAmount, 2, java.math.RoundingMode.HALF_UP)
                .doubleValue();
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BudgetLine that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
