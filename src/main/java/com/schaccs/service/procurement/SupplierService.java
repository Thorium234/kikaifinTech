package com.schaccs.service.procurement;

import com.schaccs.config.AppConfig;
import com.schaccs.model.procurement.Supplier;
import com.schaccs.repository.PersistenceService;
import com.schaccs.service.audit.AuditService;
import com.schaccs.store.ProcurementStore;
import com.schaccs.service.Services;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class SupplierService {

    private final ProcurementStore store;
    private final AuditService audit;

    public SupplierService() {
        this(ProcurementStore.getInstance(), new AuditService());
    }

    public SupplierService(ProcurementStore store, AuditService audit) {
        this.store = store;
        this.audit = audit;
    }

    public List<String> addSupplier(Supplier supplier) {
        List<String> errors = new ArrayList<>();
        if (supplier.getBusinessName() == null || supplier.getBusinessName().isBlank()) {
            errors.add("Business name is required.");
        }
        if (supplier.getSupplierNumber() == null || supplier.getSupplierNumber().isBlank()) {
            long num = AppConfig.getInstance().getSchoolProfile().allocateSupplierNumber();
            supplier.setSupplierNumber("SUP-" + num);
        } else {
            store.findSupplierByNumber(supplier.getSupplierNumber()).ifPresent(existing -> {
                if (!existing.getId().equals(supplier.getId())) {
                    // duplicate check handled below
                }
            });
        }
        String sNum = supplier.getSupplierNumber();
        if (sNum != null) {
            Optional<Supplier> existing = store.findSupplierByNumber(sNum);
            if (existing.isPresent() && !existing.get().getId().equals(supplier.getId())) {
                errors.add("Supplier number already exists: " + sNum);
            }
        }
        if (!errors.isEmpty()) {
            return errors;
        }
        store.addSupplier(supplier);
        PersistenceService.getInstance().saveAll();
        String json = "Supplier: " + supplier.getBusinessName() + " (" + supplier.getSupplierNumber() + ")";
        audit.log("CREATE", "SUPPLIER", supplier.getId(), json);
        return errors;
    }

    public List<String> updateSupplier(Supplier supplier) {
        List<String> errors = new ArrayList<>();
        if (supplier.getBusinessName() == null || supplier.getBusinessName().isBlank()) {
            errors.add("Business name is required.");
        }
        if (supplier.getSupplierNumber() == null || supplier.getSupplierNumber().isBlank()) {
            errors.add("Supplier number is required.");
        } else {
            Optional<Supplier> existing = store.findSupplierByNumber(supplier.getSupplierNumber());
            if (existing.isPresent() && !existing.get().getId().equals(supplier.getId())) {
                errors.add("Supplier number already exists: " + supplier.getSupplierNumber());
            }
        }
        if (!errors.isEmpty()) {
            return errors;
        }
        store.findSupplierById(supplier.getId()).ifPresent(old -> {
            String user = AppConfig.getInstance().getCurrentUser();
            logIfChanged(old.getBusinessName(), supplier.getBusinessName(), "businessName", supplier.getId(), user);
            logIfChanged(old.getContactPerson(), supplier.getContactPerson(), "contactPerson", supplier.getId(), user);
            logIfChanged(old.getEmail(), supplier.getEmail(), "email", supplier.getId(), user);
            logIfChanged(old.getPhone(), supplier.getPhone(), "phone", supplier.getId(), user);
            logIfChanged(old.getKraPin(), supplier.getKraPin(), "kraPin", supplier.getId(), user);
            logIfChanged(old.getRegistrationNumber(), supplier.getRegistrationNumber(), "registrationNumber", supplier.getId(), user);
            logIfChanged(old.getAddress(), supplier.getAddress(), "address", supplier.getId(), user);
            logIfChanged(old.getCategory(), supplier.getCategory(), "category", supplier.getId(), user);
            logIfChanged(old.getNotes(), supplier.getNotes(), "notes", supplier.getId(), user);
            logIfChanged(String.valueOf(old.isActive()), String.valueOf(supplier.isActive()), "active", supplier.getId(), user);
            logIfChanged(String.valueOf(old.isBlacklisted()), String.valueOf(supplier.isBlacklisted()), "blacklisted", supplier.getId(), user);
            logIfChanged(old.getBlacklistReason(), supplier.getBlacklistReason(), "blacklistReason", supplier.getId(), user);
        });
        PersistenceService.getInstance().saveAll();
        return errors;
    }

    public boolean deactivateSupplier(Supplier supplier) {
        Optional<Supplier> opt = store.findSupplierById(supplier.getId());
        if (opt.isEmpty()) return false;
        Supplier s = opt.get();
        s.setActive(false);
        PersistenceService.getInstance().saveAll();
        String json = "Supplier: " + s.getBusinessName() + " (" + s.getSupplierNumber() + ")";
        audit.log("DEACTIVATE", "SUPPLIER", s.getId(), json);
        return true;
    }

    public boolean activateSupplier(Supplier supplier) {
        Optional<Supplier> opt = store.findSupplierById(supplier.getId());
        if (opt.isEmpty()) return false;
        Supplier s = opt.get();
        s.setActive(true);
        PersistenceService.getInstance().saveAll();
        String json = "Supplier: " + s.getBusinessName() + " (" + s.getSupplierNumber() + ")";
        audit.log("ACTIVATE", "SUPPLIER", s.getId(), json);
        return true;
    }

    public boolean blacklistSupplier(Supplier supplier, String reason) {
        Optional<Supplier> opt = store.findSupplierById(supplier.getId());
        if (opt.isEmpty()) return false;
        Supplier s = opt.get();
        s.setBlacklisted(true);
        s.setBlacklistReason(reason);
        PersistenceService.getInstance().saveAll();
        String json = "Supplier: " + s.getBusinessName() + " (" + s.getSupplierNumber() + ") - Reason: " + reason;
        audit.log("BLACKLIST", "SUPPLIER", s.getId(), json);
        return true;
    }

    public boolean removeBlacklist(Supplier supplier) {
        Optional<Supplier> opt = store.findSupplierById(supplier.getId());
        if (opt.isEmpty()) return false;
        Supplier s = opt.get();
        s.setBlacklisted(false);
        s.setBlacklistReason(null);
        PersistenceService.getInstance().saveAll();
        String json = "Supplier: " + s.getBusinessName() + " (" + s.getSupplierNumber() + ")";
        audit.log("REMOVE_BLACKLIST", "SUPPLIER", s.getId(), json);
        return true;
    }

    public Optional<Supplier> findById(String id) {
        return store.findSupplierById(id);
    }

    public Optional<Supplier> findByNumber(String number) {
        return store.findSupplierByNumber(number);
    }

    public List<Supplier> activeSuppliers() {
        return store.getSuppliers().stream()
                .filter(s -> s.isActive() && !s.isBlacklisted())
                .toList();
    }

    public List<Supplier> blacklistedSuppliers() {
        return store.getSuppliers().stream()
                .filter(Supplier::isBlacklisted)
                .toList();
    }

    public boolean deleteSupplier(Supplier supplier) {
        Optional<Supplier> opt = store.findSupplierById(supplier.getId());
        if (opt.isEmpty()) return false;
        Supplier s = opt.get();
        if (!store.bidsForSupplier(s.getId()).isEmpty()) {
            return false;
        }
        store.removeSupplier(s);
        PersistenceService.getInstance().saveAll();
        String json = "Supplier: " + s.getBusinessName() + " (" + s.getSupplierNumber() + ")";
        audit.log("DELETE", "SUPPLIER", s.getId(), json);
        return true;
    }

    private void logIfChanged(String oldValue, String newValue, String fieldName,
                              String entityId, String user) {
        String oldVal = oldValue != null ? oldValue : "";
        String newVal = newValue != null ? newValue : "";
        if (!oldVal.equals(newVal)) {
            audit.logFieldChange("SUPPLIER", entityId, fieldName, oldVal, newVal, user);
        }
    }
}
