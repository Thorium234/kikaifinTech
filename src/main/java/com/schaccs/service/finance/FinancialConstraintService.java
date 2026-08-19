package com.schaccs.service.finance;

import com.schaccs.config.CurrencyConfig;
import com.schaccs.enums.AccountType;
import com.schaccs.enums.ContractStatus;
import com.schaccs.model.procurement.Contract;
import com.schaccs.model.procurement.ContractMilestone;
import com.schaccs.store.LedgerStore;
import com.schaccs.store.ProcurementStore;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Centralises financial constraint enforcement for Kenyan public secondary school
 * accounting:
 * <ul>
 *   <li>Negative cash prevention — blocks payouts that would overdraw ring-fenced bank accounts</li>
 *   <li>Closed fiscal year lock — blocks transactions in closed periods</li>
 *   <li>Contract value overflow guard — prevents disbursements exceeding contract value</li>
 *   <li>Ring-fencing enforcement — prevents cross-subsidization between government
 *       capitation, infrastructure grants, and parent-funded fee accounts</li>
 * </ul>
 */
public final class FinancialConstraintService {

    private static final FinancialConstraintService INSTANCE = new FinancialConstraintService();

    private final FiscalYearService fiscalYearService;
    private final LedgerStore ledgerStore;
    private final ProcurementStore procurementStore;

    public FinancialConstraintService() {
        this(new FiscalYearService(), LedgerStore.getInstance(), ProcurementStore.getInstance());
    }

    public FinancialConstraintService(FiscalYearService fiscalYearService,
                                       LedgerStore ledgerStore,
                                       ProcurementStore procurementStore) {
        this.fiscalYearService = fiscalYearService;
        this.ledgerStore = ledgerStore;
        this.procurementStore = procurementStore;
    }

    public static FinancialConstraintService getInstance() { return INSTANCE; }

    /**
     * Validates that a cash/bank payout of the given amount will not drive the
     * balance below zero on the specified bank account. If {@code bankAccount} is
     * null, checks the legacy CASH_AT_BANK.
     */
    public String checkNegativeCash(BigDecimal amount, AccountType bankAccount) {
        AccountType target = bankAccount != null ? bankAccount : AccountType.CASH_AT_BANK;
        BigDecimal balance = ledgerStore.getAccountBalance(target);
        if (balance.subtract(amount).compareTo(BigDecimal.ZERO) < 0) {
            return "Insufficient balance in " + target.getDisplayName() + ". Available: "
                    + CurrencyConfig.format(balance) + ", requested payout: "
                    + CurrencyConfig.format(amount) + ".";
        }
        return null;
    }

    /** Overload for backward compatibility — checks legacy CASH_AT_BANK. */
    public String checkNegativeCash(BigDecimal amount) {
        return checkNegativeCash(amount, null);
    }

    /**
     * Validates that the given date falls within an open fiscal year.
     * Returns an error message if blocked, or null if OK.
     */
    public String checkFiscalYearOpen(LocalDate date) {
        if (date == null) {
            return null;
        }
        if (!fiscalYearService.isTransactionAllowed(date)) {
            return "Transaction date " + date + " is outside the open fiscal year.";
        }
        return null;
    }

    /**
     * Validates that total disbursements against a contract do not exceed the
     * agreed contract value. Returns an error message if blocked, or null if OK.
     */
    public String checkContractOverflow(String contractId, BigDecimal proposedAmount) {
        if (contractId == null || proposedAmount == null) {
            return null;
        }
        return procurementStore.findContractById(contractId)
                .filter(c -> c.getStatus() == ContractStatus.ACTIVE
                        || c.getStatus() == ContractStatus.EXTENDED)
                .map(contract -> {
                    BigDecimal contractValue = CurrencyConfig.money(contract.getContractValue());
                    BigDecimal alreadyPaid = totalDisbursed(contractId);
                    BigDecimal remaining = contractValue.subtract(alreadyPaid);
                    if (proposedAmount.compareTo(remaining) > 0) {
                        return "Disbursement of " + CurrencyConfig.format(proposedAmount)
                                + " exceeds remaining contract value. Contract: "
                                + CurrencyConfig.format(contractValue)
                                + ", already disbursed: " + CurrencyConfig.format(alreadyPaid)
                                + ", remaining: " + CurrencyConfig.format(remaining) + ".";
                    }
                    return null;
                })
                .orElse(null);
    }

