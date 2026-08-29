package com.schaccs.enums;

public enum StudentStatus {
    ACTIVE("Active"),
    INACTIVE("Inactive"),
    TRANSFERRED("Transferred"),
    DROPPED("Dropped Out"),
    COMPLETED("Completed"),
    GRADUATED("Graduated"),
    SUSPENDED("Suspended");

    private final String displayName;

    StudentStatus(String displayName) {
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
