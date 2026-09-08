package com.schaccs.service;

import com.schaccs.accounting.AccountingEngine;
import com.schaccs.config.CurrencyConfig;
import com.schaccs.enums.AccountType;
import com.schaccs.enums.TransactionType;
import com.schaccs.model.finance.JournalEntry;
import com.schaccs.model.payroll.Employee;
import com.schaccs.model.payroll.PayrollItem;
import com.schaccs.model.payroll.PayrollRun;
import com.schaccs.model.payroll.SalaryStructure;
import com.schaccs.model.payroll.StatutoryConfig;
import com.schaccs.repository.PersistenceService;
import com.schaccs.service.payroll.PayrollCalculationEngine;
import com.schaccs.service.payroll.PayrollService;
import com.schaccs.store.EmployeeStore;
import com.schaccs.store.LedgerStore;
import com.schaccs.store.StatutoryConfigStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Payroll spec-compliance core (payroll.md):
 *   §1 statutory config dynamism, SHIF/AHL no-floor, NSSF tiers, threshold PAYE
 *   §2 PE votehead budget-overrun guard + inter-votehead authorization
 *   §3 One-Third Rule (Employment Act §19)
 *   §4 mid-month proration + unpaid leave exception
 *   §5 spec double-entry postings (AHL payable, employer AHL, net clearing,
 *      staff advances credit-back)
 */
class PayrollCoreSpecTest {

    private PayrollService service;

    @BeforeEach
    void setUp() {
        PersistenceService.getInstance().clearAll();
        service = new PayrollService();
    }

    @AfterEach
    void tearDown() {
        PersistenceService.getInstance().clearAll();
    }

    private Employee addEmployee(String number, String name, LocalDate employmentDate) {
        Employee emp = new Employee();
        emp.setEmployeeNumber(number);
        emp.setFirstName(name);
        emp.setLastName("");
        emp.setDepartment("Admin");
        emp.setEmploymentDate(employmentDate);
        emp.setEmploymentStatus(Employee.EmploymentStatus.ACTIVE);
        EmployeeStore.getInstance().getEmployees().add(emp);
        return emp;
    }

    private SalaryStructure salary(Employee emp, String basic, String house) {
        return salary(emp, basic, house, false);
    }

    private SalaryStructure salary(Employee emp, String basic, String house, boolean withDeductions) {
        SalaryStructure s = new SalaryStructure();
        s.setEmployeeId(emp.getId());
        s.setBasicSalary(CurrencyConfig.money(basic));
        s.setHouseAllowance(CurrencyConfig.money(house));
        if (withDeductions) {
            s.setWelfareContribution(CurrencyConfig.money("28000"));
        }
        s.setActive(true);
        EmployeeStore.getInstance().getSalaryStructures().add(s);
        return s;
    }

    private static void eq(BigDecimal expected, BigDecimal actual) {
        assertEquals(0, expected.compareTo(actual), "expected " + expected + " but was " + actual);
    }

    @Test
    @DisplayName("SHIF and AHL levy on a below-threshold wage with PAYE at zero")
    void leviesApplyBelowTaxThreshold() {
        // basic 20,000, no allowances: below the 24,000 personal-relief threshold
        Employee emp = addEmployee("EMP001", "Casual", LocalDate.of(2020, 1, 5));
        salary(emp, "20000", "0");

        PayrollItem item = PayrollCalculationEngine.calculate(
                EmployeeStore.getInstance().findActiveSalaryStructure(emp.getId()).orElseThrow(),
                new PayrollItem());

        eq(CurrencyConfig.zero(), item.getPaye());
        eq(CurrencyConfig.money("550"), item.getShif());   // 20,000 x 2.75%
        eq(CurrencyConfig.money("300"), item.getAhl());    // 20,000 x 1.5%
        assertTrue(item.getNetPay().signum() > 0);
    }

