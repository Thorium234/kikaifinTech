package com.schaccs.enums;

public enum TransactionType {
    FEE_RECEIPT("Fee Receipt"),
    FEE_CHARGE("Fee Charge"),
    PAYMENT_VOUCHER("Payment Voucher"),
    JOURNAL("Journal Entry"),
    OPENING_BALANCE("Opening Balance"),
    ADJUSTMENT("Adjustment"),
    REVERSAL("Reversal"),
    REFUND("Refund"),
    TRANSFER("Transfer"),
    CREDIT_NOTE("Credit Note"),
    DEBIT_NOTE("Debit Note"),
    DEPRECIATION("Depreciation"),
    CONTRA("Contra Entry"),
    PAYROLL("Payroll"),
    PAYROLL_REVERSAL("Payroll Reversal");

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
