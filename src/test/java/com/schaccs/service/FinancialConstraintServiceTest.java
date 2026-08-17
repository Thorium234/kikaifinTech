package com.schaccs.service;

import com.schaccs.accounting.DoubleEntryEngine;
import com.schaccs.config.CurrencyConfig;
import com.schaccs.enums.AccountType;
import com.schaccs.model.finance.LedgerEntry;
import com.schaccs.service.finance.FinancialConstraintService;
import com.schaccs.store.LedgerStore;
import com.schaccs.repository.PersistenceService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FinancialConstraintServiceTest {

    private FinancialConstraintService service;

    @BeforeEach
    void setUp() {
        PersistenceService.getInstance().clearAll();
        LedgerStore store = LedgerStore.getInstance();
        service = new FinancialConstraintService(
                new com.schaccs.service.finance.FiscalYearService(),
                store,
                com.schaccs.store.ProcurementStore.getInstance());
    }

    @AfterEach
    void tearDown() {
        PersistenceService.getInstance().clearAll();
    }

    private void seedBalance(AccountType type, BigDecimal debit, BigDecimal credit) {
        LedgerEntry entry = new LedgerEntry();
        entry.setAccountType(type);
        entry.setDebit(debit);
        entry.setCredit(credit);
        entry.setDate(java.time.LocalDate.now());
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
                    java.time.LocalDate.now().plusDays(1),
                    null);
            assertTrue(errors.isEmpty());
        }
    }
}
