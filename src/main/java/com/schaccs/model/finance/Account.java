package com.schaccs.model.finance;

import com.schaccs.enums.AccountType;
import com.schaccs.enums.NormalBalance;
import com.schaccs.enums.StatementCategory;

import java.util.UUID;

public class Account {

    private final String id;
    private String code;
    private String name;
    private String parentId;
    private AccountType accountType;
    private NormalBalance normalBalance;
    private StatementCategory statementCategory;
    private boolean active = true;
    private boolean isControlAccount;

    public Account() {
        this.id = UUID.randomUUID().toString();
    }

    private Account(String id) {
        this.id = id != null ? id : UUID.randomUUID().toString();
    }

    public static Account withId(String id) {
        return new Account(id);
    }

    public String getId() { return id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getParentId() { return parentId; }
    public void setParentId(String parentId) { this.parentId = parentId; }
    public AccountType getAccountType() { return accountType; }
    public void setAccountType(AccountType accountType) {
        this.accountType = accountType;
        if (accountType != null) {
            this.normalBalance = accountType.getNormalBalance();
            this.statementCategory = accountType.getStatementCategory();
        }
    }
    public NormalBalance getNormalBalance() { return normalBalance; }
    public void setNormalBalance(NormalBalance normalBalance) { this.normalBalance = normalBalance; }
    public StatementCategory getStatementCategory() { return statementCategory; }
    public void setStatementCategory(StatementCategory statementCategory) { this.statementCategory = statementCategory; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public boolean isControlAccount() { return isControlAccount; }
    public void setControlAccount(boolean controlAccount) { isControlAccount = controlAccount; }

    @Override
    public String toString() { return code + " - " + name; }
}
