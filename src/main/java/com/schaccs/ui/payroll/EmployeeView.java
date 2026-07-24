package com.schaccs.ui.payroll;

import com.schaccs.config.CurrencyConfig;
import com.schaccs.model.payroll.Employee;
import com.schaccs.model.payroll.SalaryStructure;
import com.schaccs.service.Services;
import com.schaccs.service.payroll.EmployeeService;
import com.schaccs.style.AppStyles;
import com.schaccs.ui.layout.MainLayout;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.time.LocalDate;
import java.util.Optional;

public class EmployeeView extends VBox implements MainLayout.Refreshable {

    private final EmployeeService service;
    private final TextField searchField = new TextField();
    private final TableView<Employee> table = new TableView<>();
    private final FilteredList<Employee> filteredList;

    public EmployeeView() {
        this(Services.getInstance().employee());
    }

    public EmployeeView(EmployeeService service) {
        this.service = service;
        this.filteredList = new FilteredList<>(service.getStore().getEmployees());
        getStyleClass().add("view-container");
        setSpacing(12);
        setPadding(new Insets(16));

        buildHeader();
        buildTable();
    }

    private void buildHeader() {
        Label title = new Label("Employee Management");
        title.getStyleClass().add("view-title");

        searchField.setPromptText("Search employees...");
        searchField.setPrefWidth(280);
        searchField.textProperty().addListener((obs, oldVal, newVal) -> {
            String q = newVal == null ? "" : newVal.trim().toLowerCase();
            filteredList.setPredicate(emp -> emp.matchesSearch(q));
        });

        Button addBtn = new Button("+ New Employee");
        addBtn.getStyleClass().add("btn-primary");
        addBtn.setOnAction(e -> showAddDialog());

        HBox header = new HBox(12, title, searchField, addBtn);
        header.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(searchField, Priority.ALWAYS);
        getChildren().add(header);
    }

