package com.schaccs.repository;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * SQLite connection + schema. Data lives under ~/.schaccs/schaccs.db
 */
public final class Database {

    private static final String DB_DIR = System.getProperty("user.home") + "/.schaccs";
    private static final String DB_URL = "jdbc:sqlite:" + DB_DIR + "/schaccs.db";

    private static Database instance;
    private Connection connection;

    private Database() {
    }

    public static synchronized Database getInstance() {
        if (instance == null) {
            instance = new Database();
        }
        return instance;
    }

    public Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            try {
                Files.createDirectories(Path.of(DB_DIR));
            } catch (Exception e) {
                throw new SQLException("Cannot create data directory: " + DB_DIR, e);
            }
            connection = DriverManager.getConnection(DB_URL);
            connection.setAutoCommit(true);
            initSchema(connection);
        }
        return connection;
    }

    public Path getDatabasePath() {
        return Path.of(DB_DIR, "schaccs.db");
    }

    private void initSchema(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute("""
                    CREATE TABLE IF NOT EXISTS meta (
                        key TEXT PRIMARY KEY,
                        value TEXT
                    )
                    """);
            st.execute("""
                    CREATE TABLE IF NOT EXISTS school_settings (
                        id INTEGER PRIMARY KEY CHECK (id = 1),
                        school_name TEXT,
                        location TEXT,
                        ministry TEXT,
                        principal TEXT,
                        bank_name TEXT,
                        bank_account TEXT,
                        pay_bill TEXT,
                        pay_bill_account TEXT,
                        cash_policy TEXT,
                        academic_year INTEGER,
                        next_receipt_number INTEGER,
                        next_voucher_number INTEGER,
                        current_user TEXT
                    )
                    """);
            st.execute("""
                    CREATE TABLE IF NOT EXISTS voteheads (
                        code TEXT PRIMARY KEY,
                        id TEXT,
                        name TEXT,
                        account_type TEXT,
                        priority INTEGER,
                        active INTEGER
                    )
                    """);
            st.execute("""
                    CREATE TABLE IF NOT EXISTS fee_structures (
                        id TEXT PRIMARY KEY,
                        academic_year INTEGER,
                        form_class TEXT,
                        boarding_status TEXT,
                        name TEXT
                    )
                    """);
            st.execute("""
                    CREATE TABLE IF NOT EXISTS fee_structure_items (
                        id TEXT PRIMARY KEY,
                        structure_id TEXT,
                        votehead_code TEXT,
                        votehead_name TEXT,
                        term TEXT,
                        boarding_status TEXT,
                        amount TEXT,
                        FOREIGN KEY (structure_id) REFERENCES fee_structures(id)
                    )
                    """);
            st.execute("""
                    CREATE TABLE IF NOT EXISTS students (
                        id TEXT PRIMARY KEY,
                        admission_number TEXT UNIQUE,
                        upi TEXT,
                        name TEXT,
                        gender TEXT,
                        form_class TEXT,
                        stream TEXT,
                        boarding_status TEXT,
                        parent_name TEXT,
                        phone TEXT,
                        year_of_admission INTEGER,
                        academic_year INTEGER,
                        status TEXT
                    )
                    """);
            st.execute("""
                    CREATE TABLE IF NOT EXISTS student_ledgers (
                        student_id TEXT PRIMARY KEY,
                        arrears TEXT,
                        current_term TEXT,
                        FOREIGN KEY (student_id) REFERENCES students(id)
                    )
                    """);
            st.execute("""
                    CREATE TABLE IF NOT EXISTS student_ledger_lines (
                        student_id TEXT,
                        votehead_code TEXT,
                        kind TEXT,
                        amount TEXT,
                        PRIMARY KEY (student_id, votehead_code, kind)
                    )
                    """);
            st.execute("""
                    CREATE TABLE IF NOT EXISTS receipts (
                        id TEXT PRIMARY KEY,
                        receipt_number INTEGER,
                        date TEXT,
                        student_id TEXT,
                        admission_number TEXT,
                        student_name TEXT,
                        class_label TEXT,
                        amount TEXT,
                        payment_mode TEXT,
                        bank_reference TEXT,
                        received_by TEXT,
                        notes TEXT,
                        created_at TEXT
                    )
                    """);
            st.execute("""
                    CREATE TABLE IF NOT EXISTS receipt_lines (
                        id TEXT PRIMARY KEY,
                        receipt_id TEXT,
                        votehead_code TEXT,
                        votehead_name TEXT,
                        amount TEXT,
                        FOREIGN KEY (receipt_id) REFERENCES receipts(id)
                    )
                    """);
            st.execute("""
                    CREATE TABLE IF NOT EXISTS transactions (
                        id TEXT PRIMARY KEY,
                        date TEXT,
                        type TEXT,
                        account_type TEXT,
                        votehead_code TEXT,
                        reference TEXT,
                        description TEXT,
                        debit TEXT,
                        credit TEXT,
                        student_id TEXT,
                        receipt_id TEXT,
                        voucher_id TEXT,
                        created_by TEXT,
                        created_at TEXT
                    )
                    """);
            st.execute("""
                    CREATE TABLE IF NOT EXISTS ledger_entries (
                        id TEXT PRIMARY KEY,
                        date TEXT,
                        account_type TEXT,
                        votehead_code TEXT,
                        reference TEXT,
                        description TEXT,
                        debit TEXT,
                        credit TEXT,
                        balance TEXT,
                        transaction_id TEXT
                    )
                    """);
            st.execute("""
                    CREATE TABLE IF NOT EXISTS creditors (
                        id TEXT PRIMARY KEY,
                        name TEXT,
                        phone TEXT,
                        description TEXT
                    )
                    """);
            st.execute("""
                    CREATE TABLE IF NOT EXISTS commitments (
                        id TEXT PRIMARY KEY,
                        date TEXT,
                        creditor_id TEXT,
                        creditor_name TEXT,
                        votehead_code TEXT,
                        votehead_name TEXT,
                        account_type TEXT,
                        description TEXT,
                        amount TEXT,
                        amount_paid TEXT,
                        status TEXT,
                        reference TEXT
                    )
                    """);
            st.execute("""
                    CREATE TABLE IF NOT EXISTS payment_vouchers (
                        id TEXT PRIMARY KEY,
                        voucher_number INTEGER,
                        date TEXT,
                        creditor_id TEXT,
                        creditor_name TEXT,
                        commitment_id TEXT,
                        votehead_code TEXT,
                        votehead_name TEXT,
                        account_type TEXT,
                        amount TEXT,
                        description TEXT,
                        status TEXT,
                        payment_mode TEXT,
                        bank_reference TEXT,
                        prepared_by TEXT,
                        approved_by TEXT,
                        notes TEXT,
                        created_at TEXT
                    )
                    """);
        }
    }

    public void close() {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException ignored) {
            }
            connection = null;
        }
    }
}
