package com.schaccs.service.payroll;

import com.schaccs.accounting.AccountingEngine;
import com.schaccs.config.AppConfig;
import com.schaccs.config.CurrencyConfig;
import com.schaccs.enums.AccountType;
import com.schaccs.enums.TransactionType;
import com.schaccs.model.finance.JournalEntry;
import com.schaccs.model.payroll.Employee;
import com.schaccs.model.payroll.PayrollItem;
import com.schaccs.model.payroll.PayrollRun;
import com.schaccs.model.payroll.SalaryStructure;
import com.schaccs.repository.PersistenceService;
import com.schaccs.service.audit.AuditService;
import com.schaccs.store.EmployeeStore;
import com.schaccs.store.LedgerStore;
import com.schaccs.store.PayrollStore;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.function.Function;

public class PayrollService {

    private final PayrollStore payrollStore;
    private final EmployeeStore employeeStore;
    private final AuditService auditService;
    private final PayrollAccountingIntegration accountingIntegration;
    private final AccountingEngine accountingEngine;

    public PayrollService() {
        this(PayrollStore.getInstance(), EmployeeStore.getInstance(), new AuditService(),
                new PayrollAccountingIntegration());
    }

    public PayrollService(PayrollStore payrollStore, EmployeeStore employeeStore,
                          AuditService auditService, PayrollAccountingIntegration accountingIntegration) {
        this.payrollStore = payrollStore;
        this.employeeStore = employeeStore;
        this.auditService = auditService;
        this.accountingIntegration = accountingIntegration;
        this.accountingEngine = new AccountingEngine();
    }

    /**
     * Generate a new payroll run for the given month/year.
     * Calculates PAYE, NSSF, SHIF, AHL for all active employees; mid-month hires
     * are prorated to days worked (payroll.md §4).
     */
    public PayrollRun generatePayroll(int month, int year) {
        // Check for existing run in this period
        Optional<PayrollRun> existing = payrollStore.findRunByPeriod(month, year);
        if (existing.isPresent() && existing.get().getStatus() != PayrollRun.PayrollStatus.REVERSED) {
            throw new IllegalStateException("Payroll for " + month + "/" + year + " already exists with status: "
                    + existing.get().getStatus());
        }

        List<Employee> activeEmployees = employeeStore.findActiveEmployees();
        if (activeEmployees.isEmpty()) {
            throw new IllegalStateException("No active employees to process payroll for.");
        }

        PayrollRun run = new PayrollRun();
        run.setRunNumber(generateRunNumber(month, year));
        run.setMonth(month);
        run.setYear(year);
        YearMonth ym = YearMonth.of(year, month);
        run.setPeriodStart(ym.atDay(1));
        run.setPeriodEnd(ym.atEndOfMonth());
        run.setStatus(PayrollRun.PayrollStatus.DRAFT);
        run.setPreparedBy(AppConfig.getInstance().getCurrentUser());
        run.setPreparedAt(LocalDateTime.now());
        run.setCreatedAt(LocalDateTime.now());

        calculateAndApply(run, activeEmployees);
        checkBudgetOverrun(run);

        payrollStore.getPayrollRuns().add(0, run);

        auditService.log("PAYROLL_GENERATED", "PayrollRun", run.getId(),
                "Generated payroll " + run.getRunNumber() + " — " + run.getEmployeeCount()
                        + " employees, net pay: " + run.getTotalNetPay());
        PersistenceService.getInstance().saveAll();

        return run;
    }

