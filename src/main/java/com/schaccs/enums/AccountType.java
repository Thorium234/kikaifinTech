package com.schaccs.enums;

/**
 * Chart of Account types with normal balance direction, statement classification,
 * and ring-fencing support for Kenyan public secondary school accounting.
 *
 * <p>Account codes follow the MoE / NEMIS standard for sub-county and county schools.
 * Ring-fenced accounts carry a {@code restrictedGroup} that prevents cross-subsidization
 * between government capitation, infrastructure grants, and parent-funded fees.
 */
public enum AccountType {

    // =====================================================================
    // ASSETS — Ring-Fenced Bank Accounts (Debit Normal, Balance Sheet)
    // =====================================================================
    BANK_TUITION("Bank - Subsidized Tuition", "1000", NormalBalance.DEBIT,
            StatementCategory.BALANCE_SHEET, "GOVT"),
    BANK_INFRASTRUCTURE("Bank - Operation / Infrastructure", "1010", NormalBalance.DEBIT,
            StatementCategory.BALANCE_SHEET, "GOVT"),
    BANK_BOARDING("Bank - Boarding / PTA Account", "1020", NormalBalance.DEBIT,
            StatementCategory.BALANCE_SHEET, "PARENT"),

    // Legacy generic bank (kept for backward compatibility and test seeding)
    CASH_AT_BANK("Cash at Bank", "CASH", NormalBalance.DEBIT,
            StatementCategory.BALANCE_SHEET, null),
    MPESA_CLEARING("M-Pesa Clearing - In Transit", "1021", NormalBalance.DEBIT,
            StatementCategory.BALANCE_SHEET, null),
    ACCOUNTS_RECEIVABLE("Accounts Receivable", "AR", NormalBalance.DEBIT,
            StatementCategory.BALANCE_SHEET, null),
    FEE_ARREARS("Fee Arrears", "ARREARS", NormalBalance.DEBIT,
            StatementCategory.BALANCE_SHEET, null),
    ADVANCE_BALANCES("Advance Balances", "ADV", NormalBalance.DEBIT,
            StatementCategory.BALANCE_SHEET, null),
    PETTY_CASH("Petty Cash", "PETTY", NormalBalance.DEBIT,
            StatementCategory.BALANCE_SHEET, null),
    FIXED_ASSETS("Fixed Assets", "FA", NormalBalance.DEBIT,
            StatementCategory.BALANCE_SHEET, null),

    // =====================================================================
    // LIABILITIES (Credit Normal, Balance Sheet)
    // =====================================================================
    ACCOUNTS_PAYABLE("Accounts Payable", "AP", NormalBalance.CREDIT,
            StatementCategory.BALANCE_SHEET, null),
    ACCRUED_EXPENSES("Accrued Expenses", "ACCRUAL", NormalBalance.CREDIT,
            StatementCategory.BALANCE_SHEET, null),
    DEFERRED_REVENUE("Deferred Revenue", "DEF_REV", NormalBalance.CREDIT,
            StatementCategory.BALANCE_SHEET, null),

    // =====================================================================
    // INCOME — Government Capitation (Credit Normal, I&E)
    // =====================================================================
    GOVT_CAPITATION_TUITION("Government Capitation - Tuition", "3000", NormalBalance.CREDIT,
            StatementCategory.INCOME_EXPENDITURE, "GOVT"),
    INFRASTRUCTURE_GRANT("Infrastructure Grant", "3010", NormalBalance.CREDIT,
            StatementCategory.INCOME_EXPENDITURE, "GOVT"),

    // =====================================================================
    // INCOME — Parent-Funded Fees (Credit Normal, I&E)
    // =====================================================================
    FEES_BOARDING_ACTIVITY("School Fees - Boarding/Activity", "3020", NormalBalance.CREDIT,
            StatementCategory.INCOME_EXPENDITURE, "PARENT"),

    // Legacy income accounts (kept for backward compatibility)
    SCHOOL_FUND("School Fund", "SF", NormalBalance.CREDIT,
            StatementCategory.INCOME_EXPENDITURE, null),
    TUITION_FEES("Tuition Fees", "TUITION", NormalBalance.CREDIT,
            StatementCategory.INCOME_EXPENDITURE, null),
    BOARDING_FEES("Boarding Fees", "BOARDING", NormalBalance.CREDIT,
            StatementCategory.INCOME_EXPENDITURE, null),
    ACTIVITY_FEES("Activity Fees", "ACTIVITY", NormalBalance.CREDIT,
            StatementCategory.INCOME_EXPENDITURE, null),
    OTHER_INCOME("Other Income", "OTHER", NormalBalance.CREDIT,
            StatementCategory.INCOME_EXPENDITURE, null),
    FSE_OPERATIONS("FSE Operations Aid", "FSE-OP", NormalBalance.CREDIT,
            StatementCategory.INCOME_EXPENDITURE, null),
    FSE_TUITION("FSE Tuition Aid", "FSE-TU", NormalBalance.CREDIT,
            StatementCategory.INCOME_EXPENDITURE, null),

