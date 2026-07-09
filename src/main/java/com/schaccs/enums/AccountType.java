package com.schaccs.enums;

/**
 * Three primary school accounts used by MoE / FSE funding model.
 */
public enum AccountType {
    SCHOOL_FUND("School Fund", "SF"),
    FSE_OPERATIONS("FSE Operations", "FSE-OP"),
    FSE_TUITION("FSE Tuition", "FSE-TU");

    private final String displayName;
    private final String code;

    AccountType(String displayName, String code) {
        this.displayName = displayName;
        this.code = code;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getCode() {
        return code;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
