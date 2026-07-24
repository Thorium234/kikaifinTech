package com.schaccs.model.payroll;

import com.schaccs.config.CurrencyConfig;

import java.math.BigDecimal;
import java.util.UUID;

public class PayrollItem {

    private final String id;
    private String payrollRunId;
    private String employeeId;
    private String employeeNumber;
    private String employeeName;
    private String department;

    // Earnings
    private BigDecimal basicSalary = CurrencyConfig.zero();
    private BigDecimal houseAllowance = CurrencyConfig.zero();
    private BigDecimal responsibilityAllowance = CurrencyConfig.zero();
    private BigDecimal transportAllowance = CurrencyConfig.zero();
    private BigDecimal overtime = CurrencyConfig.zero();
    private BigDecimal bonus = CurrencyConfig.zero();
    private BigDecimal otherEarnings = CurrencyConfig.zero();
    private BigDecimal grossPay = CurrencyConfig.zero();

    // Deductions
    private BigDecimal paye = CurrencyConfig.zero();
    private BigDecimal nssf = CurrencyConfig.zero();
    private BigDecimal shif = CurrencyConfig.zero();
    private BigDecimal pension = CurrencyConfig.zero();
    private BigDecimal staffLoanRepayment = CurrencyConfig.zero();
    private BigDecimal salaryAdvanceRecovery = CurrencyConfig.zero();
    private BigDecimal welfareContribution = CurrencyConfig.zero();
    private BigDecimal customDeductions = CurrencyConfig.zero();
    private BigDecimal totalDeductions = CurrencyConfig.zero();

    // Net Pay
    private BigDecimal netPay = CurrencyConfig.zero();

    // Employer contributions (informational)
    private BigDecimal employerNssf = CurrencyConfig.zero();
    private BigDecimal employerPension = CurrencyConfig.zero();

    private String customDeductionName;

    public PayrollItem() {
        this.id = UUID.randomUUID().toString();
    }

    private PayrollItem(String id) {
        this.id = id != null ? id : UUID.randomUUID().toString();
    }

    public static PayrollItem withId(String id) {
        return new PayrollItem(id);
    }

    public String getId() { return id; }

    public String getPayrollRunId() { return payrollRunId; }
    public void setPayrollRunId(String payrollRunId) { this.payrollRunId = payrollRunId; }

    public String getEmployeeId() { return employeeId; }
    public void setEmployeeId(String employeeId) { this.employeeId = employeeId; }

    public String getEmployeeNumber() { return employeeNumber; }
    public void setEmployeeNumber(String employeeNumber) { this.employeeNumber = employeeNumber; }

    public String getEmployeeName() { return employeeName; }
    public void setEmployeeName(String employeeName) { this.employeeName = employeeName; }

    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }

    // Earnings
    public BigDecimal getBasicSalary() { return basicSalary; }
    public void setBasicSalary(BigDecimal basicSalary) { this.basicSalary = CurrencyConfig.money(basicSalary); }

    public BigDecimal getHouseAllowance() { return houseAllowance; }
    public void setHouseAllowance(BigDecimal houseAllowance) { this.houseAllowance = CurrencyConfig.money(houseAllowance); }

    public BigDecimal getResponsibilityAllowance() { return responsibilityAllowance; }
    public void setResponsibilityAllowance(BigDecimal responsibilityAllowance) { this.responsibilityAllowance = CurrencyConfig.money(responsibilityAllowance); }

    public BigDecimal getTransportAllowance() { return transportAllowance; }
    public void setTransportAllowance(BigDecimal transportAllowance) { this.transportAllowance = CurrencyConfig.money(transportAllowance); }

    public BigDecimal getOvertime() { return overtime; }
    public void setOvertime(BigDecimal overtime) { this.overtime = CurrencyConfig.money(overtime); }

    public BigDecimal getBonus() { return bonus; }
    public void setBonus(BigDecimal bonus) { this.bonus = CurrencyConfig.money(bonus); }

    public BigDecimal getOtherEarnings() { return otherEarnings; }
    public void setOtherEarnings(BigDecimal otherEarnings) { this.otherEarnings = CurrencyConfig.money(otherEarnings); }

    public BigDecimal getGrossPay() { return grossPay; }
    public void setGrossPay(BigDecimal grossPay) { this.grossPay = CurrencyConfig.money(grossPay); }

    // Deductions
    public BigDecimal getPaye() { return paye; }
    public void setPaye(BigDecimal paye) { this.paye = CurrencyConfig.money(paye); }

    public BigDecimal getNssf() { return nssf; }
    public void setNssf(BigDecimal nssf) { this.nssf = CurrencyConfig.money(nssf); }

    public BigDecimal getShif() { return shif; }
    public void setShif(BigDecimal shif) { this.shif = CurrencyConfig.money(shif); }

    public BigDecimal getPension() { return pension; }
    public void setPension(BigDecimal pension) { this.pension = CurrencyConfig.money(pension); }

    public BigDecimal getStaffLoanRepayment() { return staffLoanRepayment; }
    public void setStaffLoanRepayment(BigDecimal staffLoanRepayment) { this.staffLoanRepayment = CurrencyConfig.money(staffLoanRepayment); }

    public BigDecimal getSalaryAdvanceRecovery() { return salaryAdvanceRecovery; }
    public void setSalaryAdvanceRecovery(BigDecimal salaryAdvanceRecovery) { this.salaryAdvanceRecovery = CurrencyConfig.money(salaryAdvanceRecovery); }

    public BigDecimal getWelfareContribution() { return welfareContribution; }
    public void setWelfareContribution(BigDecimal welfareContribution) { this.welfareContribution = CurrencyConfig.money(welfareContribution); }

    public BigDecimal getCustomDeductions() { return customDeductions; }
    public void setCustomDeductions(BigDecimal customDeductions) { this.customDeductions = CurrencyConfig.money(customDeductions); }

    public String getCustomDeductionName() { return customDeductionName; }
    public void setCustomDeductionName(String customDeductionName) { this.customDeductionName = customDeductionName; }

    public BigDecimal getTotalDeductions() { return totalDeductions; }
    public void setTotalDeductions(BigDecimal totalDeductions) { this.totalDeductions = CurrencyConfig.money(totalDeductions); }

    public BigDecimal getNetPay() { return netPay; }
    public void setNetPay(BigDecimal netPay) { this.netPay = CurrencyConfig.money(netPay); }

    public BigDecimal getEmployerNssf() { return employerNssf; }
    public void setEmployerNssf(BigDecimal employerNssf) { this.employerNssf = CurrencyConfig.money(employerNssf); }

    public BigDecimal getEmployerPension() { return employerPension; }
    public void setEmployerPension(BigDecimal employerPension) { this.employerPension = CurrencyConfig.money(employerPension); }

    public BigDecimal getTotalAllowances() {
        return CurrencyConfig.money(
                getHouseAllowance().add(getResponsibilityAllowance())
                        .add(getTransportAllowance()).add(getOtherEarnings()));
    }
}
