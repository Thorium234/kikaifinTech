package com.schaccs.repository.migration;

import java.sql.Connection;
import java.sql.ResultSet;
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
        if (!hasColumn(connection, "receipts", "verification_hash")) {
            try (Statement st = connection.createStatement()) {
                st.executeUpdate("ALTER TABLE receipts ADD COLUMN verification_hash TEXT DEFAULT ''");
            }
        }
    }

    private boolean hasColumn(Connection connection, String table, String column) throws SQLException {
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (rs.next()) {
                if (column.equalsIgnoreCase(rs.getString("name"))) {
                    return true;
                }
            }
        }
        return false;
    }
}
