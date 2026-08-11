package com.schaccs.service.payroll;

import com.schaccs.config.AppConfig;
import com.schaccs.config.CurrencyConfig;
import com.schaccs.model.payroll.Employee;
import com.schaccs.model.payroll.PayrollItem;
import com.schaccs.model.payroll.PayrollRun;
import com.schaccs.model.payroll.SalaryStructure;
import com.schaccs.repository.PersistenceService;
import com.schaccs.service.audit.AuditService;
import com.schaccs.store.EmployeeStore;
import com.schaccs.store.PayrollStore;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

public class PayrollService {

    private final PayrollStore payrollStore;
    private final EmployeeStore employeeStore;
    private final AuditService auditService;
    private final PayrollAccountingIntegration accountingIntegration;

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
    }

    /**
     * Generate a new payroll run for the given month/year.
     * Calculates PAYE, NSSF, SHIF for all active employees.
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

        payrollStore.getPayrollRuns().add(0, run);

        auditService.log("PAYROLL_GENERATED", "PayrollRun", run.getId(),
                "Generated payroll " + run.getRunNumber() + " — " + run.getEmployeeCount()
                        + " employees, net pay: " + run.getTotalNetPay());
        PersistenceService.getInstance().saveAll();

        return run;
    }

    /**
     * Approve a draft payroll run.
     */
    public void approvePayroll(String runId) {
        PayrollRun run = requireRun(runId);

        if (run.getStatus() != PayrollRun.PayrollStatus.DRAFT
                && run.getStatus() != PayrollRun.PayrollStatus.PENDING_APPROVAL) {
            throw new IllegalStateException("Cannot approve payroll in status: " + run.getStatus());
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
     * Creates balanced double-entry journal entries.
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

            PayrollCalculationEngine.calculate(salary, item);
            items.add(item);
        }

        payrollStore.getPayrollItems().addAll(items);
        applyTotals(run, items);
    }

    private void applyTotals(PayrollRun run, List<PayrollItem> items) {
        run.setEmployeeCount(items.size());
        run.setTotalGrossPay(sum(items, PayrollItem::getGrossPay));
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
