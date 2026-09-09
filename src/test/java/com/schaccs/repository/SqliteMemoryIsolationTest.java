package com.schaccs.repository;

import com.schaccs.config.CurrencyConfig;
import com.schaccs.util.CurrencyUtil;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Demonstrates the in-memory SQLite ({@code jdbc:sqlite::memory:}) pattern for
 * fast, isolated database tests and verifies that money (BigDecimal) survives
 * SQLite's TEXT storage without rounding drift.
 */
class SqliteMemoryIsolationTest {

    private static Connection openMemory() throws SQLException {
        Connection conn = DriverManager.getConnection("jdbc:sqlite::memory:");
        try (Statement st = conn.createStatement()) {
            st.execute("PRAGMA foreign_keys=ON");
            st.execute("PRAGMA busy_timeout=500");
            st.execute("PRAGMA synchronous=NORMAL");
        }
        return conn;
    }

    @Test
    void dataSurvivesWhileConnectionIsOpen() throws SQLException {
        try (Connection conn = openMemory()) {
            Database.getInstance().migrateDatabase(conn, Integer.MAX_VALUE);
            try (Statement st = conn.createStatement()) {
                st.executeUpdate("INSERT INTO school_settings (id, school_name) VALUES (1, 'In-Memory Academy')");
            }
            assertEquals("In-Memory Academy", schoolName(conn));
        }
    }

    @Test
    void newConnectionSeesEmptyDatabase() throws SQLException {
        Connection first = openMemory();
        Database.getInstance().migrateDatabase(first, Integer.MAX_VALUE);
        try (Statement st = first.createStatement()) {
            st.executeUpdate("INSERT INTO school_settings (id, school_name) VALUES (1, 'Ephemeral')");
        }
        first.close();

        try (Connection second = openMemory()) {
            Database.getInstance().migrateDatabase(second, Integer.MAX_VALUE);
            assertNull(schoolName(second), "A fresh :memory: connection must never see rows from a closed one");
        }
    }

    @Test
    void moneyStoredInSqliteTextComesBackExact() throws SQLException {
        try (Connection conn = openMemory()) {
            try (Statement st = conn.createStatement()) {
                st.execute("CREATE TABLE payment (id INTEGER PRIMARY KEY, amount TEXT)");
            }
            BigDecimal value = new BigDecimal("1250.40").setScale(2, CurrencyConfig.ROUNDING);
            try (java.sql.PreparedStatement ps = conn.prepareStatement("INSERT INTO payment (id, amount) VALUES (1, ?)")) {
                ps.setString(1, value.toPlainString());
                ps.executeUpdate();
            }
            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery("SELECT amount FROM payment WHERE id = 1")) {
                assertTrue(rs.next());
                BigDecimal loaded = CurrencyUtil.parse(rs.getString("amount"));
                assertEquals(0, value.compareTo(loaded), "Money must round-trip through SQLite TEXT exactly");
            }
        }
    }

    private static String schoolName(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT school_name FROM school_settings WHERE id = 1")) {
            return rs.next() ? rs.getString("school_name") : null;
        }
    }
}