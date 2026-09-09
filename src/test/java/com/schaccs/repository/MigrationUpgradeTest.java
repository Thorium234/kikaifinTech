package com.schaccs.repository;

import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that an existing local database from an older release upgrades to
 * the current schema without losing data: builds a real schema at release V20,
 * seeds user settings, then migrates to the latest version exactly as happens
 * when a user upgrades to a newer build of the app.
 */
class MigrationUpgradeTest {

    @Test
    void oldDatabaseUpgradesToLatestWithoutDataLoss() throws SQLException {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            Database.getInstance().migrateDatabase(conn, 20);

            assertEquals(20, schemaVersion(conn), "Intermediate release schema version expected");
            assertColumnAbsent(conn, "bank_reconciliation", "bank_account_type");

            try (Statement st = conn.createStatement()) {
                st.executeUpdate("INSERT INTO school_settings (id, school_name, sibling_discount_rate) "
                        + "VALUES (1, 'Kikai FinTech Academy', '12.50')");
            }

            Database.getInstance().migrateDatabase(conn, Integer.MAX_VALUE);

            assertTrue(schemaVersion(conn) >= 31, "Latest schema must include the V31 bank reconciliation upgrade");
            assertEquals(schemaVersion(conn), latestRecordedMigration(conn),
                    "schema_version must equal the newest recorded migration");
            assertEquals("Kikai FinTech Academy", schoolName(conn), "Seeded data must survive the upgrade");
            assertEquals("12.50", siblingDiscountRate(conn), "Seeded data must survive the upgrade");

            assertColumnPresent(conn, "bank_reconciliation", "bank_account_type");
            assertColumnPresent(conn, "bank_reconciliation", "previous_month_variance");
            assertColumnPresent(conn, "bank_reconciliation_items", "clearing_date");
            assertColumnPresent(conn, "bank_reconciliation_items", "matched_statement_ref");
            assertColumnPresent(conn, "bank_reconciliation_items", "source");
            assertTablePresent(conn, "bank_statement_entry");

            int history = migrationHistoryCount(conn);
            Database.getInstance().migrateDatabase(conn, Integer.MAX_VALUE);
            assertEquals(history, migrationHistoryCount(conn), "Re-running migrations must be idempotent");
            assertEquals("Kikai FinTech Academy", schoolName(conn), "Re-running migrations must not touch data");
        }
    }

    private static int schemaVersion(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT value FROM meta WHERE key = 'schema_version'")) {
            return rs.next() ? Integer.parseInt(rs.getString("value")) : 0;
        }
    }

    private static int latestRecordedMigration(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT MAX(version) FROM migration_history")) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    private static int migrationHistoryCount(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM migration_history")) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    private static String schoolName(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT school_name FROM school_settings WHERE id = 1")) {
            return rs.next() ? rs.getString("school_name") : null;
        }
    }

    private static String siblingDiscountRate(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT sibling_discount_rate FROM school_settings WHERE id = 1")) {
            return rs.next() ? rs.getString("sibling_discount_rate") : null;
        }
    }

    private static void assertColumnPresent(Connection conn, String table, String column) throws SQLException {
        assertTrue(columnExists(conn, table, column), "Expected column " + table + "." + column);
    }

    private static void assertColumnAbsent(Connection conn, String table, String column) throws SQLException {
        assertFalse(columnExists(conn, table, column), "Column must not exist at the intermediate version: " + column);
    }

    private static boolean columnExists(Connection conn, String table, String column) throws SQLException {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (rs.next()) {
                if (column.equalsIgnoreCase(rs.getString("name"))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void assertTablePresent(Connection conn, String table) throws SQLException {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = '" + table + "'")) {
            assertTrue(rs.next(), "Expected table " + table);
        }
    }
}