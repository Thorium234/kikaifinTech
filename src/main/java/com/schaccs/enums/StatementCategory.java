package com.schaccs.enums;

public enum StatementCategory {
    BALANCE_SHEET("Balance Sheet"),
    INCOME_EXPENDITURE("Income & Expenditure");

    private final String displayName;

    StatementCategory(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
