package com.schaccs.repository.migration;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public class MigrationV1SchoolSettingsDiscount implements SchemaMigration {

    @Override
    public int version() {
        return 1;
    }

    @Override
    public String description() {
        return "Add sibling discount settings columns";
    }

    @Override
    public void apply(Connection connection) throws SQLException {
        addColumnIfMissing(connection, "school_settings", "sibling_discount_enabled", "ALTER TABLE school_settings ADD COLUMN sibling_discount_enabled INTEGER");
        addColumnIfMissing(connection, "school_settings", "sibling_discount_rate", "ALTER TABLE school_settings ADD COLUMN sibling_discount_rate TEXT");
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
