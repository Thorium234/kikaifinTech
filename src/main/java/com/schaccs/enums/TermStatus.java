package com.schaccs.enums;

/**
 * Lifecycle state of an academic term period. Only one term may be {@code ACTIVE}
 * at any timestamp; a term becomes {@code ENDED} once its end date passes, at
 * which point unpaid balances formally roll into arrears.
 */
public enum TermStatus {
    PLANNED("Planned"),
    ACTIVE("Active"),
    ENDED("Ended");

    private final String displayName;

    TermStatus(String displayName) {
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
