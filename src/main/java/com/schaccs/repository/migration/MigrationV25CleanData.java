package com.schaccs.repository.migration;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public class MigrationV25CleanData implements SchemaMigration {

    @Override
    public int version() {
        return 25;
    }

    @Override
    public String description() {
        return "Clean data table";
    }

    @Override
    public void apply(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute("""
                CREATE TABLE IF NOT EXISTS clean_data (
                    id TEXT PRIMARY KEY,
                    import_type TEXT NOT NULL,
                    payload TEXT NOT NULL,
                    created_at TEXT
                )
            """);
        }
    }
}
