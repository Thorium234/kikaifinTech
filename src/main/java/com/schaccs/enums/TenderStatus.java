package com.schaccs.enums;

public enum TenderStatus {
    DRAFT("Draft"),
    PUBLISHED("Published"),
    CLOSED("Closed"),
    EVALUATION("Under Evaluation"),
    AWARDED("Awarded"),
    CANCELLED("Cancelled");

    private final String displayName;

    TenderStatus(String displayName) {
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
