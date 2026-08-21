package com.schaccs.service.payroll;

import com.schaccs.config.AppConfig;
import com.schaccs.config.CurrencyConfig;
import com.schaccs.model.payroll.Employee;
import com.schaccs.model.payroll.SalaryStructure;
import com.schaccs.store.EmployeeStore;
import com.schaccs.service.audit.AuditService;
import com.schaccs.repository.PersistenceService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class EmployeeServiceTest {

    private EmployeeService service;
    private EmployeeStore store;

    @BeforeEach
    void setUp() {
        PersistenceService.getInstance().clearAll();
        EmployeeStore.getInstance().clear();
        store = EmployeeStore.getInstance();
        service = new EmployeeService(store, new AuditService());
        AppConfig.getInstance().setCurrentUser("admin@test.com");
    }

    @AfterEach
    void tearDown() {
        PersistenceService.getInstance().clearAll();
        EmployeeStore.getInstance().clear();
    }

    private Employee makeEmployee(String empNo, String first, String last, String dept) {
        Employee e = new Employee();
        e.setEmployeeNumber(empNo);
        e.setFirstName(first);
        e.setLastName(last);
        e.setDepartment(dept);
        e.setEmploymentDate(LocalDate.of(2024, 1, 15));
        return e;
    }

    @Test
    @DisplayName("createEmployee adds to store")
    void createEmployee() {
        Employee e = makeEmployee("EMP-001", "John", "Doe", "Teaching");
        Employee created = service.createEmployee(e);

        assertEquals(e, created);
        assertEquals(1, store.getEmployees().size());
        assertEquals("John", store.getEmployees().get(0).getFirstName());
    }

    @Test
    @DisplayName("terminateEmployee sets status to TERMINATED")
    void terminateEmployee() {
        Employee e = makeEmployee("EMP-002", "Jane", "Smith", "Admin");
        service.createEmployee(e);
        assertEquals(Employee.EmploymentStatus.ACTIVE, e.getEmploymentStatus());

        service.terminateEmployee(e, LocalDate.now());

        assertEquals(Employee.EmploymentStatus.TERMINATED, e.getEmploymentStatus());
    }

    @Test
    @DisplayName("findByEmployeeNumber finds correct employee")
    void findByEmployeeNumber() {
        service.createEmployee(makeEmployee("EMP-003", "Alice", "Wong", "Finance"));
        service.createEmployee(makeEmployee("EMP-004", "Bob", "Lee", "Admin"));

        Optional<Employee> found = service.findByEmployeeNumber("EMP-003");
        assertTrue(found.isPresent());
        assertEquals("Alice", found.get().getFirstName());
    }

    @Test
    @DisplayName("findByEmployeeNumber returns empty for unknown")
    void findByEmployeeNumber_unknown() {
        assertTrue(service.findByEmployeeNumber("NONEXISTENT").isEmpty());
    }

    @Test
    @DisplayName("findById finds correct employee")
    void findById() {
        Employee e = makeEmployee("EMP-005", "Carol", "King", "Teaching");
        service.createEmployee(e);

        Optional<Employee> found = service.findById(e.getId());
        assertTrue(found.isPresent());
        assertEquals("King", found.get().getLastName());
    }

    @Test
    @DisplayName("getActiveEmployees excludes terminated")
    void getActiveEmployees() {
        Employee active = makeEmployee("EMP-006", "Dan", "Active", "Finance");
        Employee terminated = makeEmployee("EMP-007", "Eve", "Terminated", "Admin");
        service.createEmployee(active);
        service.createEmployee(terminated);
        service.terminateEmployee(terminated, LocalDate.now());

        List<Employee> activeList = service.getActiveEmployees();
        assertEquals(1, activeList.size());
        assertEquals("Dan", activeList.get(0).getFirstName());
    }

    @Test
    @DisplayName("getDepartments returns distinct sorted departments")
    void getDepartments() {
        service.createEmployee(makeEmployee("EMP-008", "A", "B", "Teaching"));
        service.createEmployee(makeEmployee("EMP-009", "C", "D", "Admin"));
        service.createEmployee(makeEmployee("EMP-010", "E", "F", "Teaching"));

        List<String> depts = service.getDepartments();
        assertEquals(2, depts.size());
        assertEquals("Admin", depts.get(0));
        assertEquals("Teaching", depts.get(1));
    }

    @Test
    @DisplayName("assignSalaryStructure deactivates previous and adds new")
    void assignSalaryStructure() {
        Employee e = makeEmployee("EMP-011", "F", "G", "Teaching");
        service.createEmployee(e);

        SalaryStructure s1 = new SalaryStructure();
        s1.setEmployeeId(e.getId());
        s1.setBasicSalary(CurrencyConfig.money("50000"));
        service.assignSalaryStructure(s1);

        SalaryStructure s2 = new SalaryStructure();
        s2.setEmployeeId(e.getId());
        s2.setBasicSalary(CurrencyConfig.money("55000"));
        service.assignSalaryStructure(s2);

        Optional<SalaryStructure> active = service.getActiveSalaryStructure(e.getId());
        assertTrue(active.isPresent());
        assertEquals(0, active.get().getBasicSalary().compareTo(CurrencyConfig.money("55000")));

        List<SalaryStructure> all = store.findSalaryStructuresByEmployeeId(e.getId());
        long activeCount = all.stream().filter(SalaryStructure::isActive).count();
        assertEquals(1, activeCount, "Only one structure should be active");
    }

    @Test
    @DisplayName("getActiveSalaryStructure returns empty when none exists")
    void getActiveSalaryStructure_none() {
        assertTrue(service.getActiveSalaryStructure("nonexistent").isEmpty());
    }
}
