package com.schaccs.service.sync;

import com.schaccs.config.db.DatasourceManager;
import com.schaccs.repository.Database;
import com.schaccs.service.audit.AuditService;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class SyncEngine {

    private static final SyncEngine INSTANCE = new SyncEngine();

    private static final long HEARTBEAT_INTERVAL_MS = 30_000;
    private static final int MAX_RETRIES = 5;
    private static final long BASE_DELAY_MS = 10_000;

    private volatile boolean running = false;
    private volatile long lastHeartbeat = 0;

    private final AuditService audit;

    private SyncEngine() {
        this.audit = com.schaccs.service.Services.getInstance().audit();
    }

    public static SyncEngine getInstance() {
        return INSTANCE;
    }

    private static final List<String> SYNC_TABLES_ORDERED = List.of(
            "voteheads",
            "school_form_classes",
            "school_streams",
            "fee_structures",
            "fee_structure_items",
            "students",
            "student_ledgers",
            "receipts",
            "receipt_lines",
            "transactions",
            "ledger_entries",
            "creditors",
            "commitments",
            "payment_vouchers",
            "lpos",
            "invoices",
            "imprests",
            "school_settings",
            "audit_log",
            "bank_reconciliation",
            "bank_reconciliation_items"
    );

    private static final List<String> COMPOSITE_PK_TABLES = List.of("student_ledger_lines");
    private static final List<String> TABLES_WITHOUT_SYNCED_AT = List.of(
            "fee_structure_items", "receipt_lines", "student_ledger_lines", "bank_reconciliation_items"
    );

    public SyncResult validateConnectivity() {
        try {
            if (!DatasourceManager.getInstance().isOnline()) {
                DatasourceManager.DbConfig config = Database.getInstance().loadDbConfig();
                if (config != null) {
                    DatasourceManager.getInstance().connectRemote(config);
                }
            }
            try (Connection c = DatasourceManager.getInstance().getRemoteConnection();
                 Statement s = c.createStatement()) {
                s.execute("SELECT 1");
                lastHeartbeat = System.currentTimeMillis();
                return SyncResult.success("Remote connection OK");
            }
        } catch (Exception e) {
            return SyncResult.failure("Remote unreachable: " + sanitizeMessage(e.getMessage()));
        }
    }

    public SyncResult validateRemoteSchema() {
        List<String> missing = new ArrayList<>();
        try (Connection remote = DatasourceManager.getInstance().getRemoteConnection()) {
            DatabaseMetaData meta = remote.getMetaData();
            for (String table : SYNC_TABLES_ORDERED) {
                try (ResultSet rs = meta.getTables(null, null, table, null)) {
                    if (!rs.next()) {
                        missing.add(table);
                    }
                }
            }
        } catch (Exception e) {
            return SyncResult.failure("Schema validation failed: " + sanitizeMessage(e.getMessage()));
        }
        if (!missing.isEmpty()) {
            return SyncResult.failure("Missing remote tables: " + String.join(", ", missing));
        }
        return SyncResult.success("Remote schema validated (" + SYNC_TABLES_ORDERED.size() + " tables present)");
    }

    public SyncResult validateSchemaVersion() {
        int localVersion;
        int remoteVersion;
        try (Connection local = Database.getInstance().getConnection()) {
            localVersion = readSchemaVersion(local);
        } catch (SQLException e) {
            return SyncResult.failure("Cannot read local schema version: " + e.getMessage());
        }
        try (Connection remote = DatasourceManager.getInstance().getRemoteConnection()) {
            remoteVersion = readSchemaVersion(remote);
        } catch (SQLException e) {
            remoteVersion = 0;
        }
        if (remoteVersion < localVersion) {
            return SyncResult.failure("Remote schema v" + remoteVersion
                    + " is behind local v" + localVersion + ". Run remote migrations first.");
        }
        if (remoteVersion > localVersion) {
            return SyncResult.failure("Remote schema v" + remoteVersion
                    + " ahead of local v" + localVersion + ". Upgrade local first.");
        }
        return SyncResult.success("Schema version matches (v" + localVersion + ")");
    }

    private String dbType = "postgresql";

    private String getDbType() {
        try {
            DatasourceManager.DbConfig cfg = Database.getInstance().loadDbConfig();
            if (cfg != null && cfg.getDbType() != null) {
                dbType = cfg.getDbType().toLowerCase();
            }
        } catch (Exception ignored) {
        }
        return dbType;
    }

    public SyncSummary syncAll() {
        SyncSummary summary = new SyncSummary();
        if (!DatasourceManager.getInstance().isOnline()) {
            summary.error = "Remote is offline. Cannot sync.";
            return summary;
        }
        getDbType();
        running = true;
        long startedAt = System.currentTimeMillis();
        try {
            for (String table : SYNC_TABLES_ORDERED) {
                if (!running) break;
                syncTable(table, summary);
            }
            summary.durationMs = System.currentTimeMillis() - startedAt;
            summary.completed = true;
            logSyncSummary(summary);
        } catch (Exception e) {
            summary.error = "Sync failed: " + e.getMessage();
            summary.durationMs = System.currentTimeMillis() - startedAt;
        } finally {
            running = false;
        }
        return summary;
    }

    private void syncTable(String table, SyncSummary summary) {
        boolean hasSyncedAt = !TABLES_WITHOUT_SYNCED_AT.contains(table);
        boolean isCompositePk = COMPOSITE_PK_TABLES.contains(table);

        List<Map<String, Object>> unsyncedRows = new ArrayList<>();
        List<String> columnNames = new ArrayList<>();
        String idColumn = isCompositePk ? null : "id";

        String selectSql = hasSyncedAt
                ? "SELECT * FROM " + table + " WHERE synced_at IS NULL"
                : "SELECT * FROM " + table;

        try (Connection local = Database.getInstance().getConnection();
             Statement st = local.createStatement();
             ResultSet rs = st.executeQuery(selectSql)) {

            ResultSetMetaData meta = rs.getMetaData();
            int colCount = meta.getColumnCount();
            for (int i = 1; i <= colCount; i++) {
                columnNames.add(meta.getColumnName(i));
            }

            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (int i = 1; i <= colCount; i++) {
                    row.put(meta.getColumnName(i), rs.getObject(i));
                }
                unsyncedRows.add(row);
            }
        } catch (SQLException e) {
            logSyncError(table, null, "READ_ERROR", e.getMessage());
            summary.skipped++;
            return;
        }

        if (unsyncedRows.isEmpty()) return;

        String upsertSql = buildUpsertSql(table, columnNames, isCompositePk);

        for (Map<String, Object> row : unsyncedRows) {
            String entityId = idColumn != null ? String.valueOf(row.get(idColumn)) : row.toString();
            long rowStarted = System.currentTimeMillis();
            try {
                Database.getInstance().inTransaction(localConn -> {
                    try (Connection remote = DatasourceManager.getInstance().getRemoteConnection();
                         PreparedStatement ps = remote.prepareStatement(upsertSql)) {

                        for (int i = 0; i < columnNames.size(); i++) {
                            ps.setObject(i + 1, row.get(columnNames.get(i)));
                        }
                        ps.executeUpdate();
                    }

                    if (hasSyncedAt && idColumn != null) {
                        try (PreparedStatement update = localConn.prepareStatement(
                                "UPDATE " + table + " SET synced_at = ?, updated_at = ? WHERE id = ?")) {
                            String now = LocalDateTime.now().toString();
                            update.setString(1, now);
                            update.setString(2, now);
                            update.setString(3, entityId);
                            update.executeUpdate();
                        }
                    }
                });

                summary.synced++;
                logSyncEvent(table, entityId, "SYNCED", null, System.currentTimeMillis() - rowStarted);
            } catch (Exception e) {
                if (isConnectivityError(e)) {
                    summary.error = "Connection lost during sync";
                    running = false;
                    return;
                }
                summary.failed++;
                logSyncEvent(table, entityId, "FAILED", e.getMessage(), System.currentTimeMillis() - rowStarted);
            }
        }
    }

    private String buildUpsertSql(String table, List<String> columns, boolean isCompositePk) {
        String conflictTarget = isCompositePk ? "(student_id, votehead_code, kind)" : "(id)";
        StringBuilder sql = new StringBuilder("INSERT INTO ");
        sql.append(table).append(" (");
        for (int i = 0; i < columns.size(); i++) {
            if (i > 0) sql.append(", ");
            sql.append(columns.get(i));
        }
        sql.append(") VALUES (");
        for (int i = 0; i < columns.size(); i++) {
            if (i > 0) sql.append(", ");
            sql.append("?");
        }
        sql.append(") ");

        String type = getDbType();
        if ("mysql".equals(type) || "mariadb".equals(type)) {
            sql.append("ON DUPLICATE KEY UPDATE ");
            boolean first = true;
            for (String col : columns) {
                if (first) { first = false; continue; }
                if (!first) sql.append(", ");
                sql.append(col).append(" = VALUES(").append(col).append(")");
            }
        } else {
            sql.append("ON CONFLICT ").append(conflictTarget).append(" DO UPDATE SET ");
            boolean first = true;
            for (String col : columns) {
                if (first) { first = false; continue; }
                if (!first) sql.append(", ");
                sql.append(col).append(" = EXCLUDED.").append(col);
            }
        }
        return sql.toString();
    }

    private int readSchemaVersion(Connection conn) {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT value FROM meta WHERE key = 'schema_version'")) {
            return rs.next() ? Integer.parseInt(rs.getString("value")) : 0;
        } catch (SQLException e) {
            return 0;
        }
    }

    private boolean isConnectivityError(Exception e) {
        String msg = e.getMessage();
        if (msg == null) return false;
        String lower = msg.toLowerCase();
        return lower.contains("connection") || lower.contains("timeout")
                || lower.contains("closed") || lower.contains("network")
                || lower.contains("refused") || lower.contains("reset");
    }

    private void logSyncEvent(String entityType, String entityId, String status, String message, long durationMs) {
        try (Connection conn = Database.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO sync_log (id, entity_type, entity_id, action, status, message, started_at, completed_at, duration_ms) "
                             + "VALUES (?,?,?,?,?,?,?,?,?)")) {
            String now = LocalDateTime.now().toString();
            ps.setString(1, UUID.randomUUID().toString());
            ps.setString(2, entityType);
            ps.setString(3, entityId);
            ps.setString(4, "SYNC");
            ps.setString(5, status);
            ps.setString(6, message);
            ps.setString(7, now);
            ps.setString(8, now);
            ps.setLong(9, durationMs);
            ps.executeUpdate();
        } catch (SQLException ignored) {
        }
    }

    private void logSyncError(String table, String entityId, String action, String message) {
        logSyncEvent(table, entityId, "ERROR", message, 0);
    }

    private void logSyncSummary(SyncSummary summary) {
        audit.log("SYNC", "System", null,
                "Sync completed: " + summary.synced + " synced, "
                        + summary.failed + " failed, "
                        + summary.skipped + " skipped in "
                        + summary.durationMs + "ms");
    }

    public boolean isRunning() {
        return running;
    }

    public void cancel() {
        running = false;
    }

    public static class SyncResult {
        private final boolean success;
        private final String message;

        private SyncResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }

        public static SyncResult success(String message) {
            return new SyncResult(true, message);
        }

        public static SyncResult failure(String message) {
            return new SyncResult(false, message);
        }

        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
    }

    public static class SyncSummary {
        private boolean completed;
        private int synced;
        private int failed;
        private int skipped;
        private long durationMs;
        private String error;

        public boolean isCompleted() { return completed; }
        public int getSynced() { return synced; }
        public int getFailed() { return failed; }
        public int getSkipped() { return skipped; }
        public long getDurationMs() { return durationMs; }
        public String getError() { return error; }
        public int getTotal() { return synced + failed + skipped; }

        public String toDisplayString() {
            if (error != null) return "Error: " + error;
            return "Synced: " + synced + ", Failed: " + failed + ", Skipped: " + skipped
                    + " (" + durationMs + "ms)";
        }
    }

    private static String sanitizeMessage(String msg) {
        if (msg == null) return "";
        return msg.replaceAll("(?i)(password|user|passwd)=[^\\s,);'\"!]+", "$1=***");
    }
}
