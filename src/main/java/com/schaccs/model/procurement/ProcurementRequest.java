package com.schaccs.model.procurement;

import com.schaccs.config.CurrencyConfig;
import com.schaccs.enums.ProcurementRequestStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.Objects;

public class ProcurementRequest {

    private final String id;
    private String requestNumber;
    private LocalDate requestDate;
    private String department;
    private String requestedBy;
    private String itemDescription;
    private int quantity;
    private BigDecimal estimatedCost = CurrencyConfig.zero();
    private String justification;
    private LocalDate requiredDate;
    private String budgetAccount;
    private ProcurementRequestStatus status = ProcurementRequestStatus.DRAFT;
    private String tenderId;
    private LocalDateTime createdAt = LocalDateTime.now();

    public ProcurementRequest() {
        this.id = UUID.randomUUID().toString();
    }

    private ProcurementRequest(String id) {
        this.id = id != null ? id : UUID.randomUUID().toString();
    }

    public static ProcurementRequest withId(String id) {
        return new ProcurementRequest(id);
    }

    public String getId() { return id; }

    public String getRequestNumber() { return requestNumber; }
    public void setRequestNumber(String requestNumber) { this.requestNumber = requestNumber; }

    public LocalDate getRequestDate() { return requestDate; }
    public void setRequestDate(LocalDate requestDate) { this.requestDate = requestDate; }

    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }

    public String getRequestedBy() { return requestedBy; }
    public void setRequestedBy(String requestedBy) { this.requestedBy = requestedBy; }

    public String getItemDescription() { return itemDescription; }
    public void setItemDescription(String itemDescription) { this.itemDescription = itemDescription; }

    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }

    public BigDecimal getEstimatedCost() { return estimatedCost; }
    public void setEstimatedCost(BigDecimal estimatedCost) { this.estimatedCost = CurrencyConfig.money(estimatedCost); }

    public String getJustification() { return justification; }
    public void setJustification(String justification) { this.justification = justification; }

    public LocalDate getRequiredDate() { return requiredDate; }
    public void setRequiredDate(LocalDate requiredDate) { this.requiredDate = requiredDate; }

    public String getBudgetAccount() { return budgetAccount; }
    public void setBudgetAccount(String budgetAccount) { this.budgetAccount = budgetAccount; }

    public ProcurementRequestStatus getStatus() { return status; }
    public void setStatus(ProcurementRequestStatus status) { this.status = status; }

    public String getTenderId() { return tenderId; }
    public void setTenderId(String tenderId) { this.tenderId = tenderId; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    @Override
    public String toString() {
        return requestNumber != null ? requestNumber : id;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ProcurementRequest that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
