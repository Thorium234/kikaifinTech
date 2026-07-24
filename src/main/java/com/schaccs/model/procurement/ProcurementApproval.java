package com.schaccs.model.procurement;

import com.schaccs.enums.ApprovalAction;

import java.time.LocalDateTime;
import java.util.UUID;

public class ProcurementApproval {

    private final String id;
    private String entityType;
    private String entityId;
    private ApprovalAction action;
    private String performedBy;
    private String role;
    private String comments;
    private LocalDateTime timestamp = LocalDateTime.now();

    public ProcurementApproval() {
        this.id = UUID.randomUUID().toString();
    }

    private ProcurementApproval(String id) {
        this.id = id != null ? id : UUID.randomUUID().toString();
    }

    public static ProcurementApproval withId(String id) {
        return new ProcurementApproval(id);
    }

    public String getId() { return id; }

    public String getEntityType() { return entityType; }
    public void setEntityType(String entityType) { this.entityType = entityType; }

    public String getEntityId() { return entityId; }
    public void setEntityId(String entityId) { this.entityId = entityId; }

    public ApprovalAction getAction() { return action; }
    public void setAction(ApprovalAction action) { this.action = action; }

    public String getPerformedBy() { return performedBy; }
    public void setPerformedBy(String performedBy) { this.performedBy = performedBy; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getComments() { return comments; }
    public void setComments(String comments) { this.comments = comments; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }

    @Override
    public String toString() {
        return (action != null ? action.getDisplayName() : "Action") +
                " by " + (performedBy != null ? performedBy : "Unknown");
    }
}
