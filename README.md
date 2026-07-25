# SCHACCS — School Accounting System (V1)

**School:** Friends School Kikai Boys Secondary School  
**Location:** P.O. Box 345-50202, Chwele  
**Ministry:** Republic of Kenya, Ministry of Education

Accounting-first fee management for Kenyan secondary schools. Version 1 focuses on student registry, fee structures, receipting with automatic votehead allocation, core double-entry postings, and bursar reports.

## Requirements

- JDK 25+
- Maven 3.9+
- JavaFX (resolved via Maven)

## Run

```bash
mvn clean javafx:run
```

Or compile then run:

```bash
mvn clean compile
mvn javafx:run
```

## Student import

You can bulk import students from the **Students** module using either:

- `.csv`
- `.xlsx`

Recommended columns:

```text
Admission Number, Full Name, Gender, Form Class, Stream, Boarding Status,
Parent Name, Guardian Key, Phone, UPI, Academic Year, Year Of Admission, Student Status
```

Column names are matched flexibly, so common variants like `Adm No`, `Name`, `Class`, and `Boarding` also work.

You can also download a ready-made import template from the **Students** module.

## Exporting

The app can export students and report outputs to:

- `.csv`
- `.xlsx`

Supported export points include student registry, fee balances, defaulters, daily collection, votehead summary, ageing, trial balance, statements, and receipts.

## Printing

Receipts can be previewed and printed from:

- **Receipting** after posting a payment
- **Reports → Receipt Reprint** for existing receipts

## Testing

Run the automated test suite with:

```bash
mvn test
```

## Data storage

SQLite database (auto-created on first run):

```text
~/.schaccs/schaccs.db
```

- First launch seeds sample school data, then persists it.
- Later launches load from the database (receipts, students, ledgers, vouchers survive restarts).
- Saves run after student/receipt/voucher/settings changes and on exit.

## V1 Modules

| Module | Description |
|--------|-------------|
| **Dashboard** | KPIs: collection, outstanding, students, receipts |
| **Students** | Registry — add, edit, search, boarding status |
| **Fee Structure** | Year/term voteheads (2026 boarding structure preloaded) |
| **Receipting** | Search student → pay → auto-allocate voteheads → post ledger |
| **Reports** | Fee balances, defaulters, daily collection, student statement, votehead summary |
| **Settings** | School profile, academic year, receipt sequence |

## Architecture

```
com.schaccs
  config / enums / model / store / service / accounting
  validation / util / ui (layout, views, components)
```

All money movements go through `AccountingEngine` and `ReceiptAllocationEngine`. The UI never updates balances directly.

## School fee data (2026 Boarding)

| Vote Head | Term 1 | Term 2 | Term 3 | Total |
|-----------|--------|--------|--------|-------|
| Boarding | 14,000 | 6,500 | 4,000 | 24,500 |
| EWC | 1,000 | 1,000 | 500 | 2,500 |
| Personal Emolument | 2,000 | 1,000 | 500 | 3,500 |
| RMI | 1,000 | 1,000 | 500 | 2,500 |
| Administration | 1,000 | 1,000 | 500 | 2,500 |
| L.T & T | 1,000 | 1,000 | 500 | 2,500 |
| Activity | 1,000 | 1,000 | 500 | 2,500 |
| **Total** | **21,000** | **12,500** | **7,000** | **40,500** |

Day scholars: lunch KSh 5,500 (Term 1).

## Bank details

- National Bank, Bungoma Branch  
- A/C: 0121054619700  
- Pay Bill: 7230546  
- Account: 1260057495  

> Note: School policy — no cash except bank pay-in slip approved by the Principal.

## Version 2 (planned)

Payment vouchers, commitment register, full cashbook, trial balance, financial statements, audit trail.
