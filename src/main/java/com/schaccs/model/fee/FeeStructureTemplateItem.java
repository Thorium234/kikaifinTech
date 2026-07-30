package com.schaccs.model.fee;

import com.schaccs.config.CurrencyConfig;
import com.schaccs.enums.AcademicTerm;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

public class FeeStructureTemplateItem {

    private final String id;
    private String voteheadCode;
    private String voteheadName;
    private AcademicTerm term;
    private BigDecimal amount = CurrencyConfig.zero();

    public FeeStructureTemplateItem() {
        this.id = UUID.randomUUID().toString();
    }

    public FeeStructureTemplateItem(String voteheadCode, String voteheadName, AcademicTerm term, BigDecimal amount) {
        this();
        this.voteheadCode = voteheadCode;
        this.voteheadName = voteheadName;
        this.term = term;
        this.amount = CurrencyConfig.money(amount);
    }

    public String getId() { return id; }
    public String getVoteheadCode() { return voteheadCode; }
    public void setVoteheadCode(String voteheadCode) { this.voteheadCode = voteheadCode; }
    public String getVoteheadName() { return voteheadName; }
    public void setVoteheadName(String voteheadName) { this.voteheadName = voteheadName; }
    public AcademicTerm getTerm() { return term; }
    public void setTerm(AcademicTerm term) { this.term = term; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = CurrencyConfig.money(amount); }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FeeStructureTemplateItem that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
