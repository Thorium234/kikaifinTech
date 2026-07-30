package com.schaccs.model.procurement;

import com.schaccs.config.CurrencyConfig;
import com.schaccs.enums.BidStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.Objects;

public class TenderBid {

    private final String id;
    private String tenderId;
    private String supplierId;
    private LocalDate submissionDate;
    private BigDecimal bidAmount = CurrencyConfig.zero();
    private BigDecimal technicalScore = CurrencyConfig.zero();
    private BigDecimal financialScore = CurrencyConfig.zero();
    private BigDecimal weightedScore = CurrencyConfig.zero();
    private String documents;
    private String remarks;
    private BidStatus status = BidStatus.SUBMITTED;
    private int rank;
    private LocalDateTime createdAt = LocalDateTime.now();

    public TenderBid() {
        this.id = UUID.randomUUID().toString();
    }

    private TenderBid(String id) {
        this.id = id != null ? id : UUID.randomUUID().toString();
    }

    public static TenderBid withId(String id) {
        return new TenderBid(id);
    }

    public String getId() { return id; }

    public String getTenderId() { return tenderId; }
    public void setTenderId(String tenderId) { this.tenderId = tenderId; }

    public String getSupplierId() { return supplierId; }
    public void setSupplierId(String supplierId) { this.supplierId = supplierId; }

    public LocalDate getSubmissionDate() { return submissionDate; }
    public void setSubmissionDate(LocalDate submissionDate) { this.submissionDate = submissionDate; }

    public BigDecimal getBidAmount() { return bidAmount; }
    public void setBidAmount(BigDecimal bidAmount) { this.bidAmount = CurrencyConfig.money(bidAmount); }

    public BigDecimal getTechnicalScore() { return technicalScore; }
    public void setTechnicalScore(BigDecimal technicalScore) { this.technicalScore = CurrencyConfig.money(technicalScore); }

    public BigDecimal getFinancialScore() { return financialScore; }
    public void setFinancialScore(BigDecimal financialScore) { this.financialScore = CurrencyConfig.money(financialScore); }

    public BigDecimal getWeightedScore() { return weightedScore; }
    public void setWeightedScore(BigDecimal weightedScore) { this.weightedScore = CurrencyConfig.money(weightedScore); }

    public String getDocuments() { return documents; }
    public void setDocuments(String documents) { this.documents = documents; }

    public String getRemarks() { return remarks; }
    public void setRemarks(String remarks) { this.remarks = remarks; }

    public BidStatus getStatus() { return status; }
    public void setStatus(BidStatus status) { this.status = status; }

    public int getRank() { return rank; }
    public void setRank(int rank) { this.rank = rank; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public void computeWeightedScore(BigDecimal techWeight, BigDecimal finWeight) {
        this.weightedScore = CurrencyConfig.money(
                technicalScore.multiply(techWeight).add(financialScore.multiply(finWeight))
        );
    }

    @Override
    public String toString() {
        return "Bid-" + (supplierId != null ? supplierId.substring(0, Math.min(8, supplierId.length())) : id);
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TenderBid that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
