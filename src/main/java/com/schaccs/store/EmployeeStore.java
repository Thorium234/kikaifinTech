package com.schaccs.store;

import com.schaccs.model.payroll.Employee;
import com.schaccs.model.payroll.SalaryStructure;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.util.List;
import java.util.Optional;

public final class EmployeeStore {

    private static final EmployeeStore INSTANCE = new EmployeeStore();

    private final ObservableList<Employee> employees = FXCollections.observableArrayList();
    private final ObservableList<SalaryStructure> salaryStructures = FXCollections.observableArrayList();

    private EmployeeStore() {}

    public static EmployeeStore getInstance() { return INSTANCE; }

    public ObservableList<Employee> getEmployees() { return employees; }

    public ObservableList<SalaryStructure> getSalaryStructures() { return salaryStructures; }

    public Optional<Employee> findById(String id) {
        return employees.stream().filter(e -> id.equals(e.getId())).findFirst();
    }

    public Optional<Employee> findByEmployeeNumber(String empNo) {
        return employees.stream().filter(e -> empNo.equals(e.getEmployeeNumber())).findFirst();
    }

    public List<SalaryStructure> findSalaryStructuresByEmployeeId(String employeeId) {
        return salaryStructures.stream()
                .filter(s -> employeeId.equals(s.getEmployeeId()) && s.isActive())
                .toList();
    }

    public Optional<SalaryStructure> findActiveSalaryStructure(String employeeId) {
        return salaryStructures.stream()
                .filter(s -> employeeId.equals(s.getEmployeeId()) && s.isActive())
                .findFirst();
    }

    public List<Employee> findActiveEmployees() {
        return employees.stream()
                .filter(e -> e.getEmploymentStatus() == Employee.EmploymentStatus.ACTIVE)
                .toList();
    }

    public List<String> getDepartments() {
        return employees.stream()
                .map(Employee::getDepartment)
                .filter(d -> d != null && !d.isBlank())
                .distinct()
                .sorted()
                .toList();
    }

    public synchronized void clear() {
        employees.clear();
        salaryStructures.clear();
    }
}
