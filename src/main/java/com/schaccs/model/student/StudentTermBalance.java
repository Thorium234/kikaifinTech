package com.schaccs.model.student;

import com.schaccs.config.CurrencyConfig;
import com.schaccs.enums.AcademicTerm;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable snapshot of a student's financial position at the end of a specific
 * academic term. Records fee billed, arrears carried forward, amount paid, and
 * closing balance for audit and cohort-reconstruction purposes.
 */
public class StudentTermBalance {

    private final String id;
    private final String studentId;
    private final int academicYear;
    private final AcademicTerm term;
    private final BigDecimal feeBilled;
    private final BigDecimal arrearsBroughtForward;
    private final BigDecimal amountPaid;
    private final BigDecimal closingBalance;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    public StudentTermBalance(String studentId, int academicYear, AcademicTerm term,
                              BigDecimal feeBilled, BigDecimal arrearsBroughtForward,
                              BigDecimal amountPaid, BigDecimal closingBalance) {
        this.id = UUID.randomUUID().toString();
        this.studentId = studentId;
        this.academicYear = academicYear;
        this.term = term;
        this.feeBilled = CurrencyConfig.money(feeBilled);
        this.arrearsBroughtForward = CurrencyConfig.money(arrearsBroughtForward);
        this.amountPaid = CurrencyConfig.money(amountPaid);
        this.closingBalance = CurrencyConfig.money(closingBalance);
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    private StudentTermBalance(String id, String studentId, int academicYear, AcademicTerm term,
                               BigDecimal feeBilled, BigDecimal arrearsBroughtForward,
                               BigDecimal amountPaid, BigDecimal closingBalance,
                               LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.studentId = studentId;
        this.academicYear = academicYear;
        this.term = term;
        this.feeBilled = feeBilled;
        this.arrearsBroughtForward = arrearsBroughtForward;
        this.amountPaid = amountPaid;
        this.closingBalance = closingBalance;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /** Reconstruct from the database. */
    public static StudentTermBalance restore(String id, String studentId, int academicYear,
                                            AcademicTerm term, BigDecimal feeBilled,
                                            BigDecimal arrearsBroughtForward, BigDecimal amountPaid,
                                            BigDecimal closingBalance, LocalDateTime createdAt,
                                            LocalDateTime updatedAt) {
        return new StudentTermBalance(id, studentId, academicYear, term, feeBilled,
                arrearsBroughtForward, amountPaid, closingBalance, createdAt, updatedAt);
    }

    public String getId() { return id; }
    public String getStudentId() { return studentId; }
    public int getAcademicYear() { return academicYear; }
    public AcademicTerm getTerm() { return term; }
    public BigDecimal getFeeBilled() { return feeBilled; }
    public BigDecimal getArrearsBroughtForward() { return arrearsBroughtForward; }
    public BigDecimal getAmountPaid() { return amountPaid; }
    public BigDecimal getClosingBalance() { return closingBalance; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    /**
     * Compute closing balance: arrears + fee billed − amount paid.
     */
    public static BigDecimal computeClosingBalance(BigDecimal arrearsBroughtForward,
                                                   BigDecimal feeBilled, BigDecimal amountPaid) {
        return CurrencyConfig.money(
                arrearsBroughtForward.add(feeBilled).subtract(amountPaid));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof StudentTermBalance that)) return false;
        return academicYear == that.academicYear
                && Objects.equals(studentId, that.studentId)
                && term == that.term;
    }

    @Override
    public int hashCode() {
        return Objects.hash(studentId, academicYear, term);
    }
}
