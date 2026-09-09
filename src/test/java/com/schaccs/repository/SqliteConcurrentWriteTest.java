package com.schaccs.repository;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Simulates two application instances (two connections over the same local
 * SQLite file) racing to write: the second writer must receive a busy/locked
 * error, the app must recognise it, and {@link Database#runWithBusyRetry} must
 * complete the write once the first writer commits.
 */
class SqliteConcurrentWriteTest {

    @Test
    void secondWriterGetsBusyUntilFirstCommits() throws Exception {
        Path dir = Files.createTempDirectory("thorcash-lock-");
        Path db = dir.resolve(UUID.randomUUID() + ".db");
        Database dbApi = Database.getInstance();
        Connection first = null;
        Connection second = null;
        try {
            first = DriverManager.getConnection("jdbc:sqlite:" + db);
            second = DriverManager.getConnection("jdbc:sqlite:" + db);

            try (Statement st = first.createStatement()) {
                st.execute("CREATE TABLE t (id INTEGER PRIMARY KEY, value TEXT)");
            }

            first.setAutoCommit(false);
            try (Statement st = first.createStatement()) {
                st.execute("INSERT INTO t (id, value) VALUES (1, 'writer-one')");
            }

            assertBusyOnSecondWriter(second);

            first.commit();

            Connection blocked = second;
            dbApi.runWithBusyRetry(() -> {
                try (Statement st = blocked.createStatement()) {
                    st.execute("INSERT INTO t (id, value) VALUES (2, 'writer-two')");
                }
            });

            int count = 0;
            try (Statement st = second.createStatement();
                 java.sql.ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM t")) {
                if (rs.next()) {
                    count = rs.getInt(1);
                }
            }
            assertEquals(2, count, "Both writes must be present after the lock clears");
        } finally {
            closeQuietly(second);
            closeQuietly(first);
            deleteQuietly(db);
            try {
                Files.deleteIfExists(dir);
            } catch (Exception ignored) {
                // best-effort cleanup
            }
        }
    }

    private static void assertBusyOnSecondWriter(Connection second) throws SQLException {
        try (Statement st = second.createStatement()) {
            st.execute("INSERT INTO t (id, value) VALUES (99, 'blocked')");
            fail("Expected an SQLITE_BUSY error while the first writer holds the write lock");
        } catch (SQLException e) {
            assertTrue(Database.isBusy(e), "Error must be recognised as a busy/locked database: " + e.getMessage());
        }
    }

    private static void closeQuietly(Connection conn) {
        if (conn != null) {
            try {
                conn.close();
            } catch (Exception ignored) {
                // best-effort cleanup
            }
        }
    }

    private static void deleteQuietly(Path path) {
        for (String suffix : new String[]{"", "-wal", "-shm"}) {
            try {
                Files.deleteIfExists(Path.of(path.toString() + suffix));
            } catch (Exception ignored) {
                // best-effort cleanup
            }
        }
    }
}