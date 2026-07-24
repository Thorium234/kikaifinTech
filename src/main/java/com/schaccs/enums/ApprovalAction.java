package com.schaccs.enums;

public enum ApprovalAction {
    SUBMIT("Submit"),
    APPROVE("Approve"),
    REJECT("Reject"),
    ESCALATE("Escalate"),
    RETURN("Return for Revision");

    private final String displayName;

    ApprovalAction(String displayName) {
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
