package com.schaccs.enums;

public enum ContractStatus {
    DRAFT("Draft"),
    ACTIVE("Active"),
    COMPLETED("Completed"),
    EXTENDED("Extended"),
    TERMINATED("Terminated");

    private final String displayName;

    ContractStatus(String displayName) {
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
