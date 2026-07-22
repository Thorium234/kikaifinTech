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
import com.schaccs.model.voucher.Invoice;
import com.schaccs.model.voucher.Imprest;
import com.schaccs.model.voucher.Lpo;
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
        // approvedBy intentionally null — set by a separate approval step
        voucher.setNotes(notes);

        BigDecimal prevAmountPaid = commitment.getAmountPaid();

        try {
            PersistenceService.getInstance().transactional(conn -> {
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
                commitment.applyPayment(amount);
            });
            store.addVoucher(voucher);
            PersistenceService.getInstance().saveAll();
            return errors;
        } catch (Exception e) {
            commitment.setAmountPaid(prevAmountPaid);
            errors.add("Failed to post payment voucher: " + e.getMessage());
            return errors;
        }
    }

    public void approveVoucher(PaymentVoucher voucher) {
        if (voucher.getApprovedBy() != null) {
            throw new IllegalStateException("Voucher " + voucher.getVoucherNumber() + " is already approved.");
        }
        String currentUser = AppConfig.getInstance().getCurrentUser();
        if (currentUser.equals(voucher.getPreparedBy())) {
            throw new IllegalStateException("Preparer and approver cannot be the same person.");
        }
        voucher.setApprovedBy(currentUser);
        voucher.setStatus(VoucherStatus.APPROVED);
        PersistenceService.getInstance().saveAll();
    }

    public List<String> createLpo(Creditor creditor, Votehead votehead, BigDecimal amount,
                                  String lpoNumber, String description, LocalDate date) {
        List<String> errors = validateVoucherBase(creditor, votehead, amount);
        if (lpoNumber == null || lpoNumber.isBlank()) {
            errors.add("LPO number is required.");
        }
        if (!errors.isEmpty()) {
            return errors;
        }
        Lpo lpo = new Lpo();
        lpo.setLpoNumber(lpoNumber.trim());
        lpo.setDate(date != null ? date : LocalDate.now());
        lpo.setCreditorId(creditor.getId());
        lpo.setCreditorName(creditor.getName());
        lpo.setVoteheadCode(votehead.getCode());
        lpo.setVoteheadName(votehead.getName());
        lpo.setAccountType(votehead.getAccountType());
        lpo.setAmount(amount);
        lpo.setDescription(description);
        store.addLpo(lpo);
        PersistenceService.getInstance().saveAll();
        return errors;
    }

    public List<String> createInvoice(Creditor creditor, Votehead votehead, BigDecimal amount,
                                      String invoiceNumber, String description, LocalDate date, Lpo linkedLpo) {
        List<String> errors = validateVoucherBase(creditor, votehead, amount);
        if (invoiceNumber == null || invoiceNumber.isBlank()) {
            errors.add("Invoice number is required.");
        }
        if (!errors.isEmpty()) {
            return errors;
        }
        Invoice invoice = new Invoice();
        invoice.setInvoiceNumber(invoiceNumber.trim());
        invoice.setDate(date != null ? date : LocalDate.now());
        invoice.setCreditorId(creditor.getId());
        invoice.setCreditorName(creditor.getName());
        invoice.setLpoId(linkedLpo != null ? linkedLpo.getId() : null);
        invoice.setVoteheadCode(votehead.getCode());
        invoice.setVoteheadName(votehead.getName());
        invoice.setAccountType(votehead.getAccountType());
        invoice.setAmount(amount);
        invoice.setDescription(description);
        store.addInvoice(invoice);
        if (linkedLpo != null) {
            linkedLpo.setStatus(Lpo.INVOICED);
        }
        PersistenceService.getInstance().saveAll();
        return errors;
    }

    public List<String> createImprest(String staffName, Votehead votehead, BigDecimal amount,
                                      String purpose, LocalDate date) {
        List<String> errors = new ArrayList<>();
        if (staffName == null || staffName.isBlank()) {
            errors.add("Staff name is required.");
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
        Imprest imprest = new Imprest();
        imprest.setStaffName(staffName.trim());
        imprest.setDate(date != null ? date : LocalDate.now());
        imprest.setVoteheadCode(votehead.getCode());
        imprest.setVoteheadName(votehead.getName());
        imprest.setAccountType(votehead.getAccountType());
        imprest.setAmount(amount);
        imprest.setPurpose(purpose);
        store.addImprest(imprest);
        PersistenceService.getInstance().saveAll();
        return errors;
    }

    public List<String> surrenderImprest(Imprest imprest, BigDecimal surrenderedAmount, LocalDate surrenderDate) {
        List<String> errors = new ArrayList<>();
        if (imprest == null) {
            errors.add("Select an imprest.");
        } else if (Imprest.SURRENDERED.equals(imprest.getStatus())) {
            errors.add("Imprest is already surrendered.");
        }
        if (surrenderedAmount == null || surrenderedAmount.compareTo(BigDecimal.ZERO) < 0) {
            errors.add("Surrendered amount cannot be negative.");
        }
        if (!errors.isEmpty()) {
            return errors;
        }
        imprest.setSurrenderedAmount(surrenderedAmount);
        imprest.setSurrenderDate(surrenderDate != null ? surrenderDate : LocalDate.now());
        imprest.setStatus(Imprest.SURRENDERED);
        PersistenceService.getInstance().saveAll();
        return errors;
    }

    private List<String> validateVoucherBase(Creditor creditor, Votehead votehead, BigDecimal amount) {
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
        return errors;
    }

    public List<String> updateLpo(Lpo lpo, Creditor creditor, Votehead votehead, BigDecimal amount,
                                  String lpoNumber, String description, LocalDate date) {
        List<String> errors = validateVoucherBase(creditor, votehead, amount);
        if (lpo == null) {
            errors.add("Select an LPO.");
        } else if (Lpo.CANCELLED.equals(lpo.getStatus())) {
            errors.add("Cancelled LPOs cannot be edited.");
        } else if (Lpo.INVOICED.equals(lpo.getStatus())) {
            errors.add("Invoiced LPOs cannot be edited.");
        }
        if (lpoNumber == null || lpoNumber.isBlank()) {
            errors.add("LPO number is required.");
        }
        if (!errors.isEmpty()) {
            return errors;
        }
        lpo.setLpoNumber(lpoNumber.trim());
        lpo.setDate(date != null ? date : LocalDate.now());
        lpo.setCreditorId(creditor.getId());
        lpo.setCreditorName(creditor.getName());
        lpo.setVoteheadCode(votehead.getCode());
        lpo.setVoteheadName(votehead.getName());
        lpo.setAccountType(votehead.getAccountType());
        lpo.setAmount(amount);
        lpo.setDescription(description);
        PersistenceService.getInstance().saveAll();
        return errors;
    }

    public List<String> cancelLpo(Lpo lpo) {
        List<String> errors = new ArrayList<>();
        if (lpo == null) {
            errors.add("Select an LPO.");
            return errors;
        }
        if (Lpo.CANCELLED.equals(lpo.getStatus())) {
            errors.add("LPO is already cancelled.");
            return errors;
        }
        if (Lpo.INVOICED.equals(lpo.getStatus())) {
            errors.add("Invoiced LPOs cannot be cancelled.");
            return errors;
        }
        lpo.setStatus(Lpo.CANCELLED);
        PersistenceService.getInstance().saveAll();
        return errors;
    }

    public List<String> deleteLpo(Lpo lpo) {
        List<String> errors = new ArrayList<>();
        if (lpo == null) {
            errors.add("Select an LPO.");
            return errors;
        }
        if (Lpo.INVOICED.equals(lpo.getStatus())) {
            errors.add("Invoiced LPOs cannot be deleted.");
            return errors;
        }
        store.removeLpo(lpo);
        PersistenceService.getInstance().saveAll();
        return errors;
    }

    public List<String> updateInvoice(Invoice invoice, Creditor creditor, Votehead votehead, BigDecimal amount,
                                      String invoiceNumber, String description, LocalDate date, Lpo linkedLpo) {
        List<String> errors = validateVoucherBase(creditor, votehead, amount);
        if (invoice == null) {
            errors.add("Select an invoice.");
        } else if (Invoice.CANCELLED.equals(invoice.getStatus())) {
            errors.add("Cancelled invoices cannot be edited.");
        } else if (Invoice.PAID.equals(invoice.getStatus())) {
            errors.add("Paid invoices cannot be edited.");
        }
        if (invoiceNumber == null || invoiceNumber.isBlank()) {
            errors.add("Invoice number is required.");
        }
        if (!errors.isEmpty()) {
            return errors;
        }
        invoice.setInvoiceNumber(invoiceNumber.trim());
        invoice.setDate(date != null ? date : LocalDate.now());
        invoice.setCreditorId(creditor.getId());
        invoice.setCreditorName(creditor.getName());
        invoice.setLpoId(linkedLpo != null ? linkedLpo.getId() : null);
        invoice.setVoteheadCode(votehead.getCode());
        invoice.setVoteheadName(votehead.getName());
        invoice.setAccountType(votehead.getAccountType());
        invoice.setAmount(amount);
        invoice.setDescription(description);
        PersistenceService.getInstance().saveAll();
        return errors;
    }

    public List<String> cancelInvoice(Invoice invoice) {
        List<String> errors = new ArrayList<>();
        if (invoice == null) {
            errors.add("Select an invoice.");
            return errors;
        }
        if (Invoice.CANCELLED.equals(invoice.getStatus())) {
            errors.add("Invoice is already cancelled.");
            return errors;
        }
        if (Invoice.PAID.equals(invoice.getStatus())) {
            errors.add("Paid invoices cannot be cancelled.");
            return errors;
        }
        invoice.setStatus(Invoice.CANCELLED);
        PersistenceService.getInstance().saveAll();
        return errors;
    }

    public List<String> deleteInvoice(Invoice invoice) {
        List<String> errors = new ArrayList<>();
        if (invoice == null) {
            errors.add("Select an invoice.");
            return errors;
        }
        if (Invoice.PAID.equals(invoice.getStatus())) {
            errors.add("Paid invoices cannot be deleted.");
            return errors;
        }
        store.removeInvoice(invoice);
        PersistenceService.getInstance().saveAll();
        return errors;
    }

    public List<String> updateImprest(Imprest imprest, String staffName, Votehead votehead, BigDecimal amount,
                                      String purpose, LocalDate date) {
        List<String> errors = new ArrayList<>();
        if (imprest == null) {
            errors.add("Select an imprest.");
        } else if (Imprest.SURRENDERED.equals(imprest.getStatus())) {
            errors.add("Surrendered imprests cannot be edited.");
        }
        if (staffName == null || staffName.isBlank()) {
            errors.add("Staff name is required.");
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
        imprest.setStaffName(staffName.trim());
        imprest.setDate(date != null ? date : LocalDate.now());
        imprest.setVoteheadCode(votehead.getCode());
        imprest.setVoteheadName(votehead.getName());
        imprest.setAccountType(votehead.getAccountType());
        imprest.setAmount(amount);
        imprest.setPurpose(purpose);
        PersistenceService.getInstance().saveAll();
        return errors;
    }

    public List<String> deleteImprest(Imprest imprest) {
        List<String> errors = new ArrayList<>();
        if (imprest == null) {
            errors.add("Select an imprest.");
            return errors;
        }
        if (Imprest.SURRENDERED.equals(imprest.getStatus())) {
            errors.add("Surrendered imprests cannot be deleted.");
            return errors;
        }
        store.removeImprest(imprest);
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
