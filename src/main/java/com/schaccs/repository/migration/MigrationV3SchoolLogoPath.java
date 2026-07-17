package com.schaccs.repository.migration;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public class MigrationV3SchoolLogoPath implements SchemaMigration {

    @Override
    public int version() {
        return 3;
    }

    @Override
    public String description() {
        return "Add logo path to school settings";
    }

    @Override
    public void apply(Connection connection) throws SQLException {
        addColumnIfMissing(connection, "school_settings", "logo_path", "ALTER TABLE school_settings ADD COLUMN logo_path TEXT");
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