    @Test
    @DisplayName("AHL is 1.5% from the employee and 1.5% from the employer")
    void ahlEmployeeAndEmployerShares() {
        PayrollItem item = PayrollCalculationEngine.calculate(
                structure("60000", "0"), new PayrollItem());

        eq(CurrencyConfig.money("900"), item.getAhl());
        eq(CurrencyConfig.money("900"), item.getEmployerAhl());
    }

    @Test
    @DisplayName("NSSF splits Tier I and Tier II by the pensionable-salary ceilings")
    void nssfTierSplit() {
        // 7,000 at 6% = 420 (Tier I only); 36,000 = 7,000@6% + 29,000@6% = 2,160
        eq(CurrencyConfig.money("420"), PayrollCalculationEngine.calculateNssf(CurrencyConfig.money("7000")));
        eq(CurrencyConfig.money("2160"), PayrollCalculationEngine.calculateNssf(CurrencyConfig.money("36000")));
        eq(CurrencyConfig.money("2160"), PayrollCalculationEngine.calculateNssf(CurrencyConfig.money("100000")));
    }

    @Test
    @DisplayName("Engine reads statutory parameters dynamically from StatutoryConfig")
    void statutoryConfigDrivesEngine() {
        StatutoryConfig config = PayrollCalculationEngine.defaultConfig();
        config.setAhlEmployeeRate(new BigDecimal("0.02"));
        config.setPersonalRelief(CurrencyConfig.money("3000"));
        StatutoryConfigStore.getInstance().getConfigs().add(config);

        // PAYE on 40,000 taxable = 6,783.35; relief 3,000 -> 3,783.35 (vs 4,383.35 default)
        eq(CurrencyConfig.money("3783.35"), PayrollCalculationEngine.calculatePaye(CurrencyConfig.money("40000"),
                config));
        eq(CurrencyConfig.money("1000"), PayrollCalculationEngine.calculateAhl(
                CurrencyConfig.money("50000"), config.getAhlEmployeeRate()));
    }

    @Test
    @DisplayName("A mid-month hire's gross is prorated to days worked and statutory is prorated too")
    void midMonthHireIsProrated() {
        // Employed on 18 March 2026 -> 14 active days in a 31-day month
        Employee emp = addEmployee("EMP001", "Science T.", LocalDate.of(2026, 3, 18));
        salary(emp, "10000", "40000");

        PayrollRun run = service.generatePayroll(3, 2026);
        PayrollItem item = service.findItemsByRunId(run.getId()).get(0);

        eq(new BigDecimal("14"), item.getDaysWorked());
        eq(new BigDecimal("31"), item.getDaysInMonth());
        eq(CurrencyConfig.money("22580.65"), item.getGrossPay());         // 50,000 x 14/31
        eq(CurrencyConfig.money("620.97"), item.getShif());                // 2.75% of prorated gross
        eq(CurrencyConfig.money("338.71"), item.getAhl());                 // 1.5% of prorated gross
        // net + deductions == prorated gross
        eq(item.getGrossPay(), item.getNetPay().add(item.getTotalDeductions()));
    }

    @Test
    @DisplayName("Employees hired before the run month are paid in full")
    void priorHirePaidInFull() {
        Employee emp = addEmployee("EMP001", "Jane", LocalDate.of(2025, 1, 15));
        salary(emp, "40000", "10000");

        PayrollRun run = service.generatePayroll(3, 2026);
        PayrollItem item = service.findItemsByRunId(run.getId()).get(0);

        assertNull(item.getDaysWorked());
        eq(CurrencyConfig.money("50000"), item.getGrossPay());
    }

