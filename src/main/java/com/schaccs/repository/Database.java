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
import com.schaccs.repository.migration.MigrationV17FeeTemplates;
import com.schaccs.repository.migration.MigrationV18BackfillReceiptHashes;
import com.schaccs.repository.migration.MigrationV19DropStudentFields;
import com.schaccs.repository.migration.MigrationV20AcademicCalendar;
import com.schaccs.repository.migration.MigrationV22MidTermEnrollments;
import com.schaccs.repository.migration.MigrationV23RecycleBin;
import com.schaccs.repository.migration.MigrationV24EnabledPaymentModes;
import com.schaccs.repository.migration.MigrationV25CleanData;
import com.schaccs.repository.migration.MigrationV26TermStatusAndCourseTracking;
import com.schaccs.repository.migration.MigrationV30MultiYearFeeMatrix;
import com.schaccs.repository.migration.SchemaMigration;
import com.schaccs.util.CredentialCrypto;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
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
import java.util.logging.Logger;

/**
 * SQLite connection + schema.
 *
 * <p>Data location (resolved once, at first use):
 * <ul>
 *   <li>Packaged app (jpackage): a {@code database} folder next to the application
 *       executable, e.g. {@code C:\Program Files\ThorCash\database\schaccs.db}.
 *       If that folder is not writable (per-machine install run by a standard
 *       user), the app falls back to the user-home location.</li>
 *   <li>Development / running from a jar: {@code ~/.schaccs/schaccs.db}.</li>
 * </ul>
 */
public final class Database {

    private static final Logger LOG = Logger.getLogger(Database.class.getName());
    private static final Path DEFAULT_DATA_DIR = Path.of(System.getProperty("user.home"), ".schaccs");

