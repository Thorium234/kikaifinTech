package com.schaccs.service.payroll;

import com.schaccs.config.CurrencyConfig;
import com.schaccs.model.payroll.PayrollItem;
import com.schaccs.model.payroll.SalaryStructure;
import com.schaccs.model.payroll.StatutoryConfig;
import com.schaccs.store.StatutoryConfigStore;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;

/**
 * Kenya payroll calculation engine.
 *
 * <p>Computes PAYE, NSSF, SHIF, AHL (Affordable Housing Levy) and net pay.
 * All statutory parameters are read at run time from the active
 * {@link StatutoryConfig} so rate changes mid-year are applied without a code
 * change (payroll.md §1). Falls back to Finance Act defaults if no config is
 * stored yet.
 *
 * <p>Spec-driven behaviour (payroll.md):
 * <ul>
 *   <li>SHIF is scaled at the absolute 2.75% rate of gross with no minimum or
 *       maximum income floor.</li>
 *   <li>AHL is 1.5% from the employee and a matching 1.5% from the employer,
 *       with no minimum-income floor.</li>
 *   <li>NSSF is split into Tier I and Tier II by pensionable-salary ceilings.</li>
 *   <li>Proration: a mid-month hire is paid Gross &times; (days worked / days in month)
 *       and SHIF/AHL are computed against the prorated gross.</li>
 * </ul>
 */
public final class PayrollCalculationEngine {

    private PayrollCalculationEngine() {}

    /**
     * Default Finance Act rates used when no {@link StatutoryConfig} is stored.
     * Mirrors the seeded MigrationV32 defaults so the engine is deterministic
     * in unit tests that do not exercise persistence.
     */
    public static StatutoryConfig defaultConfig() {
        StatutoryConfig c = new StatutoryConfig();
        c.getPayeBands().add(new StatutoryConfig.PayeBand(CurrencyConfig.money(24000), new BigDecimal("0.10")));
        c.getPayeBands().add(new StatutoryConfig.PayeBand(CurrencyConfig.money(8333), new BigDecimal("0.25")));
        c.getPayeBands().add(new StatutoryConfig.PayeBand(CurrencyConfig.money(467667), new BigDecimal("0.30")));
        c.getPayeBands().add(new StatutoryConfig.PayeBand(CurrencyConfig.money(300000), new BigDecimal("0.325")));
        c.setPayeTopRate(new BigDecimal("0.35"));
        c.setPersonalRelief(CurrencyConfig.money(2400));
        c.setShifRate(new BigDecimal("0.0275"));
        c.setAhlEmployeeRate(new BigDecimal("0.015"));
        c.setAhlEmployerRate(new BigDecimal("0.015"));
        c.setNssfTierILower(CurrencyConfig.money(0));
        c.setNssfTierICeiling(CurrencyConfig.money(7000));
        c.setNssfTierIICeiling(CurrencyConfig.money(36000));
        c.setNssfRate(new BigDecimal("0.06"));
        c.setNssfEmployerRate(new BigDecimal("0.06"));
        return c;
    }

    /** Active config from the store, or the built-in defaults when none exists. */
    public static StatutoryConfig activeConfig() {
        Optional<StatutoryConfig> active = StatutoryConfigStore.getInstance().findActive();
        return active.orElseGet(PayrollCalculationEngine::defaultConfig);
    }

    /**
     * Calculate the full payroll item from a salary structure. Uses the active
     * statutory config. Honor {@code daysWorked}/{@code daysInMonth} (proration)
     * and {@code unpaidLeaveDays} already set on the item.
     */
    public static PayrollItem calculate(SalaryStructure structure, PayrollItem item) {
        return calculate(structure, item, activeConfig());
    }

