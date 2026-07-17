package com.schaccs.service;

import com.schaccs.service.fee.ArrearsService;
import com.schaccs.service.fee.FeeAllocationService;
import com.schaccs.service.fee.FeeCalculationService;
import com.schaccs.service.finance.AccountingService;
import com.schaccs.service.receipt.ReceiptService;
import com.schaccs.service.report.ReportService;
import com.schaccs.service.student.StudentService;
import com.schaccs.service.voucher.PaymentVoucherService;

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
}
