package com.schaccs.model.voucher;

import com.schaccs.config.CurrencyConfig;
import com.schaccs.enums.AccountType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public class Imprest {

    public static final String ISSUED = "ISSUED";
    public static final String SURRENDERED = "SURRENDERED";

    private final String id;
    private String staffName;
    private LocalDate date = LocalDate.now();
    private BigDecimal amount = CurrencyConfig.zero();
    private String voteheadCode;
    private String voteheadName;
    private AccountType accountType = AccountType.SCHOOL_FUND;
    private String purpose;
    private String status = ISSUED;
    private BigDecimal surrenderedAmount = CurrencyConfig.zero();
    private LocalDate surrenderDate;

    public Imprest() {
        this.id = UUID.randomUUID().toString();
    }

    public Imprest(String id) {
        this.id = id != null ? id : UUID.randomUUID().toString();
    }

    public static Imprest withId(String id) {
        return new Imprest(id);
    }

    public String getId() {
        return id;
    }

    public String getStaffName() {
        return staffName;
    }

    public void setStaffName(String staffName) {
        this.staffName = staffName;
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = CurrencyConfig.money(amount);
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

    public AccountType getAccountType() {
        return accountType;
    }

    public void setAccountType(AccountType accountType) {
        this.accountType = accountType;
    }

    public String getPurpose() {
        return purpose;
    }

    public void setPurpose(String purpose) {
        this.purpose = purpose;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public BigDecimal getSurrenderedAmount() {
        return surrenderedAmount;
    }

    public void setSurrenderedAmount(BigDecimal surrenderedAmount) {
        this.surrenderedAmount = CurrencyConfig.money(surrenderedAmount);
    }

    public LocalDate getSurrenderDate() {
        return surrenderDate;
    }

    public void setSurrenderDate(LocalDate surrenderDate) {
        this.surrenderDate = surrenderDate;
    }

    @Override
    public String toString() {
        return staffName + " — " + CurrencyConfig.format(amount) + " (" + status + ")";
    }
}
