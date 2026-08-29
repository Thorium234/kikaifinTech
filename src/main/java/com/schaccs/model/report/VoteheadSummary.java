package com.schaccs.model.report;

import com.schaccs.config.CurrencyConfig;

import java.math.BigDecimal;
import java.util.Objects;

public class VoteheadSummary {

    private final String voteheadCode;
    private final String voteheadName;
    private final BigDecimal charged;
    private final BigDecimal collected;
    private final BigDecimal outstanding;
    private final BigDecimal bankBalance;
    private final boolean overdraft;

    public VoteheadSummary(String voteheadCode, String voteheadName,
                           BigDecimal charged, BigDecimal collected) {
        this(voteheadCode, voteheadName, charged, collected, null);
    }

    public VoteheadSummary(String voteheadCode, String voteheadName,
                           BigDecimal charged, BigDecimal collected, BigDecimal bankBalance) {
        this.voteheadCode = voteheadCode;
        this.voteheadName = voteheadName;
        this.charged = CurrencyConfig.money(charged);
        this.collected = CurrencyConfig.money(collected);
        this.outstanding = CurrencyConfig.money(charged.subtract(collected).max(BigDecimal.ZERO));
        this.bankBalance = bankBalance != null ? CurrencyConfig.money(bankBalance) : null;
        this.overdraft = bankBalance != null && collected.compareTo(bankBalance) > 0;
    }

    public String getVoteheadCode() {
        return voteheadCode;
    }

    public String getVoteheadName() {
        return voteheadName;
    }

    public BigDecimal getCharged() {
        return charged;
    }

    public BigDecimal getCollected() {
        return collected;
    }

    public BigDecimal getOutstanding() {
        return outstanding;
    }

    public BigDecimal getBankBalance() {
        return bankBalance;
    }

    /** True when collected income exceeds the cash held in the votehead's ring-fenced bank account. */
    public boolean isOverdraft() {
        return overdraft;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof VoteheadSummary that)) return false;
        return Objects.equals(voteheadCode, that.voteheadCode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(voteheadCode);
    }
}
