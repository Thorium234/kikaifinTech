package com.schaccs.repository.migration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Multi-Year Time-Variant Fee Matrix Engine.
 * <ul>
 *   <li>Creates {@code student_categories} table (BOARDING, DAY).</li>
 *   <li>Adds {@code category_id} and {@code created_at} to {@code fee_structures}.</li>
 *   <li>Adds {@code term1_amount}, {@code term2_amount}, {@code term3_amount} to
 *       {@code fee_structure_items} and backfills from existing per-term rows.</li>
 * </ul>
 */
public class MigrationV30MultiYearFeeMatrix implements SchemaMigration {

    @Override
    public int version() { return 30; }

    @Override
    public String description() { return "Multi-year time-variant fee matrix + student categories"; }

    @Override
    public void apply(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute("""
                CREATE TABLE IF NOT EXISTS student_categories (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name VARCHAR(50) UNIQUE NOT NULL
                )
                """);

            List<String> existingCats = existingColumnValues(conn, "student_categories", "name");
            if (!existingCats.contains("BOARDING")) {
                st.execute("INSERT INTO student_categories (name) VALUES ('BOARDING')");
            }
            if (!existingCats.contains("DAY")) {
                st.execute("INSERT INTO student_categories (name) VALUES ('DAY')");
            }

            List<String> fsCols = existingColumns(conn, "fee_structures");
            if (!fsCols.contains("category_id")) {
                st.execute("ALTER TABLE fee_structures ADD COLUMN category_id INTEGER");
            }
            if (!fsCols.contains("created_at")) {
                st.execute("ALTER TABLE fee_structures ADD COLUMN created_at TEXT");
            }

            st.execute("""
                UPDATE fee_structures
                SET category_id = (
                    SELECT sc.id FROM student_categories sc
                    WHERE sc.name = fee_structures.boarding_status
                )
                WHERE category_id IS NULL AND boarding_status IS NOT NULL
                """);

            st.execute("""
                CREATE INDEX IF NOT EXISTS idx_fee_structures_year_cat
                ON fee_structures(academic_year, category_id)
                """);

            List<String> fsiCols = existingColumns(conn, "fee_structure_items");
            if (!fsiCols.contains("term1_amount")) {
                st.execute("ALTER TABLE fee_structure_items ADD COLUMN term1_amount TEXT DEFAULT '0.00'");
            }
            if (!fsiCols.contains("term2_amount")) {
                st.execute("ALTER TABLE fee_structure_items ADD COLUMN term2_amount TEXT DEFAULT '0.00'");
            }
            if (!fsiCols.contains("term3_amount")) {
                st.execute("ALTER TABLE fee_structure_items ADD COLUMN term3_amount TEXT DEFAULT '0.00'");
            }

            backfillTermAmounts(conn);
        }
    }

    private void backfillTermAmounts(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT id, term, amount FROM fee_structure_items WHERE term1_amount = '0.00' AND term2_amount = '0.00' AND term3_amount = '0.00'")) {
            List<Object[]> rows = new ArrayList<>();
            while (rs.next()) {
                rows.add(new Object[]{
                        rs.getString("id"),
                        rs.getString("term"),
                        rs.getString("amount")
                });
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE fee_structure_items SET term1_amount = ?, term2_amount = ?, term3_amount = ? WHERE id = ?")) {
                for (Object[] row : rows) {
                    String id = (String) row[0];
                    String term = (String) row[1];
                    String amount = (String) row[2];
                    if (amount == null || amount.isBlank()) amount = "0.00";
                    String t1 = "0.00", t2 = "0.00", t3 = "0.00";
                    if ("TERM_1".equals(term)) t1 = amount;
                    else if ("TERM_2".equals(term)) t2 = amount;
                    else if ("TERM_3".equals(term)) t3 = amount;
                    ps.setString(1, t1);
                    ps.setString(2, t2);
                    ps.setString(3, t3);
                    ps.setString(4, id);
                    ps.addBatch();
                }
                ps.executeBatch();
            }
        }
    }

    private List<String> existingColumns(Connection conn, String table) throws SQLException {
        List<String> columns = new ArrayList<>();
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (rs.next()) {
                columns.add(rs.getString("name"));
            }
        }
        return columns;
    }

    private List<String> existingColumnValues(Connection conn, String table, String column) throws SQLException {
        List<String> values = new ArrayList<>();
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT " + column + " FROM " + table)) {
            while (rs.next()) {
                values.add(rs.getString(1));
            }
        }
        return values;
    }
}
