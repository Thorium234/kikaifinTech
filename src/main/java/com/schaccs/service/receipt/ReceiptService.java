package com.schaccs.service.receipt;

import com.schaccs.accounting.AccountingEngine;
import com.schaccs.accounting.ReceiptAllocationEngine;
import com.schaccs.config.AppConfig;
import com.schaccs.config.CurrencyConfig;
import com.schaccs.enums.AcademicTerm;
import com.schaccs.enums.AccountType;
import com.schaccs.enums.PaymentMode;
import com.schaccs.model.fee.FeeAllocation;
import com.schaccs.model.fee.FeeStructure;
import com.schaccs.model.finance.Votehead;
import com.schaccs.model.finance.FinancialTransaction;
import com.schaccs.model.receipt.Receipt;
import com.schaccs.model.receipt.ReceiptLine;
import com.schaccs.model.student.Student;
import com.schaccs.model.student.StudentFeeLedger;
import com.schaccs.repository.PersistenceService;
import com.schaccs.store.FeeStructureStore;
import com.schaccs.store.LedgerStore;
import com.schaccs.store.ReceiptStore;
import com.schaccs.store.StudentStore;
import com.schaccs.service.audit.AuditService;
import com.schaccs.service.finance.FiscalYearService;
import com.schaccs.util.DisasterRecoveryEngine;
import com.schaccs.validation.ReceiptValidator;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Coordinates receipt creation: validate → allocate → post ledger → update student balance → store.
 */
public class ReceiptService {

    private final ReceiptStore receiptStore;
    private final StudentStore studentStore;
    private final FeeStructureStore feeStore;
    private final ReceiptValidator validator;
    private final ReceiptAllocationEngine allocationEngine;
    private final AccountingEngine accountingEngine;
    private final ReceiptNumberService numberService;
    private final Runnable persistenceAction;
    private final AuditService auditService;
    private final FiscalYearService fiscalYearService = new FiscalYearService();

    public ReceiptService() {
        this(ReceiptStore.getInstance(), StudentStore.getInstance(), FeeStructureStore.getInstance(),
                new ReceiptValidator(), new ReceiptAllocationEngine(), new AccountingEngine(),
                new ReceiptNumberService(), PersistenceService.getInstance()::saveAll,
                new AuditService());
    }

    public ReceiptService(ReceiptStore receiptStore, StudentStore studentStore, FeeStructureStore feeStore,
                          ReceiptValidator validator, ReceiptAllocationEngine allocationEngine,
                          AccountingEngine accountingEngine, ReceiptNumberService numberService) {
        this(receiptStore, studentStore, feeStore, validator, allocationEngine,
                accountingEngine, numberService, PersistenceService.getInstance()::saveAll,
                new AuditService());
    }

    public ReceiptService(ReceiptStore receiptStore, StudentStore studentStore, FeeStructureStore feeStore,
                          ReceiptValidator validator, ReceiptAllocationEngine allocationEngine,
                          AccountingEngine accountingEngine, ReceiptNumberService numberService,
                          Runnable persistenceAction) {
        this(receiptStore, studentStore, feeStore, validator, allocationEngine,
                accountingEngine, numberService, persistenceAction, new AuditService());
    }

    public ReceiptService(ReceiptStore receiptStore, StudentStore studentStore, FeeStructureStore feeStore,
                          ReceiptValidator validator, ReceiptAllocationEngine allocationEngine,
                          AccountingEngine accountingEngine, ReceiptNumberService numberService,
                          Runnable persistenceAction, AuditService auditService) {
        this.receiptStore = receiptStore;
        this.studentStore = studentStore;
        this.feeStore = feeStore;
        this.validator = validator;
        this.allocationEngine = allocationEngine;
        this.accountingEngine = accountingEngine;
        this.numberService = numberService;
        this.persistenceAction = persistenceAction;
        this.auditService = auditService;
    }