    // =====================================================================
    // EXPENSES — Teaching & Learning (Debit Normal, I&E)
    // =====================================================================
    TEACHING_LEARNING_MATERIALS("Teaching & Learning Materials", "4000", NormalBalance.DEBIT,
            StatementCategory.INCOME_EXPENDITURE, "GOVT"),

    // =====================================================================
    // EXPENSES — Infrastructure (Debit Normal, I&E)
    // =====================================================================
    INFRASTRUCTURE_EXPANSION("Infrastructure Expansion", "4010", NormalBalance.DEBIT,
            StatementCategory.INCOME_EXPENDITURE, "GOVT"),

    // =====================================================================
    // EXPENSES — Bad Debts (Debit Normal, I&E)
    // =====================================================================
    BAD_DEBTS_EXPENSE("Bad Debts Expense", "4020", NormalBalance.DEBIT,
            StatementCategory.INCOME_EXPENDITURE, null),

    // Legacy expense accounts (kept for backward compatibility)
    SALARIES("Salaries & Wages", "SALARY", NormalBalance.DEBIT,
            StatementCategory.INCOME_EXPENDITURE, null),
    UTILITIES("Utilities", "UTIL", NormalBalance.DEBIT,
            StatementCategory.INCOME_EXPENDITURE, null),
    MAINTENANCE("Maintenance", "MAINT", NormalBalance.DEBIT,
            StatementCategory.INCOME_EXPENDITURE, null),
    SUPPLIES("Supplies", "SUPPLY", NormalBalance.DEBIT,
            StatementCategory.INCOME_EXPENDITURE, null),
    TRAVEL("Travel & Transport", "TRAVEL", NormalBalance.DEBIT,
            StatementCategory.INCOME_EXPENDITURE, null),
    GENERAL_EXPENSES("General Expenses", "GEN_EXP", NormalBalance.DEBIT,
            StatementCategory.INCOME_EXPENDITURE, null),

    // =====================================================================
    // EQUITY (Credit Normal, Balance Sheet)
    // =====================================================================
    RETAINED_EARNINGS("Retained Earnings", "RE", NormalBalance.CREDIT,
            StatementCategory.BALANCE_SHEET, null),

    // =====================================================================
    // PAYROLL LIABILITIES (Credit Normal, Balance Sheet)
    // =====================================================================
    PAYE_PAYABLE("PAYE Payable", "PAYE", NormalBalance.CREDIT,
            StatementCategory.BALANCE_SHEET, null),
    NSSF_PAYABLE("NSSF Payable", "NSSF", NormalBalance.CREDIT,
            StatementCategory.BALANCE_SHEET, null),
    SHIF_PAYABLE("SHIF Payable", "SHIF", NormalBalance.CREDIT,
            StatementCategory.BALANCE_SHEET, null),
    PENSION_PAYABLE("Pension Payable", "PENSION", NormalBalance.CREDIT,
            StatementCategory.BALANCE_SHEET, null),
    STAFF_LOAN_CONTROL("Staff Loan Control", "SLOAN", NormalBalance.CREDIT,
            StatementCategory.BALANCE_SHEET, null),
    BANK_CONTROL("Bank Control Account", "BNKCTRL", NormalBalance.CREDIT,
            StatementCategory.BALANCE_SHEET, "GOVT");

    private final String displayName;
    private final String code;
    private final NormalBalance normalBalance;
    private final StatementCategory statementCategory;

    /** Ring-fencing group: "GOVT" = government capitation, "PARENT" = parent fees, null = unrestricted. */
    private final String restrictedGroup;

    AccountType(String displayName, String code, NormalBalance normalBalance,
                StatementCategory statementCategory, String restrictedGroup) {
        this.displayName = displayName;
        this.code = code;
        this.normalBalance = normalBalance;
        this.statementCategory = statementCategory;
        this.restrictedGroup = restrictedGroup;
    }

    public String getDisplayName() { return displayName; }
    public String getCode() { return code; }
    public NormalBalance getNormalBalance() { return normalBalance; }
    public StatementCategory getStatementCategory() { return statementCategory; }
    public String getRestrictedGroup() { return restrictedGroup; }

    public boolean isDebitNormal() { return normalBalance == NormalBalance.DEBIT; }
    public boolean isCreditNormal() { return normalBalance == NormalBalance.CREDIT; }

    /** Returns true if this account is ring-fenced (cannot cross-subsidize with other restricted accounts). */
    public boolean isRestricted() { return restrictedGroup != null; }

    /** Returns true if this account is a ring-fenced bank account. */
    public boolean isRingFencedBank() {
        return restrictedGroup != null && isDebitNormal()
                && (this == BANK_TUITION || this == BANK_INFRASTRUCTURE || this == BANK_BOARDING);
    }

    @Override
    public String toString() { return displayName; }
}
