package com.schaccs.enums;

/**
 * Unit used for a course/program duration: a fixed number of academic years or
 * a fixed number of terms. Drives the automatic expected-completion date and
 * the point at which standard term fee generation freezes.
 */
public enum DurationUnit {
    YEARS("Years"),
    TERMS("Terms");

    private final String displayName;

    DurationUnit(String displayName) {
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
