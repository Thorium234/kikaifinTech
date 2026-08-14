package com.schaccs.repository.migration;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public class MigrationV24EnabledPaymentModes implements SchemaMigration {

    @Override
    public int version() {
        return 24;
    }

    @Override
    public String description() {
        return "Add enabled_payment_modes to school_settings";
    }

    @Override
    public void apply(Connection connection) throws SQLException {
        addColumnIfMissing(connection, "school_settings", "enabled_payment_modes",
                "ALTER TABLE school_settings ADD COLUMN enabled_payment_modes TEXT");
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
