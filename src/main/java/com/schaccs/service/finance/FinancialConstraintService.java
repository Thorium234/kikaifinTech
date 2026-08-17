package com.schaccs.service.finance;

import com.schaccs.config.CurrencyConfig;
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
 * Centralises financial constraint enforcement:
 * <ul>
 *   <li>Negative cash prevention — blocks payouts that would overdraw cash/bank</li>
 *   <li>Closed fiscal year lock — blocks transactions in closed periods</li>
 *   <li>Contract value overflow guard — prevents disbursements exceeding contract value</li>
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
     * cash balance below zero. Returns an error message if blocked, or null if OK.
     */
    public String checkNegativeCash(BigDecimal amount) {
        BigDecimal cashBalance = ledgerStore.getAccountBalance(
                com.schaccs.enums.AccountType.CASH_AT_BANK);
        if (cashBalance.subtract(amount).compareTo(BigDecimal.ZERO) < 0) {
            return "Insufficient cash balance. Available: "
                    + CurrencyConfig.format(cashBalance) + ", requested payout: "
                    + CurrencyConfig.format(amount) + ".";
        }
        return null;
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
     * Runs all constraint checks for a proposed payment. Returns a list of error
     * messages; empty list means all checks passed.
     */
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
