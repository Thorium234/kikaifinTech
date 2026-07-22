package com.schaccs.repository.migration;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.TreeMap;

public class MigrationV11AccountingFoundation implements SchemaMigration {

    @Override
    public int version() {
        return 11;
    }

    @Override
    public String description() {
        return "Chart of Accounts, Fiscal Years, Budgets, Assets, Running Balance Backfill";
    }

    @Override
    public void apply(Connection connection) throws SQLException {
        try (Statement st = connection.createStatement()) {
            st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS accounts (
                        id TEXT PRIMARY KEY, code TEXT UNIQUE, name TEXT,
                        parent_id TEXT, normal_balance TEXT, statement_category TEXT,
                        active INTEGER NOT NULL DEFAULT 1, is_control_account INTEGER NOT NULL DEFAULT 0
                    )""");

            st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS fiscal_years (
                        id TEXT PRIMARY KEY, year INTEGER UNIQUE, start_date TEXT, end_date TEXT,
                        is_open INTEGER NOT NULL DEFAULT 1, is_closed INTEGER NOT NULL DEFAULT 0,
                        closed_at TEXT, closed_by TEXT
                    )""");

            st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS budgets (
                        id TEXT PRIMARY KEY, fiscal_year_id TEXT, name TEXT,
                        is_approved INTEGER NOT NULL DEFAULT 0,
                        approved_at TEXT, approved_by TEXT
                    )""");

            st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS budget_lines (
                        id TEXT PRIMARY KEY, budget_id TEXT, account_id TEXT,
                        votehead_code TEXT,
                        allocated_amount TEXT NOT NULL DEFAULT '0',
                        spent_amount TEXT NOT NULL DEFAULT '0',
                        committed_amount TEXT NOT NULL DEFAULT '0'
                    )""");

