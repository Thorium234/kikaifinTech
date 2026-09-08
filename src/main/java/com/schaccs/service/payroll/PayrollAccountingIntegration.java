package com.schaccs.service.payroll;

import com.schaccs.accounting.AccountingEngine;
import com.schaccs.config.AppConfig;
import com.schaccs.enums.AccountType;
import com.schaccs.enums.TransactionType;
import com.schaccs.model.finance.JournalEntry;
import com.schaccs.model.payroll.PayrollItem;
import com.schaccs.model.payroll.PayrollRun;

import java.math.BigDecimal;
import java.util.List;

/**
 * Posts payroll journal entries to the general ledger.
 *
 * <p>Spec (payroll.md §5): the payroll run is converted into a unified, balanced
 * double-entry journal voucher:
 * <pre>
 *   DEBIT  Expense: BOM Teacher Salaries (PE)      total gross pay
 *   DEBIT  Expense: Employer AHL Contribution      employer matching share
 *   CREDIT Liability: KRA PAYE Payable             deducted tax
 *   CREDIT Liability: KRA AHL Payable              combined employee + employer
 *   CREDIT Liability: SHIF Payable                 deducted medical levy
 *   CREDIT Liability: NSSF Payable                 employee + employer
 *   CREDIT Liability: Pension Payable
 *   CREDIT Liability: Staff Loan Control           loan repayments
 *   CREDIT Asset: Staff Advances Receivable        advance recovery credit-back
 *   CREDIT Accounts Payable                        welfare/custom deductions
 *   CREDIT Liability: Net Salary Clearing          net cash to staff banks
 * </pre>
 */
public class PayrollAccountingIntegration {

    private final AccountingEngine accountingEngine;

    public PayrollAccountingIntegration() {
        this(new AccountingEngine());
    }

    public PayrollAccountingIntegration(AccountingEngine accountingEngine) {
        this.accountingEngine = accountingEngine;
    }

