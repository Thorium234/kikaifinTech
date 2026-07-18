package com.schaccs.model.finance;

import com.schaccs.config.CurrencyConfig;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class BankReconciliation {

    private final String id;
    private LocalDate statementDate;
    private BigDecimal statementBalance = CurrencyConfig.zero();
    private BigDecimal bookBalance = CurrencyConfig.zero();
    private BigDecimal adjustedBalance = CurrencyConfig.zero();
    private BigDecimal difference = CurrencyConfig.zero();
    private String status = "DRAFT";
    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime reconciledAt;
    private String notes;
    private final List<ReconciliationItem> items = new ArrayList<>();

    public BankReconciliation() {
        this.id = UUID.randomUUID().toString();
    }

    private BankReconciliation(String id) {
        this.id = id != null ? id : UUID.randomUUID().toString();
    }

    public static BankReconciliation withId(String id) { return new BankReconciliation(id); }

    public String getId() { return id; }
    public LocalDate getStatementDate() { return statementDate; }
    public void setStatementDate(LocalDate statementDate) { this.statementDate = statementDate; }
    public BigDecimal getStatementBalance() { return statementBalance; }
    public void setStatementBalance(BigDecimal statementBalance) { this.statementBalance = CurrencyConfig.money(statementBalance); }
    public BigDecimal getBookBalance() { return bookBalance; }
    public void setBookBalance(BigDecimal bookBalance) { this.bookBalance = CurrencyConfig.money(bookBalance); }
    public BigDecimal getAdjustedBalance() { return adjustedBalance; }
    public void setAdjustedBalance(BigDecimal adjustedBalance) { this.adjustedBalance = CurrencyConfig.money(adjustedBalance); }
    public BigDecimal getDifference() { return difference; }
    public void setDifference(BigDecimal difference) { this.difference = CurrencyConfig.money(difference); }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getReconciledAt() { return reconciledAt; }
    public void setReconciledAt(LocalDateTime reconciledAt) { this.reconciledAt = reconciledAt; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public List<ReconciliationItem> getItems() { return items; }
    public void addItem(ReconciliationItem item) { items.add(item); }

    public void calculate() {
        BigDecimal unclearedDeposits = CurrencyConfig.zero();
        BigDecimal unclearedCheques = CurrencyConfig.zero();
        for (ReconciliationItem item : items) {
            if (!item.isCleared()) {
                if ("DEPOSIT".equals(item.getType())) {
                    unclearedDeposits = unclearedDeposits.add(item.getAmount());
                } else if ("CHEQUE".equals(item.getType())) {
                    unclearedCheques = unclearedCheques.add(item.getAmount());
                }
            }
        }
        adjustedBalance = CurrencyConfig.money(statementBalance.add(unclearedDeposits).subtract(unclearedCheques));
        difference = CurrencyConfig.money(adjustedBalance.subtract(bookBalance));
    }

    public static class ReconciliationItem {
        private final String id;
        private String type;
        private String reference;
        private String description;
        private BigDecimal amount = CurrencyConfig.zero();
        private boolean cleared;

        public ReconciliationItem() { this.id = UUID.randomUUID().toString(); }

        public String getId() { return id; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getReference() { return reference; }
        public void setReference(String reference) { this.reference = reference; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public BigDecimal getAmount() { return amount; }
        public void setAmount(BigDecimal amount) { this.amount = CurrencyConfig.money(amount); }
        public boolean isCleared() { return cleared; }
        public void setCleared(boolean cleared) { this.cleared = cleared; }
    }
}
