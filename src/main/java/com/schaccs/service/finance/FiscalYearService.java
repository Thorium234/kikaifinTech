package com.schaccs.service.finance;

import com.schaccs.accounting.DoubleEntryEngine;
import com.schaccs.config.AppConfig;
import com.schaccs.config.CurrencyConfig;
import com.schaccs.enums.AccountType;
import com.schaccs.enums.TransactionType;
import com.schaccs.model.finance.FinancialTransaction;
import com.schaccs.model.finance.FiscalYear;
import com.schaccs.model.finance.JournalEntry;
import com.schaccs.model.finance.LedgerEntry;
import com.schaccs.store.AccountStore;
import com.schaccs.store.LedgerStore;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class FiscalYearService {

    private final AccountStore accountStore;
    private final LedgerStore ledgerStore;
    private final DoubleEntryEngine doubleEntryEngine;

    public FiscalYearService() {
        this(AccountStore.getInstance(), LedgerStore.getInstance(), new DoubleEntryEngine());
    }

    public FiscalYearService(AccountStore accountStore, LedgerStore ledgerStore, DoubleEntryEngine doubleEntryEngine) {
        this.accountStore = accountStore;
        this.ledgerStore = ledgerStore;
        this.doubleEntryEngine = doubleEntryEngine;
    }

    public FiscalYear openFiscalYear(int year) {
        com.schaccs.util.RoleGuard.requireFinanceAdmin();
        Optional<FiscalYear> existing = accountStore.findFiscalYearByYear(year);
        if (existing.isPresent()) {
            throw new IllegalStateException("Fiscal year " + year + " already exists");
        }

        FiscalYear fy = new FiscalYear();
        fy.setYear(year);
        fy.setStartDate(LocalDate.of(year, 1, 1));
        fy.setEndDate(LocalDate.of(year, 12, 31));
        fy.setOpen(true);
        fy.setClosed(false);

        accountStore.getFiscalYears().add(fy);
        return fy;
    }

    public FiscalYear getCurrentFiscalYear() {
        return accountStore.findOpenFiscalYear()
                .orElseThrow(() -> new IllegalStateException("No open fiscal year found"));
    }

    public boolean isTransactionAllowed(LocalDate date) {
        if (date == null) {
            return true;
        }
        return accountStore.findOpenFiscalYear()
                .map(fy -> !date.isBefore(fy.getStartDate()) && !date.isAfter(fy.getEndDate()))
                .orElse(true);
    }

    public void closeFiscalYear(FiscalYear fy) {
        com.schaccs.util.RoleGuard.requireFinanceAdmin();
        if (!fy.isOpen()) {
            throw new IllegalStateException("Fiscal year " + fy.getYear() + " is not open");
        }

        List<FinancialTransaction> lateTransactions = ledgerStore.getTransactions().stream()
                .filter(t -> t.getDate().isAfter(fy.getEndDate()))
                .collect(Collectors.toList());
        if (!lateTransactions.isEmpty()) {
            throw new IllegalStateException("Found " + lateTransactions.size()
                    + " transaction(s) dated after fiscal year end " + fy.getEndDate()
                    + ". Close cannot proceed.");
        }

        BigDecimal totalIncome = CurrencyConfig.zero();
        BigDecimal totalExpenses = CurrencyConfig.zero();

        for (AccountType type : AccountType.values()) {
            if (type.getStatementCategory() == com.schaccs.enums.StatementCategory.INCOME_EXPENDITURE) {
                BigDecimal balance = ledgerStore.getAccountBalance(type);
                if (type.isCreditNormal()) {
                    totalIncome = totalIncome.add(balance);
                } else {
                    totalExpenses = totalExpenses.add(balance);
                }
            }
        }

        BigDecimal netIncome = totalIncome.subtract(totalExpenses);

        JournalEntry closingJournal = new JournalEntry();
        closingJournal.setDate(fy.getEndDate());
        closingJournal.setReference("FY-CLOSE-" + fy.getYear());
        closingJournal.setNarration("Closing entries for fiscal year " + fy.getYear());

        for (AccountType type : AccountType.values()) {
            if (type.getStatementCategory() == com.schaccs.enums.StatementCategory.INCOME_EXPENDITURE) {
                BigDecimal balance = ledgerStore.getAccountBalance(type);
                if (balance.compareTo(CurrencyConfig.zero()) != 0) {
                    if (type.isCreditNormal()) {
                        closingJournal.addLine(type, type.getCode(), balance, CurrencyConfig.zero(),
                                "Close " + type.getDisplayName());
                    } else {
                        closingJournal.addLine(type, type.getCode(), CurrencyConfig.zero(), balance,
                                "Close " + type.getDisplayName());
                    }
                }
            }
        }

        if (netIncome.compareTo(CurrencyConfig.zero()) >= 0) {
            closingJournal.addLine(AccountType.RETAINED_EARNINGS, "RE", CurrencyConfig.zero(), netIncome,
                    "Net income for FY " + fy.getYear());
        } else {
            BigDecimal loss = netIncome.abs();
            closingJournal.addLine(AccountType.RETAINED_EARNINGS, "RE", loss, CurrencyConfig.zero(),
                    "Net loss for FY " + fy.getYear());
        }

        String user = AppConfig.getInstance().getCurrentUser();
        doubleEntryEngine.postJournal(closingJournal, user, null, null, null, TransactionType.JOURNAL);

        fy.setOpen(false);
        fy.setClosed(true);
        fy.setClosedAt(LocalDateTime.now());
        fy.setClosedBy(user);

        int nextYear = fy.getYear() + 1;
        Optional<FiscalYear> nextFyOpt = accountStore.findFiscalYearByYear(nextYear);
        if (nextFyOpt.isEmpty()) {
            openFiscalYear(nextYear);
        } else {
            FiscalYear nextFy = nextFyOpt.get();
            nextFy.setOpen(true);
            nextFy.setClosed(false);
        }
    }
}
