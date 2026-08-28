To design a truly robust financial system for Friends School Kikai Boys, we have to look past the "happy path" and stress-test the system with real-world scenarios, administrative edge cases, and hard accounting rules.
Here is an extensive breakdown of the exact user personas, real-world interactions, and tough questions ThorCash must answer.
------------------------------
## 1. The School Bursar (Daily Core Operations)
The Bursar handles the money, reconciles accounts, and deals with demanding parents and auditors.
## Use Case A: Payment Processing & Allocation Edge Cases

* The Situation: A parent arrives with a bank pay-in slip for KSh 25,000 in Term 1. The total Term 1 boarding fee is KSh 21,000.
* The Questions ThorCash Must Answer:
* "If the payment exceeds the Term 1 total, does the ReceiptAllocationEngine automatically push the remaining KSh 4,000 to Term 2 voteheads, or does it hold it as an unallocated overpayment?"
   * "What if a parent pays KSh 10,000 specifically demanding it only goes toward 'Boarding' because they don't want their child sent home, but the system's auto-allocation rules distribute it proportionally across all voteheads (EWC, RMI, Activity)? Can I override the auto-allocation manually?"
   * "What if a parent pays fees via the Pay Bill number but types the wrong Admission Number? When I find the error, how do I reverse or transfer that posted receipt without breaking the sequential receipt numbering sequence?"

## Use Case B: Day Scholar vs. Boarder Status Shifts

* The Situation: A student starts Term 1 as a day scholar (paying the KSh 5,500 lunch fee) but transfers into the boarding house mid-term.
* The Questions ThorCash Must Answer:
* "When I change the student's status from Day Scholar to Boarder in the registry, does the system automatically generate a retroactive debit invoice for the KSh 21,000 boarding fee, or does it calculate a pro-rated balance based on the weeks remaining?"
   * "If they already paid the KSh 5,500 lunch fee, does that money get absorbed into the general Boarding votehead, or does it remain as a separate line item?"
   * "What happens if I enter a payment for a day scholar, but the system accidentally applies the boarding votehead structure? How do I clear the false balance?"

------------------------------
## 2. The School Auditor & Accountant (Double-Entry & Trial Balance Integrity)
Auditors care about the integrity of the ledger. They look for imbalances, fraud, and broken accounting rules.
## Use Case C: Trial Balance & Ledger Reconciliation

* The Situation: It is the end of the term, and the Bursar prints out the Trial Balance to present to the Board of Management.
* The Questions ThorCash Must Answer:
* "What if the Trial Balance does not balance? Where is the audit view showing me which specific transaction id caused a debit-credit mismatch in the AccountingEngine?"
   * "When a fee is charged at the start of the year, does the system debit 'Student Accounts Receivable' and credit individual 'Votehead Revenue' accounts? If a student drops out or transfers away, how do we write off that bad debt without deleting historical registry logs?"
   * "If we pull a report for the previous academic year, does the system safely isolate those balances, or do uncleared arrears bleed into the current year's Trial Balance as a messy, combined lump sum?"

------------------------------
## 3. The School Principal (Executive Oversight & Policy Enforcement)
The Principal uses reports to make strategic decisions: hiring Board of Management (BOM) teachers, funding sports, or dealing with the Ministry of Education.
## Use Case D: Defaulter Management & Financial Planning

* The Situation: The school needs to buy food for the boarding section, but the bank account is running low. The Principal calls the Bursar.
* The Questions ThorCash Must Answer:
* "Show me a list of all students who owe more than KSh 15,000 for Term 2, filtered by their Form Class and Stream, so we can issue targeted fee reminder letters."
   * "If I look at the Votehead Summary report, exactly how much money have we collected specifically for 'Personal Emolument' (used to pay BOM teachers)? Is that cash actually sitting in the bank, or has it been used to cover overruns in the 'Boarding' (food) votehead?"
   * "Can the system generate an Ageing Report showing how long arrears have been pending (e.g., 0–30 days, 31–90 days, or over a year) so we know which balances are from chronic defaulters who have already completed Form 4?"

------------------------------
## 4. System Administrators & Support Techs (Data Integrity & Edge Cases)
The technical team managing the SQLite database behind the scenes.
## Use Case E: Bulk Data Discrepancies & Local Storage Glitches

