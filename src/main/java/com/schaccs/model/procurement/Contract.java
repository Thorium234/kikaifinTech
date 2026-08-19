package com.schaccs.model.procurement;

import com.schaccs.config.CurrencyConfig;
import com.schaccs.enums.ContractStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.Objects;

public class Contract {

    private final String id;
    private String contractNumber;
    private String tenderId;
    private String supplierId;
    private LocalDate startDate;
    private LocalDate endDate;
    private BigDecimal contractValue = CurrencyConfig.zero();
    private String deliverables;
    private ContractStatus status = ContractStatus.DRAFT;
    private String voteheadCode;
    private String notes;
    private LocalDateTime createdAt = LocalDateTime.now();

    public Contract() {
        this.id = UUID.randomUUID().toString();
    }

    private Contract(String id) {
        this.id = id != null ? id : UUID.randomUUID().toString();
    }

    public static Contract withId(String id) {
        return new Contract(id);
    }

    public String getId() { return id; }

    public String getContractNumber() { return contractNumber; }
    public void setContractNumber(String contractNumber) { this.contractNumber = contractNumber; }

    public String getTenderId() { return tenderId; }
    public void setTenderId(String tenderId) { this.tenderId = tenderId; }

    public String getSupplierId() { return supplierId; }
    public void setSupplierId(String supplierId) { this.supplierId = supplierId; }

    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }

    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }

    public BigDecimal getContractValue() { return contractValue; }
    public void setContractValue(BigDecimal contractValue) { this.contractValue = CurrencyConfig.money(contractValue); }

    public String getDeliverables() { return deliverables; }
    public void setDeliverables(String deliverables) { this.deliverables = deliverables; }

    public ContractStatus getStatus() { return status; }
    public void setStatus(ContractStatus status) { this.status = status; }

    public String getVoteheadCode() { return voteheadCode; }
    public void setVoteheadCode(String voteheadCode) { this.voteheadCode = voteheadCode; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public boolean isExpired() {
        return endDate != null && LocalDate.now().isAfter(endDate);
    }

    public long getDaysRemaining() {
        if (endDate == null) return 0;
        return ChronoUnit.DAYS.between(LocalDate.now(), endDate);
    }

    @Override
    public String toString() {
        return contractNumber != null ? contractNumber : id;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Contract that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