    /**
     * Post the payroll journal entry for a posted payroll run.
     * Creates a single balanced journal with all gross pay debited to Salaries Expense
     * and all deductions/credits posted to their respective payable accounts.
     */
    public String postPayroll(PayrollRun run, List<PayrollItem> items) {
        BigDecimal totalGross = run.getTotalGrossPay();
        BigDecimal totalPaye = run.getTotalPAYE();
        BigDecimal totalNssf = run.getTotalNSSF();
        BigDecimal totalShif = run.getTotalSHIF();
        BigDecimal totalAhlEmployee = sum(items, PayrollItem::getAhl);
        BigDecimal totalAhlEmployer = sum(items, PayrollItem::getEmployerAhl);
        BigDecimal totalPension = run.getTotalPension();
        BigDecimal totalLoans = sum(items, PayrollItem::getStaffLoanRepayment);
        BigDecimal totalAdvances = sum(items, PayrollItem::getSalaryAdvanceRecovery);
        BigDecimal totalWelfare = sum(items, PayrollItem::getWelfareContribution);
        BigDecimal totalCustom = sum(items, PayrollItem::getCustomDeductions);
        BigDecimal totalNet = run.getTotalNetPay();
        BigDecimal totalEmployerNssf = sum(items, PayrollItem::getEmployerNssf);

        // Build the journal entry
        JournalEntry journal = new JournalEntry();
        journal.setDate(java.time.LocalDate.now());
        journal.setReference("PAYROLL-" + run.getRunNumber());
        journal.setNarration("Payroll posting for " + run.getPeriodLabel()
                + " (" + run.getEmployeeCount() + " employees)");

        // DEBIT: Salaries Expense (gross pay + employer NSSF as employment cost)
        BigDecimal totalSalariesExpense = totalGross.add(totalEmployerNssf);
        journal.addLine(AccountType.SALARIES, "SALARY",
                totalSalariesExpense, BigDecimal.ZERO,
                "Salaries & Wages — " + run.getPeriodLabel());

        // DEBIT: Employer AHL matching share
        if (totalAhlEmployer.compareTo(BigDecimal.ZERO) > 0) {
            journal.addLine(AccountType.EMPLOYER_AHL_EXPENSE, "AHLEXP",
                    totalAhlEmployer, BigDecimal.ZERO,
                    "Employer AHL contribution (matching 1.5%) — " + run.getPeriodLabel());
        }

        // CREDIT: PAYE Payable
        if (totalPaye.compareTo(BigDecimal.ZERO) > 0) {
            journal.addLine(AccountType.PAYE_PAYABLE, "PAYE",
                    BigDecimal.ZERO, totalPaye,
                    "PAYE deduction — " + run.getPeriodLabel());
        }

        // CREDIT: NSSF Payable (employee + employer share)
        BigDecimal totalNssfAll = totalNssf.add(totalEmployerNssf);
        if (totalNssfAll.compareTo(BigDecimal.ZERO) > 0) {
            journal.addLine(AccountType.NSSF_PAYABLE, "NSSF",
                    BigDecimal.ZERO, totalNssfAll,
                    "NSSF contribution (employee + employer) — " + run.getPeriodLabel());
        }

        // CREDIT: SHIF Payable
        if (totalShif.compareTo(BigDecimal.ZERO) > 0) {
            journal.addLine(AccountType.SHIF_PAYABLE, "SHIF",
                    BigDecimal.ZERO, totalShif,
                    "SHIF contribution — " + run.getPeriodLabel());
        }

        // CREDIT: AHL Payable (combined employee + employer)
        BigDecimal totalAhlAll = totalAhlEmployee.add(totalAhlEmployer);
        if (totalAhlAll.compareTo(BigDecimal.ZERO) > 0) {
            journal.addLine(AccountType.AHL_PAYABLE, "AHL",
                    BigDecimal.ZERO, totalAhlAll,
                    "Affordable Housing Levy (employee + employer) — " + run.getPeriodLabel());
        }

        // CREDIT: Pension Payable
        if (totalPension.compareTo(BigDecimal.ZERO) > 0) {
            journal.addLine(AccountType.PENSION_PAYABLE, "PENSION",
                    BigDecimal.ZERO, totalPension,
                    "Pension contribution — " + run.getPeriodLabel());
        }

        // CREDIT: Staff Loan Control
        if (totalLoans.compareTo(BigDecimal.ZERO) > 0) {
            journal.addLine(AccountType.STAFF_LOAN_CONTROL, "SLOAN",
                    BigDecimal.ZERO, totalLoans,
                    "Staff loan repayments — " + run.getPeriodLabel());
        }

        // CREDIT: Staff Advances Receivable (credit-back to zero)
        if (totalAdvances.compareTo(BigDecimal.ZERO) > 0) {
            journal.addLine(AccountType.STAFF_ADVANCES_RECEIVABLE, "SADV",
                    BigDecimal.ZERO, totalAdvances,
                    "Staff advance recovery credit-back — " + run.getPeriodLabel());
        }

        // CREDIT: Other deductions via general expenses clearing
        BigDecimal otherDeductions = totalWelfare.add(totalCustom);
        if (otherDeductions.compareTo(BigDecimal.ZERO) > 0) {
            journal.addLine(AccountType.ACCOUNTS_PAYABLE, "AP",
                    BigDecimal.ZERO, otherDeductions,
                    "Welfare/custom deductions — " + run.getPeriodLabel());
        }

        // CREDIT: Net Salary Clearing (net pay to be disbursed)
        journal.addLine(AccountType.NET_SALARY_CLEARING, "NETPAY",
                BigDecimal.ZERO, totalNet,
                "Net pay — " + run.getPeriodLabel());

        // Post through the accounting engine
        String user = AppConfig.getInstance().getCurrentUser();
        accountingEngine.postTransaction(journal, TransactionType.PAYROLL,
                null, null, run.getId());

        return journal.getId();
    }

