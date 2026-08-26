# Developer Implementation Guide: ThorCash V2 Features

Welcome to the ThorCash development team. This document outlines the strict guidelines, architecture rules, and functional requirements for implementing Version 2 (V2) features. 

As a developer on this project, your mandate is **incremental implementation only**. Do not rewrite, refactor, or remove existing Version 1 (V1) modules, database schemas, or accounting rules without explicit authorization.

---

## 1. Core Rule: Architectural Integrity
ThorCash is an accounting-first system. The user interface (UI) must never modify financial balances, student records, or ledgers directly.

* **Do Not Touch V1 Core Engines:** The `AccountingEngine` and `ReceiptAllocationEngine` are fully operational. V2 features must consume these engines or extend them via new service methods.
* **Double-Entry Discipline:** Every transaction created in V2 must balance (Debits = Credits).
* **Database Invariant:** The SQLite database schema (`~/.schaccs/schaccs.db`) for V1 (Students, Receipts, Voteheads) must remain backward-compatible. Any new tables for V2 must use foreign keys referencing existing V1 tables.

---

## 2. V2 Feature Implementation Roadmap
You are required to implement the following features, which are already planned but lack full implementation. Use the existing packaging structure (`com.schaccs.*`).

### Feature 1: Payment Vouchers & Commitment Register
* **Objective:** Track school expenditures and ring-fence funds for pending obligations.
* **UI Location:** Create a new view under `com.schaccs.ui.views.vouchers`.
* **Rules:**
  * Payment Vouchers (PVs) must be approved against a specific votehead budget (e.g., RMI, EWC).
  * Check budget availability before allowing a voucher to save.
  * A "Commitment" must reduce the "Available Balance" of a votehead before actual cash leaves the bank.

### Feature 2: Full Cashbook
* **Objective:** Merge income (from `Receipting`) and expenses (from `Payment Vouchers`) into a standard multi-column school cashbook.
* **Rules:**
  * Pull data directly from the ledger stores. Do not create a separate independent tracking file.
  * Must separate transactions by funding source (e.g., Ministry funding vs. Parent Fees).
  * Filterable by Date Range, Term, and Bank Account (National Bank A/C: 0121054619700).

### Feature 3: Trial Balance & Financial Statements
* **Objective:** Generate standard MoE (Ministry of Education) financial reports.
* **Rules:**
  * **Trial Balance:** Aggregates all ledger balances into Debit and Credit columns. Must balance perfectly.
  * **Financial Statements:** Generate Income & Expenditure statements and Balance Sheets.
  * **Exporting:** Hook into the existing export service to support `.csv` and `.xlsx` formats for these new reports.

### Feature 4: System Audit Trail
* **Objective:** Log all critical operations for school audit compliance.
* **Rules:**
  * Create an internal logging utility that records: Timestamp, Action (e.g., Voucher Created, Receipt Reprinted), and User/System flag.
  * Ensure this log is write-only; logs must never be editable or removable via the UI.

---

## 3. Development & Testing Workflow

Before submitting a Pull Request, you must guarantee that V1 features remain completely unaffected.

1. **Verify V1 Base:** Run the app using `mvn clean javafx:run` and verify that student imports, 2026 boarding fee structures, and auto-allocation work exactly as before.
2. **Automated Testing:** Run `mvn test` before writing code. Your new V2 code must include automated unit tests under `src/test/java` for any new service or accounting logic.
3. **Database Changes:** If your features require new tables (e.g., `payment_vouchers`), write a clean database migration or programmatic setup script inside `com.schaccs.store` that executes safely without wiping existing student or receipt tables.
