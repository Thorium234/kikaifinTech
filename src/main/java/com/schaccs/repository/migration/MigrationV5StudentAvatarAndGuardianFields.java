package com.schaccs.repository.migration;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public class MigrationV5StudentAvatarAndGuardianFields implements SchemaMigration {

    @Override
    public int version() {
        return 5;
    }

    @Override
    public String description() {
        return "Add guardian key and avatar path to students";
    }

    @Override
    public void apply(Connection connection) throws SQLException {
        addColumnIfMissing(connection, "students", "guardian_key", "ALTER TABLE students ADD COLUMN guardian_key TEXT");
        addColumnIfMissing(connection, "students", "avatar_path", "ALTER TABLE students ADD COLUMN avatar_path TEXT");
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
