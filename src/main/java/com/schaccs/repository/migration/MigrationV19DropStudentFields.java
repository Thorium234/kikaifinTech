package com.schaccs.repository.migration;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Drops the unused student fields: UPI, guardian phone, guardian national ID,
 * and guardian key. Kept for forward compatibility on older installs.
 */
public class MigrationV19DropStudentFields implements SchemaMigration {

    private static final List<String> COLUMNS = List.of("upi", "guardian_phone", "guardian_id", "guardian_key");

    @Override
    public int version() {
        return 19;
    }

    @Override
    public String description() {
        return "Drop unused student fields (upi, guardian_phone, guardian_id, guardian_key)";
    }

    @Override
    public void apply(Connection connection) throws SQLException {
        for (String column : existingColumns(connection, "students")) {
            if (COLUMNS.contains(column)) {
                try (Statement st = connection.createStatement()) {
                    st.execute("ALTER TABLE students DROP COLUMN " + column);
                }
            }
        }
    }

    private List<String> existingColumns(Connection connection, String table) throws SQLException {
        List<String> columns = new ArrayList<>();
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (rs.next()) {
                columns.add(rs.getString("name"));
            }
        }
        return columns;
    }
}