    /**
     * Approve a draft payroll run. Enforces the Kenyan One-Third Rule
     * (Employment Act, Section 19): a full-period employee's net pay must not
     * fall below one-third of their basic salary (payroll.md §3).
     */
    public void approvePayroll(String runId) {
        PayrollRun run = requireRun(runId);

        if (run.getStatus() != PayrollRun.PayrollStatus.DRAFT
                && run.getStatus() != PayrollRun.PayrollStatus.PENDING_APPROVAL) {
            throw new IllegalStateException("Cannot approve payroll in status: " + run.getStatus());
        }

        for (PayrollItem item : payrollStore.findItemsByRunId(runId)) {
            if (isPartialPeriod(item)) continue; // prorated/unpaid-leave items are exempt
            BigDecimal oneThirdBasic = item.getBasicSalary().divide(
                    new BigDecimal("3"), 2, RoundingMode.HALF_UP);
            if (item.getNetPay().compareTo(oneThirdBasic) < 0) {
                throw new IllegalStateException("Cannot process payroll: Employee " + item.getEmployeeName()
                        + " (" + item.getEmployeeNumber() + ") net pay falls below 1/3 basic pay limit.");
            }
        }

        run.setStatus(PayrollRun.PayrollStatus.APPROVED);
        run.setApprovedBy(AppConfig.getInstance().getCurrentUser());
        run.setApprovedAt(LocalDateTime.now());

        auditService.log("PAYROLL_APPROVED", "PayrollRun", run.getId(),
                "Approved payroll " + run.getRunNumber());
        PersistenceService.getInstance().saveAll();
    }

    /**
     * Post an approved payroll run to the general ledger.
     * Re-checks the PE budget overrun flag before posting, then creates balanced
     * double-entry journal entries (payroll.md §2, §5).
     */
    public void postPayroll(String runId) {
        PayrollRun run = requireRun(runId);

        if (run.getStatus() != PayrollRun.PayrollStatus.APPROVED) {
            throw new IllegalStateException("Only approved payroll can be posted. Current status: " + run.getStatus());
        }

        List<PayrollItem> items = payrollStore.findItemsByRunId(runId);
        if (items.isEmpty()) {
            throw new IllegalStateException("No payroll items found for run: " + runId);
        }

        checkBudgetOverrun(run);

        // Post journal entry to accounting engine
        String journalId = accountingIntegration.postPayroll(run, items);

        run.setStatus(PayrollRun.PayrollStatus.POSTED);
        run.setPostedBy(AppConfig.getInstance().getCurrentUser());
        run.setPostedAt(LocalDateTime.now());
        run.setJournalId(journalId);

        auditService.log("PAYROLL_POSTED", "PayrollRun", run.getId(),
                "Posted payroll " + run.getRunNumber() + " — Journal: " + journalId);
        PersistenceService.getInstance().saveAll();
    }

    /**
     * Reverse a posted payroll run.
     * Creates reversing journal entries in the general ledger.
     */
    public void reversePayroll(String runId) {
        PayrollRun run = requireRun(runId);

        if (run.getStatus() != PayrollRun.PayrollStatus.POSTED) {
            throw new IllegalStateException("Only posted payroll can be reversed. Current status: " + run.getStatus());
        }

        // Post reversal journal
        String reversalJournalId = accountingIntegration.postPayrollReversal(run, payrollStore.findItemsByRunId(runId));

        run.setStatus(PayrollRun.PayrollStatus.REVERSED);
        run.setNotes("Reversed by " + AppConfig.getInstance().getCurrentUser()
                + " — Reversal journal: " + reversalJournalId);

        auditService.log("PAYROLL_REVERSED", "PayrollRun", run.getId(),
                "Reversed payroll " + run.getRunNumber() + " — Journal: " + reversalJournalId);
        PersistenceService.getInstance().saveAll();
    }

    /**
     * Recalculate payroll items for a draft run.
     */
    public void recalculatePayroll(String runId) {
        PayrollRun run = requireRun(runId);

        if (run.getStatus() != PayrollRun.PayrollStatus.DRAFT) {
            throw new IllegalStateException("Only draft payroll can be recalculated. Current status: " + run.getStatus());
        }

        // Remove existing items, then regenerate from the current employee list
        payrollStore.getPayrollItems().removeAll(payrollStore.findItemsByRunId(runId));
        calculateAndApply(run, employeeStore.findActiveEmployees());

        auditService.log("PAYROLL_RECALCULATED", "PayrollRun", run.getId(),
                "Recalculated payroll " + run.getRunNumber());
        PersistenceService.getInstance().saveAll();
    }