    /**
     * Post a reversal journal entry for a reversed payroll run.
     * Reverses the original payroll posting.
     */
    public String postPayrollReversal(PayrollRun originalRun, List<PayrollItem> items) {
        BigDecimal totalGross = originalRun.getTotalGrossPay();
        BigDecimal totalPaye = originalRun.getTotalPAYE();
        BigDecimal totalNssf = originalRun.getTotalNSSF();
        BigDecimal totalShif = originalRun.getTotalSHIF();
        BigDecimal totalAhlEmployee = sum(items, PayrollItem::getAhl);
        BigDecimal totalAhlEmployer = sum(items, PayrollItem::getEmployerAhl);
        BigDecimal totalPension = originalRun.getTotalPension();
        BigDecimal totalLoans = sum(items, PayrollItem::getStaffLoanRepayment);
        BigDecimal totalAdvances = sum(items, PayrollItem::getSalaryAdvanceRecovery);
        BigDecimal totalWelfare = sum(items, PayrollItem::getWelfareContribution);
        BigDecimal totalCustom = sum(items, PayrollItem::getCustomDeductions);
        BigDecimal totalNet = originalRun.getTotalNetPay();
        BigDecimal totalEmployerNssf = sum(items, PayrollItem::getEmployerNssf);

        // Reverse journal: swap debits and credits
        JournalEntry journal = new JournalEntry();
        journal.setDate(java.time.LocalDate.now());
        journal.setReference("PAYROLL-REV-" + originalRun.getRunNumber());
        journal.setNarration("Payroll reversal for " + originalRun.getPeriodLabel());

        // CREDIT: Salaries Expense (reversal — gross + employer NSSF)
        journal.addLine(AccountType.SALARIES, "SALARY",
                BigDecimal.ZERO, totalGross.add(totalEmployerNssf),
                "Reversal — Salaries & Wages — " + originalRun.getPeriodLabel());

        // CREDIT: Employer AHL matching share (reversal)
        if (totalAhlEmployer.compareTo(BigDecimal.ZERO) > 0) {
            journal.addLine(AccountType.EMPLOYER_AHL_EXPENSE, "AHLEXP",
                    BigDecimal.ZERO, totalAhlEmployer,
                    "Reversal — Employer AHL — " + originalRun.getPeriodLabel());
        }

        // DEBIT: PAYE Payable (reversal)
        if (totalPaye.compareTo(BigDecimal.ZERO) > 0) {
            journal.addLine(AccountType.PAYE_PAYABLE, "PAYE",
                    totalPaye, BigDecimal.ZERO,
                    "Reversal — PAYE — " + originalRun.getPeriodLabel());
        }

        // DEBIT: NSSF Payable (employee + employer)
        BigDecimal totalNssfAll = totalNssf.add(totalEmployerNssf);
        if (totalNssfAll.compareTo(BigDecimal.ZERO) > 0) {
            journal.addLine(AccountType.NSSF_PAYABLE, "NSSF",
                    totalNssfAll, BigDecimal.ZERO,
                    "Reversal — NSSF (employee + employer) — " + originalRun.getPeriodLabel());
        }

        // DEBIT: SHIF Payable
        if (totalShif.compareTo(BigDecimal.ZERO) > 0) {
            journal.addLine(AccountType.SHIF_PAYABLE, "SHIF",
                    totalShif, BigDecimal.ZERO,
                    "Reversal — SHIF — " + originalRun.getPeriodLabel());
        }

        // DEBIT: AHL Payable (combined)
        BigDecimal totalAhlAll = totalAhlEmployee.add(totalAhlEmployer);
        if (totalAhlAll.compareTo(BigDecimal.ZERO) > 0) {
            journal.addLine(AccountType.AHL_PAYABLE, "AHL",
                    totalAhlAll, BigDecimal.ZERO,
                    "Reversal — AHL (employee + employer) — " + originalRun.getPeriodLabel());
        }

        // DEBIT: Pension Payable
        if (totalPension.compareTo(BigDecimal.ZERO) > 0) {
            journal.addLine(AccountType.PENSION_PAYABLE, "PENSION",
                    totalPension, BigDecimal.ZERO,
                    "Reversal — Pension — " + originalRun.getPeriodLabel());
        }

        // DEBIT: Staff Loan Control
        if (totalLoans.compareTo(BigDecimal.ZERO) > 0) {
            journal.addLine(AccountType.STAFF_LOAN_CONTROL, "SLOAN",
                    totalLoans, BigDecimal.ZERO,
                    "Reversal — Staff Loans — " + originalRun.getPeriodLabel());
        }

        // DEBIT: Staff Advances Receivable (reversal)
        if (totalAdvances.compareTo(BigDecimal.ZERO) > 0) {
            journal.addLine(AccountType.STAFF_ADVANCES_RECEIVABLE, "SADV",
                    totalAdvances, BigDecimal.ZERO,
                    "Reversal — Staff Advances — " + originalRun.getPeriodLabel());
        }

        // DEBIT: Accounts Payable (welfare/custom)
        BigDecimal otherDeductions = totalWelfare.add(totalCustom);
        if (otherDeductions.compareTo(BigDecimal.ZERO) > 0) {
            journal.addLine(AccountType.ACCOUNTS_PAYABLE, "AP",
                    otherDeductions, BigDecimal.ZERO,
                    "Reversal — Welfare/Custom — " + originalRun.getPeriodLabel());
        }

        // DEBIT: Net Salary Clearing (reversal)
        journal.addLine(AccountType.NET_SALARY_CLEARING, "NETPAY",
                totalNet, BigDecimal.ZERO,
                "Reversal — Net pay — " + originalRun.getPeriodLabel());

        String user = AppConfig.getInstance().getCurrentUser();
        accountingEngine.postTransaction(journal, TransactionType.PAYROLL_REVERSAL,
                null, null, originalRun.getId());

        return journal.getId();
    }

    private static BigDecimal sum(List<PayrollItem> items, java.util.function.Function<PayrollItem, BigDecimal> amount) {
        return items.stream().map(amount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
