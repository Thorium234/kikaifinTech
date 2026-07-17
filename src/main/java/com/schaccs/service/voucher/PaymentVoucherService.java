package com.schaccs.service.voucher;

import com.schaccs.config.AppConfig;
import com.schaccs.config.CurrencyConfig;
import com.schaccs.enums.AccountType;
import com.schaccs.enums.PaymentMode;
import com.schaccs.enums.TransactionType;
import com.schaccs.enums.VoucherStatus;
import com.schaccs.model.finance.JournalEntry;
import com.schaccs.model.finance.Votehead;
import com.schaccs.model.voucher.Commitment;
import com.schaccs.model.voucher.Creditor;
import com.schaccs.model.voucher.PaymentVoucher;
import com.schaccs.accounting.AccountingEngine;
import com.schaccs.repository.PersistenceService;
import com.schaccs.store.VoucherStore;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Creates commitments and posts paid vouchers through AccountingEngine.
 */
public class PaymentVoucherService {

    private final VoucherStore store;
    private final AccountingEngine accountingEngine;

    public PaymentVoucherService() {
        this(VoucherStore.getInstance(), new AccountingEngine());
    }

    public PaymentVoucherService(VoucherStore store, AccountingEngine accountingEngine) {
        this.store = store;
        this.accountingEngine = accountingEngine;
    }

    public Creditor addCreditor(String name, String phone, String description) {
        Creditor c = new Creditor(name, phone);
        c.setDescription(description);
        store.addCreditor(c);
        PersistenceService.getInstance().saveAll();
        return c;
    }

    public List<String> createCommitment(Creditor creditor, Votehead votehead, BigDecimal amount,
                                         String description, String reference, LocalDate date) {
        List<String> errors = new ArrayList<>();
        if (creditor == null) {
            errors.add("Select a creditor.");
        }
        if (votehead == null) {
            errors.add("Select a votehead.");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            errors.add("Amount must be greater than zero.");
        }
        if (!errors.isEmpty()) {
            return errors;
        }

        Commitment c = new Commitment();
        c.setDate(date != null ? date : LocalDate.now());
        c.setCreditorId(creditor.getId());
        c.setCreditorName(creditor.getName());
        c.setVoteheadCode(votehead.getCode());
        c.setVoteheadName(votehead.getName());
        c.setAccountType(votehead.getAccountType());
        c.setAmount(amount);
        c.setDescription(description);
        c.setReference(reference);
        store.addCommitment(c);
        PersistenceService.getInstance().saveAll();
        return errors;
    }

    public List<String> payVoucher(Commitment commitment, BigDecimal amount, PaymentMode mode,
                                   String bankReference, LocalDate date, String notes) {
        List<String> errors = new ArrayList<>();
        if (commitment == null) {
            errors.add("Select a commitment.");
            return errors;
        }
        if (Commitment.SETTLED.equals(commitment.getStatus()) || Commitment.CANCELLED.equals(commitment.getStatus())) {
            errors.add("Commitment is already " + commitment.getStatus().toLowerCase() + ".");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            errors.add("Payment amount must be greater than zero.");
        } else if (amount.compareTo(commitment.getOutstanding()) > 0) {
            errors.add("Amount exceeds outstanding commitment ("
                    + CurrencyConfig.format(commitment.getOutstanding()) + ").");
        }
        if (mode == null) {
            errors.add("Payment mode is required.");
        }
        if ((mode == PaymentMode.BANK_SLIP || mode == PaymentMode.CHEQUE || mode == PaymentMode.MPESA)
                && (bankReference == null || bankReference.isBlank())) {
            errors.add("Bank / payment reference is required.");
        }
        if (!errors.isEmpty()) {
            return errors;
        }

        PaymentVoucher voucher = new PaymentVoucher();
        voucher.setVoucherNumber(AppConfig.getInstance().getSchoolProfile().allocateVoucherNumber());
        voucher.setDate(date != null ? date : LocalDate.now());
        voucher.setCreditorId(commitment.getCreditorId());
        voucher.setCreditorName(commitment.getCreditorName());
        voucher.setCommitmentId(commitment.getId());
        voucher.setVoteheadCode(commitment.getVoteheadCode());
        voucher.setVoteheadName(commitment.getVoteheadName());
        voucher.setAccountType(commitment.getAccountType() != null
                ? commitment.getAccountType() : AccountType.SCHOOL_FUND);
        voucher.setAmount(amount);
        voucher.setDescription(commitment.getDescription());
        voucher.setStatus(VoucherStatus.PAID);
        voucher.setPaymentMode(mode);
        voucher.setBankReference(bankReference);
        voucher.setPreparedBy(AppConfig.getInstance().getCurrentUser());
        voucher.setApprovedBy(AppConfig.getInstance().getCurrentUser());
        voucher.setNotes(notes);

        // Expense: Debit votehead expense account, Credit Bank (School Fund)
        JournalEntry journal = new JournalEntry();
        journal.setDate(voucher.getDate());
        journal.setReference("PV-" + voucher.getVoucherNumber());
        journal.setNarration("Payment voucher " + voucher.getVoucherNumber() + " — "
                + voucher.getCreditorName() + " / " + voucher.getVoteheadName());
        journal.addLine(voucher.getAccountType(), voucher.getVoteheadCode(),
                amount, CurrencyConfig.zero(),
                "Expense — " + voucher.getVoteheadName());
        journal.addLine(AccountType.SCHOOL_FUND, "CASH_BANK",
                CurrencyConfig.zero(), amount,
                "Bank payment — " + voucher.getCreditorName());

        accountingEngine.postTransaction(journal, TransactionType.PAYMENT_VOUCHER,
                null, null, voucher.getId());

        // Voucher id is now stamped on the linked ledger transactions for audit tracing.

        commitment.applyPayment(amount);
        store.addVoucher(voucher);
        PersistenceService.getInstance().saveAll();
        return errors;
    }

    public List<Commitment> openCommitments() {
        return store.getCommitments().stream()
                .filter(c -> !Commitment.SETTLED.equals(c.getStatus())
                        && !Commitment.CANCELLED.equals(c.getStatus()))
                .toList();
    }
}
