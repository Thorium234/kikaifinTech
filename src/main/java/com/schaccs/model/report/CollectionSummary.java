package com.schaccs.model.report;

import com.schaccs.config.CurrencyConfig;
import com.schaccs.enums.PaymentMode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

public class CollectionSummary {

    private final LocalDate date;
    private final PaymentMode paymentMode;
    private final int receiptCount;
    private final BigDecimal totalAmount;

    public CollectionSummary(LocalDate date, PaymentMode paymentMode, int receiptCount, BigDecimal totalAmount) {
        this.date = date;
        this.paymentMode = paymentMode;
        this.receiptCount = receiptCount;
        this.totalAmount = CurrencyConfig.money(totalAmount);
    }

    public LocalDate getDate() {
        return date;
    }

    public PaymentMode getPaymentMode() {
        return paymentMode;
    }

    public int getReceiptCount() {
        return receiptCount;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CollectionSummary that)) return false;
        return Objects.equals(date, that.date) && paymentMode == that.paymentMode;
    }

    @Override
    public int hashCode() {
        return Objects.hash(date, paymentMode);
    }
}
