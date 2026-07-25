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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class SyncEngine {

    private static final SyncEngine INSTANCE = new SyncEngine();

    private static final long HEARTBEAT_INTERVAL_MS = 30_000;
    private static final int MAX_RETRIES = 5;
    private static final long BASE_DELAY_MS = 2_000;
    private static final int PAGE_SIZE = 500;
    private static final int BATCH_SIZE = 50;

    private volatile boolean running = false;
    private volatile long lastHeartbeat = 0;
    private String dbType = "postgresql";
    private AuditService audit;

    private SyncEngine() {
    }

    private AuditService audit() {
        if (audit == null) {
            audit = com.schaccs.service.Services.getInstance().audit();
        }
        return audit;
    }

    public static SyncEngine getInstance() {
        return INSTANCE;
    }

    private static final List<String> SYNC_TABLES_ORDERED = List.of(
            "voteheads",
            "school_form_classes",
            "school_streams",
            "fee_structures",
            "students",
            "student_ledgers",
            "receipts",
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
            "bank_reconciliation"
    );

    private static final List<String> CHILD_TABLES = List.of(
            "fee_structure_items", "receipt_lines", "student_ledger_lines", "bank_reconciliation_items"
    );

    private static final List<String> COMPOSITE_PK_TABLES = List.of("student_ledger_lines");

    private static final Map<String, String> TABLE_PK = new HashMap<>();
    static {
        TABLE_PK.put("voteheads", "code");
        TABLE_PK.put("student_ledgers", "student_id");
    }

    private static final Map<String, String> CHILD_PARENT_FK = new HashMap<>();
    private static final Map<String, String> CHILD_FK_COLUMN = new HashMap<>();
    static {
        CHILD_PARENT_FK.put("fee_structure_items", "fee_structures");
        CHILD_FK_COLUMN.put("fee_structure_items", "structure_id");
        CHILD_PARENT_FK.put("receipt_lines", "receipts");
        CHILD_FK_COLUMN.put("receipt_lines", "receipt_id");
        CHILD_PARENT_FK.put("student_ledger_lines", "students");
        CHILD_FK_COLUMN.put("student_ledger_lines", "student_id");
        CHILD_PARENT_FK.put("bank_reconciliation_items", "bank_reconciliation");
        CHILD_FK_COLUMN.put("bank_reconciliation_items", "reconciliation_id");
    }

    private static String pkColumn(String table) {
        return TABLE_PK.getOrDefault(table, "id");
    }

    private static boolean hasIdColumn(String table) {
        return !COMPOSITE_PK_TABLES.contains(table) && !TABLE_PK.containsKey(table);
    }

    private static String validateIdentifier(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            throw new IllegalArgumentException("Identifier must not be blank");
        }
        if (!identifier.matches("[A-Za-z0-9_]+")) {
            throw new IllegalArgumentException("Invalid identifier: " + identifier);
        }
        return identifier;
    }

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
        List<String> allTables = new ArrayList<>(SYNC_TABLES_ORDERED);
        allTables.addAll(CHILD_TABLES);
        try (Connection remote = DatasourceManager.getInstance().getRemoteConnection()) {
            DatabaseMetaData meta = remote.getMetaData();
            for (String table : allTables) {
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
        return SyncResult.success("Remote schema validated (" + allTables.size() + " tables present)");
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
        try (Connection remote = DatasourceManager.getInstance().getRemoteConnection()) {
            for (String table : SYNC_TABLES_ORDERED) {
                if (!running) break;
                syncTable(table, remote, summary);
            }
            for (String table : CHILD_TABLES) {
                if (!running) break;
                syncChildTable(table, remote, summary);
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

    private void syncTable(String table, Connection remote, SyncSummary summary) {
        List<String> columnNames = new ArrayList<>();
        String upsertSql;
        int offset = 0;

        try (Connection local = Database.getInstance().getConnection();
             Statement st = local.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM " + table + " LIMIT 1")) {
            ResultSetMetaData meta = rs.getMetaData();
            int colCount = meta.getColumnCount();
            for (int i = 1; i <= colCount; i++) {
                columnNames.add(meta.getColumnName(i));
            }
        } catch (SQLException e) {
            logSyncError(table, null, "SCHEMA_READ_ERROR", e.getMessage());
            summary.skipped++;
            return;
        }

        boolean isCompositePk = COMPOSITE_PK_TABLES.contains(table);
        upsertSql = buildUpsertSql(table, columnNames, isCompositePk);

        while (running) {
            List<Map<String, Object>> page = readPage(table, columnNames, offset, PAGE_SIZE);
            if (page.isEmpty()) break;
            offset += page.size();

            processBatch(table, page, columnNames, upsertSql, remote, summary);
        }
    }

    private void syncChildTable(String table, Connection remote, SyncSummary summary) {
        String parentTable = CHILD_PARENT_FK.get(table);
        if (parentTable == null) return;

        List<String> columnNames = new ArrayList<>();
        try (Connection local = Database.getInstance().getConnection();
             Statement st = local.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM " + table + " LIMIT 1")) {
            ResultSetMetaData meta = rs.getMetaData();
            int colCount = meta.getColumnCount();
            for (int i = 1; i <= colCount; i++) {
                columnNames.add(meta.getColumnName(i));
            }
        } catch (SQLException e) {
            logSyncError(table, null, "SCHEMA_READ_ERROR", e.getMessage());
            summary.skipped++;
            return;
        }

        boolean isCompositePk = COMPOSITE_PK_TABLES.contains(table);
        String upsertSql = buildUpsertSql(table, columnNames, isCompositePk);

        int offset = 0;
        while (running) {
            List<Map<String, Object>> page = readChildPage(table, parentTable, columnNames, offset, PAGE_SIZE);
            if (page.isEmpty()) break;
            offset += page.size();
            processBatch(table, page, columnNames, upsertSql, remote, summary);
        }
    }

    private List<Map<String, Object>> readPage(String table, List<String> columns, int offset, int limit) {
        List<Map<String, Object>> rows = new ArrayList<>();
        String safeTable = validateIdentifier(table);
        String orderCol = validateIdentifier(pkColumn(table));
        String sql = "SELECT * FROM " + safeTable + " WHERE synced_at IS NULL ORDER BY " + orderCol + " LIMIT " + limit + " OFFSET " + offset;
        try (Connection local = Database.getInstance().getConnection();
             Statement st = local.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (String col : columns) {
                    row.put(col, rs.getObject(col));
                }
                rows.add(row);
            }
        } catch (SQLException e) {
            logSyncError(table, null, "READ_ERROR", e.getMessage());
        }
        return rows;
    }

    private List<Map<String, Object>> readChildPage(String table, String parentTable, List<String> columns, int offset, int limit) {
        List<Map<String, Object>> rows = new ArrayList<>();
        String safeTable = validateIdentifier(table);
        String safeParentTable = validateIdentifier(parentTable);
        String fkCol = CHILD_FK_COLUMN.get(table);
        if (fkCol == null) return rows;
        String parentPk = validateIdentifier(pkColumn(parentTable));
        String sql = "SELECT c.* FROM " + safeTable + " c "
                + "INNER JOIN " + safeParentTable + " p ON p." + parentPk + " = c." + validateIdentifier(fkCol)
                + " WHERE p.synced_at IS NOT NULL "
                + "ORDER BY c." + validateIdentifier(fkCol) + " LIMIT " + limit + " OFFSET " + offset;
        try (Connection local = Database.getInstance().getConnection();
             Statement st = local.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (String col : columns) {
                    row.put(col, rs.getObject(col));
                }
                rows.add(row);
            }
        } catch (SQLException e) {
            logSyncError(table, null, "READ_ERROR", e.getMessage());
        }
        return rows;
    }

    private void processBatch(String table, List<Map<String, Object>> rows, List<String> columns,
                              String upsertSql, Connection remote, SyncSummary summary) {
        if (rows.isEmpty()) return;

        String safeTable = validateIdentifier(table);
        boolean isCompositePk = COMPOSITE_PK_TABLES.contains(table);
        String pkCol = validateIdentifier(pkColumn(table));

        for (int i = 0; i < rows.size(); i += BATCH_SIZE) {
            if (!running) return;
            int end = Math.min(i + BATCH_SIZE, rows.size());
            List<Map<String, Object>> batch = rows.subList(i, end);

            try {
                Database.getInstance().inTransaction(localConn -> {
                    try (PreparedStatement ps = remote.prepareStatement(upsertSql)) {
                        for (Map<String, Object> row : batch) {
                            for (int j = 0; j < columns.size(); j++) {
                                ps.setObject(j + 1, row.get(columns.get(j)));
                            }
                            ps.addBatch();
                        }
                        ps.executeBatch();
                    }

                    if (!isCompositePk) {
                        try (PreparedStatement update = localConn.prepareStatement(
                                "UPDATE " + safeTable + " SET synced_at = ?, updated_at = ? WHERE " + pkCol + " = ?")) {
                            String now = LocalDateTime.now().toString();
                            for (Map<String, Object> row : batch) {
                                update.setString(1, now);
                                update.setString(2, now);
                                update.setString(3, String.valueOf(row.get(pkCol)));
                                update.addBatch();
                            }
                            update.executeBatch();
                        }
                    }
                });

                for (Map<String, Object> row : batch) {
                    String eid = String.valueOf(row.get(pkCol));
                    summary.synced++;
                    logSyncEvent(table, eid, "SYNCED", null, 0);
                }
            } catch (Exception e) {
                if (isConnectivityError(e)) {
                    summary.error = "Connection lost during sync";
                    running = false;
                    return;
                }
                retryBatchRowByRow(table, batch, columns, upsertSql, remote, summary, 1);
            }
        }
    }

    private void retryBatchRowByRow(String table, List<Map<String, Object>> batch, List<String> columns,
                                    String upsertSql, Connection remote, SyncSummary summary, int attempt) {
        String safeTable = validateIdentifier(table);
        String pkCol = validateIdentifier(pkColumn(table));
        if (attempt > MAX_RETRIES) {
            for (Map<String, Object> row : batch) {
                summary.failed++;
                logSyncEvent(table, String.valueOf(row.get(pkCol)), "FAILED",
                        "Exceeded max retries (" + MAX_RETRIES + ")", 0);
            }
            return;
        }

        long delay = BASE_DELAY_MS * (1L << (attempt - 1));
        try {
            Thread.sleep(Math.min(delay, 60_000));
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
            return;
        }

        for (Map<String, Object> row : batch) {
            if (!running) return;
            String entityId = String.valueOf(row.get(pkCol));
            try {
                Database.getInstance().inTransaction(localConn -> {
                    try (PreparedStatement ps = remote.prepareStatement(upsertSql)) {
                        for (int j = 0; j < columns.size(); j++) {
                            ps.setObject(j + 1, row.get(columns.get(j)));
                        }
                        ps.executeUpdate();
                    }
                    try (PreparedStatement update = localConn.prepareStatement(
                            "UPDATE " + safeTable + " SET synced_at = ?, updated_at = ? WHERE " + pkCol + " = ?")) {
                        String now = LocalDateTime.now().toString();
                        update.setString(1, now);
                        update.setString(2, now);
                        update.setString(3, entityId);
                        update.executeUpdate();
                    }
                });
                summary.synced++;
                logSyncEvent(table, entityId, "SYNCED", null, 0);
            } catch (Exception e) {
                if (isConnectivityError(e)) {
                    summary.error = "Connection lost during retry";
                    running = false;
                    return;
                }
                logSyncEvent(table, entityId, "RETRY_" + attempt, e.getMessage(), 0);
                retryBatchRowByRow(table, List.of(row), columns, upsertSql, remote, summary, attempt + 1);
            }
        }
    }

    private String buildUpsertSql(String table, List<String> columns, boolean isCompositePk) {
        String safeTable = validateIdentifier(table);
        String conflictTarget = isCompositePk ? "(student_id, votehead_code, kind)" : "(" + validateIdentifier(pkColumn(table)) + ")";
        String type = dbType;
        List<String> validatedColumns = new ArrayList<>();
        for (String column : columns) {
            validatedColumns.add(validateIdentifier(column));
        }
        StringBuilder sql = new StringBuilder("INSERT INTO ");
        sql.append(safeTable).append(" (");
        for (int i = 0; i < validatedColumns.size(); i++) {
            if (i > 0) sql.append(", ");
            sql.append(validatedColumns.get(i));
        }
        sql.append(") VALUES (");
        for (int i = 0; i < validatedColumns.size(); i++) {
            if (i > 0) sql.append(", ");
            sql.append("?");
        }
        sql.append(") ");

        if ("mysql".equals(type) || "mariadb".equals(type)) {
            sql.append("ON DUPLICATE KEY UPDATE ");
            boolean first = true;
            for (String col : validatedColumns) {
                if (first) { first = false; continue; }
                sql.append(col).append(" = VALUES(").append(col).append(")");
            }
        } else {
            sql.append("ON CONFLICT ").append(conflictTarget).append(" DO UPDATE SET ");
            boolean first = true;
            for (String col : validatedColumns) {
                if (first) { first = false; continue; }
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
        audit().log("SYNC", "System", null,
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
