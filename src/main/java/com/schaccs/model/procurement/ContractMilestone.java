package com.schaccs.model.procurement;

import com.schaccs.config.CurrencyConfig;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.Objects;

public class ContractMilestone {

    private final String id;
    private String contractId;
    private String title;
    private String description;
    private LocalDate dueDate;
    private LocalDate completedDate;
    private boolean completed = false;
    private BigDecimal amount = CurrencyConfig.zero();
    private LocalDateTime createdAt = LocalDateTime.now();

    public ContractMilestone() {
        this.id = UUID.randomUUID().toString();
    }

    private ContractMilestone(String id) {
        this.id = id != null ? id : UUID.randomUUID().toString();
    }

    public static ContractMilestone withId(String id) {
        return new ContractMilestone(id);
    }

    public String getId() { return id; }

    public String getContractId() { return contractId; }
    public void setContractId(String contractId) { this.contractId = contractId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }

    public LocalDate getCompletedDate() { return completedDate; }
    public void setCompletedDate(LocalDate completedDate) { this.completedDate = completedDate; }

    public boolean isCompleted() { return completed; }
    public void setCompleted(boolean completed) { this.completed = completed; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = CurrencyConfig.money(amount); }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public boolean isOverdue() {
        return !completed && dueDate != null && LocalDate.now().isAfter(dueDate);
    }

    @Override
    public String toString() {
        return title != null ? title : "Milestone-" + id;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ContractMilestone that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
