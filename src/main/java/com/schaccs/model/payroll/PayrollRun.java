package com.schaccs.model.payroll;

import com.schaccs.config.CurrencyConfig;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.Objects;

public class PayrollRun {

    public enum PayrollStatus {
        DRAFT("Draft"),
        PENDING_APPROVAL("Pending Approval"),
        APPROVED("Approved"),
        POSTED("Posted"),
        REVERSED("Reversed");

        private final String displayName;
        PayrollStatus(String displayName) { this.displayName = displayName; }
        public String getDisplayName() { return displayName; }
        @Override public String toString() { return displayName; }
    }

    private final String id;
    private String runNumber;
    private int month;
    private int year;
    private LocalDate periodStart;
    private LocalDate periodEnd;
    private PayrollStatus status = PayrollStatus.DRAFT;
    private BigDecimal totalGrossPay = CurrencyConfig.zero();
    private BigDecimal totalDeductions = CurrencyConfig.zero();
    private BigDecimal totalNetPay = CurrencyConfig.zero();
    private BigDecimal totalPAYE = CurrencyConfig.zero();
    private BigDecimal totalNSSF = CurrencyConfig.zero();
    private BigDecimal totalSHIF = CurrencyConfig.zero();
    private BigDecimal totalPension = CurrencyConfig.zero();
    private int employeeCount;
    private String preparedBy;
    private String approvedBy;
    private String postedBy;
    private LocalDateTime preparedAt;
    private LocalDateTime approvedAt;
    private LocalDateTime postedAt;
    private String journalId;
    private String reversalOfId;
    private String notes;
    private LocalDateTime createdAt;

    public PayrollRun() {
        this.id = UUID.randomUUID().toString();
        this.createdAt = LocalDateTime.now();
    }

    private PayrollRun(String id) {
        this.id = id != null ? id : UUID.randomUUID().toString();
        this.createdAt = LocalDateTime.now();
    }

    public static PayrollRun withId(String id) {
        return new PayrollRun(id);
    }

    public String getId() { return id; }

    public String getRunNumber() { return runNumber; }
    public void setRunNumber(String runNumber) { this.runNumber = runNumber; }

    public int getMonth() { return month; }
    public void setMonth(int month) { this.month = month; }

    public int getYear() { return year; }
    public void setYear(int year) { this.year = year; }

    public LocalDate getPeriodStart() { return periodStart; }
    public void setPeriodStart(LocalDate periodStart) { this.periodStart = periodStart; }

    public LocalDate getPeriodEnd() { return periodEnd; }
    public void setPeriodEnd(LocalDate periodEnd) { this.periodEnd = periodEnd; }

    public PayrollStatus getStatus() { return status; }
    public void setStatus(PayrollStatus status) { this.status = status; }

    public BigDecimal getTotalGrossPay() { return totalGrossPay; }
    public void setTotalGrossPay(BigDecimal totalGrossPay) { this.totalGrossPay = CurrencyConfig.money(totalGrossPay); }

    public BigDecimal getTotalDeductions() { return totalDeductions; }
    public void setTotalDeductions(BigDecimal totalDeductions) { this.totalDeductions = CurrencyConfig.money(totalDeductions); }

    public BigDecimal getTotalNetPay() { return totalNetPay; }
    public void setTotalNetPay(BigDecimal totalNetPay) { this.totalNetPay = CurrencyConfig.money(totalNetPay); }

    public BigDecimal getTotalPAYE() { return totalPAYE; }
    public void setTotalPAYE(BigDecimal totalPAYE) { this.totalPAYE = CurrencyConfig.money(totalPAYE); }

    public BigDecimal getTotalNSSF() { return totalNSSF; }
    public void setTotalNSSF(BigDecimal totalNSSF) { this.totalNSSF = CurrencyConfig.money(totalNSSF); }

    public BigDecimal getTotalSHIF() { return totalSHIF; }
    public void setTotalSHIF(BigDecimal totalSHIF) { this.totalSHIF = CurrencyConfig.money(totalSHIF); }

    public BigDecimal getTotalPension() { return totalPension; }
    public void setTotalPension(BigDecimal totalPension) { this.totalPension = CurrencyConfig.money(totalPension); }

    public int getEmployeeCount() { return employeeCount; }
    public void setEmployeeCount(int employeeCount) { this.employeeCount = employeeCount; }

    public String getPreparedBy() { return preparedBy; }
    public void setPreparedBy(String preparedBy) { this.preparedBy = preparedBy; }

    public String getApprovedBy() { return approvedBy; }
    public void setApprovedBy(String approvedBy) { this.approvedBy = approvedBy; }

    public String getPostedBy() { return postedBy; }
    public void setPostedBy(String postedBy) { this.postedBy = postedBy; }

    public LocalDateTime getPreparedAt() { return preparedAt; }
    public void setPreparedAt(LocalDateTime preparedAt) { this.preparedAt = preparedAt; }

    public LocalDateTime getApprovedAt() { return approvedAt; }
    public void setApprovedAt(LocalDateTime approvedAt) { this.approvedAt = approvedAt; }

    public LocalDateTime getPostedAt() { return postedAt; }
    public void setPostedAt(LocalDateTime postedAt) { this.postedAt = postedAt; }

    public String getJournalId() { return journalId; }
    public void setJournalId(String journalId) { this.journalId = journalId; }

    public String getReversalOfId() { return reversalOfId; }
    public void setReversalOfId(String reversalOfId) { this.reversalOfId = reversalOfId; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public String getPeriodLabel() {
        return String.format("%02d/%d", month, year);
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PayrollRun that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
