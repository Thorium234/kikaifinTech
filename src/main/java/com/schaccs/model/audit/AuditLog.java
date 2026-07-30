package com.schaccs.model.audit;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.Objects;

public class AuditLog {

    private final String id;
    private LocalDateTime timestamp;
    private String actionType;
    private String entityType;
    private String entityId;
    private String detailsJson;
    private String performedBy;
    private String fieldName;
    private String oldValue;
    private String newValue;

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
    public String getFieldName() { return fieldName; }
    public void setFieldName(String fieldName) { this.fieldName = fieldName; }
    public String getOldValue() { return oldValue; }
    public void setOldValue(String oldValue) { this.oldValue = oldValue; }
    public String getNewValue() { return newValue; }
    public void setNewValue(String newValue) { this.newValue = newValue; }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AuditLog that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
