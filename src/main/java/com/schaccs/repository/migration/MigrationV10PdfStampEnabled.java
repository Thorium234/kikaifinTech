package com.schaccs.repository.migration;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public class MigrationV10PdfStampEnabled implements SchemaMigration {

    @Override
    public int version() {
        return 10;
    }

    @Override
    public String description() {
        return "Add pdf_stamp_enabled to school_settings";
    }

    @Override
    public void apply(Connection connection) throws SQLException {
        addColumnIfMissing(connection, "school_settings", "pdf_stamp_enabled",
                "ALTER TABLE school_settings ADD COLUMN pdf_stamp_enabled INTEGER NOT NULL DEFAULT 1");
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
