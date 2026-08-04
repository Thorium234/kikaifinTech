package com.schaccs.service;

import com.schaccs.config.AppConfig;
import com.schaccs.config.CurrencyConfig;
import com.schaccs.enums.AccountType;
import com.schaccs.enums.BoardingStatus;
import com.schaccs.enums.PaymentMode;
import com.schaccs.model.finance.Votehead;
import com.schaccs.model.receipt.Receipt;
import com.schaccs.model.student.Student;
import com.schaccs.model.student.StudentFeeLedger;
import com.schaccs.repository.Database;
import com.schaccs.repository.PersistenceService;
import com.schaccs.service.receipt.ReceiptService;
import com.schaccs.store.FeeStructureStore;
import com.schaccs.store.ReceiptStore;
import com.schaccs.store.StudentStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end integrity tests: a posted receipt must stay verified across a
 * real save/load round trip, and any tampering with the SQLite storage must be
 * detected on reload.
 */
class ReceiptIntegrityRoundTripTest {

    private String receiptId;

    @BeforeEach
    void setUp() {
        PersistenceService.getInstance().clearAll();
        FeeStructureStore.getInstance().addVotehead(new Votehead("BOARD", "Boarding", AccountType.SCHOOL_FUND, 1));
        FeeStructureStore.getInstance().addVotehead(new Votehead("TUITION", "Tuition", AccountType.TUITION_FEES, 2));
        AppConfig.getInstance().getSchoolProfile().setNextReceiptNumber(70000);
    }

    @AfterEach
    void tearDown() {
        PersistenceService.getInstance().clearAll();
        Database.getInstance().close();
    }

    private void postReceipt() {
        Student student = new Student();
        student.setAdmissionNumber("ADM-INT-" + UUID.randomUUID());
        student.setName("Integrity Tester");
        student.setFormClass("Form 2");
        student.setBoardingStatus(BoardingStatus.BOARDING);
        StudentStore.getInstance().add(student);
        StudentFeeLedger ledger = StudentStore.getInstance().getLedger(student.getId());
        ledger.charge("BOARD", CurrencyConfig.money("20000"));
        ledger.charge("TUITION", CurrencyConfig.money("10000"));

        ReceiptService service = new ReceiptService();
        ReceiptService.Result result = service.receivePayment(student, CurrencyConfig.money("15000"),
                PaymentMode.BANK_SLIP, "INT-REF", LocalDate.now(), null);
        assertTrue(result.isSuccess(), () -> String.join(", ", result.getErrors()));
        assertTrue(result.getReceipt().isVerified(), "Freshly posted receipt must be verified");
        receiptId = result.getReceipt().getId();
    }

    private Receipt reloaded() {
        return ReceiptStore.getInstance().getReceipts().stream()
                .filter(r -> r.getId().equals(receiptId))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Receipt not found after reload"));
    }

    @Test
    void receiptStaysVerifiedAcrossSaveAndReload() throws Exception {
        postReceipt();
        PersistenceService.getInstance().saveAll();

        PersistenceService.getInstance().loadAll();

        assertTrue(reloaded().isVerified(),
                "Receipt must remain verified after a full save + reload round trip");
    }

    @Test
    void tamperingWithAllocationLineIsDetectedAfterReload() throws Exception {
        postReceipt();
        PersistenceService.getInstance().saveAll();

        Connection conn = Database.getInstance().getConnection();
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE receipt_lines SET amount = '9999.99' "
                        + "WHERE id = (SELECT id FROM receipt_lines WHERE receipt_id = ? ORDER BY rowid LIMIT 1)")) {
            ps.setString(1, receiptId);
            ps.executeUpdate();
        }

        PersistenceService.getInstance().loadAll();

        assertFalse(reloaded().isVerified(),
                "Changing a stored allocation line amount must invalidate the receipt on reload");
    }

    @Test
    void tamperingWithBaseAmountIsDetectedAfterReload() throws Exception {
        postReceipt();
        PersistenceService.getInstance().saveAll();

        Connection conn = Database.getInstance().getConnection();
        try (Statement st = conn.createStatement()) {
            st.executeUpdate("UPDATE receipts SET amount = '9999.99' WHERE id = '" + receiptId + "'");
        }

        PersistenceService.getInstance().loadAll();

        assertFalse(reloaded().isVerified(),
                "Changing the stored receipt amount must invalidate the receipt on reload");
    }

    @Test
    void tamperingWithReversedFlagIsDetectedAfterReload() throws Exception {
        postReceipt();
        PersistenceService.getInstance().saveAll();

        Connection conn = Database.getInstance().getConnection();
        try (Statement st = conn.createStatement()) {
            st.executeUpdate("UPDATE receipts SET reversed = 1 WHERE id = '" + receiptId + "'");
        }

        PersistenceService.getInstance().loadAll();

        assertFalse(reloaded().isVerified(),
                "Flipping the stored reversed flag must invalidate the receipt on reload");
    }

    @Test
    void backfilledLegacyReceiptStaysVerifiedAcrossReload() throws Exception {
        Student student = new Student();
        student.setAdmissionNumber("ADM-LEG-" + UUID.randomUUID());
        student.setName("Legacy Tester");
        student.setFormClass("Form 2");
        student.setBoardingStatus(BoardingStatus.BOARDING);
        StudentStore.getInstance().add(student);
        StudentFeeLedger ledger = StudentStore.getInstance().getLedger(student.getId());
        ledger.charge("BOARD", CurrencyConfig.money("10000"));

        ReceiptService service = new ReceiptService();
        ReceiptService.Result result = service.receivePayment(student, CurrencyConfig.money("6000"),
                PaymentMode.MPESA, "LEG-REF", LocalDate.now(), null);
        assertTrue(result.isSuccess());
        Receipt receipt = result.getReceipt();
        receiptId = receipt.getId();

        String v1Hash = legacyHash(receipt);
        PersistenceService.getInstance().saveAll();

        Connection conn = Database.getInstance().getConnection();
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE receipts SET verification_hash = ? WHERE id = ?")) {
            ps.setString(1, v1Hash);
            ps.setString(2, receiptId);
            ps.executeUpdate();
        }

        PersistenceService.getInstance().loadAll();

        assertTrue(reloaded().isVerified(),
                "A legacy v1 hash must still validate across a reload round trip");
    }

    private static String legacyHash(Receipt receipt) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            String raw = receipt.getReceiptNumber() + "|" + receipt.getDate() + "|" + receipt.getStudentId()
                    + "|" + receipt.getAmount() + "|" + receipt.getPaymentMode() + "|"
                    + receipt.getBankReference() + "|" + receipt.getAmount();
            byte[] bytes = md.digest(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}
