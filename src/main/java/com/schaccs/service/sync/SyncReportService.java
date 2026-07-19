package com.schaccs.service.sync;

import com.schaccs.repository.Database;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class SyncReportService {

    private static final SyncReportService INSTANCE = new SyncReportService();

    private SyncReportService() {}

    public static SyncReportService getInstance() {
        return INSTANCE;
    }

    public SyncReport generate() {
        SyncReport report = new SyncReport();
        try (Connection conn = Database.getInstance().getConnection();
             Statement st = conn.createStatement()) {

            report.totalLocalRecords = countTotalRecords(st);
            report.totalSynced = countSyncedRecords(st);
            report.totalPending = report.totalLocalRecords - report.totalSynced;
            report.totalFailed = countFailedSyncs(st);
            report.lastSyncTime = lastSyncTime(st);
            report.recentErrors = recentErrors(st, 50);
            report.perTableBreakdown = perTableBreakdown(st);

        } catch (SQLException e) {
            report.error = e.getMessage();
        }
        return report;
    }

    private int countTotalRecords(Statement st) throws SQLException {
        String[] tables = {"students", "receipts", "voteheads", "fee_structures",
                "transactions", "ledger_entries", "creditors", "commitments",
                "payment_vouchers", "lpos", "invoices", "imprests",
                "school_form_classes", "school_streams"};
        int total = 0;
        for (String t : tables) {
            try (ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM " + t)) {
                if (rs.next()) total += rs.getInt(1);
            }
        }
        return total;
    }

    private int countSyncedRecords(Statement st) throws SQLException {
        String[] tables = {"students", "receipts", "voteheads", "fee_structures",
                "transactions", "ledger_entries", "creditors", "commitments",
                "payment_vouchers", "lpos", "invoices", "imprests",
                "school_form_classes", "school_streams"};
        int total = 0;
        for (String t : tables) {
            try (ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM " + t + " WHERE synced_at IS NOT NULL")) {
                if (rs.next()) total += rs.getInt(1);
            }
        }
        return total;
    }

    private int countFailedSyncs(Statement st) throws SQLException {
        try (ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM sync_log WHERE status = 'FAILED'")) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    private LocalDateTime lastSyncTime(Statement st) throws SQLException {
        try (ResultSet rs = st.executeQuery(
                "SELECT MAX(completed_at) FROM sync_log WHERE status = 'SYNCED'")) {
            if (rs.next()) {
                String s = rs.getString(1);
                if (s != null) return LocalDateTime.parse(s);
            }
        }
        return null;
    }

    private List<String> recentErrors(Statement st, int limit) throws SQLException {
        List<String> errors = new ArrayList<>();
        try (ResultSet rs = st.executeQuery(
                "SELECT entity_type, entity_id, message, completed_at FROM sync_log "
                        + "WHERE status IN ('FAILED','ERROR') ORDER BY completed_at DESC LIMIT " + limit)) {
            while (rs.next()) {
                errors.add(rs.getString("completed_at") + " | "
                        + rs.getString("entity_type") + "/" + rs.getString("entity_id")
                        + ": " + rs.getString("message"));
            }
        }
        return errors;
    }

    private List<TableBreakdown> perTableBreakdown(Statement st) throws SQLException {
        List<TableBreakdown> breakdowns = new ArrayList<>();
        String[] tables = {"students", "receipts", "voteheads", "fee_structures",
                "transactions", "ledger_entries", "creditors", "commitments",
                "payment_vouchers", "lpos", "invoices", "imprests",
                "school_form_classes", "school_streams"};
        for (String t : tables) {
            TableBreakdown b = new TableBreakdown();
            b.tableName = t;
            try (ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM " + t)) {
                if (rs.next()) b.total = rs.getInt(1);
            }
            try (ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM " + t + " WHERE synced_at IS NOT NULL")) {
                if (rs.next()) b.synced = rs.getInt(1);
            }
            breakdowns.add(b);
        }
        return breakdowns;
    }

    public static class SyncReport {
        private int totalLocalRecords;
        private int totalSynced;
        private int totalPending;
        private int totalFailed;
        private LocalDateTime lastSyncTime;
        private List<String> recentErrors = new ArrayList<>();
        private List<TableBreakdown> perTableBreakdown = new ArrayList<>();
        private String error;

        public int getTotalLocalRecords() { return totalLocalRecords; }
        public int getTotalSynced() { return totalSynced; }
        public int getTotalPending() { return totalPending; }
        public int getTotalFailed() { return totalFailed; }
        public LocalDateTime getLastSyncTime() { return lastSyncTime; }
        public List<String> getRecentErrors() { return recentErrors; }
        public List<TableBreakdown> getPerTableBreakdown() { return perTableBreakdown; }
        public String getError() { return error; }
        public boolean hasError() { return error != null; }
    }

    public static class TableBreakdown {
        private String tableName;
        private int total;
        private int synced;

        public String getTableName() { return tableName; }
        public int getTotal() { return total; }
        public int getSynced() { return synced; }
        public int getPending() { return total - synced; }
        public double getSyncPercentage() {
            return total == 0 ? 100.0 : (synced * 100.0 / total);
        }
    }
}
