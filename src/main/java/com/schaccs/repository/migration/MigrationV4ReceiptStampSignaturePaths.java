package com.schaccs.repository.migration;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public class MigrationV4ReceiptStampSignaturePaths implements SchemaMigration {

    @Override
    public int version() {
        return 4;
    }

    @Override
    public String description() {
        return "Add stamp and signature image paths to school settings";
    }

    @Override
    public void apply(Connection connection) throws SQLException {
        addColumnIfMissing(connection, "school_settings", "stamp_path", "ALTER TABLE school_settings ADD COLUMN stamp_path TEXT");
        addColumnIfMissing(connection, "school_settings", "signature_path", "ALTER TABLE school_settings ADD COLUMN signature_path TEXT");
    }

    private void addColumnIfMissing(Connection connection, String table, String column, String sql) throws SQLException {
        try (Statement st = connection.createStatement()) {
            try {
                st.executeQuery("SELECT " + column + " FROM " + table + " LIMIT 1").close();
            } catch (SQLException ignored) {
                st.execute(sql);
            }
        }
    }
}
