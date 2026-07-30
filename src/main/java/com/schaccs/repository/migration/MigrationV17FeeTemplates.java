package com.schaccs.repository.migration;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public class MigrationV17FeeTemplates implements SchemaMigration {

    @Override
    public int version() {
        return 17;
    }

    @Override
    public String description() {
        return "Add fee_template and fee_template_items tables";
    }

    @Override
    public void apply(Connection connection) throws SQLException {
        try (Statement st = connection.createStatement()) {
            st.execute("""
                    CREATE TABLE IF NOT EXISTS fee_templates (
                        id TEXT PRIMARY KEY,
                        name TEXT NOT NULL
                    )
                    """);
            st.execute("""
                    CREATE TABLE IF NOT EXISTS fee_template_items (
                        id TEXT PRIMARY KEY,
                        template_id TEXT,
                        votehead_code TEXT,
                        votehead_name TEXT,
                        term TEXT,
                        boarding_status TEXT,
                        amount TEXT,
                        FOREIGN KEY (template_id) REFERENCES fee_templates(id)
                    )
                    """);
        }
    }
}
