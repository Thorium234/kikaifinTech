package com.schaccs.service;

import com.schaccs.config.CurrencyConfig;
import com.schaccs.model.payroll.Employee;
import com.schaccs.model.payroll.PayrollItem;
import com.schaccs.model.payroll.PayrollRun;
import com.schaccs.model.payroll.SalaryStructure;
import com.schaccs.repository.PersistenceService;
import com.schaccs.service.payroll.PayrollCalculationEngine;
import com.schaccs.service.payroll.PayrollService;
import com.schaccs.store.EmployeeStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Payroll: Kenya statutory calculations (PAYE, NSSF, SHIF) and the run
 * lifecycle (generate draft, approve, post to GL, reverse, recalculate).
 */
class PayrollTest {

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

    private Employee addEmployee(String number, String name, String dept) {
        Employee emp = new Employee();
        emp.setEmployeeNumber(number);
        emp.setFirstName(name);
        emp.setLastName("");
        emp.setDepartment(dept);
        emp.setEmploymentStatus(Employee.EmploymentStatus.ACTIVE);
        EmployeeStore.getInstance().getEmployees().add(emp);
        return emp;
    }

    private SalaryStructure salary(Employee emp, String basic, String house) {
        SalaryStructure s = new SalaryStructure();
        s.setEmployeeId(emp.getId());
        s.setBasicSalary(CurrencyConfig.money(basic));
        s.setHouseAllowance(CurrencyConfig.money(house));
        s.setActive(true);
        EmployeeStore.getInstance().getSalaryStructures().add(s);
        return s;
    }

    @Test
    @DisplayName("PAYE is zero on income within the 24,000 tax-free band")
    void payeBelowThresholdIsZero() {
        assertEquals(0, PayrollCalculationEngine.calculatePaye(CurrencyConfig.money("20000"))
                .compareTo(BigDecimal.ZERO));
    }

    @Test
    @DisplayName("PAYE crosses the 10% and 25% bands then applies personal relief")
    void payeAcrosFirstBands() {
        // 40,000 taxable: 24,000@10% + 8,333@25% + 7,667@30% = 6,783.35 - 2,400 relief
        assertEquals(0, PayrollCalculationEngine.calculatePaye(CurrencyConfig.money("40000"))
                .compareTo(CurrencyConfig.money("4383.35")));
    }

    @Test
    @DisplayName("NSSF is 6% of pensionable earnings, zero under 7,000, capped at 2,160")
    void nssfFormula() {
        assertEquals(0, PayrollCalculationEngine.calculateNssf(CurrencyConfig.money("5000"))
                .compareTo(BigDecimal.ZERO));
        assertEquals(0, PayrollCalculationEngine.calculateNssf(CurrencyConfig.money("10000"))
                .compareTo(CurrencyConfig.money("600")));
        assertEquals(0, PayrollCalculationEngine.calculateNssf(CurrencyConfig.money("50000"))
                .compareTo(CurrencyConfig.money("2160")));
    }

    @Test
    @DisplayName("SHIF is 2.75% of gross, with a 300 floor and a 5,000 cap")
    void shifFormula() {
        assertEquals(0, PayrollCalculationEngine.calculateShif(CurrencyConfig.money("20000"))
                .compareTo(CurrencyConfig.money("550")));
        assertEquals(0, PayrollCalculationEngine.calculateShif(CurrencyConfig.money("5000"))
                .compareTo(CurrencyConfig.money("300")));
        assertEquals(0, PayrollCalculationEngine.calculateShif(CurrencyConfig.money("200000"))
                .compareTo(CurrencyConfig.money("5000")));
    }

    @Test
    @DisplayName("Net pay equals gross pay minus all deductions")
    void engineComputesNetPay() {
        SalaryStructure s = new SalaryStructure();
        s.setBasicSalary(CurrencyConfig.money("50000"));
        s.setHouseAllowance(CurrencyConfig.money("10000"));
        s.setStaffLoanRepayment(CurrencyConfig.money("5000"));

        PayrollItem item = PayrollCalculationEngine.calculate(s, new PayrollItem());

        assertEquals(0, item.getGrossPay().compareTo(CurrencyConfig.money("60000")));
        BigDecimal expectedDeductions = item.getPaye().add(item.getNssf()).add(item.getShif())
                .add(item.getStaffLoanRepayment());
        assertEquals(0, item.getTotalDeductions().compareTo(expectedDeductions));
        assertEquals(0, item.getNetPay().compareTo(item.getGrossPay().subtract(item.getTotalDeductions())));
    }

