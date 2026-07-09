package com.schaccs.enums;

public enum BoardingStatus {
    BOARDING("Boarding"),
    DAY("Day");

    private final String displayName;

    BoardingStatus(String displayName) {
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
