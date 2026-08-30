package com.schaccs.model.finance;

import com.schaccs.config.CurrencyConfig;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * A single imported row from the bank statement (e.g. National Bank of Kenya
 * .xlsx or .csv export). Represents one bank movement that must be matched
 * against unreconciled cashbook items (deposits in transit / unpresented
 * cheques / direct credits & charges).
 */
public class BankStatementEntry {

    private final String id;
    private LocalDate statementDate;
    private String description;
    private String reference;
    private BigDecimal debit = CurrencyConfig.zero();
    private BigDecimal credit = CurrencyConfig.zero();
    private BigDecimal balance = CurrencyConfig.zero();
    private boolean reconciled;
    private String matchedItemId;

    public BankStatementEntry() { this.id = UUID.randomUUID().toString(); }

    public String getId() { return id; }
    public LocalDate getStatementDate() { return statementDate; }
    public void setStatementDate(LocalDate statementDate) { this.statementDate = statementDate; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getReference() { return reference; }
    public void setReference(String reference) { this.reference = reference; }
    public BigDecimal getDebit() { return debit; }
    public void setDebit(BigDecimal debit) { this.debit = CurrencyConfig.money(debit); }
    public BigDecimal getCredit() { return credit; }
    public void setCredit(BigDecimal credit) { this.credit = CurrencyConfig.money(credit); }
    public BigDecimal getBalance() { return balance; }
    public void setBalance(BigDecimal balance) { this.balance = CurrencyConfig.money(balance); }
    public boolean isReconciled() { return reconciled; }
    public void setReconciled(boolean reconciled) { this.reconciled = reconciled; }
    public String getMatchedItemId() { return matchedItemId; }
    public void setMatchedItemId(String matchedItemId) { this.matchedItemId = matchedItemId; }

    public boolean isCredit() { return credit.compareTo(BigDecimal.ZERO) > 0; }
    public boolean isDebit() { return debit.compareTo(BigDecimal.ZERO) > 0; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BankStatementEntry that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
