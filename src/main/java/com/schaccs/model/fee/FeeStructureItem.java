package com.schaccs.model.fee;

import com.schaccs.config.CurrencyConfig;
import com.schaccs.enums.AcademicTerm;
import com.schaccs.enums.BoardingStatus;

import java.math.BigDecimal;
import java.util.UUID;

public class FeeStructureItem {

    private final String id;
    private String voteheadCode;
    private String voteheadName;
    private AcademicTerm term;
    private BoardingStatus boardingStatus;
    private BigDecimal amount = CurrencyConfig.zero();

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
}
