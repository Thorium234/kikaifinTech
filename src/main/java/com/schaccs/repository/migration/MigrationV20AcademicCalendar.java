package com.schaccs.repository.migration;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public class MigrationV20AcademicCalendar implements SchemaMigration {

    @Override
    public int version() {
        return 20;
    }

    @Override
    public String description() {
        return "Academic calendar: term periods table";
    }

    @Override
    public void apply(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute("""
                CREATE TABLE IF NOT EXISTS academic_calendar (
                    id TEXT PRIMARY KEY,
                    term TEXT NOT NULL,
                    from_date TEXT NOT NULL,
                    to_date TEXT NOT NULL
                )
            """);
        }
    }
}
