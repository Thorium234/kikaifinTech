package com.schaccs.service;

import com.schaccs.service.audit.AuditService;
import com.schaccs.service.fee.ArrearsService;
import com.schaccs.service.fee.FeeAllocationService;
import com.schaccs.service.fee.FeeCalculationService;
import com.schaccs.service.finance.AccountingService;
import com.schaccs.service.finance.BackupService;
import com.schaccs.service.finance.BankReconciliationService;
import com.schaccs.service.finance.BudgetService;
import com.schaccs.service.finance.FiscalYearService;
import com.schaccs.service.receipt.ReceiptService;
import com.schaccs.service.report.ReportService;
import com.schaccs.service.school.AcademicCalendarService;
import com.schaccs.service.school.SchoolCustomService;
import com.schaccs.service.student.StudentService;
import com.schaccs.service.sync.SyncEngine;
import com.schaccs.service.sync.SyncReportService;
import com.schaccs.service.voucher.PaymentVoucherService;
import com.schaccs.service.payroll.EmployeeService;
import com.schaccs.service.payroll.PayrollService;
import com.schaccs.service.procurement.SupplierService;
import com.schaccs.service.procurement.ProcurementService;
import com.schaccs.service.procurement.TenderService;
import com.schaccs.service.procurement.ContractService;

/**
 * Central locator for application services.
 * Services are lightweight stateless wrappers over shared singleton stores,
 * so a single shared instance per type is safe and makes the shared-state
 * boundary explicit (and easier to swap for tests).
 */
public final class Services {

    private static final Services INSTANCE = new Services();

    private final StudentService studentService = new StudentService();
    private final FeeCalculationService feeCalculationService = new FeeCalculationService();
    private final FeeAllocationService feeAllocationService = new FeeAllocationService();
    private final ArrearsService arrearsService = new ArrearsService();
    private final ReceiptService receiptService = new ReceiptService();
    private final ReportService reportService = new ReportService();
    private final AccountingService accountingService = new AccountingService();
    private final PaymentVoucherService paymentVoucherService = new PaymentVoucherService();
    private final AuditService auditService = new AuditService();
    private final BankReconciliationService bankReconciliationService = new BankReconciliationService();
    private final FiscalYearService fiscalYearService = new FiscalYearService();
    private final BudgetService budgetService = new BudgetService();
    private final BackupService backupService = new BackupService();
    private final SchoolCustomService schoolCustomService = new SchoolCustomService();
    private final AcademicCalendarService academicCalendarService = new AcademicCalendarService();
    private final SyncEngine syncEngine = SyncEngine.getInstance();
    private final SyncReportService syncReportService = SyncReportService.getInstance();
    private final EmployeeService employeeService = new EmployeeService();
    private final PayrollService payrollService = new PayrollService();
    private final SupplierService supplierService = new SupplierService();
    private final ProcurementService procurementService = new ProcurementService();
    private final TenderService tenderService = new TenderService();
    private final ContractService contractService = new ContractService();

    private Services() {
    }

    public static Services getInstance() {
        return INSTANCE;
    }

    public StudentService student() {
        return studentService;
    }

    public FeeCalculationService feeCalculation() {
        return feeCalculationService;
    }

    public FeeAllocationService feeAllocation() {
        return feeAllocationService;
    }

    public ArrearsService arrears() {
        return arrearsService;
    }

    public ReceiptService receipt() {
        return receiptService;
    }

    public ReportService report() {
        return reportService;
    }

    public AccountingService accounting() {
        return accountingService;
    }

    public PaymentVoucherService voucher() {
        return paymentVoucherService;
    }

    public AuditService audit() {
        return auditService;
    }

    public BankReconciliationService bankReconciliation() {
        return bankReconciliationService;
    }

    public FiscalYearService fiscalYear() {
        return fiscalYearService;
    }

    public BudgetService budget() {
        return budgetService;
    }

    public BackupService backup() {
        return backupService;
    }

    public SchoolCustomService schoolCustom() {
        return schoolCustomService;
    }

    public AcademicCalendarService academicCalendar() {
        return academicCalendarService;
    }

    public SyncEngine sync() {
        return syncEngine;
    }

    public SyncReportService syncReport() {
        return syncReportService;
    }

    public EmployeeService employee() {
        return employeeService;
    }

    public PayrollService payroll() {
        return payrollService;
    }

    public SupplierService supplier() {
        return supplierService;
    }

    public ProcurementService procurement() {
        return procurementService;
    }

    public TenderService tender() {
        return tenderService;
    }

    public ContractService contract() {
        return contractService;
    }
}
