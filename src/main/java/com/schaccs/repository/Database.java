package com.schaccs.repository;

import com.schaccs.repository.migration.MigrationV1SchoolSettingsDiscount;
import com.schaccs.repository.migration.MigrationV2ReceiptReversed;
import com.schaccs.repository.migration.MigrationV3SchoolLogoPath;
import com.schaccs.repository.migration.MigrationV4ReceiptStampSignaturePaths;
import com.schaccs.repository.migration.MigrationV5StudentAvatarAndGuardianFields;
import com.schaccs.repository.migration.MigrationV9StudentGuardianPhoneId;
import com.schaccs.repository.migration.MigrationV10PdfStampEnabled;
import com.schaccs.repository.migration.MigrationV11AccountingFoundation;
import com.schaccs.repository.migration.MigrationV12AddReceiptHash;
import com.schaccs.repository.migration.MigrationV13AddAuditTrail;
import com.schaccs.repository.migration.SchemaMigration;
import com.schaccs.util.CredentialCrypto;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

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
            configureConnection(connection);
            connection.setAutoCommit(true);
            initSchema(connection);
        }
        return connection;
    }

    public Path getDatabasePath() {
        return Path.of(DB_DIR, "schaccs.db");
    }

    public synchronized void inTransaction(SqlRunnable action) throws SQLException {
        Connection conn = getConnection();
        boolean previousAutoCommit = conn.getAutoCommit();
        if (!previousAutoCommit) {
            try {
                action.run(conn);
                return;
            } catch (Exception e) {
                if (e instanceof SQLException sqlException) {
                    throw sqlException;
                }
                throw new SQLException("Transaction failed: " + e.getMessage(), e);
            }
        }
        conn.setAutoCommit(false);
        try {
            action.run(conn);
            conn.commit();
        } catch (Exception e) {
            conn.rollback();
            if (e instanceof SQLException sqlException) {
                throw sqlException;
            }
            throw new SQLException("Transaction failed: " + e.getMessage(), e);
        } finally {
            conn.setAutoCommit(true);
        }
    }

    public synchronized <T> T inTransaction(SqlFunction<T> action) throws SQLException {
        Connection conn = getConnection();
        boolean previousAutoCommit = conn.getAutoCommit();
        if (!previousAutoCommit) {
            try {
                return action.apply(conn);
            } catch (Exception e) {
                if (e instanceof SQLException sqlException) {
                    throw sqlException;
                }
                throw new SQLException("Transaction failed: " + e.getMessage(), e);
            }
        }
        conn.setAutoCommit(false);
        try {
            T result = action.apply(conn);
            conn.commit();
            return result;
        } catch (Exception e) {
            conn.rollback();
            if (e instanceof SQLException sqlException) {
                throw sqlException;
            }
            throw new SQLException("Transaction failed: " + e.getMessage(), e);
        } finally {
            conn.setAutoCommit(true);
        }
    }

    private void configureConnection(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute("PRAGMA foreign_keys=ON");
            st.execute("PRAGMA journal_mode=WAL");
        }
    }

    private int schemaVersion(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT value FROM meta WHERE key = 'schema_version'")) {
            if (rs.next()) {
                return Integer.parseInt(rs.getString("value"));
            }
        } catch (SQLException ignored) {
            // meta table may not yet exist
        }
        return 0;
    }

    private void setSchemaVersion(Connection conn, int version) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO meta (key, value) VALUES ('schema_version', ?) "
                        + "ON CONFLICT(key) DO UPDATE SET value = ?")) {
            ps.setString(1, String.valueOf(version));
            ps.setString(2, String.valueOf(version));
            ps.executeUpdate();
        }
    }

    private boolean hasMigrationHistory(Connection conn, int version, String checksum) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT 1 FROM migration_history WHERE version = ? AND checksum = ? LIMIT 1")) {
            ps.setInt(1, version);
            ps.setString(2, checksum);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private boolean hasAnyMigrationHistory(Connection conn, int version) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT 1 FROM migration_history WHERE version = ? LIMIT 1")) {
            ps.setInt(1, version);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private void recordMigrationHistory(Connection conn, SchemaMigration migration) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO migration_history(version, migration_name, description, checksum, applied_at) VALUES (?, ?, ?, ?, ?)")) {
            ps.setInt(1, migration.version());
            ps.setString(2, migration.getClass().getSimpleName());
            ps.setString(3, migration.description());
            ps.setString(4, migration.checksum());
            ps.setString(5, LocalDateTime.now().toString());
            ps.executeUpdate();
        }
    }

    private void migrate(Connection conn, int fromVersion) throws SQLException {
        List<SchemaMigration> migrations = List.of(
                new MigrationV1SchoolSettingsDiscount(),
                new MigrationV2ReceiptReversed(),
                new MigrationV3SchoolLogoPath(),
                new MigrationV4ReceiptStampSignaturePaths(),
                new MigrationV5StudentAvatarAndGuardianFields(),
                new com.schaccs.repository.migration.MigrationV6V2Infrastructure(),
                new com.schaccs.repository.migration.MigrationV7SchoolCustomTables(),
                new com.schaccs.repository.migration.MigrationV8SyncInfrastructure(),
                new MigrationV9StudentGuardianPhoneId(),
                new MigrationV10PdfStampEnabled(),
                new MigrationV11AccountingFoundation(),
                new MigrationV12AddReceiptHash(),
                new MigrationV13AddAuditTrail(),
                new com.schaccs.repository.migration.MigrationV14PayrollModule(),
                new com.schaccs.repository.migration.MigrationV15ReceiptLineOutstandingBefore()
        );
        int version = fromVersion;
        for (SchemaMigration migration : migrations) {
            if (version < migration.version()) {
                migration.apply(conn);
                version = migration.version();
                setSchemaVersion(conn, version);
                if (!hasMigrationHistory(conn, migration.version(), migration.checksum())) {
                    recordMigrationHistory(conn, migration);
                }
            } else if (!hasAnyMigrationHistory(conn, migration.version())) {
                recordMigrationHistory(conn, migration);
            }
        }
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
                    CREATE TABLE IF NOT EXISTS migration_history (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        version INTEGER NOT NULL,
                        migration_name TEXT NOT NULL,
                        description TEXT,
                        checksum TEXT NOT NULL,
                        applied_at TEXT NOT NULL,
                        UNIQUE(version, checksum)
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
                        current_user TEXT,
                        sibling_discount_enabled INTEGER,
                        sibling_discount_rate TEXT,
                        logo_path TEXT,
                        stamp_path TEXT,
                        signature_path TEXT,
                        pdf_stamp_enabled INTEGER NOT NULL DEFAULT 1
                    )
                    """);
            st.execute("""
                    CREATE TABLE IF NOT EXISTS voteheads (
                        code TEXT PRIMARY KEY,
                        id TEXT,
                        name TEXT,
                        account_type TEXT,
                        priority INTEGER,
                        active INTEGER,
                        annual_budget TEXT,
                        termly_budget TEXT
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
                        guardian_phone TEXT,
                        guardian_id TEXT,
                        guardian_key TEXT,
                        phone TEXT,
                        avatar_path TEXT,
                        year_of_admission INTEGER,
                        academic_year INTEGER,
                        status TEXT
                    )
                    """);
            st.execute("""
                    CREATE TABLE IF NOT EXISTS student_ledgers (
                        student_id TEXT PRIMARY KEY,
                        arrears TEXT,
                        advance TEXT,
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
                        receipt_number INTEGER UNIQUE,
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
                        created_at TEXT,
                        reversed INTEGER
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
                        voucher_number INTEGER UNIQUE,
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
            st.execute("""
                    CREATE TABLE IF NOT EXISTS lpos (
                        id TEXT PRIMARY KEY,
                        lpo_number TEXT,
                        date TEXT,
                        creditor_id TEXT,
                        creditor_name TEXT,
                        votehead_code TEXT,
                        votehead_name TEXT,
                        account_type TEXT,
                        description TEXT,
                        amount TEXT,
                        status TEXT
                    )
                    """);
            st.execute("""
                    CREATE TABLE IF NOT EXISTS invoices (
                        id TEXT PRIMARY KEY,
                        invoice_number TEXT,
                        date TEXT,
                        creditor_id TEXT,
                        creditor_name TEXT,
                        lpo_id TEXT,
                        votehead_code TEXT,
                        votehead_name TEXT,
                        account_type TEXT,
                        description TEXT,
                        amount TEXT,
                        status TEXT
                    )
                    """);
            st.execute("""
                    CREATE TABLE IF NOT EXISTS imprests (
                        id TEXT PRIMARY KEY,
                        staff_name TEXT,
                        date TEXT,
                        amount TEXT,
                        votehead_code TEXT,
                        votehead_name TEXT,
                        account_type TEXT,
                        purpose TEXT,
                        status TEXT,
                        surrendered_amount TEXT,
                        surrender_date TEXT
                    )
                    """);
        }
        migrate(conn, schemaVersion(conn));
    }

    public List<String[]> migrationHistory() throws SQLException {
        List<String[]> rows = new ArrayList<>();
        Connection conn = getConnection();
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT version, migration_name, description, checksum, applied_at FROM migration_history ORDER BY version ASC, id ASC")) {
            while (rs.next()) {
                rows.add(new String[]{
                        String.valueOf(rs.getInt("version")),
                        rs.getString("migration_name"),
                        rs.getString("description"),
                        rs.getString("checksum"),
                        rs.getString("applied_at")
                });
            }
        }
        return rows;
    }

    @FunctionalInterface
    public interface SqlRunnable {
        void run(Connection connection) throws Exception;
    }

    @FunctionalInterface
    public interface SqlFunction<T> {
        T apply(Connection connection) throws Exception;
    }

    public void saveDbConfig(com.schaccs.config.db.DatasourceManager.DbConfig config) {
        try (PreparedStatement ps = getConnection().prepareStatement(
                "INSERT OR REPLACE INTO db_config (id, db_type, host, port, database_name, username, password, ssl_mode, active, jdbc_url) "
                        + "VALUES (1,?,?,?,?,?,?,?,?,?)")) {
            ps.setString(1, config.getDbType());
            ps.setString(2, config.getHost());
            ps.setInt(3, config.getPort());
            ps.setString(4, config.getDatabaseName());
            ps.setString(5, config.getUsername());
            ps.setString(6, CredentialCrypto.encrypt(config.getPassword()));
            ps.setString(7, config.getSslMode());
            ps.setInt(8, config.isActive() ? 1 : 0);
            ps.setString(9, config.getJdbcUrl());
            ps.executeUpdate();
        } catch (SQLException ignored) {
        }
    }

    public com.schaccs.config.db.DatasourceManager.DbConfig loadDbConfig() {
        try (Statement st = getConnection().createStatement();
             ResultSet rs = st.executeQuery("SELECT db_type, host, port, database_name, username, password, ssl_mode, active, jdbc_url FROM db_config LIMIT 1")) {
            if (rs.next()) {
                var config = new com.schaccs.config.db.DatasourceManager.DbConfig();
                config.setJdbcUrl(rs.getString("jdbc_url"));
                config.setDbType(rs.getString("db_type"));
                config.setHost(rs.getString("host"));
                config.setPort(rs.getInt("port"));
                config.setDatabaseName(rs.getString("database_name"));
                config.setUsername(rs.getString("username"));
                config.setPassword(CredentialCrypto.decrypt(rs.getString("password")));
                config.setSslMode(rs.getString("ssl_mode"));
                config.setActive(rs.getInt("active") == 1);
                return config;
            }
        } catch (SQLException ignored) {
        }
        return null;
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

    public void reopenConnection() throws SQLException {
        connection = null;
        getConnection();
    }
}
