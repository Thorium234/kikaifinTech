package com.schaccs.repository.migration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;

public class MigrationV14PayrollModule implements SchemaMigration {

    @Override
    public int version() {
        return 14;
    }

    @Override
    public String description() {
        return "Payroll Management Module — employees, salary structures, payroll runs, payroll items";
    }

    @Override
    public void apply(Connection connection) throws SQLException {
        try (Statement st = connection.createStatement()) {

            // Employees table
            st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS employees (
                        id TEXT PRIMARY KEY,
                        employee_number TEXT UNIQUE,
                        first_name TEXT,
                        last_name TEXT,
                        national_id TEXT,
                        department TEXT,
                        position TEXT,
                        employment_date TEXT,
                        employment_status TEXT NOT NULL DEFAULT 'ACTIVE',
                        bank_name TEXT,
                        bank_branch TEXT,
                        bank_account_number TEXT,
                        kra_pin TEXT,
                        nssf_number TEXT,
                        shif_number TEXT,
                        phone TEXT,
                        email TEXT,
                        address TEXT
                    )""");

            // Salary structures table
            st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS salary_structures (
                        id TEXT PRIMARY KEY,
                        employee_id TEXT,
                        basic_salary TEXT NOT NULL DEFAULT '0',
                        house_allowance TEXT NOT NULL DEFAULT '0',
                        responsibility_allowance TEXT NOT NULL DEFAULT '0',
                        transport_allowance TEXT NOT NULL DEFAULT '0',
                        other_earnings TEXT NOT NULL DEFAULT '0',
                        staff_loan_repayment TEXT NOT NULL DEFAULT '0',
                        salary_advance_recovery TEXT NOT NULL DEFAULT '0',
                        welfare_contribution TEXT NOT NULL DEFAULT '0',
                        effective_date TEXT,
                        active INTEGER NOT NULL DEFAULT 1,
                        FOREIGN KEY (employee_id) REFERENCES employees(id)
                    )""");

            // Payroll runs table (monthly batches)
            st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS payroll_runs (
                        id TEXT PRIMARY KEY,
                        run_number TEXT UNIQUE,
                        month INTEGER,
                        year INTEGER,
                        period_start TEXT,
                        period_end TEXT,
                        status TEXT NOT NULL DEFAULT 'DRAFT',
                        total_gross_pay TEXT NOT NULL DEFAULT '0',
                        total_deductions TEXT NOT NULL DEFAULT '0',
                        total_net_pay TEXT NOT NULL DEFAULT '0',
                        total_paye TEXT NOT NULL DEFAULT '0',
                        total_nssf TEXT NOT NULL DEFAULT '0',
                        total_shif TEXT NOT NULL DEFAULT '0',
                        total_pension TEXT NOT NULL DEFAULT '0',
                        employee_count INTEGER NOT NULL DEFAULT 0,
                        prepared_by TEXT,
                        approved_by TEXT,
                        posted_by TEXT,
                        prepared_at TEXT,
                        approved_at TEXT,
                        posted_at TEXT,
                        journal_id TEXT,
                        reversal_of_id TEXT,
                        notes TEXT,
                        created_at TEXT
                    )""");

            // Payroll items table (individual employee line items)
            st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS payroll_items (
                        id TEXT PRIMARY KEY,
                        payroll_run_id TEXT,
                        employee_id TEXT,
                        employee_number TEXT,
                        employee_name TEXT,
                        department TEXT,
                        basic_salary TEXT NOT NULL DEFAULT '0',
                        house_allowance TEXT NOT NULL DEFAULT '0',
                        responsibility_allowance TEXT NOT NULL DEFAULT '0',
                        transport_allowance TEXT NOT NULL DEFAULT '0',
                        overtime TEXT NOT NULL DEFAULT '0',
                        bonus TEXT NOT NULL DEFAULT '0',
                        other_earnings TEXT NOT NULL DEFAULT '0',
                        gross_pay TEXT NOT NULL DEFAULT '0',
                        paye TEXT NOT NULL DEFAULT '0',
                        nssf TEXT NOT NULL DEFAULT '0',
                        shif TEXT NOT NULL DEFAULT '0',
                        pension TEXT NOT NULL DEFAULT '0',
                        staff_loan_repayment TEXT NOT NULL DEFAULT '0',
                        salary_advance_recovery TEXT NOT NULL DEFAULT '0',
                        welfare_contribution TEXT NOT NULL DEFAULT '0',
                        custom_deductions TEXT NOT NULL DEFAULT '0',
                        custom_deduction_name TEXT,
                        total_deductions TEXT NOT NULL DEFAULT '0',
                        net_pay TEXT NOT NULL DEFAULT '0',
                        employer_nssf TEXT NOT NULL DEFAULT '0',
                        employer_pension TEXT NOT NULL DEFAULT '0',
                        FOREIGN KEY (payroll_run_id) REFERENCES payroll_runs(id),
                        FOREIGN KEY (employee_id) REFERENCES employees(id)
                    )""");

            // Create indexes for performance
            st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_employees_number ON employees(employee_number)");
            st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_employees_department ON employees(department)");
            st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_salary_structures_employee ON salary_structures(employee_id)");
            st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_payroll_runs_period ON payroll_runs(year, month)");
            st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_payroll_runs_status ON payroll_runs(status)");
            st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_payroll_items_run ON payroll_items(payroll_run_id)");
            st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_payroll_items_employee ON payroll_items(employee_id)");

            // Seed default payroll account types if not already present
            seedPayrollAccounts(connection);
        }
    }

    private void seedPayrollAccounts(Connection conn) throws SQLException {
        String[][] payrollAccounts = {
            {"PAYE", "PAYE Payable", "CREDIT", "BALANCE_SHEET"},
            {"NSSF", "NSSF Payable", "CREDIT", "BALANCE_SHEET"},
            {"SHIF", "SHIF Payable", "CREDIT", "BALANCE_SHEET"},
            {"PENSION", "Pension Payable", "CREDIT", "BALANCE_SHEET"},
            {"SLOAN", "Staff Loan Control", "CREDIT", "BALANCE_SHEET"},
            {"BNKCTRL", "Bank Control Account", "CREDIT", "BALANCE_SHEET"}
        };

        for (String[] acc : payrollAccounts) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT OR IGNORE INTO accounts (id, code, name, parent_id, normal_balance, statement_category, active, is_control_account) "
                    + "VALUES (?, ?, ?, NULL, ?, ?, 1, 1)")) {
                ps.setString(1, java.util.UUID.randomUUID().toString());
                ps.setString(2, acc[0]);
                ps.setString(3, acc[1]);
                ps.setString(4, acc[2]);
                ps.setString(5, acc[3]);
                ps.executeUpdate();
            }
        }
    }
}
