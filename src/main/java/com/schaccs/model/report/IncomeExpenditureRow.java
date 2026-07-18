package com.schaccs.model.report;

import java.math.BigDecimal;

public class IncomeExpenditureRow {

    private final String category;
    private final String item;
    private final BigDecimal amount;

    public IncomeExpenditureRow(String category, String item, BigDecimal amount) {
        this.category = category;
        this.item = item;
        this.amount = amount;
    }

    public String getCategory() { return category; }
    public String getItem() { return item; }
    public BigDecimal getAmount() { return amount; }
}
