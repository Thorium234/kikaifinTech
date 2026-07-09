package com.schaccs.enums;

public enum PaymentMode {
    CASH("Cash"),
    BANK_SLIP("Bank Slip"),
    CHEQUE("Cheque"),
    MPESA("M-Pesa"),
    MONEY_ORDER("Money Order");

    private final String displayName;

    PaymentMode(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
