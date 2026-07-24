package com.schaccs.enums;

/**
 * Chart of Account types with normal balance direction and statement classification.
 */
public enum AccountType {
    // Assets (Debit Normal, Balance Sheet)
    CASH_AT_BANK("Cash at Bank", "CASH", NormalBalance.DEBIT, StatementCategory.BALANCE_SHEET),
    ACCOUNTS_RECEIVABLE("Accounts Receivable", "AR", NormalBalance.DEBIT, StatementCategory.BALANCE_SHEET),
    FEE_ARREARS("Fee Arrears", "ARREARS", NormalBalance.DEBIT, StatementCategory.BALANCE_SHEET),
    ADVANCE_BALANCES("Advance Balances", "ADV", NormalBalance.DEBIT, StatementCategory.BALANCE_SHEET),
    PETTY_CASH("Petty Cash", "PETTY", NormalBalance.DEBIT, StatementCategory.BALANCE_SHEET),
    FIXED_ASSETS("Fixed Assets", "FA", NormalBalance.DEBIT, StatementCategory.BALANCE_SHEET),

    // Liabilities (Credit Normal, Balance Sheet)
    ACCOUNTS_PAYABLE("Accounts Payable", "AP", NormalBalance.CREDIT, StatementCategory.BALANCE_SHEET),
    ACCRUED_EXPENSES("Accrued Expenses", "ACCRUAL", NormalBalance.CREDIT, StatementCategory.BALANCE_SHEET),
    DEFERRED_REVENUE("Deferred Revenue", "DEF_REV", NormalBalance.CREDIT, StatementCategory.BALANCE_SHEET),

    // Income (Credit Normal, I&E)
    SCHOOL_FUND("School Fund", "SF", NormalBalance.CREDIT, StatementCategory.INCOME_EXPENDITURE),
    TUITION_FEES("Tuition Fees", "TUITION", NormalBalance.CREDIT, StatementCategory.INCOME_EXPENDITURE),
    BOARDING_FEES("Boarding Fees", "BOARDING", NormalBalance.CREDIT, StatementCategory.INCOME_EXPENDITURE),
    ACTIVITY_FEES("Activity Fees", "ACTIVITY", NormalBalance.CREDIT, StatementCategory.INCOME_EXPENDITURE),
    OTHER_INCOME("Other Income", "OTHER", NormalBalance.CREDIT, StatementCategory.INCOME_EXPENDITURE),
    FSE_OPERATIONS("FSE Operations Aid", "FSE-OP", NormalBalance.CREDIT, StatementCategory.INCOME_EXPENDITURE),
    FSE_TUITION("FSE Tuition Aid", "FSE-TU", NormalBalance.CREDIT, StatementCategory.INCOME_EXPENDITURE),

    // Expenses (Debit Normal, I&E)
    SALARIES("Salaries & Wages", "SALARY", NormalBalance.DEBIT, StatementCategory.INCOME_EXPENDITURE),
    UTILITIES("Utilities", "UTIL", NormalBalance.DEBIT, StatementCategory.INCOME_EXPENDITURE),
    MAINTENANCE("Maintenance", "MAINT", NormalBalance.DEBIT, StatementCategory.INCOME_EXPENDITURE),
    SUPPLIES("Supplies", "SUPPLY", NormalBalance.DEBIT, StatementCategory.INCOME_EXPENDITURE),
    TRAVEL("Travel & Transport", "TRAVEL", NormalBalance.DEBIT, StatementCategory.INCOME_EXPENDITURE),
    GENERAL_EXPENSES("General Expenses", "GEN_EXP", NormalBalance.DEBIT, StatementCategory.INCOME_EXPENDITURE),

    // Equity (Credit Normal, Balance Sheet)
    RETAINED_EARNINGS("Retained Earnings", "RE", NormalBalance.CREDIT, StatementCategory.BALANCE_SHEET),

    // Payroll Liabilities (Credit Normal, Balance Sheet)
    PAYE_PAYABLE("PAYE Payable", "PAYE", NormalBalance.CREDIT, StatementCategory.BALANCE_SHEET),
    NSSF_PAYABLE("NSSF Payable", "NSSF", NormalBalance.CREDIT, StatementCategory.BALANCE_SHEET),
    SHIF_PAYABLE("SHIF Payable", "SHIF", NormalBalance.CREDIT, StatementCategory.BALANCE_SHEET),
    PENSION_PAYABLE("Pension Payable", "PENSION", NormalBalance.CREDIT, StatementCategory.BALANCE_SHEET),
    STAFF_LOAN_CONTROL("Staff Loan Control", "SLOAN", NormalBalance.CREDIT, StatementCategory.BALANCE_SHEET),
    BANK_CONTROL("Bank Control Account", "BNKCTRL", NormalBalance.CREDIT, StatementCategory.BALANCE_SHEET);

    private final String displayName;
    private final String code;
    private final NormalBalance normalBalance;
    private final StatementCategory statementCategory;

    AccountType(String displayName, String code, NormalBalance normalBalance, StatementCategory statementCategory) {
        this.displayName = displayName;
        this.code = code;
        this.normalBalance = normalBalance;
        this.statementCategory = statementCategory;
    }

    public String getDisplayName() { return displayName; }
    public String getCode() { return code; }
    public NormalBalance getNormalBalance() { return normalBalance; }
    public StatementCategory getStatementCategory() { return statementCategory; }

    public boolean isDebitNormal() { return normalBalance == NormalBalance.DEBIT; }
    public boolean isCreditNormal() { return normalBalance == NormalBalance.CREDIT; }

    @Override
    public String toString() { return displayName; }
}
