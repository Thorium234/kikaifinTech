package com.schaccs.model.report;

import com.schaccs.enums.AccountType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

public class CashbookRow {

    private final LocalDate date;
    private final String reference;
    private final String description;
    private final BigDecimal receipts;
    private final BigDecimal payments;
    private final BigDecimal balance;
    private final AccountType accountType;

    public CashbookRow(LocalDate date, String reference, String description,
                       BigDecimal receipts, BigDecimal payments, BigDecimal balance) {
        this(date, reference, description, receipts, payments, balance, null);
    }

    public CashbookRow(LocalDate date, String reference, String description,
                       BigDecimal receipts, BigDecimal payments, BigDecimal balance,
                       AccountType accountType) {
        this.date = date;
        this.reference = reference;
        this.description = description;
        this.receipts = receipts;
        this.payments = payments;
        this.balance = balance;
        this.accountType = accountType;
    }

    public LocalDate getDate() { return date; }
    public String getReference() { return reference; }
    public String getDescription() { return description; }
    public BigDecimal getReceipts() { return receipts; }
    public BigDecimal getPayments() { return payments; }
    public BigDecimal getBalance() { return balance; }
    public AccountType getAccountType() { return accountType; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CashbookRow that)) return false;
        return Objects.equals(reference, that.reference);
    }

    @Override
    public int hashCode() {
        return Objects.hash(reference);
    }
}
