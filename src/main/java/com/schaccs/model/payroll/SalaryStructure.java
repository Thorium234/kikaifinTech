package com.schaccs.model.payroll;

import com.schaccs.config.CurrencyConfig;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public class SalaryStructure {

    private final String id;
    private String employeeId;
    private BigDecimal basicSalary = CurrencyConfig.zero();
    private BigDecimal houseAllowance = CurrencyConfig.zero();
    private BigDecimal responsibilityAllowance = CurrencyConfig.zero();
    private BigDecimal transportAllowance = CurrencyConfig.zero();
    private BigDecimal otherEarnings = CurrencyConfig.zero();
    private BigDecimal staffLoanRepayment = CurrencyConfig.zero();
    private BigDecimal salaryAdvanceRecovery = CurrencyConfig.zero();
    private BigDecimal welfareContribution = CurrencyConfig.zero();
    private LocalDate effectiveDate;
    private boolean active = true;

    public SalaryStructure() {
        this.id = UUID.randomUUID().toString();
    }

    private SalaryStructure(String id) {
        this.id = id != null ? id : UUID.randomUUID().toString();
    }

    public static SalaryStructure withId(String id) {
        return new SalaryStructure(id);
    }

    public String getId() { return id; }

    public String getEmployeeId() { return employeeId; }
    public void setEmployeeId(String employeeId) { this.employeeId = employeeId; }

    public BigDecimal getBasicSalary() { return basicSalary; }
    public void setBasicSalary(BigDecimal basicSalary) { this.basicSalary = CurrencyConfig.money(basicSalary); }

    public BigDecimal getHouseAllowance() { return houseAllowance; }
    public void setHouseAllowance(BigDecimal houseAllowance) { this.houseAllowance = CurrencyConfig.money(houseAllowance); }

    public BigDecimal getResponsibilityAllowance() { return responsibilityAllowance; }
    public void setResponsibilityAllowance(BigDecimal responsibilityAllowance) { this.responsibilityAllowance = CurrencyConfig.money(responsibilityAllowance); }

    public BigDecimal getTransportAllowance() { return transportAllowance; }
    public void setTransportAllowance(BigDecimal transportAllowance) { this.transportAllowance = CurrencyConfig.money(transportAllowance); }

    public BigDecimal getOtherEarnings() { return otherEarnings; }
    public void setOtherEarnings(BigDecimal otherEarnings) { this.otherEarnings = CurrencyConfig.money(otherEarnings); }

    public BigDecimal getStaffLoanRepayment() { return staffLoanRepayment; }
    public void setStaffLoanRepayment(BigDecimal staffLoanRepayment) { this.staffLoanRepayment = CurrencyConfig.money(staffLoanRepayment); }

    public BigDecimal getSalaryAdvanceRecovery() { return salaryAdvanceRecovery; }
    public void setSalaryAdvanceRecovery(BigDecimal salaryAdvanceRecovery) { this.salaryAdvanceRecovery = CurrencyConfig.money(salaryAdvanceRecovery); }

    public BigDecimal getWelfareContribution() { return welfareContribution; }
    public void setWelfareContribution(BigDecimal welfareContribution) { this.welfareContribution = CurrencyConfig.money(welfareContribution); }

    public LocalDate getEffectiveDate() { return effectiveDate; }
    public void setEffectiveDate(LocalDate effectiveDate) { this.effectiveDate = effectiveDate; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public BigDecimal getTotalAllowances() {
        return CurrencyConfig.money(
                getHouseAllowance().add(getResponsibilityAllowance())
                        .add(getTransportAllowance()).add(getOtherEarnings()));
    }

    public BigDecimal getGrossSalary() {
        return CurrencyConfig.money(getBasicSalary().add(getTotalAllowances()));
    }

    public BigDecimal getTotalFixedDeductions() {
        return CurrencyConfig.money(
                getStaffLoanRepayment().add(getSalaryAdvanceRecovery())
                        .add(getWelfareContribution()));
    }
}
