package com.schaccs.enums;

public enum TransactionType {
    FEE_RECEIPT("Fee Receipt"),
    FEE_CHARGE("Fee Charge"),
    PAYMENT_VOUCHER("Payment Voucher"),
    JOURNAL("Journal Entry"),
    OPENING_BALANCE("Opening Balance"),
    ADJUSTMENT("Adjustment");

    private final String displayName;

    TransactionType(String displayName) {
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
