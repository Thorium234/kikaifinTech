package com.schaccs.service;

import com.schaccs.model.receipt.Receipt;
import com.schaccs.model.receipt.ReceiptLine;
import com.schaccs.repository.migration.MigrationV18BackfillReceiptHashes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class MigrationBackfillReceiptHashTest {

    private Connection conn;

    @BeforeEach
    void setUp() throws Exception {
        conn = DriverManager.getConnection("jdbc:sqlite::memory:");
        try (Statement st = conn.createStatement()) {
            st.execute("""
                    CREATE TABLE receipts (
                        id TEXT PRIMARY KEY,
                        receipt_number INTEGER,
                        date TEXT,
                        student_id TEXT,
                        admission_number TEXT,
                        student_name TEXT,
                        class_label TEXT,
                        amount TEXT,
                        payment_mode TEXT,
                        bank_reference TEXT,
                        received_by TEXT,
                        notes TEXT,
                        created_at TEXT,
                        reversed INTEGER,
                        verification_hash TEXT DEFAULT ''
                    )
                    """);
            st.execute("""
                    CREATE TABLE receipt_lines (
                        id TEXT PRIMARY KEY,
                        receipt_id TEXT,
                        votehead_code TEXT,
                        votehead_name TEXT,
                        amount TEXT,
                        outstanding_before TEXT
                    )
                    """);
        }
    }

    @AfterEach
    void tearDown() throws Exception {
        conn.close();
    }

    private void insertReceipt(String id, long number, String amount, String mode,
                               String bankRef, String notes, int reversed, String hash) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO receipts (id, receipt_number, date, student_id, admission_number, student_name, "
                        + "class_label, amount, payment_mode, bank_reference, received_by, notes, created_at, "
                        + "reversed, verification_hash) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)")) {
            ps.setString(1, id);
            ps.setLong(2, number);
            ps.setString(3, "2026-03-10");
            ps.setString(4, "student-" + id);
            ps.setString(5, "ADM-" + id);
            ps.setString(6, "Test Student " + id);
            ps.setString(7, "Form 2");
            ps.setString(8, amount);
            ps.setString(9, mode);
            ps.setString(10, bankRef);
            ps.setString(11, "Tester");
            ps.setString(12, notes);
            ps.setString(13, "2026-03-10T09:00:00");
            ps.setInt(14, reversed);
            ps.setString(15, hash);
            ps.executeUpdate();
        }
    }

    private void insertLine(String id, String receiptId, String code, String amount) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO receipt_lines (id, receipt_id, votehead_code, votehead_name, amount, outstanding_before) "
                        + "VALUES (?,?,?,?,?,?)")) {
            ps.setString(1, id);
            ps.setString(2, receiptId);
            ps.setString(3, code);
            ps.setString(4, code);
            ps.setString(5, amount);
            ps.setString(6, null);
            ps.executeUpdate();
        }
    }

    private String readHash(String id) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT verification_hash FROM receipts WHERE id = ?")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                return rs.getString("verification_hash");
            }
        }
    }

    private Receipt reconstruct(String id, String hash) {
        Receipt r = new Receipt();
        r.setReceiptNumber(50001);
        r.setDate(LocalDate.of(2026, 3, 10));
        r.setStudentId("student-" + id);
        r.setAmount(new BigDecimal("15000.00"));
        r.setPaymentMode(com.schaccs.enums.PaymentMode.BANK_SLIP);
        r.setBankReference("REF-BANK");
        r.setReversed(true);
        r.setNotes("LEGACY NOTE");
        r.setVerificationHash(hash);
        r.addLine(new ReceiptLine("BOARD", "Boarding", new BigDecimal("10000.00"), null));
        r.addLine(new ReceiptLine("TUITION", "Tuition", new BigDecimal("5000.00"), null));
        return r;
    }

    @Test
    void backfillsEmptyHashThatMatchesInMemoryVerification() throws Exception {
        insertReceipt("r1", 50001, "15000.00", "BANK_SLIP", "REF-BANK", "LEGACY NOTE", 1, "");
        insertLine("l1", "r1", "BOARD", "10000.00");
        insertLine("l2", "r1", "TUITION", "5000.00");

        new MigrationV18BackfillReceiptHashes().apply(conn);

        String hash = readHash("r1");
        assertTrue(hash.startsWith("v2:"), "Backfilled hash must be versioned v2, got: " + hash);

        Receipt receipt = reconstruct("r1", hash);
        assertTrue(receipt.isVerified(),
                "Migration hash must match the in-memory recomputation over the same fields");
    }

    @Test
    void backfillsHashWithNullReferenceAndNotes() throws Exception {
        insertReceipt("r2", 50002, "5000.00", "CASH", null, null, 0, "");
        insertLine("l3", "r2", "TUITION", "5000.00");

        new MigrationV18BackfillReceiptHashes().apply(conn);

        String hash = readHash("r2");
        assertTrue(hash.startsWith("v2:"));

        Receipt r = new Receipt();
        r.setReceiptNumber(50002);
        r.setDate(LocalDate.of(2026, 3, 10));
        r.setStudentId("student-r2");
        r.setAmount(new BigDecimal("5000.00"));
        r.setPaymentMode(com.schaccs.enums.PaymentMode.CASH);
        r.setBankReference(null);
        r.setReversed(false);
        r.setNotes(null);
        r.setVerificationHash(hash);
        r.addLine(new ReceiptLine("TUITION", "Tuition", new BigDecimal("5000.00"), null));
        assertTrue(r.isVerified(),
                "Hash over NULL reference/notes must match the in-memory recomputation");
    }

    @Test
    void leavesExistingV1AndV2HashesUntouched() throws Exception {
        insertReceipt("r3", 50003, "10000.00", "MPESA", "REF-M", null, 0, "v2:existing-v2");
        insertReceipt("r4", 50004, "10000.00", "MPESA", "REF-M", null, 0, "deadbeef");
        insertLine("l4", "r3", "BOARD", "10000.00");
        insertLine("l5", "r4", "BOARD", "10000.00");

        new MigrationV18BackfillReceiptHashes().apply(conn);

        assertEquals("v2:existing-v2", readHash("r3"));
        assertEquals("deadbeef", readHash("r4"));
    }

    @Test
    void backfillsHashWithNullPaymentModeMatchingReloadDefault() throws Exception {
        insertReceipt("r5", 50005, "8000.00", null, "REF-N", "NOTE", 0, "");
        insertLine("l6", "r5", "TUITION", "8000.00");

        new MigrationV18BackfillReceiptHashes().apply(conn);

        String hash = readHash("r5");
        assertTrue(hash.startsWith("v2:"));

        Receipt r = new Receipt();
        r.setReceiptNumber(50005);
        r.setDate(LocalDate.of(2026, 3, 10));
        r.setStudentId("student-r5");
        r.setAmount(new BigDecimal("8000.00"));
        r.setBankReference("REF-N");
        r.setNotes("NOTE");
        r.setVerificationHash(hash);
        r.addLine(new ReceiptLine("TUITION", "Tuition", new BigDecimal("8000.00"), null));
        assertTrue(r.isVerified(),
                "A NULL payment_mode loads as the model default (BANK_SLIP), so the migration hash must use it");
    }

    @Test
    void backfillsHashWithUnscaledAmounts() throws Exception {
        insertReceipt("r6", 50006, "6000", "MPESA", "REF-U", null, 0, "");
        insertLine("l7", "r6", "BOARD", "6000");

        new MigrationV18BackfillReceiptHashes().apply(conn);

        String hash = readHash("r6");
        assertTrue(hash.startsWith("v2:"));

        Receipt r = new Receipt();
        r.setReceiptNumber(50006);
        r.setDate(LocalDate.of(2026, 3, 10));
        r.setStudentId("student-r6");
        r.setAmount(new BigDecimal("6000.00"));
        r.setPaymentMode(com.schaccs.enums.PaymentMode.MPESA);
        r.setBankReference("REF-U");
        r.setVerificationHash(hash);
        r.addLine(new ReceiptLine("BOARD", "Boarding", new BigDecimal("6000.00"), null));
        assertTrue(r.isVerified(),
                "Amounts stored without decimals must be normalized to scale 2 exactly as load does");
    }

    @Test
    void skipsWhenColumnMissing() throws Exception {
        try (Statement st = conn.createStatement()) {
            st.executeUpdate("ALTER TABLE receipts DROP COLUMN verification_hash");
        }
        assertDoesNotThrow(() -> new MigrationV18BackfillReceiptHashes().apply(conn));
    }
}
