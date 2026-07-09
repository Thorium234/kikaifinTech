package com.schaccs.service.finance;

import com.schaccs.accounting.AccountingEngine;
import com.schaccs.enums.AccountType;
import com.schaccs.model.finance.FinancialTransaction;
import com.schaccs.store.LedgerStore;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public class AccountingService {

    private final AccountingEngine engine;
    private final LedgerStore ledgerStore;

    public AccountingService() {
        this(new AccountingEngine(), LedgerStore.getInstance());
    }

    public AccountingService(AccountingEngine engine, LedgerStore ledgerStore) {
        this.engine = engine;
        this.ledgerStore = ledgerStore;
    }

    public BigDecimal balance(AccountType type) {
        return engine.accountBalance(type);
    }

    public Map<AccountType, BigDecimal> allBalances() {
        return ledgerStore.getAccountBalances();
    }

    public List<FinancialTransaction> transactions() {
        return ledgerStore.getTransactions();
    }
}
