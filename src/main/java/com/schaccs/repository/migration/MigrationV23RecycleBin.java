package com.schaccs.repository.migration;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public class MigrationV23RecycleBin implements SchemaMigration {

    @Override
    public int version() {
        return 23;
    }

    @Override
    public String description() {
        return "Recycle bin table";
    }

    @Override
    public void apply(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute("""
                CREATE TABLE IF NOT EXISTS recycle_bin (
                    id TEXT PRIMARY KEY,
                    admission_number TEXT,
                    name TEXT,
                    gender TEXT,
                    form_class TEXT,
                    stream TEXT,
                    boarding_status TEXT,
                    parent_name TEXT,
                    phone TEXT,
                    avatar_path TEXT,
                    year_of_admission INTEGER,
                    academic_year INTEGER,
                    status TEXT,
                    deleted_at TEXT
                )
            """);
        }
    }
}
