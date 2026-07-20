package com.schaccs.repository.migration;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public class MigrationV9StudentGuardianPhoneId implements SchemaMigration {

    @Override
    public int version() {
        return 9;
    }

    @Override
    public String description() {
        return "Add guardian_phone and guardian_id to students";
    }

    @Override
    public void apply(Connection connection) throws SQLException {
        addColumnIfMissing(connection, "students", "guardian_phone", "ALTER TABLE students ADD COLUMN guardian_phone TEXT");
        addColumnIfMissing(connection, "students", "guardian_id", "ALTER TABLE students ADD COLUMN guardian_id TEXT");
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
