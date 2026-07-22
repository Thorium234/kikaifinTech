package com.schaccs.enums;

public enum NormalBalance {
    DEBIT("Debit Normal"),
    CREDIT("Credit Normal");

    private final String displayName;

    NormalBalance(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
