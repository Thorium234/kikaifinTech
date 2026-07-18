package com.schaccs.repository.migration;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public class MigrationV7SchoolCustomTables implements SchemaMigration {

    @Override
    public int version() {
        return 7;
    }

    @Override
    public String description() {
        return "School custom: form_classes and streams tables";
    }

    @Override
    public void apply(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute("""
                CREATE TABLE IF NOT EXISTS school_form_classes (
                    id TEXT PRIMARY KEY,
                    name TEXT NOT NULL
                )
            """);
            st.execute("""
                CREATE TABLE IF NOT EXISTS school_streams (
                    id TEXT PRIMARY KEY,
                    name TEXT NOT NULL
                )
            """);
        }
    }
}
