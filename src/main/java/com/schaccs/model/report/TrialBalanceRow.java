package com.schaccs.model.report;

import com.schaccs.config.CurrencyConfig;
import com.schaccs.enums.AccountType;

import java.math.BigDecimal;

public class TrialBalanceRow {

    private final AccountType accountType;
    private final String accountName;
    private final BigDecimal debit;
    private final BigDecimal credit;

    public TrialBalanceRow(AccountType accountType, BigDecimal debit, BigDecimal credit) {
        this.accountType = accountType;
        this.accountName = accountType.getDisplayName();
        this.debit = CurrencyConfig.money(debit);
        this.credit = CurrencyConfig.money(credit);
    }

    public AccountType getAccountType() {
        return accountType;
    }

    public String getAccountName() {
        return accountName;
    }

    public BigDecimal getDebit() {
        return debit;
    }

    public BigDecimal getCredit() {
        return credit;
    }
}
