package com.schaccs.service.procurement;

import com.schaccs.accounting.AccountingEngine;
import com.schaccs.enums.AccountType;
import com.schaccs.enums.TransactionType;
import com.schaccs.model.finance.JournalEntry;
import com.schaccs.model.procurement.Contract;
import com.schaccs.model.procurement.ContractMilestone;

import java.math.BigDecimal;

/**
 * Posts procurement journal entries to the general ledger.
 *
 * When goods are received:
 *   DEBIT  Expense/Asset Account (goods value)
 *   CREDIT ACCOUNTS_PAYABLE (goods value)
 *
 * When a supplier invoice is booked:
 *   DEBIT  ACCOUNTS_PAYABLE (invoice value)
 *   CREDIT CASH_AT_BANK (invoice value)
 *
 * When a supplier is paid:
 *   DEBIT  ACCOUNTS_PAYABLE (payment value)
 *   CREDIT CASH_AT_BANK (payment value)
 *
 * This ensures balanced double-entry and no procurement transaction
 * bypasses the accounting engine.
 */
public class ProcurementAccountingIntegration {

    private final AccountingEngine accountingEngine;

    public ProcurementAccountingIntegration() {
        this(new AccountingEngine());
    }

    public ProcurementAccountingIntegration(AccountingEngine accountingEngine) {
        this.accountingEngine = accountingEngine;
    }

    /**
     * Post a journal entry for goods received against a contract.
     * DEBIT: the relevant expense/asset account (resolved from accountTypeCode)
     * CREDIT: ACCOUNTS_PAYABLE
     */
    public void postGoodsReceived(String contractId, String supplierName,
                                  BigDecimal amount, String accountTypeCode,
                                  String description) {
        AccountType expenseAccount = resolveAccountType(accountTypeCode);

        JournalEntry journal = new JournalEntry();
        journal.setDate(java.time.LocalDate.now());
        journal.setReference("GRN-" + contractId.substring(0, Math.min(8, contractId.length())));
        journal.setNarration("Goods received \u2014 " + supplierName + " \u2014 " + description);

        // DEBIT: Expense/Asset account
        journal.addLine(expenseAccount, accountTypeCode,
                amount, BigDecimal.ZERO,
                "Goods received \u2014 " + supplierName + " \u2014 " + description);

        // CREDIT: Accounts Payable
        journal.addLine(AccountType.ACCOUNTS_PAYABLE, "AP",
                BigDecimal.ZERO, amount,
                "Goods received liability \u2014 " + supplierName);

        accountingEngine.postTransaction(journal, TransactionType.PROCUREMENT_GOODS_RECEIVED,
                null, null, contractId);
    }

    /**
     * Post a journal entry for a supplier invoice received.
     * DEBIT: ACCOUNTS_PAYABLE
     * CREDIT: CASH_AT_BANK
     */
    public void postSupplierInvoice(String contractId, String supplierName,
                                    BigDecimal amount, String voteheadCode) {
        JournalEntry journal = new JournalEntry();
        journal.setDate(java.time.LocalDate.now());
        journal.setReference("INV-SUP-" + contractId.substring(0, Math.min(8, contractId.length())));
        journal.setNarration("Supplier invoice \u2014 " + supplierName);

        // DEBIT: Accounts Payable
        journal.addLine(AccountType.ACCOUNTS_PAYABLE, "AP",
                amount, BigDecimal.ZERO,
                "Supplier invoice \u2014 " + supplierName);

        // CREDIT: Cash at Bank
        journal.addLine(AccountType.CASH_AT_BANK, voteheadCode,
                BigDecimal.ZERO, amount,
                "Supplier invoice payment \u2014 " + supplierName);

        accountingEngine.postTransaction(journal, TransactionType.PROCUREMENT_INVOICE,
                null, null, contractId);
    }

    /**
     * Post a journal entry for a supplier payment made.
     * DEBIT: ACCOUNTS_PAYABLE
     * CREDIT: CASH_AT_BANK
     */
    public void postSupplierPayment(String contractId, String supplierName,
                                    BigDecimal amount, String voteheadCode) {
        JournalEntry journal = new JournalEntry();
        journal.setDate(java.time.LocalDate.now());
        journal.setReference("PAY-SUP-" + contractId.substring(0, Math.min(8, contractId.length())));
        journal.setNarration("Supplier payment \u2014 " + supplierName);

        // DEBIT: Accounts Payable
        journal.addLine(AccountType.ACCOUNTS_PAYABLE, "AP",
                amount, BigDecimal.ZERO,
                "Supplier payment \u2014 " + supplierName);

        // CREDIT: Cash at Bank
        journal.addLine(AccountType.CASH_AT_BANK, voteheadCode,
                BigDecimal.ZERO, amount,
                "Supplier payment disbursement \u2014 " + supplierName);

        accountingEngine.postTransaction(journal, TransactionType.PROCUREMENT_PAYMENT,
                null, null, contractId);
    }

    /**
     * Post a journal entry for a milestone payment on a contract.
     * Delegates to postSupplierPayment using the milestone amount and contract details.
     */
    public void postMilestonePayment(Contract contract, ContractMilestone milestone) {
        postSupplierPayment(
                contract.getId(),
                contract.getSupplierId(),
                milestone.getAmount(),
                "MILESTONE");
    }

    /**
     * Resolve an AccountType from a code or name string.
     * Tries exact name match first, then code match.
     * Falls back to SUPPLIES if no match is found.
     */
    AccountType resolveAccountType(String code) {
        if (code == null || code.trim().isEmpty()) {
            return AccountType.SUPPLIES;
        }

        // Try matching by enum name (e.g. "SUPPLIES", "UTILITIES")
        for (AccountType type : AccountType.values()) {
            if (type.name().equalsIgnoreCase(code.trim())) {
                return type;
            }
        }

        // Try matching by code (e.g. "SUPPLY", "UTIL")
        for (AccountType type : AccountType.values()) {
            if (type.getCode().equalsIgnoreCase(code.trim())) {
                return type;
            }
        }

        // Try matching by display name (e.g. "Supplies", "Utilities")
        for (AccountType type : AccountType.values()) {
            if (type.getDisplayName().equalsIgnoreCase(code.trim())) {
                return type;
            }
        }

        return AccountType.SUPPLIES;
    }
}
