package com.schaccs.enums;

public enum ProcurementCategory {
    GOODS("Goods"),
    WORKS("Works"),
    SERVICES("Services"),
    CONSULTANCY("Consultancy");

    private final String displayName;

    ProcurementCategory(String displayName) {
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
