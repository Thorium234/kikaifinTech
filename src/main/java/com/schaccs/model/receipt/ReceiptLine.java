package com.schaccs.model.receipt;

import com.schaccs.config.CurrencyConfig;

import java.math.BigDecimal;
import java.util.UUID;

public class ReceiptLine {

    private final String id;
    private String voteheadCode;
    private String voteheadName;
    private BigDecimal amount = CurrencyConfig.zero();
    private BigDecimal outstandingBefore = CurrencyConfig.zero();

    public ReceiptLine() {
        this.id = UUID.randomUUID().toString();
    }

    public ReceiptLine(String voteheadCode, String voteheadName, BigDecimal amount) {
        this();
        this.voteheadCode = voteheadCode;
        this.voteheadName = voteheadName;
        this.amount = CurrencyConfig.money(amount);
    }

    public ReceiptLine(String voteheadCode, String voteheadName, BigDecimal amount, BigDecimal outstandingBefore) {
        this(voteheadCode, voteheadName, amount);
        this.outstandingBefore = CurrencyConfig.money(outstandingBefore);
    }

    public String getId() {
        return id;
    }

    public String getVoteheadCode() {
        return voteheadCode;
    }

    public void setVoteheadCode(String voteheadCode) {
        this.voteheadCode = voteheadCode;
    }

    public String getVoteheadName() {
        return voteheadName;
    }

    public void setVoteheadName(String voteheadName) {
        this.voteheadName = voteheadName;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = CurrencyConfig.money(amount);
    }

    public BigDecimal getOutstandingBefore() {
        return outstandingBefore;
    }

    public void setOutstandingBefore(BigDecimal outstandingBefore) {
        this.outstandingBefore = CurrencyConfig.money(outstandingBefore);
    }
}