* The Situation: The Bursar imports a CSV file containing 300 new Form 1 students, but the power goes out mid-import, or the file contains formatted bugs.
* The Questions ThorCash Must Answer:
* "If the Excel import sheet contains a student with an Admission Number that already exists in schaccs.db, does the system skip them, overwrite the old data, or crash entirely?"
   * "What if the user types a name with special characters or a phone number missing the Kenyan prefix (e.g., entering 07... instead of +254...)? Does validation block it before it hits the SQLite layer?"
   * "Since the database sits locally on the Bursar's machine (~/.schaccs/schaccs.db), what happens if two school computers try to access the same database file over a local network share at the same time? How does ThorCash handle file locking and concurrent edits?"

------------------------------
## Summary of System Validation Checklists
To make ThorCash production-ready for Kenyan schools, the codebase must programmatically answer these "What If" scenarios:

[Transaction Triggered] 
       │
       ▼
 ┌───────────┐      NO      ┌──────────────────────────────────────────┐
 │ Balanced? │─────────────►│ Block Commit & Log Error to Audit Trail  │
 └───────────┘              └──────────────────────────────────────────┘
       │ YES
       ▼
 ┌───────────┐      YES     ┌──────────────────────────────────────────┐
 │ Day Boy?  │─────────────►│ Apply Flat KSh 5,500 Lunch Fee Only      │
 └───────────┘              └──────────────────────────────────────────┘
       │ NO
       ▼
 ┌─────────────────────────────────────────────────────────────────────┐
 │ Cascade to 2026 Boarding Structure (Boarding -> EWC -> PE -> RMI)   │
 └─────────────────────────────────────────────────────────────────────┘

To prove that ThorCash is a high-utility financial management system rather than a generic database template, it must robustly resolve these messy, non-linear accounting failures that Kenyan school bursars face daily.
Here is how ThorCash programmatically answers and resolves every tough user scenario, featuring an architectural deep dive into how the AccountingEngine handles a Trial Balance imbalance.
------------------------------
## 1. Payment Processing & Allocation Edge Cases## Q: If a payment exceeds the Term 1 total, does the engine push the remainder to Term 2, or hold it as an unallocated overpayment?

* ThorCash Solution: The ReceiptAllocationEngine processes payments chronologically based on the active Academic Year configuration. If a parent pays KSh 25,000 against a Term 1 bill of KSh 21,000, the engine satisfies all Term 1 votehead sub-accounts to zero. The remaining KSh 4,000 immediately cascades into the Term 2 votehead allocations (starting with Boarding, then EWC, etc.). It is never left loose as "unallocated cash" unless all terms for that year are completely paid.

## Q: Can a bursar override the auto-allocation if a parent demands a payment go only to a specific votehead (e.g., Boarding)?

* ThorCash Solution: By default, the system enforces proportional or prioritized cascading rules to prevent votehead starvation (e.g., spending all money on food while utilities go unpaid). However, the UI includes a "Manual Override Account Flag". When toggled, the bursar can manually input specific values against individual voteheads. The AccountingEngine still double-validates that the sum of these manual inputs exactly equals the total value of the bank pay-in slip before committing the transaction.

## Q: How do you reverse or transfer a receipt posted to the wrong student without breaking the sequential receipt numbering sequence?

* ThorCash Solution: Per strict auditing guidelines, posted receipts can never be deleted or modified. To fix a misapplied payment, the bursar uses a "Reverse & Reissue" workflow.


   1. The system generates a Credit Note (CN) transaction that mirrors the exact votehead layout of the bad receipt, debiting the wrong student's revenue accounts and crediting their accounts receivable.
   2. This reversal is assigned a completely new, unique document number in the audit sequence, leaving the original receipt intact.
   3. The funds are then safely re-posted to the correct student's account under a new receipt number.

------------------------------
## 2. Day Scholar vs. Boarder Status Shifts## Q: When a student switches from Day Scholar to Boarder mid-term, does the system auto-invoice the full fee or prorate it?

* ThorCash Solution: The change in status triggers an internal billing lifecycle event. ThorCash does not delete the old invoice. Instead, it checks the System Settings for the school's proration policy. If configured for full fees, it posts a supplementary debit invoice for the difference (KSh 21,000 Boarding Total - KSh 5,500 Paid Lunch = KSh 15,500 due). If set to prorate, the bursar inputs the effective week of the term, and the system divides the boarding votehead dynamically before posting the adjustment ledger entry.

## Q: If a day scholar transitions to boarding, what happens to their prepaid KSh 5,500 lunch fee?

