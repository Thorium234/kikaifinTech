package com.schaccs.model.report;

import java.math.BigDecimal;

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
}
