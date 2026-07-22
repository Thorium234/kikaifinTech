package com.schaccs.model.finance;

import java.time.LocalDateTime;
import java.util.UUID;

public class Budget {

    private final String id;
    private String fiscalYearId;
    private String name;
    private boolean isApproved;
    private LocalDateTime approvedAt;
    private String approvedBy;

    public Budget() {
        this.id = UUID.randomUUID().toString();
    }

    private Budget(String id) {
        this.id = id != null ? id : UUID.randomUUID().toString();
    }

    public static Budget withId(String id) {
        return new Budget(id);
    }

    public String getId() { return id; }
    public String getFiscalYearId() { return fiscalYearId; }
    public void setFiscalYearId(String fiscalYearId) { this.fiscalYearId = fiscalYearId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public boolean isApproved() { return isApproved; }
    public void setApproved(boolean approved) { isApproved = approved; }
    public LocalDateTime getApprovedAt() { return approvedAt; }
    public void setApprovedAt(LocalDateTime approvedAt) { this.approvedAt = approvedAt; }
    public String getApprovedBy() { return approvedBy; }
    public void setApprovedBy(String approvedBy) { this.approvedBy = approvedBy; }

    @Override
    public String toString() { return name; }
}
