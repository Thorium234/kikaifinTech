package com.schaccs.model.audit;

import java.time.LocalDateTime;
import java.util.UUID;

public class AuditLog {

    private final String id;
    private LocalDateTime timestamp;
    private String actionType;
    private String entityType;
    private String entityId;
    private String detailsJson;
    private String performedBy;

    public AuditLog() {
        this.id = UUID.randomUUID().toString();
        this.timestamp = LocalDateTime.now();
    }

    private AuditLog(String id) {
        this.id = id != null ? id : UUID.randomUUID().toString();
        this.timestamp = LocalDateTime.now();
    }

    public static AuditLog withId(String id) {
        return new AuditLog(id);
    }

    public String getId() { return id; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
    public String getActionType() { return actionType; }
    public void setActionType(String actionType) { this.actionType = actionType; }
    public String getEntityType() { return entityType; }
    public void setEntityType(String entityType) { this.entityType = entityType; }
    public String getEntityId() { return entityId; }
    public void setEntityId(String entityId) { this.entityId = entityId; }
    public String getDetailsJson() { return detailsJson; }
    public void setDetailsJson(String detailsJson) { this.detailsJson = detailsJson; }
    public String getPerformedBy() { return performedBy; }
    public void setPerformedBy(String performedBy) { this.performedBy = performedBy; }
}
