package com.schaccs.model.fee;

import com.schaccs.config.CurrencyConfig;
import com.schaccs.enums.AcademicTerm;
import com.schaccs.enums.BoardingStatus;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.Objects;

public class FeeStructureItem {

    private final String id;
    private String voteheadCode;
    private String voteheadName;
    private AcademicTerm term;
    private BoardingStatus boardingStatus;
    private BigDecimal amount = CurrencyConfig.zero();
    private BigDecimal term1Amount = CurrencyConfig.zero();
    private BigDecimal term2Amount = CurrencyConfig.zero();
    private BigDecimal term3Amount = CurrencyConfig.zero();

    public FeeStructureItem() {
        this.id = UUID.randomUUID().toString();
    }

    public FeeStructureItem(String voteheadCode, String voteheadName, AcademicTerm term,
                            BoardingStatus boardingStatus, BigDecimal amount) {
        this();
        this.voteheadCode = voteheadCode;
        this.voteheadName = voteheadName;
        this.term = term;
        this.boardingStatus = boardingStatus;
        this.amount = CurrencyConfig.money(amount);
        if (term != null) {
            setAmountForTerm(term, amount);
        }
    }

    public FeeStructureItem(String voteheadCode, String voteheadName, BoardingStatus boardingStatus,
                            BigDecimal term1Amount, BigDecimal term2Amount, BigDecimal term3Amount) {
        this();
        this.voteheadCode = voteheadCode;
        this.voteheadName = voteheadName;
        this.boardingStatus = boardingStatus;
        this.term1Amount = CurrencyConfig.money(term1Amount);
        this.term2Amount = CurrencyConfig.money(term2Amount);
        this.term3Amount = CurrencyConfig.money(term3Amount);
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

    public AcademicTerm getTerm() {
        return term;
    }

    public void setTerm(AcademicTerm term) {
        this.term = term;
    }

    public BoardingStatus getBoardingStatus() {
        return boardingStatus;
    }

    public void setBoardingStatus(BoardingStatus boardingStatus) {
        this.boardingStatus = boardingStatus;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = CurrencyConfig.money(amount);
    }

    public BigDecimal getTerm1Amount() {
        return term1Amount;
    }

    public void setTerm1Amount(BigDecimal v) {
        this.term1Amount = CurrencyConfig.money(v);
    }

    public BigDecimal getTerm2Amount() {
        return term2Amount;
    }

    public void setTerm2Amount(BigDecimal v) {
        this.term2Amount = CurrencyConfig.money(v);
    }

    public BigDecimal getTerm3Amount() {
        return term3Amount;
    }

    public void setTerm3Amount(BigDecimal v) {
        this.term3Amount = CurrencyConfig.money(v);
    }

    public BigDecimal amountForTerm(AcademicTerm t) {
        if (t == null) return CurrencyConfig.zero();
        return switch (t) {
            case TERM_1 -> term1Amount;
            case TERM_2 -> term2Amount;
            case TERM_3 -> term3Amount;
        };
    }

    public void setAmountForTerm(AcademicTerm t, BigDecimal v) {
        BigDecimal m = CurrencyConfig.money(v);
        switch (t) {
            case TERM_1 -> term1Amount = m;
            case TERM_2 -> term2Amount = m;
            case TERM_3 -> term3Amount = m;
        }
    }

    public BigDecimal annualTotal() {
        return CurrencyConfig.money(term1Amount.add(term2Amount).add(term3Amount));
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FeeStructureItem that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
