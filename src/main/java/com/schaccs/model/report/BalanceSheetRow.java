package com.schaccs.model.report;

import java.math.BigDecimal;
import java.util.Objects;

public class BalanceSheetRow {

    private final String section;
    private final String item;
    private final BigDecimal amount;

    public BalanceSheetRow(String section, String item, BigDecimal amount) {
        this.section = section;
        this.item = item;
        this.amount = amount;
    }

    public String getSection() { return section; }
    public String getItem() { return item; }
    public BigDecimal getAmount() { return amount; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BalanceSheetRow that)) return false;
        return Objects.equals(section, that.section) && Objects.equals(item, that.item);
    }

    @Override
    public int hashCode() {
        return Objects.hash(section, item);
    }
}
