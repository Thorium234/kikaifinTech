package com.schaccs.repository.migration;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public class MigrationV22MidTermEnrollments implements SchemaMigration {

    @Override
    public int version() {
        return 22;
    }

    @Override
    public String description() {
        return "Mid-term enrollments table";
    }

    @Override
    public void apply(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute("""
                CREATE TABLE IF NOT EXISTS mid_term_enrollments (
                    id TEXT PRIMARY KEY,
                    student_id TEXT NOT NULL,
                    admission_number TEXT,
                    name TEXT,
                    date_joined TEXT,
                    charge_current_term INTEGER,
                    mid_term_fee TEXT,
                    status TEXT
                )
            """);
        }
    }
}
