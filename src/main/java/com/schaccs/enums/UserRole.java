package com.schaccs.enums;

public enum UserRole {
    PRINCIPAL("Principal"),
    BURSAR("Bursar"),
    CLERK("Clerk"),
    ADMIN("Administrator"),
    VIEWER("Viewer");

    private final String displayName;

    UserRole(String displayName) {
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
