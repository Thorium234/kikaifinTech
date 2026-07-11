package com.schaccs.store;

/**
 * Facade over in-memory stores used by services and UI.
 * All posting still goes through accounting engines / services.
 */
public final class AccountStore {

    private static final AccountStore INSTANCE = new AccountStore();

    private AccountStore() {
    }

    public static AccountStore getInstance() {
        return INSTANCE;
    }

    public StudentStore students() {
        return StudentStore.getInstance();
    }

    public FeeStructureStore fees() {
        return FeeStructureStore.getInstance();
    }

    public ReceiptStore receipts() {
        return ReceiptStore.getInstance();
    }

    public LedgerStore ledger() {
        return LedgerStore.getInstance();
    }

    public void clearAll() {
        students().clear();
        fees().clear();
        receipts().clear();
        ledger().clear();
        VoucherStore.getInstance().clear();
    }
}
