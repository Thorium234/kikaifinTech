package com.schaccs.model.voucher;

import com.schaccs.config.CurrencyConfig;
import com.schaccs.enums.AccountType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public class Invoice {

    public static final String UNPAID = "UNPAID";
    public static final String PAID = "PAID";
    public static final String CANCELLED = "CANCELLED";

    private final String id;
    private String invoiceNumber;
    private LocalDate date = LocalDate.now();
    private String creditorId;
    private String creditorName;
    private String lpoId; // Link to associated LPO if any
    private String voteheadCode;
    private String voteheadName;
    private AccountType accountType = AccountType.SCHOOL_FUND;
    private String description;
    private BigDecimal amount = CurrencyConfig.zero();
    private String status = UNPAID;

    public Invoice() {
        this.id = UUID.randomUUID().toString();
    }

    public Invoice(String id) {
        this.id = id != null ? id : UUID.randomUUID().toString();
    }

    public static Invoice withId(String id) {
        return new Invoice(id);
    }

    public String getId() {
        return id;
    }

    public String getInvoiceNumber() {
        return invoiceNumber;
    }

    public void setInvoiceNumber(String invoiceNumber) {
        this.invoiceNumber = invoiceNumber;
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

    public String getLpoId() {
        return lpoId;
    }

    public void setLpoId(String lpoId) {
        this.lpoId = lpoId;
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
        return invoiceNumber + " — " + creditorName + " (" + CurrencyConfig.format(amount) + ")";
    }
}
