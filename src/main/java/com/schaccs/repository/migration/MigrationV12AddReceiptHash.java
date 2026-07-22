package com.schaccs.repository.migration;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public class MigrationV12AddReceiptHash implements SchemaMigration {

    @Override
    public int version() {
        return 12;
    }

    @Override
    public String description() {
        return "Add verification_hash column to receipts table";
    }

    @Override
    public void apply(Connection connection) throws SQLException {
        try (Statement st = connection.createStatement()) {
            st.executeUpdate("ALTER TABLE receipts ADD COLUMN verification_hash TEXT DEFAULT ''");
        } catch (SQLException e) {
            // Column may already exist
        }
    }
}
