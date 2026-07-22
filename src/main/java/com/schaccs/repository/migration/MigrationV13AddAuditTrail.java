package com.schaccs.repository.migration;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public class MigrationV13AddAuditTrail implements SchemaMigration {

    @Override
    public int version() {
        return 13;
    }

    @Override
    public String description() {
        return "Add field-level audit trail columns (field_name, old_value, new_value)";
    }

    @Override
    public void apply(Connection connection) throws SQLException {
        try (Statement st = connection.createStatement()) {
            st.executeUpdate("ALTER TABLE audit_log ADD COLUMN field_name TEXT DEFAULT ''");
            st.executeUpdate("ALTER TABLE audit_log ADD COLUMN old_value TEXT DEFAULT ''");
            st.executeUpdate("ALTER TABLE audit_log ADD COLUMN new_value TEXT DEFAULT ''");
        } catch (SQLException e) {
            // Columns may already exist
        }
    }
}
