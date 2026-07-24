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
 * When payroll is posted:
 *   DEBIT  Salaries Expense (total gross pay)
 *   CREDIT PAYE Payable (total PAYE)
 *   CREDIT NSSF Payable (total employee NSSF)
 *   CREDIT SHIF Payable (total SHIF)
 *   CREDIT Pension Payable (total pension)
 *   CREDIT Staff Loan Control (total loan repayments)
 *   CREDIT Bank Control Account (total net pay)
 *
 * This ensures balanced double-entry and no payroll bypasses the accounting engine.
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
        BigDecimal totalPension = run.getTotalPension();
        BigDecimal totalLoans = items.stream()
                .map(PayrollItem::getStaffLoanRepayment)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalAdvances = items.stream()
                .map(PayrollItem::getSalaryAdvanceRecovery)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalWelfare = items.stream()
                .map(PayrollItem::getWelfareContribution)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalCustom = items.stream()
                .map(PayrollItem::getCustomDeductions)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalNet = run.getTotalNetPay();
        BigDecimal totalEmployerNssf = items.stream()
                .map(PayrollItem::getEmployerNssf)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

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

        // CREDIT: Other deductions via general expenses clearing
        BigDecimal otherDeductions = totalAdvances.add(totalWelfare).add(totalCustom);
        if (otherDeductions.compareTo(BigDecimal.ZERO) > 0) {
            journal.addLine(AccountType.ACCOUNTS_PAYABLE, "AP",
                    BigDecimal.ZERO, otherDeductions,
                    "Salary advances/welfare/custom — " + run.getPeriodLabel());
        }

        // CREDIT: Bank Control Account (net pay to be disbursed)
        journal.addLine(AccountType.BANK_CONTROL, "BNKCTRL",
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
        BigDecimal totalPension = originalRun.getTotalPension();
        BigDecimal totalLoans = items.stream()
                .map(PayrollItem::getStaffLoanRepayment)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalAdvances = items.stream()
                .map(PayrollItem::getSalaryAdvanceRecovery)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalWelfare = items.stream()
                .map(PayrollItem::getWelfareContribution)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalCustom = items.stream()
                .map(PayrollItem::getCustomDeductions)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalNet = originalRun.getTotalNetPay();
        BigDecimal totalEmployerNssf = items.stream()
                .map(PayrollItem::getEmployerNssf)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Reverse journal: swap debits and credits
        JournalEntry journal = new JournalEntry();
        journal.setDate(java.time.LocalDate.now());
        journal.setReference("PAYROLL-REV-" + originalRun.getRunNumber());
        journal.setNarration("Payroll reversal for " + originalRun.getPeriodLabel());

        // CREDIT: Salaries Expense (reversal — includes employer NSSF)
        journal.addLine(AccountType.SALARIES, "SALARY",
                BigDecimal.ZERO, totalGross.add(totalEmployerNssf),
                "Reversal — Salaries & Wages — " + originalRun.getPeriodLabel());

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

        // DEBIT: Other deductions
        BigDecimal otherDeductions = totalAdvances.add(totalWelfare).add(totalCustom);
        if (otherDeductions.compareTo(BigDecimal.ZERO) > 0) {
            journal.addLine(AccountType.ACCOUNTS_PAYABLE, "AP",
                    otherDeductions, BigDecimal.ZERO,
                    "Reversal — Advances/Welfare/Custom — " + originalRun.getPeriodLabel());
        }

        // DEBIT: Bank Control Account (reversal)
        journal.addLine(AccountType.BANK_CONTROL, "BNKCTRL",
                totalNet, BigDecimal.ZERO,
                "Reversal — Net pay — " + originalRun.getPeriodLabel());

        String user = AppConfig.getInstance().getCurrentUser();
        accountingEngine.postTransaction(journal, TransactionType.PAYROLL_REVERSAL,
                null, null, originalRun.getId());

        return journal.getId();
    }
}
