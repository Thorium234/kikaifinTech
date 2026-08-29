package com.schaccs.model.receipt;

import com.schaccs.config.CurrencyConfig;
import com.schaccs.enums.PaymentMode;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.Objects;

public class Receipt {

    private final String id;
    private long receiptNumber;
    private LocalDate date;
    private String studentId;
    private String admissionNumber;
    private String studentName;
    private String classLabel;
    private BigDecimal amount = CurrencyConfig.zero();
    private PaymentMode paymentMode = PaymentMode.BANK_SLIP;
    private String bankReference;
    private String receivedBy;
    private String notes;
    private LocalDateTime createdAt;
    private boolean reversed = false;
    private String verificationHash;
    private final ObservableList<ReceiptLine> lines = FXCollections.observableArrayList();

    public Receipt() {
        this.id = UUID.randomUUID().toString();
        this.date = LocalDate.now();
        this.createdAt = LocalDateTime.now();
    }

    private Receipt(String id) {
        this.id = id != null ? id : UUID.randomUUID().toString();
        this.date = LocalDate.now();
        this.createdAt = LocalDateTime.now();
    }

    public static Receipt withId(String id) {
        return new Receipt(id);
    }

    public String getId() {
        return id;
    }

    public long getReceiptNumber() {
        return receiptNumber;
    }

    public void setReceiptNumber(long receiptNumber) {
        this.receiptNumber = receiptNumber;
    }

    public String getReceiptNumberDisplay() {
        return String.valueOf(receiptNumber);
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public String getStudentId() {
        return studentId;
    }

    public void setStudentId(String studentId) {
        this.studentId = studentId;
    }

    public String getAdmissionNumber() {
        return admissionNumber;
    }

    public void setAdmissionNumber(String admissionNumber) {
        this.admissionNumber = admissionNumber;
    }

    public String getStudentName() {
        return studentName;
    }

    public void setStudentName(String studentName) {
        this.studentName = studentName;
    }

    public String getClassLabel() {
        return classLabel;
    }

    public void setClassLabel(String classLabel) {
        this.classLabel = classLabel;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = CurrencyConfig.money(amount);
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

    public String getReceivedBy() {
        return receivedBy;
    }

    public void setReceivedBy(String receivedBy) {
        this.receivedBy = receivedBy;
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

    public boolean isReversed() {
        return reversed;
    }

    public void setReversed(boolean reversed) {
        this.reversed = reversed;
    }

    private boolean manualOverride = false;

    public boolean isManualOverride() {
        return manualOverride;
    }

    public void setManualOverride(boolean manualOverride) {
        this.manualOverride = manualOverride;
    }

    public String getVerificationHash() {
        return verificationHash;
    }

    public void setVerificationHash(String verificationHash) {
        this.verificationHash = verificationHash;
    }

    public void computeVerificationHash() {
        this.verificationHash = HASH_VERSION + ":" + sha256(buildHashPayload());
    }

    /**
     * True when the stored hash matches a recomputation over the current fields.
     * Understands both the current versioned (v2) hash and the legacy v1 format
     * used by receipts stored before this field set was extended.
     */
    public boolean isVerified() {
        if (verificationHash == null || verificationHash.isEmpty()) {
            return false;
        }
        if (verificationHash.startsWith(HASH_VERSION + ":")) {
            return verificationHash.equals(HASH_VERSION + ":" + sha256(buildHashPayload()));
        }
        return verificationHash.equals(sha256(buildLegacyHashPayload()));
    }

    private static final String HASH_VERSION = "v2";

    private static String sha256(String raw) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    private String buildHashPayload() {
        StringBuilder sb = new StringBuilder();
        sb.append(receiptNumber).append('|').append(date)
                .append('|').append(studentId)
                .append('|').append(amount)
                .append('|').append(paymentMode != null ? paymentMode.name() : PaymentMode.BANK_SLIP.name())
                .append('|').append(bankReference)
                .append('|').append(reversed)
                .append('|').append(notes);
        for (ReceiptLine line : lines) {
            sb.append('|').append(line.getVoteheadCode()).append('=').append(line.getAmount());
        }
        return sb.toString();
    }

    private String buildLegacyHashPayload() {
        return receiptNumber + "|" + date + "|" + studentId + "|" + amount + "|"
                + paymentMode + "|" + bankReference + "|" + amount;
    }

    public ObservableList<ReceiptLine> getLines() {
        return lines;
    }

    public void addLine(ReceiptLine line) {
        lines.add(line);
    }

    public BigDecimal linesTotal() {
        return lines.stream()
                .map(ReceiptLine::getAmount)
                .reduce(CurrencyConfig.zero(), BigDecimal::add);
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Receipt that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
