package com.schaccs.model.finance;

import com.schaccs.config.CurrencyConfig;
import com.schaccs.enums.AccountType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import java.util.Objects;

public class LedgerEntry {

    private final String id;
    private LocalDate date;
    private AccountType accountType;
    private String voteheadCode;
    private String reference;
    private String description;
    private BigDecimal debit = CurrencyConfig.zero();
    private BigDecimal credit = CurrencyConfig.zero();
    private BigDecimal balance = CurrencyConfig.zero();
    private String transactionId;

    public LedgerEntry() {
        this.id = UUID.randomUUID().toString();
    }

    private LedgerEntry(String id) {
        this.id = id != null ? id : UUID.randomUUID().toString();
    }

    public static LedgerEntry withId(String id) {
        return new LedgerEntry(id);
    }

    public String getId() {
        return id;
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public AccountType getAccountType() {
        return accountType;
    }

    public void setAccountType(AccountType accountType) {
        this.accountType = accountType;
    }

    public String getVoteheadCode() {
        return voteheadCode;
    }

    public void setVoteheadCode(String voteheadCode) {
        this.voteheadCode = voteheadCode;
    }

    public String getReference() {
        return reference;
    }

    public void setReference(String reference) {
        this.reference = reference;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BigDecimal getDebit() {
        return debit;
    }

    public void setDebit(BigDecimal debit) {
        this.debit = CurrencyConfig.money(debit);
    }

    public BigDecimal getCredit() {
        return credit;
    }

    public void setCredit(BigDecimal credit) {
        this.credit = CurrencyConfig.money(credit);
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public void setBalance(BigDecimal balance) {
        this.balance = CurrencyConfig.money(balance);
    }

    public String getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(String transactionId) {
        this.transactionId = transactionId;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof LedgerEntry that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
