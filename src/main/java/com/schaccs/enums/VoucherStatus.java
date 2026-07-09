package com.schaccs.enums;

public enum VoucherStatus {
    DRAFT("Draft"),
    PENDING("Pending Approval"),
    APPROVED("Approved"),
    PAID("Paid"),
    CANCELLED("Cancelled");

    private final String displayName;

    VoucherStatus(String displayName) {
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
