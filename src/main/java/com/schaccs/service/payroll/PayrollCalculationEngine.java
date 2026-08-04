package com.schaccs.service.payroll;

import com.schaccs.config.CurrencyConfig;
import com.schaccs.model.payroll.PayrollItem;
import com.schaccs.model.payroll.SalaryStructure;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Kenya payroll calculation engine.
 * Computes PAYE (tax), NSSF, SHIF (health insurance), and net pay.
 *
 * Tax rates based on Kenya Finance Act 2023/2024:
 * - PAYE: graduated rates with personal relief of KES 2,400/month
 * - NSSF: 6% of pensionable earnings (Tier I + Tier II), max KES 2,160
 * - SHIF: 2.75% of gross salary, min KES 300, max KES 5,000
 */
public final class PayrollCalculationEngine {

    // PAYE Tax bands (monthly) — rates stored as-is (not via CurrencyConfig.money which rounds to 2dp)
    private static final BigDecimal[][] PAYE_BANDS = {
            {CurrencyConfig.money(24000), new BigDecimal("0.10")},
            {CurrencyConfig.money(8333), new BigDecimal("0.25")},
            {CurrencyConfig.money(467667), new BigDecimal("0.30")},
            {CurrencyConfig.money(300000), new BigDecimal("0.325")},
            // Above 800,000 at 35%
    };
    private static final BigDecimal PAYE_TOP_RATE = new BigDecimal("0.35");
    private static final BigDecimal PERSONAL_RELIEF = CurrencyConfig.money(2400);
    private static final BigDecimal INSURANCE_RELIEF = CurrencyConfig.money(500);

    // NSSF
    private static final BigDecimal NSSF_LOWER_LIMIT = CurrencyConfig.money(7000);
    private static final BigDecimal NSSF_UPPER_LIMIT = CurrencyConfig.money(36000);
    private static final BigDecimal NSSF_RATE = new BigDecimal("0.06");
    private static final BigDecimal NSSF_MAX_CONTRIBUTION = CurrencyConfig.money(2160);
    private static final BigDecimal NSSF_EMPLOYER_RATE = new BigDecimal("0.06");

    // SHIF (Social Health Insurance Fund)
    private static final BigDecimal SHIF_RATE = new BigDecimal("0.0275");
    private static final BigDecimal SHIF_MIN = CurrencyConfig.money(300);
    private static final BigDecimal SHIF_MAX = CurrencyConfig.money(5000);

    private PayrollCalculationEngine() {}

    /**
     * Calculate the full payroll item from a salary structure.
     * Populates all earnings, deductions, employer contributions, and net pay.
     */
    public static PayrollItem calculate(SalaryStructure structure, PayrollItem item) {
        // Copy earnings from salary structure
        item.setBasicSalary(structure.getBasicSalary());
        item.setHouseAllowance(structure.getHouseAllowance());
        item.setResponsibilityAllowance(structure.getResponsibilityAllowance());
        item.setTransportAllowance(structure.getTransportAllowance());
        item.setOtherEarnings(structure.getOtherEarnings());
        item.setStaffLoanRepayment(structure.getStaffLoanRepayment());
        item.setSalaryAdvanceRecovery(structure.getSalaryAdvanceRecovery());
        item.setWelfareContribution(structure.getWelfareContribution());

        // Calculate gross pay. getGrossSalary() already includes otherEarnings via getTotalAllowances(),
        // so only add variable earnings (overtime, bonus) on top.
        BigDecimal grossPay = structure.getGrossSalary()
                .add(item.getOvertime())
                .add(item.getBonus());
        item.setGrossPay(CurrencyConfig.money(grossPay));

        // Calculate statutory deductions
        BigDecimal nssf = calculateNssf(grossPay);
        BigDecimal shif = calculateShif(grossPay);
        BigDecimal taxableIncome = grossPay.subtract(nssf); // NSSF is tax-deductible
        BigDecimal paye = calculatePaye(taxableIncome);

        item.setNssf(nssf);
        item.setShif(shif);
        item.setPaye(paye);

        // Employer contributions (for information)
        item.setEmployerNssf(calculateEmployerNssf(grossPay));
        item.setEmployerPension(structure.getStaffLoanRepayment().multiply(BigDecimal.ZERO)); // employer pension if applicable

        // Total deductions
        BigDecimal totalDeductions = paye.add(nssf).add(shif)
                .add(item.getPension())
                .add(item.getStaffLoanRepayment())
                .add(item.getSalaryAdvanceRecovery())
                .add(item.getWelfareContribution())
                .add(item.getCustomDeductions());
        item.setTotalDeductions(CurrencyConfig.money(totalDeductions));

        // Net pay
        BigDecimal netPay = grossPay.subtract(totalDeductions);
        item.setNetPay(CurrencyConfig.money(netPay));

        return item;
    }

    /**
     * Calculate PAYE using Kenya graduated tax rates.
     * @param taxableIncome gross pay minus NSSF
     */
    public static BigDecimal calculatePaye(BigDecimal taxableIncome) {
        if (taxableIncome == null || taxableIncome.compareTo(BigDecimal.ZERO) <= 0) {
            return CurrencyConfig.zero();
        }

        BigDecimal tax = BigDecimal.ZERO;
        BigDecimal remaining = taxableIncome;

        for (BigDecimal[] band : PAYE_BANDS) {
            BigDecimal bandLimit = band[0];
            BigDecimal rate = band[1];
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) break;

            BigDecimal taxable = remaining.min(bandLimit);
            tax = tax.add(taxable.multiply(rate).setScale(2, RoundingMode.HALF_UP));
            remaining = remaining.subtract(taxable);
        }

        // Top rate for remaining amount above 800,000
        if (remaining.compareTo(BigDecimal.ZERO) > 0) {
            tax = tax.add(remaining.multiply(PAYE_TOP_RATE).setScale(2, RoundingMode.HALF_UP));
        }

        // Apply personal relief
        tax = tax.subtract(PERSONAL_RELIEF);

        // PAYE cannot be negative
        if (tax.compareTo(BigDecimal.ZERO) < 0) {
            return CurrencyConfig.zero();
        }

        return CurrencyConfig.money(tax);
    }

    /**
     * Calculate NSSF employee contribution (Tier I + Tier II).
     */
    public static BigDecimal calculateNssf(BigDecimal pensionableEarnings) {
        if (pensionableEarnings == null || pensionableEarnings.compareTo(NSSF_LOWER_LIMIT) < 0) {
            return CurrencyConfig.zero();
        }

        BigDecimal earnings = pensionableEarnings.min(NSSF_UPPER_LIMIT);
        BigDecimal contribution = earnings.multiply(NSSF_RATE)
                .setScale(2, RoundingMode.HALF_UP);

        return contribution.min(NSSF_MAX_CONTRIBUTION);
    }

    /**
     * Calculate NSSF employer contribution.
     */
    public static BigDecimal calculateEmployerNssf(BigDecimal pensionableEarnings) {
        return calculateNssf(pensionableEarnings); // Same formula for employer
    }

    /**
     * Calculate SHIF (Social Health Insurance Fund) contribution.
     */
    public static BigDecimal calculateShif(BigDecimal grossSalary) {
        if (grossSalary == null || grossSalary.compareTo(BigDecimal.ZERO) <= 0) {
            return CurrencyConfig.zero();
        }

        BigDecimal contribution = grossSalary.multiply(SHIF_RATE)
                .setScale(2, RoundingMode.HALF_UP);

        return contribution.max(SHIF_MIN).min(SHIF_MAX);
    }
}
