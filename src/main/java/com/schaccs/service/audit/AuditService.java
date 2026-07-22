package com.schaccs.service.audit;

import com.schaccs.config.AppConfig;
import com.schaccs.model.audit.AuditLog;
import com.schaccs.repository.PersistenceService;
import com.schaccs.store.AuditStore;

import java.time.LocalDateTime;

public class AuditService {

    private final AuditStore store;

    public AuditService() {
        this(AuditStore.getInstance());
    }

    public AuditService(AuditStore store) {
        this.store = store;
    }

    public void log(String actionType, String entityType, String entityId, String detailsJson) {
        AuditLog entry = new AuditLog();
        entry.setTimestamp(LocalDateTime.now());
        entry.setActionType(actionType);
        entry.setEntityType(entityType);
        entry.setEntityId(entityId);
        entry.setDetailsJson(detailsJson);
        entry.setPerformedBy(AppConfig.getInstance().getCurrentUser());
        store.add(entry);
        PersistenceService.getInstance().saveAll();
    }

    public void logFieldChange(String entityType, String entityId, String fieldName,
                               String oldValue, String newValue, String changedBy) {
        AuditLog log = new AuditLog();
        log.setEntityType(entityType);
        log.setEntityId(entityId);
        log.setFieldName(fieldName);
        log.setOldValue(oldValue);
        log.setNewValue(newValue);
        log.setPerformedBy(changedBy);
        log.setTimestamp(LocalDateTime.now());
        store.add(log);
        PersistenceService.getInstance().saveAll();
    }

    public AuditStore getStore() {
        return store;
    }
}