    /**
     * Calculate the full payroll item from a salary structure and an explicit
     * statutory config (used for deterministic tests and mid-year rate changes).
     */
    public static PayrollItem calculate(SalaryStructure structure, PayrollItem item, StatutoryConfig config) {
        // Copy earnings from salary structure
        item.setBasicSalary(structure.getBasicSalary());
        item.setHouseAllowance(structure.getHouseAllowance());
        item.setResponsibilityAllowance(structure.getResponsibilityAllowance());
        item.setTransportAllowance(structure.getTransportAllowance());
        item.setOtherEarnings(structure.getOtherEarnings());
        item.setStaffLoanRepayment(structure.getStaffLoanRepayment());
        item.setSalaryAdvanceRecovery(structure.getSalaryAdvanceRecovery());
        item.setWelfareContribution(structure.getWelfareContribution());

        // Full contract gross, then prorate for a mid-month hire.
        BigDecimal monthlyGross = structure.getGrossSalary()
                .add(item.getOvertime())
                .add(item.getBonus());
        BigDecimal daysInMonth = item.getDaysInMonth();
        BigDecimal daysWorked = item.getDaysWorked();
        boolean prorating = daysInMonth != null && daysInMonth.compareTo(BigDecimal.ZERO) > 0
                && daysWorked != null && daysWorked.compareTo(BigDecimal.ZERO) >= 0
                && daysWorked.compareTo(daysInMonth) < 0;
        BigDecimal grossPay = prorating
                ? monthlyGross.multiply(daysWorked)
                        .divide(daysInMonth, 2, RoundingMode.HALF_UP)
                : monthlyGross;
        // Unpaid leave: the days not worked reduce gross before statutory
        // deductions; the amount is recorded on the item for payslip labelling.
        BigDecimal unpaidLeaveDeduction = calculateUnpaidLeaveDeduction(monthlyGross, item);
        BigDecimal effectiveGross = grossPay.subtract(unpaidLeaveDeduction);
        item.setUnpaidLeaveDeduction(unpaidLeaveDeduction);
        item.setGrossPay(CurrencyConfig.money(grossPay));

        // Statutory deductions are computed against the (possibly adjusted) gross.
        BigDecimal grossForStatutory = effectiveGross;
        BigDecimal nssf = calculateNssf(grossForStatutory, config);
        BigDecimal shif = calculateShif(grossForStatutory, config);
        BigDecimal ahlEmployee = calculateAhl(grossForStatutory, config.getAhlEmployeeRate());
        BigDecimal taxableIncome = grossForStatutory.subtract(nssf); // NSSF is tax-deductible
        BigDecimal paye = calculatePaye(taxableIncome, config);

        item.setNssf(nssf);
        item.setShif(shif);
        item.setAhl(ahlEmployee);
        item.setPaye(paye);

        // Employer contributions (for information + accounting)
        item.setEmployerNssf(calculateNssf(grossForStatutory, config));
        item.setEmployerAhl(calculateAhl(grossForStatutory, config.getAhlEmployerRate()));

        // Total deductions (excludes unpaid leave: it already reduced gross)
        BigDecimal totalDeductions = paye.add(nssf).add(shif).add(ahlEmployee)
                .add(item.getPension())
                .add(item.getStaffLoanRepayment())
                .add(item.getSalaryAdvanceRecovery())
                .add(item.getWelfareContribution())
                .add(item.getCustomDeductions());
        item.setTotalDeductions(CurrencyConfig.money(totalDeductions));

        // Net pay
        BigDecimal netPay = effectiveGross.subtract(totalDeductions);
        item.setNetPay(CurrencyConfig.money(netPay));

        return item;
    }

    /**
     * Daily deduction for an unpaid-leave days input, labelled separately on the
     * payslip. Returns zero when no unpaid leave days are set on the item.
     */
    public static BigDecimal calculateUnpaidLeaveDeduction(BigDecimal monthlyGross, PayrollItem item) {
        BigDecimal unpaidDays = item.getUnpaidLeaveDays();
        BigDecimal daysInMonth = item.getDaysInMonth();
        if (unpaidDays == null || unpaidDays.compareTo(BigDecimal.ZERO) <= 0) {
            return CurrencyConfig.zero();
        }
        if (daysInMonth == null || daysInMonth.compareTo(BigDecimal.ZERO) <= 0) {
            return CurrencyConfig.zero();
        }
        return CurrencyConfig.money(monthlyGross
                .multiply(unpaidDays)
                .divide(daysInMonth, 2, RoundingMode.HALF_UP));
    }

    /**
     * Calculate PAYE using the config's graduated rates and personal relief.
     * @param taxableIncome gross pay minus NSSF
     */
    public static BigDecimal calculatePaye(BigDecimal taxableIncome) {
        return calculatePaye(taxableIncome, activeConfig());
    }

