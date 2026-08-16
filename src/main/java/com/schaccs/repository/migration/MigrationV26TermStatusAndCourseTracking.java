package com.schaccs.repository.migration;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Adds the term lifecycle status column to the academic calendar and the
 * course-duration tracking columns to the students table (course code, duration
 * value/unit, enrollment date and expected completion date). All columns are
 * optional so existing rows keep their current data.
 */
public class MigrationV26TermStatusAndCourseTracking implements SchemaMigration {

    private static final List<String> CALENDAR_COLUMNS = List.of("status");
    private static final List<String> STUDENT_COLUMNS = List.of(
            "course_code", "duration_value", "duration_unit", "enrollment_date", "expected_completion_date");

    @Override
    public int version() {
        return 26;
    }

    @Override
    public String description() {
        return "Term lifecycle status + student course-duration tracking";
    }

    @Override
    public void apply(Connection connection) throws SQLException {
        addMissingColumns(connection, "academic_calendar", CALENDAR_COLUMNS);
        addMissingColumns(connection, "students", STUDENT_COLUMNS);
    }

    private void addMissingColumns(Connection connection, String table, List<String> wanted) throws SQLException {
        List<String> existing = existingColumns(connection, table);
        for (String column : wanted) {
            if (existing.contains(column)) {
                continue;
            }
            String sql = switch (column) {
                case "status" -> "ALTER TABLE " + table + " ADD COLUMN status TEXT NOT NULL DEFAULT 'PLANNED'";
                case "duration_value" -> "ALTER TABLE " + table + " ADD COLUMN duration_value INTEGER";
                case "duration_unit" -> "ALTER TABLE " + table + " ADD COLUMN duration_unit TEXT";
                default -> "ALTER TABLE " + table + " ADD COLUMN " + column + " TEXT";
            };
            try (Statement st = connection.createStatement()) {
                st.execute(sql);
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
