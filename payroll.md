For a Kenyan secondary school like Friends School Kikai Boys, the payroll feature is a high-risk compliance zone. Unlike public service teachers paid directly by the Teachers Service Commission (TSC), the school payroll manages Board of Management (BOM) teachers and Support Staff (lab technicians, cooks, security, groundsperson) paid directly from the school’s internal Personal Emolument (PE) votehead.
Here are the critical real-world use cases, operational "what-if" questions, and exact financial resolutions that the ThorCash Payroll module must handle to be auditable.
------------------------------
## 1. Statutory Compliance & Deductions (KRA, SHIF, NSSF)
Kenyan payroll laws change frequently. The system must process deductions exactly according to statutory frameworks or risk heavy fines from the Kenya Revenue Authority (KRA).
## Q: How does the system handle statutory updates like the Social Health Insurance Fund (SHIF) or housing levies dynamically?

* ThorCash Answer: The system separates the Payroll Calculation Engine from tax rate variables. The application architecture includes a StatutoryConfig entity in the database storing variables for:
* PAYE Tier Rates (under the relevant Finance Act).
   * SHIF Deduction: Scaled at exactly 2.75% of gross salary.
   * Affordable Housing Levy (AHL): Calculated at 1.5% from the employee and a matching 1.5% from the employer.
   * NSSF (Tier I & Tier II): Split based on current pensionable salary ceilings.
   * Resolution: When processing monthly payroll, the engine reads these parameters dynamically. If the government adjusts a rate mid-year, the bursar updates the configurations in the Settings Module without modifying a single line of backend source code.

## Q: What if a support staff member earns below the legal minimum wage or the tax threshold? Does the system still deduct PAYE and Levies?

* ThorCash Answer: The engine applies localized logical floors. If a casual laborer's gross income falls below the personal relief threshold (e.g., KSh 24,000 monthly), the engine automatically computes PAYE as KSh 0 and applies the KSh 2,400 monthly Personal Tax Relief to clear liability. However, it still enforces the absolute 2.75% SHIF and 1.5% Housing Levy deductions, as these regulations do not have a minimum income floor.

------------------------------
## 2. Votehead Starvation & Budget Cross-Cutting
Payroll funding in public schools is highly volatile and bound to specific accounts.

                  ┌──────────────────────────────┐
                  │ Total Monthly Payroll Run    │
                  └──────────────┬───────────────┘
                                 │
                   [Check Balance of PE Ledger]
                                 │
         ┌───────────────────────┴───────────────────────┐
         ▼ INSUFFICIENT                                  ▼ SUFFICIENT
┌─────────────────────────────────┐             ┌─────────────────────────────────┐
│ Flag Overrun & Require Inter-   │             │ Post Credit: Bank Account       │
│ Votehead Authorization Journal  │             │ Post Debit: PE Expense Ledger   │
└─────────────────────────────────┘             └─────────────────────────────────┘

## Q: What happens if the monthly payroll costs KSh 350,000, but the Personal Emolument (PE) votehead ledger only has KSh 120,000 collected?

* ThorCash Answer: The system will flag a Budget Overrun Warning during payroll finalization. It will prevent the bursar from generating bank remittance files until an authorization step is logged. The bursar must post an internal Inter-Votehead Authorization Journal Entry (e.g., borrowing funds temporarily from the Administration or Activity surplus ledgers). This ensures the General Ledger accurately reflects that the school is in technical debt to its own sub-accounts.

------------------------------
## 3. Staff Advances, Loans, and Deductions## Q: If a teacher takes a school salary advance of KSh 10,000 in the middle of the term, how is this tracked and recovered without breaking the ledger?

* ThorCash Answer: When an advance is issued via a payment voucher, the system debits Asset: Staff Advances Receivable and credits Asset: Bank Account. In the payroll module, this employee's profile is assigned a Recurring Recovery Rule.
* When the month-end payroll script executes, the engine pulls all active recovery rules, lists the KSh 10,000 under Non-Statutory Deductions, drops the net salary by that amount, and automatically posts a journal entry crediting Asset: Staff Advances Receivable back to zero.

## Q: What if a staff member has multiple court-ordered deductions, HELB student loans, or welfare contributions that exceed their net pay?

* ThorCash Answer: ThorCash enforces the Kenyan One-Third Rule (Employment Act, Section 19). The engine validates that total deductions do not reduce an employee's take-home pay to less than one-third of their basic salary. If a new deduction violates this rule, the payroll run will throw a validation block: "Cannot process payroll: Employee net pay falls below 1/3 basic pay limit." The bursar must manually defer a non-statutory deduction (like a welfare contribution) before the run is approved.

------------------------------
## 4. Mid-Month Hiring, Departures, and Absences## Q: If a new BOM science teacher is employed on the 18th of the month, does the system pay them a full month's salary?

* ThorCash Answer: The system includes a Proration Selector inside the monthly payroll sheet. If the employee’s activation date falls mid-month, the system calculates their pay based on days worked:
$$\text{Prorated Pay} = \left(\frac{\text{Basic Salary}}{\text{Total Days in Month}}\right) \times \text{Days Active}$$ 
* Statutory deductions like SHIF and AHL are then calculated directly against this newly prorated gross amount rather than the full contract amount.

## Q: How does the system handle unpaid leave or disciplinary suspensions without altering the baseline employment contract details?

* ThorCash Answer: The bursar creates a Payroll Exception Entry for that specific month. Instead of modifying the employee's core contract record, the bursar inputs an Unpaid Leave Adjustment (Days). The engine calculates the corresponding daily deduction amount, labels it clearly on the employee's printable payslip as an "Unpaid Leave Deduction," and updates the double-entry postings accordingly.

------------------------------
## 5. Double-Entry Payroll Postings (The Accounting View)## Q: When payroll is closed, what exact entries hit the General Ledger?

* ThorCash Answer: The payroll validation process does not just print payslips; it converts the entire run into a unified cryptographic double-entry journal voucher.

| Ledger Account | Debit (KSh) | Credit (KSh) | Description |
|---|---|---|---|
| Expense: BOM Teacher Salaries (PE) | 350,000 | | Total Gross Pay |
| Expense: Employer AHL Contribution | 5,250 | | School's matching 1.5% |
| Liability: KRA PAYE Payable | | 45,000 | Deducted tax to remit by 9th |
| Liability: KRA AHL Payable | | 10,500 | Combined employee + employer |
| Liability: SHIF Payable | | 9,625 | Deducted medical levy |
| Liability: Net Salary Clearing | | 290,125 | Net cash to transfer to staff banks |


