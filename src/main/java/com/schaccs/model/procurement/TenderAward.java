package com.schaccs.model.procurement;

import com.schaccs.config.CurrencyConfig;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public class TenderAward {

    private final String id;
    private String tenderId;
    private String supplierId;
    private LocalDate awardDate;
    private BigDecimal awardAmount = CurrencyConfig.zero();
    private String awardReason;
    private int contractDurationMonths;
    private String approvalReference;
    private String approvedBy;
    private LocalDateTime createdAt = LocalDateTime.now();

    public TenderAward() {
        this.id = UUID.randomUUID().toString();
    }

    private TenderAward(String id) {
        this.id = id != null ? id : UUID.randomUUID().toString();
    }

    public static TenderAward withId(String id) {
        return new TenderAward(id);
    }

    public String getId() { return id; }

    public String getTenderId() { return tenderId; }
    public void setTenderId(String tenderId) { this.tenderId = tenderId; }

    public String getSupplierId() { return supplierId; }
    public void setSupplierId(String supplierId) { this.supplierId = supplierId; }

    public LocalDate getAwardDate() { return awardDate; }
    public void setAwardDate(LocalDate awardDate) { this.awardDate = awardDate; }

    public BigDecimal getAwardAmount() { return awardAmount; }
    public void setAwardAmount(BigDecimal awardAmount) { this.awardAmount = CurrencyConfig.money(awardAmount); }

    public String getAwardReason() { return awardReason; }
    public void setAwardReason(String awardReason) { this.awardReason = awardReason; }

    public int getContractDurationMonths() { return contractDurationMonths; }
    public void setContractDurationMonths(int contractDurationMonths) { this.contractDurationMonths = contractDurationMonths; }

    public String getApprovalReference() { return approvalReference; }
    public void setApprovalReference(String approvalReference) { this.approvalReference = approvalReference; }

    public String getApprovedBy() { return approvedBy; }
    public void setApprovedBy(String approvedBy) { this.approvedBy = approvedBy; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    @Override
    public String toString() {
        return "Award-" + (tenderId != null ? tenderId : id);
    }
}
