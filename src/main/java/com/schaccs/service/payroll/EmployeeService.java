package com.schaccs.service.payroll;

import com.schaccs.config.AppConfig;
import com.schaccs.model.payroll.Employee;
import com.schaccs.model.payroll.SalaryStructure;
import com.schaccs.repository.PersistenceService;
import com.schaccs.service.audit.AuditService;
import com.schaccs.store.EmployeeStore;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public class EmployeeService {

    private final EmployeeStore store;
    private final AuditService auditService;

    public EmployeeService() {
        this(EmployeeStore.getInstance(), new AuditService());
    }

    public EmployeeService(EmployeeStore store, AuditService auditService) {
        this.store = store;
        this.auditService = auditService;
    }

    public Employee createEmployee(Employee employee) {
        store.getEmployees().add(employee);
        auditService.log("CREATE", "Employee", employee.getId(),
                "Created employee " + employee.getEmployeeNumber() + " — " + employee.getFullName());
        PersistenceService.getInstance().saveAll();
        return employee;
    }

    public void updateEmployee(Employee employee) {
        auditService.logFieldChange("Employee", employee.getId(), "employee",
                null, employee.getFullName(), AppConfig.getInstance().getCurrentUser());
        PersistenceService.getInstance().saveAll();
    }

    public void terminateEmployee(Employee employee, LocalDate terminationDate) {
        Employee.EmploymentStatus oldStatus = employee.getEmploymentStatus();
        employee.setEmploymentStatus(Employee.EmploymentStatus.TERMINATED);
        auditService.logFieldChange("Employee", employee.getId(), "employmentStatus",
                oldStatus.name(), Employee.EmploymentStatus.TERMINATED.name(),
                AppConfig.getInstance().getCurrentUser());
        PersistenceService.getInstance().saveAll();
    }

    public SalaryStructure assignSalaryStructure(SalaryStructure structure) {
        // Deactivate previous active structure for this employee
        List<SalaryStructure> existing = store.findSalaryStructuresByEmployeeId(structure.getEmployeeId());
        for (SalaryStructure s : existing) {
            s.setActive(false);
        }
        store.getSalaryStructures().add(structure);
        auditService.log("CREATE", "SalaryStructure", structure.getId(),
                "Salary structure for employee " + structure.getEmployeeId()
                        + " — Basic: " + structure.getBasicSalary());
        PersistenceService.getInstance().saveAll();
        return structure;
    }

    public void updateSalaryStructure(SalaryStructure structure) {
        auditService.logFieldChange("SalaryStructure", structure.getId(), "basicSalary",
                null, structure.getBasicSalary().toPlainString(),
                AppConfig.getInstance().getCurrentUser());
        PersistenceService.getInstance().saveAll();
    }

    public Optional<Employee> findByEmployeeNumber(String empNo) {
        return store.findByEmployeeNumber(empNo);
    }

    public Optional<Employee> findById(String id) {
        return store.findById(id);
    }

    public List<Employee> getActiveEmployees() {
        return store.findActiveEmployees();
    }

    public List<String> getDepartments() {
        return store.getDepartments();
    }

    public Optional<SalaryStructure> getActiveSalaryStructure(String employeeId) {
        return store.findActiveSalaryStructure(employeeId);
    }

    public EmployeeStore getStore() {
        return store;
    }
}
