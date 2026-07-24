package com.schaccs.model.procurement;

import com.schaccs.config.CurrencyConfig;
import com.schaccs.enums.ProcurementCategory;
import com.schaccs.enums.TenderStatus;
import com.schaccs.enums.TenderType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public class Tender {

    private final String id;
    private String tenderNumber;
    private String title;
    private String description;
    private LocalDate openingDate;
    private LocalDate closingDate;
    private TenderType tenderType;
    private ProcurementCategory category;
    private BigDecimal estimatedBudget = CurrencyConfig.zero();
    private String evaluationCriteria;
    private TenderStatus status = TenderStatus.DRAFT;
    private String procurementRequestId;
    private String awardedSupplierId;
    private BigDecimal awardedAmount;
    private LocalDate awardDate;
    private String awardReason;
    private LocalDateTime createdAt = LocalDateTime.now();

    public Tender() {
        this.id = UUID.randomUUID().toString();
    }

    private Tender(String id) {
        this.id = id != null ? id : UUID.randomUUID().toString();
    }

    public static Tender withId(String id) {
        return new Tender(id);
    }

    public String getId() { return id; }

    public String getTenderNumber() { return tenderNumber; }
    public void setTenderNumber(String tenderNumber) { this.tenderNumber = tenderNumber; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public LocalDate getOpeningDate() { return openingDate; }
    public void setOpeningDate(LocalDate openingDate) { this.openingDate = openingDate; }

    public LocalDate getClosingDate() { return closingDate; }
    public void setClosingDate(LocalDate closingDate) { this.closingDate = closingDate; }

    public TenderType getTenderType() { return tenderType; }
    public void setTenderType(TenderType tenderType) { this.tenderType = tenderType; }

    public ProcurementCategory getCategory() { return category; }
    public void setCategory(ProcurementCategory category) { this.category = category; }

    public BigDecimal getEstimatedBudget() { return estimatedBudget; }
    public void setEstimatedBudget(BigDecimal estimatedBudget) { this.estimatedBudget = CurrencyConfig.money(estimatedBudget); }

    public String getEvaluationCriteria() { return evaluationCriteria; }
    public void setEvaluationCriteria(String evaluationCriteria) { this.evaluationCriteria = evaluationCriteria; }

    public TenderStatus getStatus() { return status; }
    public void setStatus(TenderStatus status) { this.status = status; }

    public String getProcurementRequestId() { return procurementRequestId; }
    public void setProcurementRequestId(String procurementRequestId) { this.procurementRequestId = procurementRequestId; }

    public String getAwardedSupplierId() { return awardedSupplierId; }
    public void setAwardedSupplierId(String awardedSupplierId) { this.awardedSupplierId = awardedSupplierId; }

    public BigDecimal getAwardedAmount() { return awardedAmount; }
    public void setAwardedAmount(BigDecimal awardedAmount) { this.awardedAmount = CurrencyConfig.money(awardedAmount); }

    public LocalDate getAwardDate() { return awardDate; }
    public void setAwardDate(LocalDate awardDate) { this.awardDate = awardDate; }

    public String getAwardReason() { return awardReason; }
    public void setAwardReason(String awardReason) { this.awardReason = awardReason; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    @Override
    public String toString() {
        return tenderNumber != null ? tenderNumber : id;
    }
}
