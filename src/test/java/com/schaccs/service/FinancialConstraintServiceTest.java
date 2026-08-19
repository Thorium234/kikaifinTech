package com.schaccs.service;

import com.schaccs.accounting.DoubleEntryEngine;
import com.schaccs.config.CurrencyConfig;
import com.schaccs.enums.AccountType;
import com.schaccs.enums.ContractStatus;
import com.schaccs.model.finance.FiscalYear;
import com.schaccs.model.finance.LedgerEntry;
import com.schaccs.model.procurement.Contract;
import com.schaccs.model.procurement.ContractMilestone;
import com.schaccs.service.finance.FinancialConstraintService;
import com.schaccs.store.AccountStore;
import com.schaccs.store.LedgerStore;
import com.schaccs.store.ProcurementStore;
import com.schaccs.repository.PersistenceService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FinancialConstraintServiceTest {

    private FinancialConstraintService service;

    @BeforeEach
    void setUp() {
        PersistenceService.getInstance().clearAll();
        AccountStore.getInstance().clear();
        LedgerStore store = LedgerStore.getInstance();
        service = new FinancialConstraintService(
                new com.schaccs.service.finance.FiscalYearService(),
                store,
                ProcurementStore.getInstance());
    }

    @AfterEach
    void tearDown() {
        PersistenceService.getInstance().clearAll();
        AccountStore.getInstance().clear();
    }

    private void seedBalance(AccountType type, BigDecimal debit, BigDecimal credit) {
        LedgerEntry entry = new LedgerEntry();
        entry.setAccountType(type);
        entry.setDebit(debit);
        entry.setCredit(credit);
        entry.setDate(LocalDate.now());
        LedgerStore.getInstance().addLedgerEntry(entry);
    }

    @Nested
    @DisplayName("Ring-fencing enforcement")
    class RingFencingTests {

        @Test
        @DisplayName("same restricted group (GOVT) is allowed")
        void sameGroupAllowed() {
            assertNull(service.checkRingFencing(
                    AccountType.GOVT_CAPITATION_TUITION, AccountType.INFRASTRUCTURE_GRANT));
        }

        @Test
        @DisplayName("different restricted groups are blocked")
        void differentGroupsBlocked() {
            String err = service.checkRingFencing(
                    AccountType.GOVT_CAPITATION_TUITION, AccountType.FEES_BOARDING_ACTIVITY);
            assertNotNull(err);
            assertTrue(err.contains("Ring-fencing violation"));
        }

        @Test
        @DisplayName("unrestricted source is always allowed")
        void unrestrictedSourceAllowed() {
            assertNull(service.checkRingFencing(
                    AccountType.SCHOOL_FUND, AccountType.GOVT_CAPITATION_TUITION));
        }

        @Test
        @DisplayName("unrestricted destination is always allowed")
        void unrestrictedDestinationAllowed() {
            assertNull(service.checkRingFencing(
                    AccountType.GOVT_CAPITATION_TUITION, AccountType.SCHOOL_FUND));
        }

        @Test
        @DisplayName("both null is OK")
        void bothNull() {
            assertNull(service.checkRingFencing(null, null));
        }

        @Test
        @DisplayName("legacy accounts (null group) are unrestricted")
        void legacyAccountsAreUnrestricted() {
            assertNull(service.checkRingFencing(
                    AccountType.TUITION_FEES, AccountType.BOARDING_FEES));
        }
    }

    @Nested
    @DisplayName("Bank account resolution for income")
    class BankRoutingTests {

        @Test
        @DisplayName("GOVT_CAPITATION_TUITION routes to BANK_TUITION")
        void govTuitionRoutesToBankTuition() {
            assertEquals(AccountType.BANK_TUITION,
                    DoubleEntryEngine.resolveBankForIncome(AccountType.GOVT_CAPITATION_TUITION));
        }

        @Test
        @DisplayName("INFRASTRUCTURE_GRANT routes to BANK_INFRASTRUCTURE")
        void govInfraRoutesToBankInfrastructure() {
            assertEquals(AccountType.BANK_INFRASTRUCTURE,
                    DoubleEntryEngine.resolveBankForIncome(AccountType.INFRASTRUCTURE_GRANT));
        }

        @Test
        @DisplayName("parent income routes to BANK_BOARDING")
        void parentIncomeRoutesToBankBoarding() {
            assertEquals(AccountType.BANK_BOARDING,
                    DoubleEntryEngine.resolveBankForIncome(AccountType.FEES_BOARDING_ACTIVITY));
        }

        @Test
        @DisplayName("unrestricted income falls back to CASH_AT_BANK")
        void unrestrictedIncomeFallsBack() {
            assertEquals(AccountType.CASH_AT_BANK,
                    DoubleEntryEngine.resolveBankForIncome(AccountType.SCHOOL_FUND));
            assertEquals(AccountType.CASH_AT_BANK,
                    DoubleEntryEngine.resolveBankForIncome(null));
        }

        @Test
        @DisplayName("legacy income accounts use legacy bank mapping")
        void legacyIncomeAccounts() {
            assertEquals(AccountType.BANK_TUITION,
                    DoubleEntryEngine.resolveBankForIncome(AccountType.TUITION_FEES));
            assertEquals(AccountType.BANK_BOARDING,
                    DoubleEntryEngine.resolveBankForIncome(AccountType.BOARDING_FEES));
            assertEquals(AccountType.BANK_BOARDING,
                    DoubleEntryEngine.resolveBankForIncome(AccountType.ACTIVITY_FEES));
        }
    }

    @Nested
    @DisplayName("Bank account resolution for expense")
    class ExpenseBankRoutingTests {

        @Test
        @DisplayName("TEACHING_LEARNING_MATERIALS routes to BANK_TUITION")
        void teachingMaterialsToBankTuition() {
            assertEquals(AccountType.BANK_TUITION,
                    DoubleEntryEngine.resolveBankForExpense(AccountType.TEACHING_LEARNING_MATERIALS));
        }

        @Test
        @DisplayName("INFRASTRUCTURE_EXPANSION routes to BANK_INFRASTRUCTURE")
        void infraExpansionToBankInfrastructure() {
            assertEquals(AccountType.BANK_INFRASTRUCTURE,
                    DoubleEntryEngine.resolveBankForExpense(AccountType.INFRASTRUCTURE_EXPANSION));
        }

        @Test
        @DisplayName("unrestricted expenses fall back to CASH_AT_BANK")
        void unrestrictedExpensesFallBack() {
            assertEquals(AccountType.CASH_AT_BANK,
                    DoubleEntryEngine.resolveBankForExpense(AccountType.GENERAL_EXPENSES));
            assertEquals(AccountType.CASH_AT_BANK,
                    DoubleEntryEngine.resolveBankForExpense(null));
        }
    }

    @Nested
    @DisplayName("AccountType ring-fence properties")
    class AccountTypeProperties {

        @Test
        @DisplayName("Kenyan education accounts are restricted")
        void restrictedAccounts() {
            assertTrue(AccountType.GOVT_CAPITATION_TUITION.isRestricted());
            assertTrue(AccountType.INFRASTRUCTURE_GRANT.isRestricted());
            assertTrue(AccountType.FEES_BOARDING_ACTIVITY.isRestricted());
            assertTrue(AccountType.TEACHING_LEARNING_MATERIALS.isRestricted());
        }

        @Test
        @DisplayName("unrestricted accounts have null restrictedGroup")
        void unrestrictedAccounts() {
            assertFalse(AccountType.SCHOOL_FUND.isRestricted());
            assertFalse(AccountType.CASH_AT_BANK.isRestricted());
            assertFalse(AccountType.ACCOUNTS_PAYABLE.isRestricted());
            assertFalse(AccountType.SALARIES.isRestricted());
        }

        @Test
        @DisplayName("ring-fenced bank accounts are correctly identified")
        void ringFencedBankAccounts() {
            assertTrue(AccountType.BANK_TUITION.isRingFencedBank());
            assertTrue(AccountType.BANK_BOARDING.isRingFencedBank());
            assertTrue(AccountType.BANK_INFRASTRUCTURE.isRingFencedBank());
            assertFalse(AccountType.CASH_AT_BANK.isRingFencedBank());
            assertFalse(AccountType.GOVT_CAPITATION_TUITION.isRingFencedBank());
        }

        @Test
        @DisplayName("all enum values have a code")
        void allHaveCodes() {
            for (AccountType type : AccountType.values()) {
                assertNotNull(type.getCode(), type.name() + " missing code");
                assertFalse(type.getCode().isBlank(), type.name() + " has blank code");
            }
        }

        @Test
        @DisplayName("all enum values have a display name")
        void allHaveDisplayNames() {
            for (AccountType type : AccountType.values()) {
                assertNotNull(type.getDisplayName(), type.name() + " missing displayName");
                assertFalse(type.getDisplayName().isBlank(), type.name() + " has blank displayName");
            }
        }

        @Test
        @DisplayName("all enum values have normal balance set")
        void allHaveNormalBalance() {
            for (AccountType type : AccountType.values()) {
                assertNotNull(type.getNormalBalance(), type.name() + " missing normalBalance");
            }
        }
    }

    @Nested
    @DisplayName("Negative cash guard on ring-fenced banks")
    class NegativeCashGuardTests {

        @Test
        @DisplayName("blocks payout when ring-fenced bank is overdrawn")
        void blocksOverdrawnRingFencedBank() {
            seedBalance(AccountType.BANK_TUITION, CurrencyConfig.money("5000"), CurrencyConfig.zero());

            String err = service.checkNegativeCash(CurrencyConfig.money("6000"), AccountType.BANK_TUITION);
            assertNotNull(err);
            assertTrue(err.contains("Insufficient balance"));
        }

        @Test
        @DisplayName("allows payout within balance on ring-fenced bank")
        void allowsPayoutWithinBalance() {
            seedBalance(AccountType.BANK_TUITION, CurrencyConfig.money("10000"), CurrencyConfig.zero());

            assertNull(service.checkNegativeCash(CurrencyConfig.money("8000"), AccountType.BANK_TUITION));
        }

        @Test
        @DisplayName("each ring-fenced bank is independent")
        void banksAreIndependent() {
            seedBalance(AccountType.BANK_TUITION, CurrencyConfig.money("10000"), CurrencyConfig.zero());
            seedBalance(AccountType.BANK_BOARDING, CurrencyConfig.money("5000"), CurrencyConfig.zero());

            assertNull(service.checkNegativeCash(CurrencyConfig.money("8000"), AccountType.BANK_TUITION));
            assertNotNull(service.checkNegativeCash(CurrencyConfig.money("8000"), AccountType.BANK_BOARDING));
        }

        @Test
        @DisplayName("null bank falls back to CASH_AT_BANK")
        void nullBankFallback() {
            seedBalance(AccountType.CASH_AT_BANK, CurrencyConfig.money("1000"), CurrencyConfig.zero());

            assertNull(service.checkNegativeCash(CurrencyConfig.money("500"), null));
            assertNotNull(service.checkNegativeCash(CurrencyConfig.money("2000"), null));
        }
    }

    @Nested
    @DisplayName("validatePayment full pipeline")
    class ValidatePaymentTests {

        @Test
        @DisplayName("returns empty list when all constraints pass")
        void allPass() {
            seedBalance(AccountType.CASH_AT_BANK, CurrencyConfig.money("50000"), CurrencyConfig.zero());
            List<String> errors = service.validatePayment(
                    CurrencyConfig.money("1000"),
                    LocalDate.now().plusDays(1),
                    null);
            assertTrue(errors.isEmpty());
        }
    }

    @Nested
    @DisplayName("Contract overflow guard")
    class ContractOverflowTests {

        private Contract createContract(String id, BigDecimal value, ContractStatus status) {
            Contract c = Contract.withId(id);
            c.setContractValue(value);
            c.setStatus(status);
            ProcurementStore.getInstance().getContracts().add(c);
            return c;
        }

        private void addMilestone(String contractId, BigDecimal amount, boolean completed) {
            ContractMilestone m = new ContractMilestone();
            m.setContractId(contractId);
            m.setAmount(amount);
            m.setCompleted(completed);
            ProcurementStore.getInstance().getMilestones().add(m);
        }

        @Test
        @DisplayName("null contractId is always allowed")
        void nullContractAllowed() {
            assertNull(service.checkContractOverflow(null, CurrencyConfig.money("1000")));
        }

        @Test
        @DisplayName("null amount is always allowed")
        void nullAmountAllowed() {
            Contract c = createContract("c1", CurrencyConfig.money("50000"), ContractStatus.ACTIVE);
            assertNull(service.checkContractOverflow(c.getId(), null));
        }

        @Test
        @DisplayName("blocks disbursement exceeding contract value")
        void blocksOverflow() {
            Contract c = createContract("c2", CurrencyConfig.money("100000"), ContractStatus.ACTIVE);
            addMilestone(c.getId(), CurrencyConfig.money("80000"), true);

            String err = service.checkContractOverflow(c.getId(), CurrencyConfig.money("30000"));
            assertNotNull(err);
            assertTrue(err.contains("exceeds remaining contract value"));
        }

        @Test
        @DisplayName("allows disbursement within remaining contract value")
        void allowsWithinRemaining() {
            Contract c = createContract("c3", CurrencyConfig.money("100000"), ContractStatus.ACTIVE);
            addMilestone(c.getId(), CurrencyConfig.money("60000"), true);

            assertNull(service.checkContractOverflow(c.getId(), CurrencyConfig.money("30000")));
        }

        @Test
        @DisplayName("only counts completed milestones")
        void onlyCompletedMilestones() {
            Contract c = createContract("c4", CurrencyConfig.money("100000"), ContractStatus.ACTIVE);
            addMilestone(c.getId(), CurrencyConfig.money("80000"), true);
            addMilestone(c.getId(), CurrencyConfig.money("30000"), false);

            assertNull(service.checkContractOverflow(c.getId(), CurrencyConfig.money("15000")));
        }

        @Test
        @DisplayName("inactive contracts are not checked")
        void inactiveContractsSkipped() {
            Contract c = createContract("c5", CurrencyConfig.money("100000"), ContractStatus.COMPLETED);
            addMilestone(c.getId(), CurrencyConfig.money("80000"), true);

            assertNull(service.checkContractOverflow(c.getId(), CurrencyConfig.money("30000")));
        }
    }

    @Nested
    @DisplayName("Fiscal year lock")
    class FiscalYearLockTests {

        private void openFiscalYear(int year) {
            FiscalYear fy = new FiscalYear();
            fy.setYear(year);
            fy.setStartDate(LocalDate.of(year, 1, 1));
            fy.setEndDate(LocalDate.of(year, 12, 31));
            fy.setOpen(true);
            fy.setClosed(false);
            AccountStore.getInstance().getFiscalYears().add(fy);
        }

        @Test
        @DisplayName("null date is always allowed")
        void nullDateAllowed() {
            assertNull(service.checkFiscalYearOpen(null));
        }

        @Test
        @DisplayName("date within open fiscal year is allowed")
        void dateWithinOpenYear() {
            openFiscalYear(LocalDate.now().getYear());

            assertNull(service.checkFiscalYearOpen(LocalDate.now()));
        }

        @Test
        @DisplayName("date outside open fiscal year is blocked")
        void dateOutsideOpenYear() {
            openFiscalYear(2024);

            String err = service.checkFiscalYearOpen(LocalDate.of(2025, 6, 1));
            assertNotNull(err);
            assertTrue(err.contains("outside the open fiscal year"));
        }

        @Test
        @DisplayName("date before fiscal year start is blocked")
        void dateBeforeStart() {
            openFiscalYear(2025);

            String err = service.checkFiscalYearOpen(LocalDate.of(2024, 12, 31));
            assertNotNull(err);
        }

        @Test
        @DisplayName("date after fiscal year end is blocked")
        void dateAfterEnd() {
            openFiscalYear(2025);

            String err = service.checkFiscalYearOpen(LocalDate.of(2026, 1, 1));
            assertNotNull(err);
        }
    }
}
