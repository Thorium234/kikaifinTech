package com.schaccs.repository.migration;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

/**
 * Adds student cohort lifecycle management: soft-delete columns on {@code students},
 * a {@code student_term_balances} table for term-level financial snapshots, and a
 * {@code deletion_reason} column on the recycle bin for audit traceability.
 */
public class MigrationV28StudentCohortLifecycle implements SchemaMigration {

    @Override
    public int version() { return 29; }

    @Override
    public String description() { return "Student cohort lifecycle + soft delete + term balances"; }

    @Override
    public void apply(Connection conn) throws SQLException {
        List<String> studentCols = List.of(
                "lifecycle_status TEXT",
                "is_deleted INTEGER DEFAULT 0",
                "deleted_at TEXT",
                "deletion_reason TEXT",
                "course_duration_years INTEGER DEFAULT 4"
        );
        List<String> existingStudents = existingColumns(conn, "students");
        try (Statement st = conn.createStatement()) {
            for (String colDef : studentCols) {
                String colName = colDef.split(" ")[0];
                if (!existingStudents.contains(colName)) {
                    st.execute("ALTER TABLE students ADD COLUMN " + colDef);
                }
            }

            List<String> recycleCols = existingColumns(conn, "recycle_bin");
            if (!recycleCols.contains("deletion_reason")) {
                st.execute("ALTER TABLE recycle_bin ADD COLUMN deletion_reason TEXT");
            }

            st.execute("""
                CREATE TABLE IF NOT EXISTS student_term_balances (
                    id TEXT PRIMARY KEY,
                    student_id TEXT NOT NULL,
                    academic_year INTEGER NOT NULL,
                    term TEXT NOT NULL,
                    fee_billed TEXT DEFAULT '0.00',
                    arrears_brought_forward TEXT DEFAULT '0.00',
                    amount_paid TEXT DEFAULT '0.00',
                    closing_balance TEXT DEFAULT '0.00',
                    created_at TEXT,
                    updated_at TEXT,
                    UNIQUE(student_id, academic_year, term)
                )
            """);
        }
    }

    private List<String> existingColumns(Connection conn, String table) throws SQLException {
        List<String> columns = new java.util.ArrayList<>();
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (rs.next()) {
                columns.add(rs.getString("name"));
            }
        }
        return columns;
    }
}
