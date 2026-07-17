package com.schaccs.util;

import com.schaccs.repository.Database;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class DatabaseBackupUtil {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private DatabaseBackupUtil() {
    }

    public static Path backupNow() throws IOException {
        Path dbPath = Database.getInstance().getDatabasePath();
        Path backupDir = dbPath.getParent().resolve("backups");
        Files.createDirectories(backupDir);
        String fileName = "schaccs-" + LocalDateTime.now().format(FORMATTER) + ".db";
        Path backupPath = backupDir.resolve(fileName);
        Files.copy(dbPath, backupPath, StandardCopyOption.REPLACE_EXISTING);
        return backupPath;
    }
}
