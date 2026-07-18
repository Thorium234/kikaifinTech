package com.schaccs.repository.migration;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public class MigrationV6V2Infrastructure implements SchemaMigration {

    @Override
    public int version() {
        return 6;
    }

    @Override
    public String description() {
        return "V2: audit_log, bank_reconciliation, db_config, sync_queue";
    }

    @Override
    public void apply(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute("""
                CREATE TABLE IF NOT EXISTS audit_log (
                    id TEXT PRIMARY KEY,
                    timestamp TEXT NOT NULL,
                    action_type TEXT NOT NULL,
                    entity_type TEXT NOT NULL,
                    entity_id TEXT,
                    details_json TEXT,
                    performed_by TEXT
                )
            """);
            st.execute("""
                CREATE TABLE IF NOT EXISTS bank_reconciliation (
                    id TEXT PRIMARY KEY,
                    statement_date TEXT NOT NULL,
                    statement_balance TEXT NOT NULL,
                    book_balance TEXT NOT NULL,
                    adjusted_balance TEXT,
                    difference TEXT,
                    status TEXT,
                    created_at TEXT,
                    reconciled_at TEXT,
                    notes TEXT
                )
            """);
            st.execute("""
                CREATE TABLE IF NOT EXISTS bank_reconciliation_items (
                    id TEXT PRIMARY KEY,
                    reconciliation_id TEXT,
                    type TEXT NOT NULL,
                    reference TEXT,
                    description TEXT,
                    amount TEXT NOT NULL,
                    cleared INTEGER
                )
            """);
            st.execute("""
                CREATE TABLE IF NOT EXISTS db_config (
                    id INTEGER PRIMARY KEY CHECK (id = 1),
                    db_type TEXT,
                    host TEXT,
                    port INTEGER,
                    database_name TEXT,
                    username TEXT,
                    password TEXT,
                    ssl_mode TEXT,
                    active INTEGER
                )
            """);
            st.execute("""
                CREATE TABLE IF NOT EXISTS sync_queue (
                    id TEXT PRIMARY KEY,
                    entity_type TEXT NOT NULL,
                    entity_id TEXT NOT NULL,
                    action_type TEXT NOT NULL,
                    payload_json TEXT,
                    status TEXT,
                    created_at TEXT,
                    processed_at TEXT,
                    retry_count INTEGER DEFAULT 0
                )
            """);
            st.execute("CREATE INDEX IF NOT EXISTS idx_audit_timestamp ON audit_log(timestamp)");
            st.execute("CREATE INDEX IF NOT EXISTS idx_audit_entity ON audit_log(entity_type, entity_id)");
            st.execute("CREATE INDEX IF NOT EXISTS idx_sync_status ON sync_queue(status)");
        }
    }
}