    @Test
    @DisplayName("Generate creates a draft run with one line item per paid employee")
    void generateCreatesDraftRun() {
        Employee emp = addEmployee("EMP001", "Jane", "Admin");
        salary(emp, "40000", "10000");

        PayrollRun run = service.generatePayroll(8, 2026);

        assertEquals(PayrollRun.PayrollStatus.DRAFT, run.getStatus());
        assertEquals(1, run.getEmployeeCount());
        PayrollItem item = service.findItemsByRunId(run.getId()).get(0);
        assertEquals("EMP001", item.getEmployeeNumber());
        assertEquals("Jane", item.getEmployeeName());
        assertEquals(0, item.getGrossPay().compareTo(CurrencyConfig.money("50000")));
        assertEquals(0, run.getTotalGrossPay().compareTo(item.getGrossPay()));
        assertEquals(0, run.getTotalNetPay().compareTo(item.getNetPay()));
    }

    @Test
    @DisplayName("A second run for the same month is rejected")
    void duplicatePeriodRejected() {
        addEmployee("EMP001", "Jane", "Admin");
        salary(EmployeeStore.getInstance().findByEmployeeNumber("EMP001").orElseThrow(), "40000", "10000");

        service.generatePayroll(8, 2026);
        assertThrows(IllegalStateException.class, () -> service.generatePayroll(8, 2026));
    }

    @Test
    @DisplayName("Active employees without a salary structure are skipped")
    void skipsEmployeesWithoutSalary() {
        addEmployee("EMP001", "Jane", "Admin");
        Employee john = addEmployee("EMP002", "John", "Bursary");
        salary(john, "30000", "0");

        PayrollRun run = service.generatePayroll(8, 2026);

        assertEquals(1, run.getEmployeeCount());
        assertEquals("EMP002", service.findItemsByRunId(run.getId()).get(0).getEmployeeNumber());
    }

    @Test
    @DisplayName("Generating with no active employees fails")
    void noActiveEmployeesFails() {
        assertThrows(IllegalStateException.class, () -> service.generatePayroll(8, 2026));
    }

    @Test
    @DisplayName("Draft can be approved, posted to GL, then reversed")
    void lifecycleApprovePostReverse() {
        Employee emp = addEmployee("EMP001", "Jane", "Admin");
        salary(emp, "40000", "10000");
        PayrollRun run = service.generatePayroll(8, 2026);

        service.approvePayroll(run.getId());
        assertEquals(PayrollRun.PayrollStatus.APPROVED, run.getStatus());

        service.postPayroll(run.getId());
        assertEquals(PayrollRun.PayrollStatus.POSTED, run.getStatus());
        assertNotNull(run.getJournalId());

        service.reversePayroll(run.getId());
        assertEquals(PayrollRun.PayrollStatus.REVERSED, run.getStatus());
        assertTrue(run.getNotes().contains("Reversed by"));
    }

    @Test
    @DisplayName("Recalculate refreshes items and totals from current salaries")
    void recalculateUsesCurrentSalaries() {
        Employee emp = addEmployee("EMP001", "Jane", "Admin");
        salary(emp, "40000", "10000");
        PayrollRun run = service.generatePayroll(8, 2026);
        assertEquals(0, service.findItemsByRunId(run.getId()).get(0).getGrossPay()
                .compareTo(CurrencyConfig.money("50000")));

        SalaryStructure active = EmployeeStore.getInstance()
                .findActiveSalaryStructure(emp.getId()).orElseThrow();
        active.setBasicSalary(CurrencyConfig.money("60000"));

        service.recalculatePayroll(run.getId());

        PayrollItem item = service.findItemsByRunId(run.getId()).get(0);
        assertEquals(0, item.getGrossPay().compareTo(CurrencyConfig.money("70000")));
        assertEquals(0, run.getTotalGrossPay().compareTo(item.getGrossPay()));
    }
}