    private void buildTable() {
        TableColumn<Employee, String> empNoCol = new TableColumn<>("Employee #");
        empNoCol.setCellValueFactory(data -> data.getValue().employeeNumberProperty());
        empNoCol.setPrefWidth(110);

        TableColumn<Employee, String> nameCol = new TableColumn<>("Full Name");
        nameCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getFullName()));
        nameCol.setPrefWidth(180);

        TableColumn<Employee, String> deptCol = new TableColumn<>("Department");
        deptCol.setCellValueFactory(data -> data.getValue().departmentProperty());
        deptCol.setPrefWidth(130);

        TableColumn<Employee, String> posCol = new TableColumn<>("Position");
        posCol.setCellValueFactory(data -> data.getValue().positionProperty());
        posCol.setPrefWidth(140);

        TableColumn<Employee, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(data -> data.getValue().employmentStatusProperty().asString());
        statusCol.setPrefWidth(100);

        TableColumn<Employee, String> phoneCol = new TableColumn<>("Phone");
        phoneCol.setCellValueFactory(data -> data.getValue().phoneProperty());
        phoneCol.setPrefWidth(120);

        TableColumn<Employee, String> kraCol = new TableColumn<>("KRA PIN");
        kraCol.setCellValueFactory(data -> data.getValue().kraPinProperty());
        kraCol.setPrefWidth(110);

        TableColumn<Employee, Void> actionCol = new TableColumn<>("Actions");
        actionCol.setPrefWidth(180);
        actionCol.setCellFactory(col -> new TableCell<>() {
            {
                Button editBtn = new Button("Edit");
                editBtn.getStyleClass().add("btn-small");
                Button salaryBtn = new Button("Salary");
                salaryBtn.getStyleClass().add("btn-small");
                Button termBtn = new Button("Terminate");
                termBtn.getStyleClass().add("btn-small-danger");

                editBtn.setOnAction(e -> {
                    Employee emp = getTableView().getItems().get(getIndex());
                    showEditDialog(emp);
                });
                salaryBtn.setOnAction(e -> {
                    Employee emp = getTableView().getItems().get(getIndex());
                    showSalaryDialog(emp);
                });
                termBtn.setOnAction(e -> {
                    Employee emp = getTableView().getItems().get(getIndex());
                    confirmTerminate(emp);
                });

                HBox box = new HBox(4, editBtn, salaryBtn, termBtn);
                setGraphic(box);
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getIndex() >= getTableView().getItems().size()) {
                    setGraphic(null);
                }
            }
        });

        table.getColumns().addAll(empNoCol, nameCol, deptCol, posCol, statusCol, phoneCol, kraCol, actionCol);
        table.setPlaceholder(new Label("No employees registered."));
        table.setRowFactory(tv -> {
            TableRow<Employee> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    showEditDialog(row.getItem());
                }
            });
            return row;
        });

        SortedList<Employee> sortedList = new SortedList<>(filteredList);
        sortedList.comparatorProperty().bind(table.comparatorProperty());
        table.setItems(sortedList);

        VBox.setVgrow(table, Priority.ALWAYS);
        getChildren().add(table);
    }

    private void showAddDialog() {
        Dialog<Employee> dialog = new Dialog<>();
        dialog.setTitle("Add New Employee");
        dialog.setHeaderText("Enter employee details");

        ButtonType saveType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveType, ButtonType.CANCEL);

        Employee emp = new Employee();

        GridPane grid = createFormGrid(emp);

        dialog.getDialogPane().setContent(grid);
        dialog.setResultConverter(btn -> {
            if (btn == saveType) {
                return emp;
            }
            return null;
        });

        dialog.showAndWait().ifPresent(result -> {
            if (result.getEmployeeNumber() != null && !result.getEmployeeNumber().isBlank()) {
                service.createEmployee(result);
                refresh();
            }
        });
    }

    private void showEditDialog(Employee emp) {
        Dialog<Employee> dialog = new Dialog<>();
        dialog.setTitle("Edit Employee — " + emp.getFullName());
        dialog.setHeaderText("Update employee details");

        ButtonType saveType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveType, ButtonType.CANCEL);

        GridPane grid = createFormGrid(emp);

        dialog.getDialogPane().setContent(grid);
        dialog.setResultConverter(btn -> {
            if (btn == saveType) return emp;
            return null;
        });

        dialog.showAndWait().ifPresent(result -> {
            service.updateEmployee(result);
            refresh();
        });
    }

    private void showSalaryDialog(Employee emp) {
        Dialog<SalaryStructure> dialog = new Dialog<>();
        dialog.setTitle("Salary Structure — " + emp.getFullName());

        ButtonType saveType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveType, ButtonType.CANCEL);

        Optional<SalaryStructure> existing = service.getActiveSalaryStructure(emp.getId());
        SalaryStructure structure = existing.orElseGet(() -> {
            SalaryStructure s = new SalaryStructure();
            s.setEmployeeId(emp.getId());
            s.setEffectiveDate(LocalDate.now());
            return s;
        });

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(8);
        grid.setPadding(new Insets(16));

        TextField basicField = new TextField(CurrencyConfig.formatPlain(structure.getBasicSalary()));
        basicField.setPromptText("0.00");
        TextField houseField = new TextField(CurrencyConfig.formatPlain(structure.getHouseAllowance()));
        houseField.setPromptText("0.00");
        TextField respField = new TextField(CurrencyConfig.formatPlain(structure.getResponsibilityAllowance()));
        respField.setPromptText("0.00");
        TextField transportField = new TextField(CurrencyConfig.formatPlain(structure.getTransportAllowance()));
        transportField.setPromptText("0.00");
        TextField loanField = new TextField(CurrencyConfig.formatPlain(structure.getStaffLoanRepayment()));
        loanField.setPromptText("0.00");
        TextField advanceField = new TextField(CurrencyConfig.formatPlain(structure.getSalaryAdvanceRecovery()));
        advanceField.setPromptText("0.00");
        TextField welfareField = new TextField(CurrencyConfig.formatPlain(structure.getWelfareContribution()));
        welfareField.setPromptText("0.00");

        int row = 0;
        grid.add(new Label("Basic Salary (KES):"), 0, row);
        grid.add(basicField, 1, row++);
        grid.add(new Label("House Allowance:"), 0, row);
        grid.add(houseField, 1, row++);
        grid.add(new Label("Responsibility Allowance:"), 0, row);
        grid.add(respField, 1, row++);
        grid.add(new Label("Transport Allowance:"), 0, row);
        grid.add(transportField, 1, row++);
        grid.add(new Label("Staff Loan Repayment:"), 0, row);
        grid.add(loanField, 1, row++);
        grid.add(new Label("Salary Advance Recovery:"), 0, row);
        grid.add(advanceField, 1, row++);
        grid.add(new Label("Welfare Contribution:"), 0, row);
        grid.add(welfareField, 1, row++);

        dialog.getDialogPane().setContent(grid);
        dialog.setResultConverter(btn -> {
            if (btn == saveType) {
                structure.setBasicSalary(CurrencyConfig.money(parseVal(basicField.getText())));
                structure.setHouseAllowance(CurrencyConfig.money(parseVal(houseField.getText())));
                structure.setResponsibilityAllowance(CurrencyConfig.money(parseVal(respField.getText())));
                structure.setTransportAllowance(CurrencyConfig.money(parseVal(transportField.getText())));
                structure.setStaffLoanRepayment(CurrencyConfig.money(parseVal(loanField.getText())));
                structure.setSalaryAdvanceRecovery(CurrencyConfig.money(parseVal(advanceField.getText())));
                structure.setWelfareContribution(CurrencyConfig.money(parseVal(welfareField.getText())));
                return structure;
            }
            return null;
        });

        dialog.showAndWait().ifPresent(result -> {
            service.assignSalaryStructure(result);
            refresh();
        });
    }

    private void confirmTerminate(Employee emp) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION,
                "Terminate employee " + emp.getFullName() + "?",
                ButtonType.YES, ButtonType.NO);
        alert.setHeaderText("Confirm Termination");
        alert.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.YES) {
                service.terminateEmployee(emp, LocalDate.now());
                refresh();
            }
        });
    }

    private GridPane createFormGrid(Employee emp) {
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(8);
        grid.setPadding(new Insets(16));

        TextField empNoField = new TextField(emp.getEmployeeNumber());
        empNoField.setPromptText("EMP001");
        TextField firstNameField = new TextField(emp.getFirstName());
        firstNameField.setPromptText("First Name");
        TextField lastNameField = new TextField(emp.getLastName());
        lastNameField.setPromptText("Last Name");
        TextField nationalIdField = new TextField(emp.getNationalId());
        nationalIdField.setPromptText("National ID");
        TextField deptField = new TextField(emp.getDepartment());
        deptField.setPromptText("Department");
        TextField posField = new TextField(emp.getPosition());
        posField.setPromptText("Position");
        DatePicker empDate = new DatePicker(emp.getEmploymentDate());
        TextField bankNameField = new TextField(emp.getBankName());
        bankNameField.setPromptText("Bank Name");
        TextField bankBranchField = new TextField(emp.getBankBranch());
        bankBranchField.setPromptText("Branch");
        TextField bankAccField = new TextField(emp.getBankAccountNumber());
        bankAccField.setPromptText("Account Number");
        TextField kraField = new TextField(emp.getKraPin());
        kraField.setPromptText("KRA PIN");
        TextField nssfField = new TextField(emp.getNssfNumber());
        nssfField.setPromptText("NSSF Number");
        TextField shifField = new TextField(emp.getShifNumber());
        shifField.setPromptText("SHIF Number");
        TextField phoneField = new TextField(emp.getPhone());
        phoneField.setPromptText("Phone");

        int row = 0;
        grid.add(new Label("Employee Number:"), 0, row);
        grid.add(empNoField, 1, row++);
        grid.add(new Label("First Name:"), 0, row);
        grid.add(firstNameField, 1, row++);
        grid.add(new Label("Last Name:"), 0, row);
        grid.add(lastNameField, 1, row++);
        grid.add(new Label("National ID:"), 0, row);
        grid.add(nationalIdField, 1, row++);
        grid.add(new Label("Department:"), 0, row);
        grid.add(deptField, 1, row++);
        grid.add(new Label("Position:"), 0, row);
        grid.add(posField, 1, row++);
        grid.add(new Label("Employment Date:"), 0, row);
        grid.add(empDate, 1, row++);
        grid.add(new Separator(), 0, row++, 2, 1);
        grid.add(new Label("Bank Name:"), 0, row);
        grid.add(bankNameField, 1, row++);
        grid.add(new Label("Bank Branch:"), 0, row);
        grid.add(bankBranchField, 1, row++);
        grid.add(new Label("Account Number:"), 0, row);
        grid.add(bankAccField, 1, row++);
        grid.add(new Separator(), 0, row++, 2, 1);
        grid.add(new Label("KRA PIN:"), 0, row);
        grid.add(kraField, 1, row++);
        grid.add(new Label("NSSF Number:"), 0, row);
        grid.add(nssfField, 1, row++);
        grid.add(new Label("SHIF Number:"), 0, row);
        grid.add(shifField, 1, row++);
        grid.add(new Label("Phone:"), 0, row);
        grid.add(phoneField, 1, row++);

        // Bind back to model on save
        emp.setEmployeeNumber(empNoField.getText());
        emp.setFirstName(firstNameField.getText());
        emp.setLastName(lastNameField.getText());
        emp.setNationalId(nationalIdField.getText());
        emp.setDepartment(deptField.getText());
        emp.setPosition(posField.getText());
        emp.setEmploymentDate(empDate.getValue());
        emp.setBankName(bankNameField.getText());
        emp.setBankBranch(bankBranchField.getText());
        emp.setBankAccountNumber(bankAccField.getText());
        emp.setKraPin(kraField.getText());
        emp.setNssfNumber(nssfField.getText());
        emp.setShifNumber(shifField.getText());
        emp.setPhone(phoneField.getText());

        return grid;
    }

    private double parseVal(String text) {
        if (text == null || text.isBlank()) return 0;
        return Double.parseDouble(text.replaceAll("[^\\d.]", ""));
    }

    @Override
    public void refresh() {
        table.refresh();
    }
}
