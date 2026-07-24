package com.schaccs.enums;

public enum BidStatus {
    SUBMITTED("Submitted"),
    EVALUATED("Evaluated"),
    SHORTLISTED("Shortlisted"),
    REJECTED("Rejected"),
    WITHDRAWN("Withdrawn");

    private final String displayName;

    BidStatus(String displayName) {
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
