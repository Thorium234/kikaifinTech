package com.schaccs.enums;

public enum TenderType {
    OPEN_TENDER("Open Tender"),
    RESTRICTED_TENDER("Restricted Tender"),
    REQUEST_FOR_QUOTATION("Request for Quotation"),
    DIRECT_PROCUREMENT("Direct Procurement"),
    EMERGENCY_PROCUREMENT("Emergency Procurement");

    private final String displayName;

    TenderType(String displayName) {
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
