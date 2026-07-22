package com.schaccs.service.finance;

import com.schaccs.repository.Database;
import com.schaccs.repository.PersistenceService;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public final class BackupService {

    private static final BackupService INSTANCE = new BackupService();

    private BackupService() {
    }

    public static BackupService getInstance() {
        return INSTANCE;
    }

    public void backup(String filePath) throws SQLException, IOException {
        PersistenceService.getInstance().saveAll();
        Path target = Path.of(filePath);
        Files.createDirectories(target.getParent());
        try (Connection conn = Database.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement("VACUUM INTO ?")) {
            ps.setString(1, target.toAbsolutePath().toString());
            ps.execute();
        }
    }

    public void restore(String filePath) throws SQLException, IOException {
        Database.getInstance().close();
        Path backupPath = Path.of(filePath);
        Path dbPath = Database.getInstance().getDatabasePath();
        Files.copy(backupPath, dbPath, StandardCopyOption.REPLACE_EXISTING);
        Database.getInstance().reopenConnection();
        PersistenceService.getInstance().loadAll();
    }

    public Path getDatabasePath() {
        return Database.getInstance().getDatabasePath();
    }

    public long getBackupSize(String filePath) throws IOException {
        return Files.size(Path.of(filePath));
    }
}
