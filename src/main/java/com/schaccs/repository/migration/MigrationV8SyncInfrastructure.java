package com.schaccs.repository.migration;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public class MigrationV8SyncInfrastructure implements SchemaMigration {

    @Override
    public int version() {
        return 8;
    }

    @Override
    public String description() {
        return "Sync: synced_at/updated_at columns, sync_log table, remote_schema_version";
    }

    @Override
    public void apply(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement()) {
            String[] tables = {"students", "receipts", "voteheads", "fee_structures",
                    "transactions", "ledger_entries", "creditors", "commitments",
                    "payment_vouchers", "lpos", "invoices", "imprests",
                    "school_form_classes", "school_streams"};

            for (String table : tables) {
                try {
                    st.execute("ALTER TABLE " + table + " ADD COLUMN synced_at TEXT");
                } catch (Exception ignored) {
                }
                try {
                    st.execute("ALTER TABLE " + table + " ADD COLUMN updated_at TEXT");
                } catch (Exception ignored) {
                }
            }

            st.execute("""
                CREATE TABLE IF NOT EXISTS sync_log (
                    id TEXT PRIMARY KEY,
                    entity_type TEXT,
                    entity_id TEXT,
                    action TEXT,
                    status TEXT,
                    message TEXT,
                    started_at TEXT,
                    completed_at TEXT,
                    duration_ms INTEGER
                )
            """);

            st.execute("""
                CREATE TABLE IF NOT EXISTS remote_schema_version (
                    id INTEGER PRIMARY KEY CHECK (id = 1),
                    version INTEGER NOT NULL DEFAULT 0,
                    last_checked_at TEXT
                )
            """);

            st.execute("CREATE INDEX IF NOT EXISTS idx_sync_log_status ON sync_log(status)");
            st.execute("CREATE INDEX IF NOT EXISTS idx_sync_log_started ON sync_log(started_at)");

            String[] indexedTables = {"students", "receipts", "voteheads", "fee_structures",
                    "transactions", "ledger_entries", "creditors", "commitments",
                    "payment_vouchers", "lpos", "invoices", "imprests",
                    "school_form_classes", "school_streams"};
            for (String t : indexedTables) {
                try {
                    st.execute("CREATE INDEX IF NOT EXISTS idx_" + t + "_synced ON " + t + "(synced_at)");
                } catch (Exception ignored) {
                }
            }

            try {
                st.execute("ALTER TABLE db_config ADD COLUMN encryption_key TEXT");
            } catch (Exception ignored) {
            }
        }
    }
}
