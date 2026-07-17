package com.schaccs.repository.migration;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public class MigrationV2ReceiptReversed implements SchemaMigration {

    @Override
    public int version() {
        return 2;
    }

    @Override
    public String description() {
        return "Add reversed flag to receipts";
    }

    @Override
    public void apply(Connection connection) throws SQLException {
        addColumnIfMissing(connection, "receipts", "reversed", "ALTER TABLE receipts ADD COLUMN reversed INTEGER DEFAULT 0");
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
