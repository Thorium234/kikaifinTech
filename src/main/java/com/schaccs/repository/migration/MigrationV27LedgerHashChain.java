package com.schaccs.repository.migration;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

/**
 * Adds hash-chain columns (previous_hash, hash) to ledger_entries for
 * tamper-evident audit trails. Each entry's hash is
 * SHA-256(previous_hash + account_type + votehead_code + reference +
 *          debit + credit + date).
 */
public class MigrationV27LedgerHashChain implements SchemaMigration {

    @Override
    public int version() { return 27; }

    @Override
    public String description() { return "Ledger hash-chain audit trail columns"; }

    @Override
    public void apply(Connection conn) throws SQLException {
        List<String> wanted = List.of("previous_hash", "hash");
        List<String> existing = existingColumns(conn, "ledger_entries");
        try (Statement st = conn.createStatement()) {
            for (String col : wanted) {
                if (!existing.contains(col)) {
                    st.execute("ALTER TABLE ledger_entries ADD COLUMN " + col + " TEXT");
                }
            }
        }
    }

    private List<String> existingColumns(Connection conn, String table) throws SQLException {
        java.util.List<String> columns = new java.util.ArrayList<>();
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (rs.next()) {
                columns.add(rs.getString("name"));
            }
        }
        return columns;
    }
}
