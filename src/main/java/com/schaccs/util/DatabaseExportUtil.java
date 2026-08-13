package com.schaccs.util;

import com.schaccs.repository.Database;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Copies the live main SQLite database to a chosen safe location so it can be
 * retrieved if the operating system fails. The exported file is a complete,
 * self-contained copy of schaccs.db (WAL is checkpointed first).
 */
public final class DatabaseExportUtil {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private DatabaseExportUtil() {
    }

    /**
     * Exports the main database into {@code targetDirectory} as a timestamped file.
     *
     * @return the path of the exported database file
     */
    public static Path exportTo(Path targetDirectory) throws Exception {
        Database database = Database.getInstance();
        database.checkpointWal();
        Path dbPath = database.getDatabasePath();
        Files.createDirectories(targetDirectory);
        String fileName = "thorcash-db-" + LocalDateTime.now().format(FORMATTER) + ".db";
        Path out = targetDirectory.resolve(fileName);
        Files.copy(dbPath, out, StandardCopyOption.REPLACE_EXISTING);
        return out;
    }
}
