package com.schaccs.model.procurement;

import com.schaccs.config.CurrencyConfig;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.Objects;

public class TenderEvaluation {

    private final String id;
    private String tenderId;
    private String bidId;
    private String evaluatorName;
    private String evaluationType;
    private BigDecimal score = CurrencyConfig.zero();
    private BigDecimal maxScore = CurrencyConfig.zero();
    private String comments;
    private LocalDate evaluatedDate;
    private LocalDateTime createdAt = LocalDateTime.now();

    public TenderEvaluation() {
        this.id = UUID.randomUUID().toString();
    }

    private TenderEvaluation(String id) {
        this.id = id != null ? id : UUID.randomUUID().toString();
    }

    public static TenderEvaluation withId(String id) {
        return new TenderEvaluation(id);
    }

    public String getId() { return id; }

    public String getTenderId() { return tenderId; }
    public void setTenderId(String tenderId) { this.tenderId = tenderId; }

    public String getBidId() { return bidId; }
    public void setBidId(String bidId) { this.bidId = bidId; }

    public String getEvaluatorName() { return evaluatorName; }
    public void setEvaluatorName(String evaluatorName) { this.evaluatorName = evaluatorName; }

    public String getEvaluationType() { return evaluationType; }
    public void setEvaluationType(String evaluationType) { this.evaluationType = evaluationType; }

    public BigDecimal getScore() { return score; }
    public void setScore(BigDecimal score) { this.score = CurrencyConfig.money(score); }

    public BigDecimal getMaxScore() { return maxScore; }
    public void setMaxScore(BigDecimal maxScore) { this.maxScore = CurrencyConfig.money(maxScore); }

    public String getComments() { return comments; }
    public void setComments(String comments) { this.comments = comments; }

    public LocalDate getEvaluatedDate() { return evaluatedDate; }
    public void setEvaluatedDate(LocalDate evaluatedDate) { this.evaluatedDate = evaluatedDate; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public double getScorePercentage() {
        if (maxScore == null || maxScore.compareTo(BigDecimal.ZERO) == 0) return 0.0;
        return score.multiply(BigDecimal.valueOf(100))
                .divide(maxScore, 2, java.math.RoundingMode.HALF_UP)
                .doubleValue();
    }

    @Override
    public String toString() {
        return evaluationType + " Evaluation (" + bidId + ")";
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TenderEvaluation that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
