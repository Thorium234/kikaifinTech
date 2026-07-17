package com.schaccs.model.report;

import com.schaccs.config.CurrencyConfig;

import java.math.BigDecimal;

/**
 * One row in the fee-ageing report (outstanding balance bucketed by days overdue).
 */
public class AgeingBucket {

    private final String label;
    private final BigDecimal amount;
    private final long students;

    public AgeingBucket(String label, BigDecimal amount, long students) {
        this.label = label;
        this.amount = CurrencyConfig.money(amount);
        this.students = students;
    }

    public String getLabel() {
        return label;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public long getStudents() {
        return students;
    }
}
