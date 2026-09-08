package com.schaccs.repository.migration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Payroll statutory-config schema.
 *
 * <p>Creates a single-row-per-config {@code statutory_config} table holding the
 * Kenya statutory parameters (PAYE bands, personal relief, SHIF, AHL, NSSF
 * ceilings) so the payroll engine can read them dynamically (payroll.md §1).
 * Seeds the current Finance Act defaults on first run.
 *
 * <p>Also extends {@code payroll_items} (AHL, employer AHL, proration/absence
 * fields) and {@code payroll_runs} (PE budget-overrun guard) for the spec-compliant
 * payroll core.
 */
public class MigrationV32PayrollStatutory implements SchemaMigration {

    @Override
    public int version() { return 32; }

    @Override
    public String description() { return "Payroll StatutoryConfig — runtime PAYE/SHIF/AHL/NSSF + AHL/proration/budget columns"; }

    @Override
    public void apply(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute("""
                CREATE TABLE IF NOT EXISTS statutory_config (
                    id TEXT PRIMARY KEY,
                    label TEXT,
                    active INTEGER NOT NULL DEFAULT 1,
                    paye_bands TEXT,
                    paye_top_rate TEXT,
                    personal_relief TEXT,
                    shif_rate TEXT,
                    ahl_employee_rate TEXT,
                    ahl_employer_rate TEXT,
                    nssf_tier_i_lower TEXT,
                    nssf_tier_i_ceiling TEXT,
                    nssf_tier_ii_ceiling TEXT,
                    nssf_rate TEXT,
                    nssf_employer_rate TEXT
                )
                """);

            addColumns(st, "payroll_items", List.of(
                    "ahl TEXT NOT NULL DEFAULT '0'",
                    "employer_ahl TEXT NOT NULL DEFAULT '0'",
                    "unpaid_leave_deduction TEXT NOT NULL DEFAULT '0'",
                    "days_worked TEXT",
                    "days_in_month TEXT",
                    "unpaid_leave_days TEXT"));

            addColumns(st, "payroll_runs", List.of(
                    "budget_overrun INTEGER NOT NULL DEFAULT 0",
                    "pe_available_balance TEXT NOT NULL DEFAULT '0'",
                    "overrun_variance TEXT NOT NULL DEFAULT '0'",
                    "budget_authorized INTEGER NOT NULL DEFAULT 0",
                    "budget_authorization_ref TEXT",
                    "budget_authorized_by TEXT"));
        }

        if (!rowExists(conn)) {
            seedDefaults(conn);
        }
    }

    private void addColumns(Statement st, String table, List<String> columns) throws SQLException {
        List<String> existing = existingColumns(st, table);
        for (String column : columns) {
            String name = column.split(" ", 2)[0];
            if (!existing.contains(name)) {
                st.execute("ALTER TABLE " + table + " ADD COLUMN " + column);
            }
        }
    }

    private List<String> existingColumns(Statement st, String table) throws SQLException {
        List<String> columns = new ArrayList<>();
        try (ResultSet rs = st.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (rs.next()) {
                columns.add(rs.getString("name"));
            }
        }
        return columns;
    }

    private boolean rowExists(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) AS c FROM statutory_config")) {
            return rs.next() && rs.getInt("c") > 0;
        }
    }

    private void seedDefaults(Connection conn) throws SQLException {
        // Kenya Finance Act bands: 24,000@10%, 8,333@25%, 467,667@30%, 300,000@32.5%, top@35%
        String bands = "24000:0.10;8333:0.25;467667:0.30;300000:0.325";
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO statutory_config (
                    id, label, active, paye_bands, paye_top_rate, personal_relief,
                    shif_rate, ahl_employee_rate, ahl_employer_rate,
                    nssf_tier_i_lower, nssf_tier_i_ceiling, nssf_tier_ii_ceiling,
                    nssf_rate, nssf_employer_rate
                ) VALUES (?, ?, 1, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            ps.setString(1, java.util.UUID.randomUUID().toString());
            ps.setString(2, "Default Kenya Statutory Rates");
            ps.setString(3, bands);
            ps.setString(4, "0.35");
            ps.setString(5, "2400");
            ps.setString(6, "0.0275");          // SHIF 2.75%
            ps.setString(7, "0.015");           // AHL employee 1.5%
            ps.setString(8, "0.015");           // AHL employer 1.5%
            ps.setString(9, "0");               // NSSF Tier I lower
            ps.setString(10, "7000");           // NSSF Tier I ceiling
            ps.setString(11, "36000");          // NSSF Tier II ceiling
            ps.setString(12, "0.06");           // NSSF rate
            ps.setString(13, "0.06");           // NSSF employer rate
            ps.executeUpdate();
        }
    }
}
