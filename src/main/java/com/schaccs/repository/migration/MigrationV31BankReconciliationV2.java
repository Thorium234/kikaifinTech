package com.schaccs.repository.migration;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * V2 Cashbook Reconciliation Framework schema.
 * <ul>
 *   <li>Adds {@code bank_account_type} to {@code bank_reconciliation} so each
 *       statement reconciles a specific ring-fenced bank account.</li>
 *   <li>Adds reconciliation-item tracking: {@code clearing_date},
 *       {@code matched_statement_ref}, {@code source} and {@code amount_side} so
 *       auto-matched and manually-paired items carry immutable audit detail.</li>
 *   <li>Creates {@code bank_statement_entry} to hold imported National Bank
 *       statement rows awaiting auto-match against unreconciled cashbook items.</li>
 * </ul>
 */
public class MigrationV31BankReconciliationV2 implements SchemaMigration {

    @Override
    public int version() { return 31; }

    @Override
    public String description() { return "V2 Cashbook Reconciliation framework (clearing detail, statement import)"; }

    @Override
    public void apply(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement()) {
            List<String> recCols = existingColumns(conn, "bank_reconciliation");
            if (!recCols.contains("bank_account_type")) {
                st.execute("ALTER TABLE bank_reconciliation ADD COLUMN bank_account_type TEXT");
            }
            if (!recCols.contains("previous_month_variance")) {
                st.execute("ALTER TABLE bank_reconciliation ADD COLUMN previous_month_variance TEXT");
            }

            List<String> itemCols = existingColumns(conn, "bank_reconciliation_items");
            if (!itemCols.contains("posted_date")) {
                st.execute("ALTER TABLE bank_reconciliation_items ADD COLUMN posted_date TEXT");
            }
            if (!itemCols.contains("clearing_date")) {
                st.execute("ALTER TABLE bank_reconciliation_items ADD COLUMN clearing_date TEXT");
            }
            if (!itemCols.contains("matched_statement_ref")) {
                st.execute("ALTER TABLE bank_reconciliation_items ADD COLUMN matched_statement_ref TEXT");
            }
            if (!itemCols.contains("source")) {
                st.execute("ALTER TABLE bank_reconciliation_items ADD COLUMN source TEXT");
            }
            if (!itemCols.contains("cleared_by")) {
                st.execute("ALTER TABLE bank_reconciliation_items ADD COLUMN cleared_by TEXT");
            }

            st.execute("""
                CREATE TABLE IF NOT EXISTS bank_statement_entry (
                    id TEXT PRIMARY KEY,
                    statement_date TEXT NOT NULL,
                    description TEXT,
                    reference TEXT,
                    debit TEXT,
                    credit TEXT,
                    balance TEXT,
                    reconciled INTEGER DEFAULT 0
                )
                """);
            st.execute("CREATE INDEX IF NOT EXISTS idx_stmt_entry_date ON bank_statement_entry(statement_date)");
        }
    }

    private List<String> existingColumns(Connection conn, String table) throws SQLException {
        List<String> columns = new ArrayList<>();
        try (Statement st = conn.createStatement();
             java.sql.ResultSet rs = st.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (rs.next()) {
                columns.add(rs.getString("name"));
            }
        }
        return columns;
    }
}