    @Test
    @DisplayName("Unpaid leave days reduce gross and are labelled on the item")
    void unpaidLeaveReducesGross() {
        Employee emp = addEmployee("EMP001", "Jane", LocalDate.of(2025, 1, 15));
        salary(emp, "40000", "10000");
        PayrollRun run = service.generatePayroll(3, 2026);

        service.applyUnpaidLeave(run.getId(), emp.getId(), new BigDecimal("5"));

        PayrollItem item = service.findItemsByRunId(run.getId()).get(0);
        eq(new BigDecimal("5"), item.getUnpaidLeaveDays());
        eq(CurrencyConfig.money("8064.52"), item.getUnpaidLeaveDeduction()); // 50,000 x 5/31
        eq(CurrencyConfig.money("50000"), item.getGrossPay());               // contract gross stays
        // net + deductions == gross - unpaid-leave deduction (effective pay)
        eq(item.getGrossPay().subtract(item.getUnpaidLeaveDeduction()),
                item.getNetPay().add(item.getTotalDeductions()));
    }

    @Test
    @DisplayName("One-Third Rule blocks approval when a non-statutory deduction starves net pay")
    void oneThirdRuleBlocksApproval() {
        Employee emp = addEmployee("EMP001", "Jane", LocalDate.of(2025, 1, 15));
        salary(emp, "30000", "0", true);
        PayrollRun run = service.generatePayroll(3, 2026);

        PayrollItem item = service.findItemsByRunId(run.getId()).get(0);
        assertTrue(item.getNetPay().compareTo(item.getBasicSalary().divide(
                new BigDecimal("3"), 2, java.math.RoundingMode.HALF_UP)) < 0,
                "test setup must leave net below 1/3 basic");

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.approvePayroll(run.getId()));
        assertTrue(ex.getMessage().contains("1/3"), ex.getMessage());
    }

    @Test
    @DisplayName("Approval succeeds when net pay respects the One-Third Rule")
    void oneThirdRuleAllowsApproval() {
        Employee emp = addEmployee("EMP001", "Jane", LocalDate.of(2025, 1, 15));
        salary(emp, "40000", "10000");
        PayrollRun run = service.generatePayroll(3, 2026);

        service.approvePayroll(run.getId());
        assertEquals(PayrollRun.PayrollStatus.APPROVED, run.getStatus());
    }

    @Test
    @DisplayName("An unfunded PE votehead flags a budget overrun at finalization")
    void unfundedPEFlagsBudgetOverrun() {
        Employee emp = addEmployee("EMP001", "Jane", LocalDate.of(2025, 1, 15));
        salary(emp, "40000", "10000");
        PayrollRun run = service.generatePayroll(3, 2026);

        assertTrue(run.isBudgetOverrun());
        eq(CurrencyConfig.zero(), run.getPeAvailableBalance());
        eq(run.getTotalGrossPay(), run.getOverrunVariance());
    }

    @Test
    @DisplayName("Bank remittance is blocked until an Inter-Votehead Authorization is logged")
    void budgetOverrunBlocksRemittanceUntilAuthorized() {
        Employee emp = addEmployee("EMP001", "Jane", LocalDate.of(2025, 1, 15));
        emp.setBankName("KCB");
        emp.setBankBranch("Main");
        emp.setBankAccountNumber("1234567890");
        salary(emp, "40000", "10000");
        PayrollRun run = service.generatePayroll(3, 2026);
        service.approvePayroll(run.getId());
        service.postPayroll(run.getId());

        IllegalStateException blocked = assertThrows(IllegalStateException.class,
                () -> service.generateBankRemittance(run.getId()));
        assertTrue(blocked.getMessage().contains("Budget Overrun"), blocked.getMessage());

        String journalId = service.authorizeBudgetOverrun(run.getId(),
                AccountType.ACCOUNTS_PAYABLE, run.getOverrunVariance(), "Borrow from Admin surplus");
        assertNotNull(journalId);
        assertTrue(run.isBudgetAuthorized());
        assertEquals(journalId, run.getBudgetAuthorizationRef());

        String csv = service.generateBankRemittance(run.getId());
        assertTrue(csv.startsWith("Employee Number,Employee Name,"
                + "Bank Name,Bank Branch,Account Number,Net Pay"));
        assertTrue(csv.contains("EMP001"));
        assertTrue(csv.contains("1234567890"));
    }

    @Test
    @DisplayName("A funded PE votehead avoids the overrun flag and allows remittance")
    void fundedPEAllowsRemittance() {
        // Fund the PE ledger: DR Receivable, CR SALARIES (PE allocation)
        JournalEntry funding = new JournalEntry();
        funding.setDate(LocalDate.now());
        funding.setReference("PE-FUND-01");
        funding.setNarration("PE votehead funding");
        funding.addLine(AccountType.ACCOUNTS_RECEIVABLE, "AR",
                CurrencyConfig.money("500000"), BigDecimal.ZERO, "PE funding");
        funding.addLine(AccountType.SALARIES, "SALARY",
                BigDecimal.ZERO, CurrencyConfig.money("500000"), "PE funding");
        new AccountingEngine().postTransaction(funding, TransactionType.JOURNAL, null, null, null);

        Employee emp = addEmployee("EMP001", "Jane", LocalDate.of(2025, 1, 15));
        salary(emp, "40000", "10000");
        PayrollRun run = service.generatePayroll(3, 2026);

        assertFalse(run.isBudgetOverrun());
        service.approvePayroll(run.getId());
        service.postPayroll(run.getId());
        assertTrue(service.generateBankRemittance(run.getId()).contains("EMP001"));
    }

    @Test
    @DisplayName("Posting creates the spec §5 journal lines on the ledger")
    void postingUsesSpecAccounts() {
        Employee emp = addEmployee("EMP001", "Jane", LocalDate.of(2025, 1, 15));
        SalaryStructure s = salary(emp, "40000", "10000");
        s.setSalaryAdvanceRecovery(CurrencyConfig.money("2000"));

        PayrollRun run = service.generatePayroll(3, 2026);
        PayrollItem item = service.findItemsByRunId(run.getId()).get(0);
        service.approvePayroll(run.getId());
        service.postPayroll(run.getId());

        LedgerStore ledger = LedgerStore.getInstance();
        eq(item.getAhl().add(item.getEmployerAhl()),
                ledger.getAccountBalance(AccountType.AHL_PAYABLE));
        eq(item.getEmployerAhl(),
                ledger.getAccountBalance(AccountType.EMPLOYER_AHL_EXPENSE));
        eq(run.getTotalNetPay(),
                ledger.getAccountBalance(AccountType.NET_SALARY_CLEARING));
        eq(item.getSalaryAdvanceRecovery().negate(),
                ledger.getAccountBalance(AccountType.STAFF_ADVANCES_RECEIVABLE));
        eq(item.getPaye(), ledger.getAccountBalance(AccountType.PAYE_PAYABLE));
        eq(item.getNssf().add(item.getEmployerNssf()),
                ledger.getAccountBalance(AccountType.NSSF_PAYABLE));
        eq(item.getShif(), ledger.getAccountBalance(AccountType.SHIF_PAYABLE));
    }

    @Test
    @DisplayName("Reversal restores the prejudgment ledger balances")
    void reversalRestoresLedger() {
        Employee emp = addEmployee("EMP001", "Jane", LocalDate.of(2025, 1, 15));
        salary(emp, "40000", "10000");
        PayrollRun run = service.generatePayroll(3, 2026);
        service.approvePayroll(run.getId());
        service.postPayroll(run.getId());
        service.reversePayroll(run.getId());

        LedgerStore ledger = LedgerStore.getInstance();
        eq(CurrencyConfig.zero(), ledger.getAccountBalance(AccountType.NET_SALARY_CLEARING));
        eq(CurrencyConfig.zero(), ledger.getAccountBalance(AccountType.AHL_PAYABLE));
        eq(CurrencyConfig.zero(), ledger.getAccountBalance(AccountType.EMPLOYER_AHL_EXPENSE));
    }

    private static SalaryStructure structure(String basic, String house) {
        SalaryStructure s = new SalaryStructure();
        s.setBasicSalary(CurrencyConfig.money(basic));
        s.setHouseAllowance(CurrencyConfig.money(house));
        s.setActive(true);
        return s;
    }
}