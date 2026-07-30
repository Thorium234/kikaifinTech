package com.schaccs.model.voucher;

import com.schaccs.config.CurrencyConfig;
import com.schaccs.enums.AccountType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import java.util.Objects;

public class Lpo {

    public static final String ISSUED = "ISSUED";
    public static final String INVOICED = "INVOICED";
    public static final String CANCELLED = "CANCELLED";

    private final String id;
    private String lpoNumber;
    private LocalDate date = LocalDate.now();
    private String creditorId;
    private String creditorName;
    private String voteheadCode;
    private String voteheadName;
    private AccountType accountType = AccountType.SCHOOL_FUND;
    private String description;
    private BigDecimal amount = CurrencyConfig.zero();
    private String status = ISSUED;

    public Lpo() {
        this.id = UUID.randomUUID().toString();
    }

    public Lpo(String id) {
        this.id = id != null ? id : UUID.randomUUID().toString();
    }

    public static Lpo withId(String id) {
        return new Lpo(id);
    }

    public String getId() {
        return id;
    }

    public String getLpoNumber() {
        return lpoNumber;
    }

    public void setLpoNumber(String lpoNumber) {
        this.lpoNumber = lpoNumber;
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public String getCreditorId() {
        return creditorId;
    }

    public void setCreditorId(String creditorId) {
        this.creditorId = creditorId;
    }

    public String getCreditorName() {
        return creditorName;
    }

    public void setCreditorName(String creditorName) {
        this.creditorName = creditorName;
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

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = CurrencyConfig.money(amount);
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    @Override
    public String toString() {
        return lpoNumber + " — " + creditorName + " (" + CurrencyConfig.format(amount) + ")";
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Lpo that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
