package com.schaccs.repository.migration;

import com.schaccs.config.CurrencyConfig;
import com.schaccs.enums.PaymentMode;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Backfills the v2 verification hash for receipts created before the
 * verification_hash column existed (older installs). Only receipts with an
 * empty hash are touched; receipts already carrying a v1 or v2 hash are left
 * as-is so previously recorded integrity checks keep their meaning.
 */
public class MigrationV18BackfillReceiptHashes implements SchemaMigration {

    private static final String HASH_VERSION = "v2";

    @Override
    public int version() {
        return 18;
    }

    @Override
    public String description() {
        return "Backfill verification hashes for legacy receipts";
    }

    @Override
    public void apply(Connection connection) throws SQLException {
        if (!hasColumn(connection, "receipts", "verification_hash")) {
            return;
        }
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery("SELECT id, receipt_number, date, student_id, amount, "
                     + "payment_mode, bank_reference, notes, reversed "
                     + "FROM receipts WHERE verification_hash IS NULL OR verification_hash = ''")) {
            while (rs.next()) {
                String id = rs.getString("id");
                String hash = sha256(buildPayload(
                        rs.getLong("receipt_number"),
                        rs.getString("date"),
                        rs.getString("student_id"),
                        rs.getString("amount"),
                        rs.getString("payment_mode"),
                        rs.getString("bank_reference"),
                        rs.getInt("reversed") == 1,
                        rs.getString("notes"),
                        loadLines(connection, id)));
                try (PreparedStatement ps = connection.prepareStatement(
                        "UPDATE receipts SET verification_hash = ? WHERE id = ?")) {
                    ps.setString(1, HASH_VERSION + ":" + hash);
                    ps.setString(2, id);
                    ps.executeUpdate();
                }
            }
        }
    }

    private StringBuilder loadLines(Connection connection, String receiptId) throws SQLException {
        StringBuilder sb = new StringBuilder();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT votehead_code, amount FROM receipt_lines WHERE receipt_id = ? ORDER BY rowid")) {
            ps.setString(1, receiptId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    sb.append('|').append(rs.getString("votehead_code"))
                            .append('=').append(normalizeMoney(rs.getString("amount")));
                }
            }
        }
        return sb;
    }

    /**
     * Mirrors PersistenceService.loadReceipts(): a NULL/blank amount loads as
     * zero and the stored plain string round-trips through CurrencyConfig.money,
     * so the migration must hash the same normalized value the model produces.
     */
    private String normalizeMoney(String raw) {
        return CurrencyConfig.money(raw).toPlainString();
    }

    private String buildPayload(long receiptNumber, String date, String studentId, String amount,
                                String paymentMode, String bankReference, boolean reversed, String notes,
                                StringBuilder lines) {
        return receiptNumber + "|" + date + "|" + studentId + "|" + normalizeMoney(amount) + "|"
                + normalizePaymentMode(paymentMode) + "|" + bankReference + "|" + reversed + "|" + notes + lines;
    }

    /**
     * Mirrors the load path: a NULL payment_mode is left at the model default
     * (BANK_SLIP), so legacy NULL rows must hash as BANK_SLIP to verify after reload.
     */
    private String normalizePaymentMode(String mode) {
        return mode != null ? mode : PaymentMode.BANK_SLIP.name();
    }

    private String sha256(String raw) {
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

    private boolean hasColumn(Connection connection, String table, String column) throws SQLException {
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (rs.next()) {
                if (column.equalsIgnoreCase(rs.getString("name"))) {
                    return true;
                }
            }
        }
        return false;
    }
}
