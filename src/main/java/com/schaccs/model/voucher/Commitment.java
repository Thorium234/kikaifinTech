package com.schaccs.model.voucher;

import com.schaccs.config.CurrencyConfig;
import com.schaccs.enums.AccountType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import java.util.Objects;

/**
 * Open commitment / creditor obligation against a votehead budget.
 */
public class Commitment {

    public static final String OPEN = "OPEN";
    public static final String PARTIAL = "PARTIAL";
    public static final String SETTLED = "SETTLED";
    public static final String CANCELLED = "CANCELLED";

    private final String id;
    private LocalDate date = LocalDate.now();
    private String creditorId;
    private String creditorName;
    private String voteheadCode;
    private String voteheadName;
    private AccountType accountType = AccountType.SCHOOL_FUND;
    private String description;
    private BigDecimal amount = CurrencyConfig.zero();
    private BigDecimal amountPaid = CurrencyConfig.zero();
    private String status = OPEN;
    private String reference;

    public Commitment() {
        this.id = UUID.randomUUID().toString();
    }

    private Commitment(String id) {
        this.id = id != null ? id : UUID.randomUUID().toString();
    }

    public static Commitment withId(String id) {
        return new Commitment(id);
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

    public BigDecimal getAmountPaid() {
        return amountPaid;
    }

    public void setAmountPaid(BigDecimal amountPaid) {
        this.amountPaid = CurrencyConfig.money(amountPaid);
        refreshStatus();
    }

    public void applyPayment(BigDecimal paid) {
        setAmountPaid(this.amountPaid.add(CurrencyConfig.money(paid)));
    }

    public BigDecimal getOutstanding() {
        return CurrencyConfig.money(amount.subtract(amountPaid).max(BigDecimal.ZERO));
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getReference() {
        return reference;
    }

    public void setReference(String reference) {
        this.reference = reference;
    }

    private void refreshStatus() {
        if (CANCELLED.equals(status)) {
            return;
        }
        if (amountPaid.compareTo(BigDecimal.ZERO) <= 0) {
            status = OPEN;
        } else if (amountPaid.compareTo(amount) >= 0) {
            status = SETTLED;
        } else {
            status = PARTIAL;
        }
    }

    @Override
    public String toString() {
        return (reference != null ? reference + " — " : "") + creditorName + " (" + status + ")";
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Commitment that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