    private BigDecimal totalDisbursed(String contractId) {
        BigDecimal total = CurrencyConfig.zero();
        for (ContractMilestone m : procurementStore.milestonesForContract(contractId)) {
            if (m.isCompleted()) {
                total = total.add(CurrencyConfig.money(m.getAmount()));
            }
        }
        return total;
    }

    /**
     * Enforces ring-fencing: prevents transfers between accounts in different
     * restricted groups. Returns an error message if the transfer violates
     * ring-fencing, or null if OK.
     *
     * <p>Rules:
     * <ul>
     *   <li>Transfers between two accounts in the SAME restricted group are allowed.</li>
     *   <li>Transfers involving an unrestricted account (null group) are allowed.</li>
     *   <li>Transfers between DIFFERENT restricted groups are BLOCKED.</li>
     * </ul>
     */
    public String checkRingFencing(AccountType source, AccountType destination) {
        if (source == null || destination == null) {
            return null;
        }
        String srcGroup = source.getRestrictedGroup();
        String dstGroup = destination.getRestrictedGroup();
        if (srcGroup == null || dstGroup == null) {
            return null;
        }
        if (!srcGroup.equals(dstGroup)) {
            return "Ring-fencing violation: cannot transfer from "
                    + source.getDisplayName() + " (" + srcGroup + ") to "
                    + destination.getDisplayName() + " (" + dstGroup
                    + "). Cross-subsidization between restricted funds is not allowed.";
        }
        return null;
    }

    /**
     * Determines which ring-fenced bank account an income account should deposit into,
     * based on the restricted-group mapping. Returns null if the income account is
     * unrestricted (caller should use CASH_AT_BANK as fallback).
     *
     * @deprecated Use {@link com.schaccs.accounting.DoubleEntryEngine#resolveBankForIncome} instead.
     */
    @Deprecated
    public static AccountType bankAccountForIncome(AccountType incomeAccount) {
        if (incomeAccount == null) return null;
        String group = incomeAccount.getRestrictedGroup();
        if (group == null) return null;
        return switch (group) {
            case "GOVT" -> AccountType.BANK_TUITION;
            case "PARENT" -> AccountType.BANK_BOARDING;
            default -> null;
        };
    }

    /**
     * Determines which ring-fenced bank account an expense should draw from,
     * based on the restricted-group mapping. Returns null if the expense account is
     * unrestricted (caller should use CASH_AT_BANK as fallback).
     *
     * @deprecated Use {@link com.schaccs.accounting.DoubleEntryEngine#resolveBankForExpense} instead.
     */
    @Deprecated
    public static AccountType bankAccountForExpense(AccountType expenseAccount) {
        if (expenseAccount == null) return null;
        String group = expenseAccount.getRestrictedGroup();
        if (group == null) return null;
        return switch (group) {
            case "GOVT" -> AccountType.BANK_TUITION;
            case "PARENT" -> AccountType.BANK_BOARDING;
            default -> null;
        };
    }

    /**
     * Runs all constraint checks for a proposed payment. Returns a list of error
     * messages; empty list means all checks passed.
     */
    public List<String> validatePayment(BigDecimal amount, LocalDate date,
                                         String contractId,
                                         AccountType expenseAccount,
                                         AccountType bankAccount) {
        List<String> errors = new ArrayList<>();
        String cashCheck = checkNegativeCash(amount, bankAccount);
        if (cashCheck != null) errors.add(cashCheck);
        String fyCheck = checkFiscalYearOpen(date);
        if (fyCheck != null) errors.add(fyCheck);
        String contractCheck = checkContractOverflow(contractId, amount);
        if (contractCheck != null) errors.add(contractCheck);
        String ringCheck = checkRingFencing(expenseAccount, bankAccount);
        if (ringCheck != null) errors.add(ringCheck);
        return errors;
    }

    /** Overload for backward compatibility — checks legacy CASH_AT_BANK, no ring-fencing. */
    public List<String> validatePayment(BigDecimal amount, LocalDate date,
                                         String contractId) {
        List<String> errors = new ArrayList<>();
        String cashCheck = checkNegativeCash(amount);
        if (cashCheck != null) errors.add(cashCheck);
        String fyCheck = checkFiscalYearOpen(date);
        if (fyCheck != null) errors.add(fyCheck);
        String contractCheck = checkContractOverflow(contractId, amount);
        if (contractCheck != null) errors.add(contractCheck);
        return errors;
    }
}