* ThorCash Solution: The KSh 5,500 paid for lunch is already tied to the Activity/Lunch ledger account. When the student's registry profile updates to "Boarder", ThorCash leaves that cash matching historical daily collections. However, it offsets the newly generated boarding obligation by applying the KSh 5,500 as an early credit, lowering the net amount the parent must deposit at the National Bank branch to clear Term 1.

------------------------------
## 3. The Core Emergency: Trial Balance Imbalance Resolution## Q: What if the Trial Balance does not balance? Where is the view showing the debit-credit mismatch?
When a Trial Balance is broken, it means a software bug, an unhandled runtime crash, or data corruption occurred where a debit was written but its matching credit failed to commit.

[System Check] -> Total Debits ≠ Total Credits -> TRIPPED ALARM
                                                      │
                                                      ▼
[Resolution] -> Scan Double-Entry Transaction Logs -> Identify Orphaned Entry -> Isolate to Ledger Audit View

## How ThorCash Handles This in the Codebase:

   1. The Double-Entry Pattern Constraint: The AccountingEngine operates inside a strict database transaction wrapper. Every accounting entry is bundled as a Transaction object containing an array of at least two LedgerEntry items (one debit, one credit). The database will refuse to save the bundle if debits.sum() != credits.sum().
   2. The "Orphaned Entry" Identification View: If a local hardware failure or forced shutdown bypasses this application layer safety net, the Reports → Trial Balance view triggers an internal integrity scan. It runs an asymmetrical hash check against all journal entries grouped by their unique transaction_id.
   3. The Audit Action: Any transaction ID where the summation does not equal zero is instantly flagged in bright red on the interface. The system provides an "Isolate Discrepancy" button, allowing the auditor to see the exact moment the ledger broke (e.g., Transaction #1042: Debited Cash Book KSh 10,000, but failed to credit Student Receivable due to unexpected App Termination). This tells the auditor exactly which entry needs a corrective journal post.

------------------------------
## 4. Executive Oversight & Policy Enforcement## Q: How can the Principal see who owes more than KSh 15,000 for Term 2, filtered by Form and Stream?

* ThorCash Solution: The Defaulters Report module does not just calculate a grand total balance. It breaks down debts by targeted billing cycles. The bursar or principal can set granular search parameters: Form: Form 3, Stream: Blue, Term: Term 2, Threshold: > 15,000. The system pulls this cleanly by subtracting the Term 2 localized allocations from the term-specific invoices, generating an actionable checklist for the principal to print or export to Excel.

## Q: How does the Principal know if "Personal Emolument" collections are sitting in the bank or were illegally consumed by Boarding overruns?

* ThorCash Solution: This is answered by the separation of the Cash Book (Asset) from the Votehead Subsidiary Ledgers (Equity/Revenue). While all money physically pools inside the National Bank account, the Votehead Summary Report maps the current balance of each votehead side-by-side with actual cash balances. If the "Boarding" votehead balance is negative (overdrawn due to food purchases), the system explicitly shows that it is temporarily borrowing cash belonging to the "Personal Emolument" account, warning the administration of a potential cash flow hazard before BOM teachers' salaries are delayed.

------------------------------
## 5. Technical Bulk Imports & Local SQLite Failures## Q: What happens if a CSV import contains a duplicate Admission Number or hits a power outage mid-way?

* ThorCash Solution:
* Duplicate Protection: The Admission Number field is set as a PRIMARY KEY UNIQUE constraint within the SQLite database. If an import sheet attempts to reload an existing admission number, the system catches the database constraint exception, skips the row, and appends the failure reason to an on-screen "Import Error Log" window instead of crashing the process.
* Power Outage/Atomic Transactions: The student bulk import runs entirely inside a single SQLite Transaction. If the computer shuts down at student 150 out of 300, no partial data is saved. When the machine restarts, the database auto-rolls back to its exact state before the import started, avoiding corrupted partial lists.

## Q: How does ThorCash handle two computers accessing schaccs.db over a local network share simultaneously?

* ThorCash Solution: Because SQLite is an embedded database engine, it relies on file-system level locks. If two instances of ThorCash attempt to write to ~/.schaccs/schaccs.db at the exact same millisecond, SQLite returns a SQLITE_BUSY error. ThorCash resolves this via a built-in Connection Pool and Retry Policy inside the config/ layer. It safely waits for a few milliseconds for the lock to release. If the lock persists, it notifies the secondary user with a non-crashing alert dialog: "Database is temporarily busy processing another transaction. Please try again in a moment."



