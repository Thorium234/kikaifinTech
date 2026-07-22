package com.schaccs.store;

import com.schaccs.enums.AccountType;
import com.schaccs.enums.NormalBalance;
import com.schaccs.enums.StatementCategory;
import com.schaccs.model.finance.Account;
import com.schaccs.model.finance.FiscalYear;
import com.schaccs.model.finance.Budget;
import com.schaccs.model.finance.BudgetLine;
import com.schaccs.model.finance.AssetCategory;
import com.schaccs.model.finance.Asset;
import com.schaccs.model.finance.DepreciationSchedule;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.util.List;
import java.util.Optional;

public final class AccountStore {

    private static final AccountStore INSTANCE = new AccountStore();

    private final ObservableList<Account> accounts = FXCollections.observableArrayList();
    private final ObservableList<FiscalYear> fiscalYears = FXCollections.observableArrayList();
    private final ObservableList<Budget> budgets = FXCollections.observableArrayList();
    private final ObservableList<BudgetLine> budgetLines = FXCollections.observableArrayList();
    private final ObservableList<AssetCategory> assetCategories = FXCollections.observableArrayList();
    private final ObservableList<Asset> assets = FXCollections.observableArrayList();
    private final ObservableList<DepreciationSchedule> depreciationSchedules = FXCollections.observableArrayList();

    private AccountStore() {}

    public static AccountStore getInstance() { return INSTANCE; }

    public ObservableList<Account> getAccounts() { return accounts; }
    public ObservableList<FiscalYear> getFiscalYears() { return fiscalYears; }
    public ObservableList<Budget> getBudgets() { return budgets; }
    public ObservableList<BudgetLine> getBudgetLines() { return budgetLines; }
    public ObservableList<AssetCategory> getAssetCategories() { return assetCategories; }
    public ObservableList<Asset> getAssets() { return assets; }
    public ObservableList<DepreciationSchedule> getDepreciationSchedules() { return depreciationSchedules; }

    public Optional<Account> findAccountByCode(String code) {
        return accounts.stream().filter(a -> code.equals(a.getCode())).findFirst();
    }

    public Optional<FiscalYear> findFiscalYearByYear(int year) {
        return fiscalYears.stream().filter(fy -> fy.getYear() == year).findFirst();
    }

    public Optional<FiscalYear> findOpenFiscalYear() {
        return fiscalYears.stream().filter(FiscalYear::isOpen).findFirst();
    }

    public List<BudgetLine> findBudgetLinesByBudgetId(String budgetId) {
        return budgetLines.stream().filter(bl -> budgetId.equals(bl.getBudgetId())).toList();
    }

    public List<BudgetLine> findBudgetLinesByVoteheadCode(String voteheadCode) {
        return budgetLines.stream().filter(bl -> voteheadCode.equals(bl.getVoteheadCode())).toList();
    }

    public List<DepreciationSchedule> findSchedulesByAssetId(String assetId) {
        return depreciationSchedules.stream().filter(ds -> assetId.equals(ds.getAssetId())).toList();
    }

    public void seedDefaultAccounts() {
        if (!accounts.isEmpty()) return;
        int order = 0;
        for (AccountType type : AccountType.values()) {
            Account a = new Account();
            a.setCode(type.getCode());
            a.setName(type.getDisplayName());
            a.setAccountType(type);
            a.setNormalBalance(type.getNormalBalance());
            a.setStatementCategory(type.getStatementCategory());
            a.setActive(true);
            accounts.add(a);
        }
    }

    public void clear() {
        accounts.clear();
        fiscalYears.clear();
        budgets.clear();
        budgetLines.clear();
        assetCategories.clear();
        assets.clear();
        depreciationSchedules.clear();
    }
}
