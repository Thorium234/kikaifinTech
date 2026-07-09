package com.schaccs.service.receipt;

import com.schaccs.accounting.AccountingEngine;
import com.schaccs.accounting.ReceiptAllocationEngine;
import com.schaccs.config.AppConfig;
import com.schaccs.config.CurrencyConfig;
import com.schaccs.enums.AccountType;
import com.schaccs.enums.PaymentMode;
import com.schaccs.model.fee.FeeAllocation;
import com.schaccs.model.finance.Votehead;
import com.schaccs.model.receipt.Receipt;
import com.schaccs.model.receipt.ReceiptLine;
import com.schaccs.model.student.Student;
import com.schaccs.model.student.StudentFeeLedger;
import com.schaccs.store.FeeStructureStore;
import com.schaccs.store.ReceiptStore;
import com.schaccs.store.StudentStore;
import com.schaccs.validation.ReceiptValidator;

import java.math.BigDecimal;
import java.time.LocalDate;
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

    public ReceiptService() {
        this(ReceiptStore.getInstance(), StudentStore.getInstance(), FeeStructureStore.getInstance(),
                new ReceiptValidator(), new ReceiptAllocationEngine(), new AccountingEngine(),
                new ReceiptNumberService());
    }

    public ReceiptService(ReceiptStore receiptStore, StudentStore studentStore, FeeStructureStore feeStore,
                          ReceiptValidator validator, ReceiptAllocationEngine allocationEngine,
                          AccountingEngine accountingEngine, ReceiptNumberService numberService) {
        this.receiptStore = receiptStore;
        this.studentStore = studentStore;
        this.feeStore = feeStore;
        this.validator = validator;
        this.allocationEngine = allocationEngine;
        this.accountingEngine = accountingEngine;
        this.numberService = numberService;
    }

    public List<FeeAllocation> previewAllocation(Student student, BigDecimal amount) {
        StudentFeeLedger ledger = studentStore.getLedger(student.getId());
        return allocationEngine.allocate(ledger, amount);
    }

    public Result receivePayment(Student student, BigDecimal amount, PaymentMode mode,
                                 String bankReference, LocalDate date, String notes) {
        List<String> errors = validator.validate(student, amount, mode, bankReference);
        if (!errors.isEmpty()) {
            return Result.failure(errors);
        }

        StudentFeeLedger ledger = studentStore.getLedger(student.getId());
        List<FeeAllocation> allocations = allocationEngine.allocate(ledger, amount);

        Receipt receipt = new Receipt();
        receipt.setReceiptNumber(numberService.next());
        receipt.setDate(date != null ? date : LocalDate.now());
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
            receipt.addLine(new ReceiptLine(alloc.getVoteheadCode(), alloc.getVoteheadName(), alloc.getAllocated()));

            // Update student ledger
            if ("ARREARS".equals(alloc.getVoteheadCode())) {
                BigDecimal newArrears = ledger.getArrears().subtract(alloc.getAllocated()).max(BigDecimal.ZERO);
                ledger.setArrears(newArrears);
            } else if (!"ADVANCE".equals(alloc.getVoteheadCode())) {
                ledger.pay(alloc.getVoteheadCode(), alloc.getAllocated());
            } else {
                // credit advance against a generic ADVANCE paid bucket
                ledger.pay("ADVANCE", alloc.getAllocated());
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
                    receipt.getDate()
            );
        }

        receiptStore.add(receipt);
        return Result.success(receipt, allocations);
    }

    public List<Receipt> allReceipts() {
        return receiptStore.getReceipts();
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
}
