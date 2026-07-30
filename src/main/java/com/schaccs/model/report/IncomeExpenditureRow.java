package com.schaccs.model.report;

import java.math.BigDecimal;
import java.util.Objects;

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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof IncomeExpenditureRow that)) return false;
        return Objects.equals(category, that.category) && Objects.equals(item, that.item);
    }

    @Override
    public int hashCode() {
        return Objects.hash(category, item);
    }
}
