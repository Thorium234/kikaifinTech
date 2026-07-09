package com.schaccs.model.report;

import com.schaccs.config.CurrencyConfig;

import java.math.BigDecimal;

public class VoteheadSummary {

    private final String voteheadCode;
    private final String voteheadName;
    private final BigDecimal charged;
    private final BigDecimal collected;
    private final BigDecimal outstanding;

    public VoteheadSummary(String voteheadCode, String voteheadName,
                           BigDecimal charged, BigDecimal collected) {
        this.voteheadCode = voteheadCode;
        this.voteheadName = voteheadName;
        this.charged = CurrencyConfig.money(charged);
        this.collected = CurrencyConfig.money(collected);
        this.outstanding = CurrencyConfig.money(charged.subtract(collected).max(BigDecimal.ZERO));
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
}
