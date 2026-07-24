package com.schaccs.repository.migration;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public class MigrationV15ReceiptLineOutstandingBefore implements SchemaMigration {

    @Override
    public int version() {
        return 15;
    }

    @Override
    public String description() {
        return "Add outstanding_before column to receipt_lines for correct reversal of advance allocations";
    }

    @Override
    public void apply(Connection connection) throws SQLException {
        try (Statement st = connection.createStatement()) {
            st.executeUpdate("ALTER TABLE receipt_lines ADD COLUMN outstanding_before TEXT NOT NULL DEFAULT '0'");
        }
    }
}
