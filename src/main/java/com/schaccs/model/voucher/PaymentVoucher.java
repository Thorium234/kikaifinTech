package com.schaccs.model.voucher;

import com.schaccs.config.CurrencyConfig;
import com.schaccs.enums.AccountType;
import com.schaccs.enums.PaymentMode;
import com.schaccs.enums.VoucherStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public class PaymentVoucher {

    private final String id;
    private long voucherNumber;
    private LocalDate date = LocalDate.now();
    private String creditorId;
    private String creditorName;
    private String commitmentId;
    private String voteheadCode;
    private String voteheadName;
    private AccountType accountType = AccountType.SCHOOL_FUND;
    private BigDecimal amount = CurrencyConfig.zero();
    private String description;
    private VoucherStatus status = VoucherStatus.DRAFT;
    private PaymentMode paymentMode = PaymentMode.BANK_SLIP;
    private String bankReference;
    private String preparedBy;
    private String approvedBy;
    private String notes;
    private LocalDateTime createdAt = LocalDateTime.now();

    public PaymentVoucher() {
        this.id = UUID.randomUUID().toString();
    }

    private PaymentVoucher(String id) {
        this.id = id != null ? id : UUID.randomUUID().toString();
    }

    public static PaymentVoucher withId(String id) {
        return new PaymentVoucher(id);
    }

    public String getId() {
        return id;
    }

    public long getVoucherNumber() {
        return voucherNumber;
    }

    public void setVoucherNumber(long voucherNumber) {
        this.voucherNumber = voucherNumber;
    }

    public String getVoucherNumberDisplay() {
        return String.valueOf(voucherNumber);
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public String getCreditorId() {
        return creditorId;
    }

    public void setCreditorId(String creditorId) {
        this.creditorId = creditorId;
    }

    public String getCreditorName() {
        return creditorName;
    }

    public void setCreditorName(String creditorName) {
        this.creditorName = creditorName;
    }

    public String getCommitmentId() {
        return commitmentId;
    }

    public void setCommitmentId(String commitmentId) {
        this.commitmentId = commitmentId;
    }

    public String getVoteheadCode() {
        return voteheadCode;
    }

    public void setVoteheadCode(String voteheadCode) {
        this.voteheadCode = voteheadCode;
    }

    public String getVoteheadName() {
        return voteheadName;
    }

    public void setVoteheadName(String voteheadName) {
        this.voteheadName = voteheadName;
    }

    public AccountType getAccountType() {
        return accountType;
    }

    public void setAccountType(AccountType accountType) {
        this.accountType = accountType;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = CurrencyConfig.money(amount);
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public VoucherStatus getStatus() {
        return status;
    }

    public void setStatus(VoucherStatus status) {
        this.status = status;
    }

    public PaymentMode getPaymentMode() {
        return paymentMode;
    }

    public void setPaymentMode(PaymentMode paymentMode) {
        this.paymentMode = paymentMode;
    }

    public String getBankReference() {
        return bankReference;
    }

    public void setBankReference(String bankReference) {
        this.bankReference = bankReference;
    }

    public String getPreparedBy() {
        return preparedBy;
    }

    public void setPreparedBy(String preparedBy) {
        this.preparedBy = preparedBy;
    }

    public String getApprovedBy() {
        return approvedBy;
    }

    public void setApprovedBy(String approvedBy) {
        this.approvedBy = approvedBy;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