    public List<FeeAllocation> previewAllocation(Student student, BigDecimal amount) {
        StudentFeeLedger ledger = studentStore.getLedger(student.getId());
        FeeStructure fs = resolveFeeStructure(student);
        if (fs == null) {
            return allocationEngine.allocate(ledger, amount);
        }
        return allocationEngine.allocate(ledger, amount, fs, ledger.getCurrentTerm());
    }

    public Result receivePayment(Student student, BigDecimal amount, PaymentMode mode,
                                 String bankReference, LocalDate date, String notes) {
        List<String> errors = validator.validate(student, amount, mode, bankReference);
        if (!errors.isEmpty()) {
            return Result.failure(errors);
        }

        LocalDate receiptDate = date != null ? date : LocalDate.now();
        if (!fiscalYearService.isTransactionAllowed(receiptDate)) {
            return Result.failure(List.of("Payment date " + receiptDate
                    + " is outside the open fiscal year."));
        }

        StudentFeeLedger ledger = studentStore.getLedger(student.getId());
        FeeStructure fs = resolveFeeStructure(student);
        List<FeeAllocation> allocations = fs != null
                ? allocationEngine.allocate(ledger, amount, fs, ledger.getCurrentTerm())
                : allocationEngine.allocate(ledger, amount);
        List<ReceiptLine> createdLines = new ArrayList<>();

        Receipt receipt = new Receipt();
        receipt.setReceiptNumber(numberService.next());
        receipt.setDate(receiptDate);
        receipt.setStudentId(student.getId());
        receipt.setAdmissionNumber(student.getAdmissionNumber());
        receipt.setStudentName(student.getName());
        receipt.setClassLabel(student.getClassLabel());
        receipt.setAmount(CurrencyConfig.money(amount));
        receipt.setPaymentMode(mode);
        receipt.setBankReference(bankReference);
        receipt.setReceivedBy(AppConfig.getInstance().getCurrentUser());
        receipt.setNotes(notes);

        String ref = "RCPT-" + receipt.getReceiptNumber();

        for (FeeAllocation alloc : allocations) {
            if (alloc.getAllocated().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            ReceiptLine line = new ReceiptLine(alloc.getVoteheadCode(), alloc.getVoteheadName(), alloc.getAllocated(),
                    alloc.getOutstandingBefore());
            receipt.addLine(line);
            createdLines.add(line);

            if (StudentFeeLedger.ADVANCE_CODE.equals(alloc.getVoteheadCode())) {
                if (alloc.getOutstandingBefore().compareTo(BigDecimal.ZERO) > 0) {
                    // Applying existing carry-forward credit to this payment
                    ledger.consumeAdvance(alloc.getAllocated());
                    ledger.pay(StudentFeeLedger.ADVANCE_CODE, alloc.getAllocated());
                } else {
                    // New overpayment recorded as carry-forward credit
                    ledger.addAdvance(alloc.getAllocated());
                }
                accountingEngine.postFeeReceiptLine(
                        ref,
                        "Fee receipt " + receipt.getReceiptNumber() + " — Advance / Credit ("
                                + student.getAdmissionNumber() + ")",
                        AccountType.DEFERRED_REVENUE,
                        StudentFeeLedger.ADVANCE_CODE,
                        alloc.getAllocated(),
                        student.getId(),
                        receipt.getId(),
                        null,
                        receipt.getDate()
                );
                continue;
            }

            if ("ARREARS".equals(alloc.getVoteheadCode())) {
                BigDecimal newArrears = ledger.getArrears().subtract(alloc.getAllocated()).max(BigDecimal.ZERO);
                ledger.setArrears(newArrears);
            } else {
                ledger.pay(alloc.getVoteheadCode(), alloc.getAllocated());
            }

            AccountType accountType = feeStore.findVoteheadByCode(alloc.getVoteheadCode())
                    .map(Votehead::getAccountType)
                    .orElse(AccountType.SCHOOL_FUND);

            accountingEngine.postFeeReceiptLine(
                    ref,
                    "Fee receipt " + receipt.getReceiptNumber() + " — " + alloc.getVoteheadName()
                            + " (" + student.getAdmissionNumber() + ")",
                    accountType,
                    alloc.getVoteheadCode(),
                    alloc.getAllocated(),
                    student.getId(),
                    receipt.getId(),
                    null,
                    receipt.getDate()
            );
        }

        receipt.computeVerificationHash();

        try {
            receiptStore.add(receipt);
            persistenceAction.run();
            DisasterRecoveryEngine.getInstance().onReceiptPosted();
            auditService.log("RECEIPT_CREATED", "Receipt", receipt.getId(),
                    "{\"receiptNumber\":" + receipt.getReceiptNumber()
                            + ",\"studentId\":\"" + student.getId()
                            + "\",\"admissionNumber\":\"" + student.getAdmissionNumber()
                            + "\",\"amount\":" + amount
                            + ",\"paymentMode\":\"" + mode + "\"}");
            return Result.success(receipt, allocations);
        } catch (Exception e) {
            for (ReceiptLine line : createdLines) {
                if (StudentFeeLedger.ADVANCE_CODE.equals(line.getVoteheadCode())) {
                    if (line.getOutstandingBefore().compareTo(BigDecimal.ZERO) > 0) {
                        ledger.addAdvance(line.getAmount());
                        ledger.reversePayment(StudentFeeLedger.ADVANCE_CODE, line.getAmount());
                    } else {
                        ledger.reduceAdvance(line.getAmount());
                    }
                } else if ("ARREARS".equals(line.getVoteheadCode())) {
                    ledger.setArrears(ledger.getArrears().add(line.getAmount()));
                } else {
                    ledger.reversePayment(line.getVoteheadCode(), line.getAmount());
                }
            }
            LedgerStore.getInstance().removeByReceiptId(receipt.getId());
            receiptStore.getReceipts().remove(receipt);
            return Result.failure(List.of("Failed to post receipt: " + e.getMessage()));
        }
    }

    private FeeStructure resolveFeeStructure(Student student) {
        int year = student.getAcademicYear() != null
                ? student.getAcademicYear()
                : AppConfig.getInstance().getAcademicYear();
        return feeStore.findStructure(year, student.getBoardingStatus()).orElse(null);
    }

    public List<Receipt> allReceipts() {
        return receiptStore.getReceipts();
    }

    public boolean verifyReceipt(Receipt r) {
        return r.isVerified();
    }

    public static final class Result {
        private final boolean success;
        private final Receipt receipt;
        private final List<FeeAllocation> allocations;
        private final List<String> errors;

        private Result(boolean success, Receipt receipt, List<FeeAllocation> allocations, List<String> errors) {
            this.success = success;
            this.receipt = receipt;
            this.allocations = allocations;
            this.errors = errors;
        }

        public static Result success(Receipt receipt, List<FeeAllocation> allocations) {
            return new Result(true, receipt, allocations, List.of());
        }

        public static Result failure(List<String> errors) {
            return new Result(false, null, List.of(), errors);
        }

        public boolean isSuccess() {
            return success;
        }

        public Receipt getReceipt() {
            return receipt;
        }

        public List<FeeAllocation> getAllocations() {
            return allocations;
        }

        public List<String> getErrors() {
            return errors;
        }
    }

    /**
     * Reverse a previously posted receipt without deleting it: posts a contra
     * double-entry (credit bank / debit income) and unwinds the student ledger so
     * balances return to their pre-receipt state. No-op if already reversed.
     */
    public Result reverseReceipt(Receipt receipt, String reason) {
        if (receipt == null) {
            return Result.failure(List.of("No receipt selected."));
        }
        if (receipt.isReversed()) {
            return Result.failure(List.of("Receipt " + receipt.getReceiptNumberDisplay() + " is already reversed."));
        }
        Student student = studentStore.findById(receipt.getStudentId()).orElse(null);
        if (student == null) {
            return Result.failure(List.of("Linked student not found; cannot reverse."));
        }
        StudentFeeLedger ledger = studentStore.getLedger(student.getId());
        BigDecimal savedArrears = ledger.getArrears();
        BigDecimal savedAdvance = ledger.getAdvance();
        java.util.Map<String, BigDecimal> savedPaid = new java.util.LinkedHashMap<>(ledger.getPaidByVotehead());
        boolean savedReversed = receipt.isReversed();
        String savedNotes = receipt.getNotes();
        java.util.Set<String> ledgerSnapshot = LedgerStore.getInstance().getTransactions().stream()
                .map(FinancialTransaction::getId)
                .collect(java.util.stream.Collectors.toSet());
        try {
            String ref = "RCPT-RV-" + receipt.getReceiptNumber();
            for (ReceiptLine line : receipt.getLines()) {
                if (StudentFeeLedger.ADVANCE_CODE.equals(line.getVoteheadCode())) {
                    if (line.getOutstandingBefore().compareTo(BigDecimal.ZERO) > 0) {
                        ledger.addAdvance(line.getAmount());
                        ledger.reversePayment(StudentFeeLedger.ADVANCE_CODE, line.getAmount());
                    } else {
                        ledger.reduceAdvance(line.getAmount());
                    }
                    accountingEngine.postFeeReceiptLine(
                            ref,
                            "Reversal of receipt " + receipt.getReceiptNumberDisplay()
                                    + " — Advance / Credit" + (reason != null ? " (" + reason + ")" : ""),
                            AccountType.DEFERRED_REVENUE,
                            StudentFeeLedger.ADVANCE_CODE,
                            line.getAmount().negate(),
                            student.getId(),
                            receipt.getId(),
                            null,
                            LocalDate.now());
                    continue;
                }
                if ("ARREARS".equals(line.getVoteheadCode())) {
                    ledger.setArrears(ledger.getArrears().add(line.getAmount()));
                } else {
                    ledger.reversePayment(line.getVoteheadCode(), line.getAmount());
                }
                AccountType accountType = feeStore.findVoteheadByCode(line.getVoteheadCode())
                        .map(Votehead::getAccountType)
                        .orElse(AccountType.SCHOOL_FUND);
                accountingEngine.postFeeReceiptLine(
                        ref,
                        "Reversal of receipt " + receipt.getReceiptNumberDisplay()
                                + " — " + line.getVoteheadName() + (reason != null ? " (" + reason + ")" : ""),
                        accountType,
                        line.getVoteheadCode(),
                        line.getAmount().negate(),
                        student.getId(),
                        receipt.getId(),
                        null,
                        LocalDate.now());
            }
            receipt.setReversed(true);
            if (receipt.getNotes() == null || receipt.getNotes().isBlank()) {
                receipt.setNotes("REVERSED" + (reason != null ? ": " + reason : ""));
            } else {
                receipt.setNotes(receipt.getNotes() + " | REVERSED" + (reason != null ? ": " + reason : ""));
            }
            receipt.computeVerificationHash();
            persistenceAction.run();
            auditService.log("RECEIPT_REVERSED", "Receipt", receipt.getId(),
                    "{\"receiptNumber\":" + receipt.getReceiptNumber()
                            + ",\"amount\":" + receipt.getAmount()
                            + (reason != null ? ",\"reason\":\"" + reason.replace("\"", "'") + "\"" : "") + "}");
            return Result.success(receipt, List.of());
        } catch (Exception e) {
            ledger.setArrears(savedArrears);
            ledger.setAdvance(savedAdvance);
            ledger.restorePaidByVotehead(savedPaid);
            receipt.setReversed(savedReversed);
            receipt.setNotes(savedNotes);
            LedgerStore.getInstance().rollbackToSnapshot(ledgerSnapshot);
            return Result.failure(List.of("Failed to reverse receipt: " + e.getMessage()));
        }
    }
}