    public static BigDecimal calculatePaye(BigDecimal taxableIncome, StatutoryConfig config) {
        if (taxableIncome == null || taxableIncome.compareTo(BigDecimal.ZERO) <= 0) {
            return CurrencyConfig.zero();
        }

        BigDecimal tax = BigDecimal.ZERO;
        BigDecimal remaining = taxableIncome;

        List<StatutoryConfig.PayeBand> bands = config.getPayeBands();
        if (bands != null) {
            for (StatutoryConfig.PayeBand band : bands) {
                if (band.getCeiling() == null || band.getRate() == null) continue;
                if (remaining.compareTo(BigDecimal.ZERO) <= 0) break;

                BigDecimal taxable = remaining.min(band.getCeiling());
                tax = tax.add(taxable.multiply(band.getRate()).setScale(2, RoundingMode.HALF_UP));
                remaining = remaining.subtract(taxable);
            }
        }

        // Top rate for the remainder above the last band ceiling
        if (remaining.compareTo(BigDecimal.ZERO) > 0) {
            tax = tax.add(remaining.multiply(config.getPayeTopRate()).setScale(2, RoundingMode.HALF_UP));
        }

        // Apply personal relief
        BigDecimal relief = config.getPersonalRelief() == null ? CurrencyConfig.zero() : config.getPersonalRelief();
        tax = tax.subtract(relief);

        // PAYE cannot be negative
        if (tax.compareTo(BigDecimal.ZERO) < 0) {
            return CurrencyConfig.zero();
        }

        return CurrencyConfig.money(tax);
    }

    /**
     * NSSF employee contribution, split into Tier I and Tier II by the
     * configured pensionable-salary ceilings. Contributes to the higher tier
     * only on the portion above the Tier I ceiling.
     */
    public static BigDecimal calculateNssf(BigDecimal pensionableEarnings) {
        return calculateNssf(pensionableEarnings, activeConfig());
    }

    public static BigDecimal calculateNssf(BigDecimal pensionableEarnings, StatutoryConfig config) {
        if (pensionableEarnings == null || pensionableEarnings.compareTo(config.getNssfTierILower()) < 0) {
            return CurrencyConfig.zero();
        }

        BigDecimal rate = config.getNssfRate();
        BigDecimal tierICeiling = config.getNssfTierICeiling();
        BigDecimal tierIICeiling = config.getNssfTierIICeiling();

        BigDecimal tierI = pensionableEarnings.min(tierICeiling)
                .multiply(rate).setScale(2, RoundingMode.HALF_UP);
        BigDecimal remaining = pensionableEarnings.subtract(tierICeiling);
        BigDecimal tierII = BigDecimal.ZERO;
        if (remaining.compareTo(BigDecimal.ZERO) > 0) {
            tierII = remaining.min(tierIICeiling.subtract(tierICeiling))
                    .multiply(rate).setScale(2, RoundingMode.HALF_UP);
        }

        return CurrencyConfig.money(tierI.add(tierII));
    }

    /**
     * NSSF employer contribution — same tier split as the employee share.
     */
    public static BigDecimal calculateEmployerNssf(BigDecimal pensionableEarnings) {
        return calculateNssf(pensionableEarnings);
    }

    /**
     * SHIF — the absolute configured rate (default 2.75%) of gross with no
     * minimum or maximum floor.
     */
    public static BigDecimal calculateShif(BigDecimal grossSalary) {
        return calculateShif(grossSalary, activeConfig());
    }

    public static BigDecimal calculateShif(BigDecimal grossSalary, StatutoryConfig config) {
        if (grossSalary == null || grossSalary.compareTo(BigDecimal.ZERO) <= 0) {
            return CurrencyConfig.zero();
        }
        return CurrencyConfig.money(grossSalary.multiply(config.getShifRate())
                .setScale(2, RoundingMode.HALF_UP));
    }

    /**
     * Affordable Housing Levy — configured % of gross (default 1.5%). No
     * minimum-income floor.
     */
    public static BigDecimal calculateAhl(BigDecimal grossSalary, BigDecimal rate) {
        if (grossSalary == null || grossSalary.compareTo(BigDecimal.ZERO) <= 0 || rate == null) {
            return CurrencyConfig.zero();
        }
        return CurrencyConfig.money(grossSalary.multiply(rate).setScale(2, RoundingMode.HALF_UP));
    }

    public static BigDecimal calculateEmployerAhl(BigDecimal grossSalary) {
        return calculateAhl(grossSalary, activeConfig().getAhlEmployerRate());
    }
}