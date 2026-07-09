package com.schaccs.model.fee;

import com.schaccs.config.CurrencyConfig;

import java.math.BigDecimal;

/**
 * Result of automatic votehead distribution for a single payment line.
 */
public class FeeAllocation {

    private final String voteheadCode;
    private final String voteheadName;
    private final BigDecimal outstandingBefore;
    private final BigDecimal allocated;
    private final BigDecimal outstandingAfter;

    public FeeAllocation(String voteheadCode, String voteheadName,
                         BigDecimal outstandingBefore, BigDecimal allocated) {
        this.voteheadCode = voteheadCode;
        this.voteheadName = voteheadName;
        this.outstandingBefore = CurrencyConfig.money(outstandingBefore);
        this.allocated = CurrencyConfig.money(allocated);
        this.outstandingAfter = CurrencyConfig.money(outstandingBefore.subtract(allocated));
    }

    public String getVoteheadCode() {
        return voteheadCode;
    }

    public String getVoteheadName() {
        return voteheadName;
    }

    public BigDecimal getOutstandingBefore() {
        return outstandingBefore;
    }

    public BigDecimal getAllocated() {
        return allocated;
    }

    public BigDecimal getOutstandingAfter() {
        return outstandingAfter;
    }
}