    private static Path resolvedDataDirectory;
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
            Path dir = dataDirectory();
            try {
                Files.createDirectories(dir);
            } catch (Exception e) {
                throw new SQLException("Cannot create data directory: " + dir, e);
            }
            connection = DriverManager.getConnection("jdbc:sqlite:" + dir.resolve("schaccs.db"));
            configureConnection(connection);
            connection.setAutoCommit(true);
            initSchema(connection);
        }
        return connection;
    }

    public Path getDatabasePath() {
        return dataDirectory().resolve("schaccs.db");
    }

    /** Directory holding the main SQLite database file. */
    public Path getDatabaseDirectory() {
        return dataDirectory();
    }

    /**
     * Merges the SQLite WAL journal into the main database file so that a file copy
     * of schaccs.db alone contains the complete dataset.
     */
    public void checkpointWal() throws SQLException {
        getConnection();
        if (connection != null && !connection.isClosed()) {
            try (Statement st = connection.createStatement()) {
                st.execute("PRAGMA wal_checkpoint(TRUNCATE)");
            }
        }
    }

    /**
     * Resolves the data directory once. Priority:
     * <ol>
     *   <li>Packaged app (jpackage): a {@code database} folder next to the application
     *       executable, if writable.</li>
     *   <li>Windows: {@code %APPDATA%\ThorCash\database} — the OS-standard user data
     *       location with guaranteed write permissions.</li>
     *   <li>Linux / macOS: {@code ~/.local/share/thorcash}.</li>
     *   <li>Ultimate fallback: {@code ~/.schaccs}.</li>
     * </ol>
     */
    private static synchronized Path dataDirectory() {
        if (resolvedDataDirectory == null) {
            resolvedDataDirectory = resolveDataDirectory();
            try {
                Files.createDirectories(resolvedDataDirectory);
                migrateFromDefaultLocationIfNeeded(resolvedDataDirectory);
            } catch (Exception e) {
                LOG.warning("Could not prepare database directory " + resolvedDataDirectory
                        + ": " + e.getMessage() + " — falling back to " + DEFAULT_DATA_DIR);
                resolvedDataDirectory = DEFAULT_DATA_DIR;
            }
        }
        return resolvedDataDirectory;
    }

    private static Path resolveDataDirectory() {
        // 1. Packaged app — database folder next to the executable
        String appPath = System.getProperty("jpackage.app-path");
        if (appPath != null && !appPath.isBlank()) {
            Path appDir = Path.of(appPath).toAbsolutePath().getParent();
            if (appDir != null) {
                Path candidate = appDir.resolve("database");
                if (isWritable(candidate)) {
                    LOG.info("Database folder: " + candidate);
                    return candidate;
                }
                LOG.warning("Database folder not writable at " + candidate
                        + " — trying OS user data directory");
            }
        }

        // 2. Windows: %APPDATA%\ThorCash\database
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) {
            String appData = System.getenv("APPDATA");
            if (appData != null && !appData.isBlank()) {
                Path candidate = Path.of(appData, "ThorCash", "database");
                if (isWritable(candidate)) {
                    LOG.info("Database folder (Windows AppData): " + candidate);
                    return candidate;
                }
            }
        }

        // 3. Linux / macOS: ~/.local/share/thorcash
        Path xdgCandidate = Path.of(System.getProperty("user.home"),
                ".local", "share", "thorcash");
        if (isWritable(xdgCandidate)) {
            LOG.info("Database folder (XDG): " + xdgCandidate);
            return xdgCandidate;
        }

        // 4. Ultimate fallback
        LOG.info("Database folder (default): " + DEFAULT_DATA_DIR);
        return DEFAULT_DATA_DIR;
    }

    private static boolean isWritable(Path dir) {
        try {
            Files.createDirectories(dir);
            Path probe = Files.createTempFile(dir, ".schaccs-probe", ".tmp");
            Files.deleteIfExists(probe);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static void migrateFromDefaultLocationIfNeeded(Path targetDir) throws Exception {
        if (targetDir.toAbsolutePath().normalize().equals(DEFAULT_DATA_DIR.toAbsolutePath().normalize())) {
            return;
        }
        Path defaultDb = DEFAULT_DATA_DIR.resolve("schaccs.db");
        Path targetDb = targetDir.resolve("schaccs.db");
        if (!Files.exists(targetDb) && Files.exists(defaultDb)) {
            Files.createDirectories(targetDir);
            Files.copy(defaultDb, targetDb, StandardCopyOption.REPLACE_EXISTING);
            for (String suffix : new String[]{".db-wal", ".db-shm"}) {
                Path src = DEFAULT_DATA_DIR.resolve("schaccs" + suffix);
                if (Files.exists(src)) {
                    Files.copy(src, targetDir.resolve("schaccs" + suffix), StandardCopyOption.REPLACE_EXISTING);
                }
            }
            LOG.info("Copied existing database from " + defaultDb + " to " + targetDb);
        }
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
            st.execute("PRAGMA busy_timeout=5000");
            st.execute("PRAGMA synchronous=NORMAL");
            st.execute("PRAGMA mmap_size=268435456");
            st.execute("PRAGMA cache_size=-8000");
        }
    }

    private int schemaVersion(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT value FROM meta WHERE key = 'schema_version'")) {
            if (rs.next()) {
                return Integer.parseInt(rs.getString("value"));
            }
        } catch (SQLException e) {
            LOG.fine("Could not read schema_version (meta table may not exist yet): " + e.getMessage());
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
                new com.schaccs.repository.migration.MigrationV15ReceiptLineOutstandingBefore(),
                new com.schaccs.repository.migration.MigrationV16ProcurementModule(),
                new MigrationV17FeeTemplates(),
                new MigrationV18BackfillReceiptHashes(),
                new MigrationV19DropStudentFields(),
                new MigrationV20AcademicCalendar(),
                new MigrationV22MidTermEnrollments(),
                new MigrationV23RecycleBin(),
                new MigrationV24EnabledPaymentModes(),
                new MigrationV25CleanData(),
                new MigrationV26TermStatusAndCourseTracking(),
                new com.schaccs.repository.migration.MigrationV27LedgerHashChain(),
                new com.schaccs.repository.migration.MigrationV28StudentCohortLifecycle(),
                new MigrationV30MultiYearFeeMatrix()
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
                        pdf_stamp_enabled INTEGER NOT NULL DEFAULT 1,
                        enabled_payment_modes TEXT
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
                    CREATE TABLE IF NOT EXISTS student_categories (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        name VARCHAR(50) UNIQUE NOT NULL
                    )
                    """);
            st.execute("""
                    CREATE TABLE IF NOT EXISTS fee_structures (
                        id TEXT PRIMARY KEY,
                        academic_year INTEGER,
                        form_class TEXT,
                        boarding_status TEXT,
                        category_id INTEGER,
                        name TEXT,
                        created_at TEXT
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
                        term1_amount TEXT DEFAULT '0.00',
                        term2_amount TEXT DEFAULT '0.00',
                        term3_amount TEXT DEFAULT '0.00',
                        FOREIGN KEY (structure_id) REFERENCES fee_structures(id)
                    )
                    """);
            st.execute("""
                    CREATE TABLE IF NOT EXISTS students (
                        id TEXT PRIMARY KEY,
                        admission_number TEXT UNIQUE,
                        name TEXT,
                        gender TEXT,
                        form_class TEXT,
                        stream TEXT,
                        boarding_status TEXT,
                        parent_name TEXT,
                        phone TEXT,
                        avatar_path TEXT,
                        year_of_admission INTEGER,
                        academic_year INTEGER,
                        status TEXT,
                        course_code TEXT,
                        duration_value INTEGER,
                        duration_unit TEXT,
                        enrollment_date TEXT,
                        expected_completion_date TEXT,
                        lifecycle_status TEXT,
                        is_deleted INTEGER DEFAULT 0,
                        deleted_at TEXT,
                        deletion_reason TEXT,
                        course_duration_years INTEGER DEFAULT 4
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
                    CREATE TABLE IF NOT EXISTS recycle_bin (
                        id TEXT PRIMARY KEY,
                        admission_number TEXT,
                        name TEXT,
                        gender TEXT,
                        form_class TEXT,
                        stream TEXT,
                        boarding_status TEXT,
                        parent_name TEXT,
                        phone TEXT,
                        avatar_path TEXT,
                        year_of_admission INTEGER,
                        academic_year INTEGER,
                        status TEXT,
                        deleted_at TEXT,
                        deletion_reason TEXT
                    )
                    """);
            st.execute("""
                    CREATE TABLE IF NOT EXISTS student_term_balances (
                        id TEXT PRIMARY KEY,
                        student_id TEXT NOT NULL,
                        academic_year INTEGER NOT NULL,
                        term TEXT NOT NULL,
                        fee_billed TEXT DEFAULT '0.00',
                        arrears_brought_forward TEXT DEFAULT '0.00',
                        amount_paid TEXT DEFAULT '0.00',
                        closing_balance TEXT DEFAULT '0.00',
                        created_at TEXT,
                        updated_at TEXT,
                        UNIQUE(student_id, academic_year, term)
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
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to persist database configuration", e);
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
                String encryptedPassword = rs.getString("password");
                if (encryptedPassword != null && !encryptedPassword.isBlank()) {
                    config.setPassword(CredentialCrypto.decrypt(encryptedPassword));
                }
                config.setSslMode(rs.getString("ssl_mode"));
                config.setActive(rs.getInt("active") == 1);
                return config;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load database configuration", e);
        }
        return null;
    }

    public void close() {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException e) {
                LOG.fine("Error closing database connection: " + e.getMessage());
            }
            connection = null;
        }
    }

    public void reopenConnection() throws SQLException {
        connection = null;
        getConnection();
    }
}
