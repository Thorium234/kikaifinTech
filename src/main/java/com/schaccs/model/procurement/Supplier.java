package com.schaccs.model.procurement;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.Objects;

public class Supplier {

    private final String id;
    private String supplierNumber;
    private String businessName;
    private String contactPerson;
    private String email;
    private String phone;
    private String kraPin;
    private String registrationNumber;
    private String address;
    private String category;
    private boolean active = true;
    private boolean blacklisted = false;
    private String blacklistReason;
    private String notes;
    private LocalDateTime createdAt = LocalDateTime.now();

    public Supplier() {
        this.id = UUID.randomUUID().toString();
    }

    private Supplier(String id) {
        this.id = id != null ? id : UUID.randomUUID().toString();
    }

    public static Supplier withId(String id) {
        return new Supplier(id);
    }

    public String getId() { return id; }

    public String getSupplierNumber() { return supplierNumber; }
    public void setSupplierNumber(String supplierNumber) { this.supplierNumber = supplierNumber; }

    public String getBusinessName() { return businessName; }
    public void setBusinessName(String businessName) { this.businessName = businessName; }

    public String getContactPerson() { return contactPerson; }
    public void setContactPerson(String contactPerson) { this.contactPerson = contactPerson; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getKraPin() { return kraPin; }
    public void setKraPin(String kraPin) { this.kraPin = kraPin; }

    public String getRegistrationNumber() { return registrationNumber; }
    public void setRegistrationNumber(String registrationNumber) { this.registrationNumber = registrationNumber; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public boolean isBlacklisted() { return blacklisted; }
    public void setBlacklisted(boolean blacklisted) { this.blacklisted = blacklisted; }

    public String getBlacklistReason() { return blacklistReason; }
    public void setBlacklistReason(String blacklistReason) { this.blacklistReason = blacklistReason; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    @Override
    public String toString() {
        return businessName != null ? businessName : supplierNumber;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Supplier that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