            st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS asset_categories (
                        id TEXT PRIMARY KEY, name TEXT,
                        depreciation_method TEXT NOT NULL DEFAULT 'STRAIGHT_LINE',
                        useful_life_years INTEGER NOT NULL DEFAULT 5,
                        salvage_value_percent REAL NOT NULL DEFAULT 0.0
                    )""");

            st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS assets (
                        id TEXT PRIMARY KEY, category_id TEXT, asset_code TEXT,
                        name TEXT, description TEXT, purchase_date TEXT,
                        purchase_cost TEXT NOT NULL DEFAULT '0',
                        current_value TEXT NOT NULL DEFAULT '0',
                        salvage_value TEXT NOT NULL DEFAULT '0',
                        location TEXT, condition TEXT, status TEXT NOT NULL DEFAULT 'IN_USE'
                    )""");

            st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS depreciation_schedules (
                        id TEXT PRIMARY KEY, asset_id TEXT,
                        period_start TEXT, period_end TEXT,
                        depreciation_amount TEXT NOT NULL DEFAULT '0',
                        accumulated_depreciation TEXT NOT NULL DEFAULT '0',
                        net_book_value TEXT NOT NULL DEFAULT '0'
                    )""");

            // Seed default accounts from AccountType enum values
            seedDefaultAccounts(connection, st);

            // Seed default fiscal year for current academic year
            seedDefaultFiscalYear(connection, st);

            // Backfill running balances in ledger_entries
            backfillRunningBalances(connection, st);
        }
    }

    private void seedDefaultAccounts(Connection conn, Statement st) throws SQLException {
        // Only seed if accounts table is empty
        try (ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM accounts")) {
            if (rs.next() && rs.getInt(1) > 0) return;
        }

        String[][] defaultAccounts = {
            {"CASH", "Cash at Bank", null, "DEBIT", "BALANCE_SHEET", "1", "0"},
            {"AR", "Accounts Receivable", null, "DEBIT", "BALANCE_SHEET", "1", "0"},
            {"ARREARS", "Fee Arrears", null, "DEBIT", "BALANCE_SHEET", "1", "0"},
            {"ADV", "Advance Balances", null, "DEBIT", "BALANCE_SHEET", "1", "0"},
            {"PETTY", "Petty Cash", null, "DEBIT", "BALANCE_SHEET", "1", "0"},
            {"FA", "Fixed Assets", null, "DEBIT", "BALANCE_SHEET", "1", "0"},
            {"AP", "Accounts Payable", null, "CREDIT", "BALANCE_SHEET", "1", "0"},
            {"ACCRUAL", "Accrued Expenses", null, "CREDIT", "BALANCE_SHEET", "1", "0"},
            {"DEF_REV", "Deferred Revenue", null, "CREDIT", "BALANCE_SHEET", "1", "0"},
            {"SF", "School Fund", null, "CREDIT", "INCOME_EXPENDITURE", "1", "0"},
            {"TUITION", "Tuition Fees", null, "CREDIT", "INCOME_EXPENDITURE", "1", "0"},
            {"BOARDING", "Boarding Fees", null, "CREDIT", "INCOME_EXPENDITURE", "1", "0"},
            {"ACTIVITY", "Activity Fees", null, "CREDIT", "INCOME_EXPENDITURE", "1", "0"},
            {"OTHER", "Other Income", null, "CREDIT", "INCOME_EXPENDITURE", "1", "0"},
            {"FSE-OP", "FSE Operations Aid", null, "CREDIT", "INCOME_EXPENDITURE", "1", "0"},
            {"FSE-TU", "FSE Tuition Aid", null, "CREDIT", "INCOME_EXPENDITURE", "1", "0"},
            {"SALARY", "Salaries & Wages", null, "DEBIT", "INCOME_EXPENDITURE", "1", "0"},
            {"UTIL", "Utilities", null, "DEBIT", "INCOME_EXPENDITURE", "1", "0"},
            {"MAINT", "Maintenance", null, "DEBIT", "INCOME_EXPENDITURE", "1", "0"},
            {"SUPPLY", "Supplies", null, "DEBIT", "INCOME_EXPENDITURE", "1", "0"},
            {"TRAVEL", "Travel & Transport", null, "DEBIT", "INCOME_EXPENDITURE", "1", "0"},
            {"GEN_EXP", "General Expenses", null, "DEBIT", "INCOME_EXPENDITURE", "1", "0"}
        };

        for (String[] acc : defaultAccounts) {
            try (java.sql.PreparedStatement ps = conn.prepareStatement(
                    "INSERT OR IGNORE INTO accounts (id, code, name, parent_id, normal_balance, statement_category, active, is_control_account) "
                    + "VALUES (?,?,?,?,?,?,?,?)")) {
                ps.setString(1, java.util.UUID.randomUUID().toString());
                ps.setString(2, acc[0]);
                ps.setString(3, acc[1]);
                ps.setString(4, acc[2]);
                ps.setString(5, acc[3]);
                ps.setString(6, acc[4]);
                ps.setString(7, acc[5]);
                ps.setString(8, acc[6]);
                ps.executeUpdate();
            }
        }
    }

    private void seedDefaultFiscalYear(Connection conn, Statement st) throws SQLException {
        try (ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM fiscal_years")) {
            if (rs.next() && rs.getInt(1) > 0) return;
        }
        int year = java.time.LocalDate.now().getYear();
        try (java.sql.PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO fiscal_years (id, year, start_date, end_date, is_open, is_closed) VALUES (?,?,?,?,1,0)")) {
            ps.setString(1, java.util.UUID.randomUUID().toString());
            ps.setInt(2, year);
            ps.setString(3, year + "-01-01");
            ps.setString(4, year + "-12-31");
            ps.executeUpdate();
        }
    }

    private void backfillRunningBalances(Connection conn, Statement st) throws SQLException {
        // Check if we have any ledger entries with zero balance that need backfill
        try (ResultSet rs = st.executeQuery(
                "SELECT COUNT(*) FROM ledger_entries WHERE balance IS NULL OR balance = '0'")) {
            if (!rs.next() || rs.getInt(1) == 0) return;
        }

        // Rebuild running balances by account_type ordered by rowid ASC
        java.util.Map<String, java.math.BigDecimal> runningBalances = new TreeMap<>();

        try (Statement querySt = conn.createStatement();
             ResultSet rs = querySt.executeQuery(
                     "SELECT rowid, account_type, debit, credit FROM ledger_entries ORDER BY rowid ASC")) {
            while (rs.next()) {
                String acctType = rs.getString("account_type");
                if (acctType == null) continue;

                String debit = rs.getString("debit");
                String credit = rs.getString("credit");
                java.math.BigDecimal d = debit != null && !debit.isBlank() ? new java.math.BigDecimal(debit) : java.math.BigDecimal.ZERO;
                java.math.BigDecimal c = credit != null && !credit.isBlank() ? new java.math.BigDecimal(credit) : java.math.BigDecimal.ZERO;

                java.math.BigDecimal current = runningBalances.getOrDefault(acctType, java.math.BigDecimal.ZERO);

                // Determine normal balance: if account_type is one of the credit-normal types
                boolean isDebitNormal = isDebitNormalAccount(acctType);
                java.math.BigDecimal next;
                if (isDebitNormal) {
                    next = current.add(d).subtract(c);
                } else {
                    next = current.add(c).subtract(d);
                }
                runningBalances.put(acctType, next);

                // Update the ledger entry with computed balance
                try (java.sql.PreparedStatement updatePs = conn.prepareStatement(
                        "UPDATE ledger_entries SET balance = ? WHERE rowid = ?")) {
                    updatePs.setString(1, next.toPlainString());
                    updatePs.setLong(2, rs.getLong("rowid"));
                    updatePs.executeUpdate();
                }
            }
        }
    }

    private boolean isDebitNormalAccount(String accountType) {
        if (accountType == null) return true;
        return switch (accountType) {
            case "CASH_AT_BANK", "ACCOUNTS_RECEIVABLE", "FEE_ARREARS", "ADVANCE_BALANCES",
                 "PETTY_CASH", "FIXED_ASSETS", "SALARIES", "UTILITIES", "MAINTENANCE",
                 "SUPPLIES", "TRAVEL", "GENERAL_EXPENSES",
                 "SALARY", "UTIL", "MAINT", "SUPPLY", "GEN_EXP" -> false;
            default -> true; // Credit-normal by default (income accounts)
        };
    }
}