    /**
     * Add an Unpaid Leave Days adjustment to an employee's draft-run item
     * (payroll.md §4). The engine labels the amount as an "Unpaid Leave Deduction"
     * on the payslip and reduces gross before statutory deductions.
     */
    public void applyUnpaidLeave(String runId, String employeeId, BigDecimal unpaidLeaveDays) {
        PayrollRun run = requireRun(runId);
        if (run.getStatus() != PayrollRun.PayrollStatus.DRAFT) {
            throw new IllegalStateException("Only draft payroll can be adjusted. Current status: " + run.getStatus());
        }
        if (unpaidLeaveDays == null || unpaidLeaveDays.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Unpaid leave days must be zero or more.");
        }

        PayrollItem item = payrollStore.findItemsByRunId(runId).stream()
                .filter(i -> employeeId.equals(i.getEmployeeId()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Employee has no item in this payroll run."));

        if (unpaidLeaveDays.compareTo(BigDecimal.ZERO) <= 0) {
            item.setUnpaidLeaveDays(null);
            item.setUnpaidLeaveDeduction(CurrencyConfig.zero());
        } else {
            int daysInMonth = YearMonth.of(run.getYear(), run.getMonth()).lengthOfMonth();
            item.setUnpaidLeaveDays(unpaidLeaveDays);
            item.setDaysInMonth(new BigDecimal(daysInMonth));
        }

        recalculateItem(item);
        applyTotals(run, payrollStore.findItemsByRunId(runId));
        checkBudgetOverrun(run);

        auditService.log("PAYROLL_UNPAID_LEAVE", "PayrollRun", run.getId(),
                "Set unpaid leave for employee " + employeeId + " to " + unpaidLeaveDays + " day(s)");
        PersistenceService.getInstance().saveAll();
    }

    /**
     * Evaluate the PE-votehead budget-overrun guard (payroll.md §2). The PE
     * ledger's unused balance is the credit side of the SALARIES account; if the
     * run's gross pay exceeds it, the run is flagged with the variance and bank
     * remittance is blocked until an Inter-Votehead Authorization is logged.
     */
    public PayrollRun checkBudgetOverrun(String runId) {
        return checkBudgetOverrun(requireRun(runId));
    }

    private PayrollRun checkBudgetOverrun(PayrollRun run) {
        BigDecimal peBalance = LedgerStore.getInstance().getAccountBalance(AccountType.SALARIES);
        BigDecimal available = peBalance.signum() < 0 ? peBalance.negate() : CurrencyConfig.zero();
        BigDecimal variance = run.getTotalGrossPay().subtract(available);
        boolean overrun = variance.signum() > 0;

        run.setPeAvailableBalance(available);
        run.setOverrunVariance(overrun ? variance : CurrencyConfig.zero());
        run.setBudgetOverrun(overrun);
        return run;
    }

    /**
     * Log an Inter-Votehead Authorization Journal when the PE votehead cannot
     * fund the payroll run (payroll.md §2). Posts a balanced transfer from a
     * surplus source account into the PE (SALARIES) ledger and marks the run
     * authorized so bank remittance can be generated.
     *
     * @param sourceAccount the surplus account borrowed from (e.g. Administration)
     * @param amount the amount to authorize (must cover the overrun variance)
     * @param narration bursar-supplied authorization note
     * @return the inter-votehead journal id
     */
    public String authorizeBudgetOverrun(String runId, AccountType sourceAccount, BigDecimal amount, String narration) {
        PayrollRun run = requireRun(runId);
        if (!run.isBudgetOverrun()) {
            throw new IllegalStateException("No budget overrun to authorize for run: " + run.getRunNumber());
        }
        if (amount == null || amount.compareTo(run.getOverrunVariance()) < 0) {
            throw new IllegalStateException("Authorization amount must cover the overrun variance ("
                    + run.getOverrunVariance() + ").");
        }
        if (sourceAccount == null) {
            throw new IllegalArgumentException("A surplus source account is required for the inter-votehead authorization.");
        }

        JournalEntry journal = new JournalEntry();
        journal.setDate(LocalDate.now());
        journal.setReference("IVA-" + run.getRunNumber());
        journal.setNarration(narration == null || narration.isBlank()
                ? "Inter-Votehead Authorization for " + run.getRunNumber()
                : narration);

        // Debit the surplus source account (reduces surplus), credit PE ledger.
        if (sourceAccount.isDebitNormal()) {
            journal.addLine(sourceAccount, sourceAccount.getCode(),
                    BigDecimal.ZERO, amount, "Transfer to PE votehead — " + run.getRunNumber());
        } else {
            journal.addLine(sourceAccount, sourceAccount.getCode(),
                    amount, BigDecimal.ZERO, "Transfer to PE votehead — " + run.getRunNumber());
        }
        journal.addLine(AccountType.SALARIES, "SALARY",
                BigDecimal.ZERO, amount, "Inter-Votehead PE top-up — " + run.getRunNumber());

        String journalId = postAuthorizationJournal(journal);
        run.setBudgetAuthorized(true);
        run.setBudgetAuthorizationRef(journalId);
        run.setBudgetAuthorizedBy(AppConfig.getInstance().getCurrentUser());
        checkBudgetOverrun(run);

        auditService.log("PAYROLL_BUDGET_AUTHORIZED", "PayrollRun", run.getId(),
                "Budget overrun authorized for " + run.getRunNumber()
                        + " — amount " + amount + " — Journal: " + journalId);
        PersistenceService.getInstance().saveAll();
        return journalId;
    }

    private String postAuthorizationJournal(JournalEntry journal) {
        accountingEngine.postTransaction(journal, TransactionType.JOURNAL,
                null, null, null);
        return journal.getId();
    }

    /**
     * Produce the bank remittance file (as CSV lines) for a posted run. Blocked
     * while a budget overrun has not been authorized (payroll.md §2).
     */
    public String generateBankRemittance(String runId) {
        PayrollRun run = requireRun(runId);
        if (run.getStatus() != PayrollRun.PayrollStatus.POSTED) {
            throw new IllegalStateException("Only posted payroll can generate a bank remittance. Current status: "
                    + run.getStatus());
        }
        if (run.isBudgetOverrun() && !run.isBudgetAuthorized()) {
            throw new IllegalStateException("Budget Overrun: PE votehead balance of " + run.getPeAvailableBalance()
                    + " is insufficient for payroll cost of " + run.getTotalGrossPay()
                    + ". Post an Inter-Votehead Authorization Journal before generating the bank remittance.");
        }

        StringJoiner csv = new StringJoiner("\n");
        csv.add("Employee Number,Employee Name,Bank Name,Bank Branch,Account Number,Net Pay");
        for (PayrollItem item : payrollStore.findItemsByRunId(runId)) {
            Employee emp = employeeStore.findByEmployeeNumber(item.getEmployeeNumber()).orElse(null);
            csv.add(String.join(",",
                    item.getEmployeeNumber(),
                    item.getEmployeeName() == null ? "" : item.getEmployeeName().replace(",", " "),
                    emp != null && emp.getBankName() != null ? emp.getBankName().replace(",", " ") : "",
                    emp != null && emp.getBankBranch() != null ? emp.getBankBranch().replace(",", " ") : "",
                    emp != null && emp.getBankAccountNumber() != null ? emp.getBankAccountNumber() : "",
                    item.getNetPay().toPlainString()));
        }
        return csv.toString();
    }

    public Optional<PayrollRun> findRunById(String id) {
        return payrollStore.findRunById(id);
    }

    public List<PayrollItem> findItemsByRunId(String runId) {
        return payrollStore.findItemsByRunId(runId);
    }

    public Optional<PayrollRun> findLatestPostedRun() {
        return payrollStore.findLatestPostedRun();
    }

    public PayrollStore getStore() {
        return payrollStore;
    }

    private PayrollRun requireRun(String runId) {
        return payrollStore.findRunById(runId)
                .orElseThrow(() -> new IllegalStateException("Payroll run not found: " + runId));
    }

    /**
     * Build one payroll item per active employee with a salary structure and
     * store them against the run. Employees without a salary structure are skipped.
     * Mid-month hires have their gross prorated to days worked (payroll.md §4).
     */
    private void calculateAndApply(PayrollRun run, List<Employee> employees) {
        List<PayrollItem> items = new ArrayList<>();

        for (Employee emp : employees) {
            Optional<SalaryStructure> salaryOpt = employeeStore.findActiveSalaryStructure(emp.getId());
            if (salaryOpt.isEmpty()) continue;

            SalaryStructure salary = salaryOpt.get();
            PayrollItem item = new PayrollItem();
            item.setPayrollRunId(run.getId());
            item.setEmployeeId(emp.getId());
            item.setEmployeeNumber(emp.getEmployeeNumber());
            item.setEmployeeName(emp.getFullName());
            item.setDepartment(emp.getDepartment());

            applyProration(run, emp, item);

            PayrollCalculationEngine.calculate(salary, item);
            items.add(item);
        }

        payrollStore.getPayrollItems().addAll(items);
        applyTotals(run, items);
    }

    private void applyProration(PayrollRun run, Employee emp, PayrollItem item) {
        LocalDate activeFrom = emp.getEmploymentDate();
        if (activeFrom == null) return;
        boolean samePeriod = activeFrom.getYear() == run.getYear() && activeFrom.getMonthValue() == run.getMonth();
        if (!samePeriod || activeFrom.getDayOfMonth() <= 1) return;

        int daysInMonth = YearMonth.of(run.getYear(), run.getMonth()).lengthOfMonth();
        int daysWorked = daysInMonth - activeFrom.getDayOfMonth() + 1;
        item.setDaysInMonth(new BigDecimal(daysInMonth));
        item.setDaysWorked(new BigDecimal(daysWorked));
    }

    private boolean isPartialPeriod(PayrollItem item) {
        return item.getDaysWorked() != null
                || (item.getUnpaidLeaveDays() != null && item.getUnpaidLeaveDays().compareTo(BigDecimal.ZERO) > 0);
    }

    private void recalculateItem(PayrollItem item) {
        Optional<SalaryStructure> salary = employeeStore.findActiveSalaryStructure(item.getEmployeeId());
        salary.ifPresent(s -> PayrollCalculationEngine.calculate(s, item));
    }

    private void applyTotals(PayrollRun run, List<PayrollItem> items) {
        run.setEmployeeCount(items.size());
        // Total gross recorded as the recognised salary cost (gross minus unpaid
        // leave) so the journal reverses exactly: gross = net + deductions.
        run.setTotalGrossPay(sum(items, i -> i.getNetPay().add(i.getTotalDeductions())));
        run.setTotalDeductions(sum(items, PayrollItem::getTotalDeductions));
        run.setTotalNetPay(sum(items, PayrollItem::getNetPay));
        run.setTotalPAYE(sum(items, PayrollItem::getPaye));
        run.setTotalNSSF(sum(items, PayrollItem::getNssf));
        run.setTotalSHIF(sum(items, PayrollItem::getShif));
        run.setTotalPension(sum(items, PayrollItem::getPension));
    }

    private static BigDecimal sum(List<PayrollItem> items, Function<PayrollItem, BigDecimal> amount) {
        return items.stream().map(amount).reduce(CurrencyConfig.zero(), BigDecimal::add);
    }

    private String generateRunNumber(int month, int year) {
        long count = payrollStore.getPayrollRuns().stream()
                .filter(r -> r.getYear() == year)
                .count();
        return String.format("PR%04d%02d%03d", year, month, count + 1);
    }
}
