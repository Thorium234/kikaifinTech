package com.schaccs.ui.payroll;

import com.schaccs.config.CurrencyConfig;
import com.schaccs.model.payroll.Employee;
import com.schaccs.model.payroll.SalaryStructure;
import com.schaccs.service.Services;
import com.schaccs.service.payroll.EmployeeService;
import com.schaccs.ui.layout.MainLayout;
import com.schaccs.util.AlertUtil;
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
    private final TabPane tabPane = new TabPane();
    private final Tab listTab = new Tab("Employees List");
    private final Tab formTab = new Tab("Add Employee");
    private final Tab detailsTab = new Tab("Employee Details");

    private Employee viewing;

    private final Label detailEmpNo = new Label();
    private final Label detailName = new Label();
    private final Label detailNationalId = new Label();
    private final Label detailDept = new Label();
    private final Label detailPos = new Label();
    private final Label detailDate = new Label();
    private final Label detailStatus = new Label();
    private final Label detailBank = new Label();
    private final Label detailBranch = new Label();
    private final Label detailAcc = new Label();
    private final Label detailKra = new Label();
    private final Label detailNssf = new Label();
    private final Label detailShif = new Label();
    private final Label detailPhone = new Label();

    private final TextField empNoField = new TextField();
    private final TextField firstNameField = new TextField();
    private final TextField lastNameField = new TextField();
    private final TextField nationalIdField = new TextField();
    private final TextField deptField = new TextField();
    private final TextField posField = new TextField();
    private final DatePicker empDate = new DatePicker();
    private final TextField bankNameField = new TextField();
    private final TextField bankBranchField = new TextField();
    private final TextField bankAccField = new TextField();
    private final TextField kraField = new TextField();
    private final TextField nssfField = new TextField();
    private final TextField shifField = new TextField();
    private final TextField phoneField = new TextField();

    private Employee editing;

    public EmployeeView() {
        this(Services.getInstance().employee());
    }

    public EmployeeView(EmployeeService service) {
        this.service = service;
        this.filteredList = new FilteredList<>(service.getStore().getEmployees());
        setSpacing(12);
        setPadding(new Insets(16));

        buildHeader();
        buildListTab();
        buildFormTab();
        buildDetailsTab();
        tabPane.getTabs().addAll(listTab, formTab, detailsTab);
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        VBox.setVgrow(tabPane, Priority.ALWAYS);

        getChildren().addAll(tabPane);
    }

    private void buildHeader() {
        Label title = new Label("Employee Management");
        title.getStyleClass().add("section-title");

        HBox header = new HBox(title);
        header.setAlignment(Pos.CENTER_LEFT);
        getChildren().add(header);
    }

    private void buildListTab() {
        searchField.setPromptText("Search employees...");
        searchField.setPrefWidth(280);
        searchField.textProperty().addListener((obs, oldVal, newVal) -> {
            String q = newVal == null ? "" : newVal.trim().toLowerCase();
            filteredList.setPredicate(emp -> emp.matchesSearch(q));
        });

        Button addBtn = new Button("Add Employee");
        addBtn.getStyleClass().add("primary-button");
        addBtn.setOnAction(e -> { clearForm(); switchToForm(); });

        HBox toolbar = new HBox(12, searchField, addBtn);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(searchField, Priority.ALWAYS);

        setupTable();

        VBox card = new VBox(12, toolbar, table);
        card.getStyleClass().add("card");
        VBox.setVgrow(table, Priority.ALWAYS);

        listTab.setContent(card);
    }

    private void setupTable() {
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
        actionCol.setPrefWidth(90);
        actionCol.setCellFactory(col -> new TableCell<>() {
            {
                Button viewBtn = new Button("View");
                viewBtn.getStyleClass().add("secondary-button");

                viewBtn.setOnAction(e -> {
                    Employee emp = getTableView().getItems().get(getIndex());
                    showDetails(emp);
                });

                HBox box = new HBox(4, viewBtn);
                box.setAlignment(Pos.CENTER);
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
                    loadForm(row.getItem());
                    switchToForm();
                }
            });
            return row;
        });

        SortedList<Employee> sortedList = new SortedList<>(filteredList);
        sortedList.comparatorProperty().bind(table.comparatorProperty());
        table.setItems(sortedList);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
    }

    private void buildFormTab() {
        empNoField.setPromptText("EMP001");
        firstNameField.setPromptText("First Name");
        lastNameField.setPromptText("Last Name");
        nationalIdField.setPromptText("National ID");
        deptField.setPromptText("Department");
        posField.setPromptText("Position");
        bankNameField.setPromptText("Bank Name");
        bankBranchField.setPromptText("Branch");
        bankAccField.setPromptText("Account Number");
        kraField.setPromptText("KRA PIN");
        nssfField.setPromptText("NSSF Number");
        shifField.setPromptText("SHIF Number");
        phoneField.setPromptText("Phone");

        Button saveBtn = new Button("Save Employee");
        saveBtn.getStyleClass().add("primary-button");
        saveBtn.setOnAction(e -> save());

        Button clearBtn = new Button("Clear");
        clearBtn.getStyleClass().add("secondary-button");
        clearBtn.setOnAction(e -> clearForm());

        HBox actions = new HBox(10, saveBtn, clearBtn);
        actions.setAlignment(Pos.CENTER_LEFT);
        actions.setPadding(new Insets(12, 0, 0, 0));

        GridPane grid = new GridPane();
        grid.setHgap(20);
        grid.setVgap(4);
        ColumnConstraints cc = new ColumnConstraints();
        cc.setFillWidth(true);
        cc.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(cc, cc);

        grid.add(labeled("Employee Number", empNoField), 0, 0);
        grid.add(labeled("First Name", firstNameField), 1, 0);
        grid.add(labeled("Last Name", lastNameField), 0, 1);
        grid.add(labeled("National ID", nationalIdField), 1, 1);
        grid.add(labeled("Department", deptField), 0, 2);
        grid.add(labeled("Position", posField), 1, 2);
        grid.add(labeled("Employment Date", empDate), 0, 3);
        grid.add(new Label(""), 1, 3);
        grid.add(new Separator(), 0, 4, 2, 1);
        grid.add(labeled("Bank Name", bankNameField), 0, 5);
        grid.add(labeled("Bank Branch", bankBranchField), 1, 5);
        grid.add(labeled("Account Number", bankAccField), 0, 6);
        grid.add(new Label(""), 1, 6);
        grid.add(new Separator(), 0, 7, 2, 1);
        grid.add(labeled("KRA PIN", kraField), 0, 8);
        grid.add(labeled("NSSF Number", nssfField), 1, 8);
        grid.add(labeled("SHIF Number", shifField), 0, 9);
        grid.add(labeled("Phone", phoneField), 1, 9);

        VBox card = new VBox(14, grid, actions);
        card.getStyleClass().add("card");
        card.setMaxWidth(760);

        ScrollPane formScroll = new ScrollPane(card);
        formScroll.setFitToWidth(true);
        formScroll.setFitToHeight(true);
        formScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        formScroll.getStyleClass().add("content-scroll");

        formTab.setContent(formScroll);
    }

    private void buildDetailsTab() {
        Button editBtn = new Button("Edit");
        editBtn.getStyleClass().add("secondary-button");
        Button salaryBtn = new Button("Salary");
        salaryBtn.getStyleClass().add("secondary-button");
        Button termBtn = new Button("Terminate");
        termBtn.getStyleClass().add("danger-button");
        Button backBtn = new Button("Back to List");
        backBtn.getStyleClass().add("secondary-button");

        editBtn.setOnAction(e -> {
            if (viewing != null) {
                loadForm(viewing);
                switchToForm();
            }
        });
        salaryBtn.setOnAction(e -> {
            if (viewing != null) {
                showSalaryDialog(viewing);
            }
        });
        termBtn.setOnAction(e -> {
            if (viewing != null) {
                confirmTerminate(viewing);
                switchToList();
            }
        });
        backBtn.setOnAction(e -> switchToList());

        HBox actions = new HBox(10, editBtn, salaryBtn, termBtn, backBtn);
        actions.setAlignment(Pos.CENTER_LEFT);
        actions.setPadding(new Insets(12, 0, 0, 0));

        GridPane grid = new GridPane();
        grid.setHgap(20);
        grid.setVgap(10);
        ColumnConstraints cc = new ColumnConstraints();
        cc.setFillWidth(true);
        cc.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(cc, cc);

        grid.add(value("Employee #", detailEmpNo), 0, 0);
        grid.add(value("Full Name", detailName), 1, 0);
        grid.add(value("National ID", detailNationalId), 0, 1);
        grid.add(value("Department", detailDept), 1, 1);
        grid.add(value("Position", detailPos), 0, 2);
        grid.add(value("Employment Date", detailDate), 1, 2);
        grid.add(value("Employment Status", detailStatus), 0, 3);
        grid.add(new Label(""), 1, 3);
        grid.add(new Separator(), 0, 4, 2, 1);
        grid.add(value("Bank Name", detailBank), 0, 5);
        grid.add(value("Bank Branch", detailBranch), 1, 5);
        grid.add(value("Account Number", detailAcc), 0, 6);
        grid.add(new Label(""), 1, 6);
        grid.add(new Separator(), 0, 7, 2, 1);
        grid.add(value("KRA PIN", detailKra), 0, 8);
        grid.add(value("NSSF Number", detailNssf), 1, 8);
        grid.add(value("SHIF Number", detailShif), 0, 9);
        grid.add(value("Phone", detailPhone), 1, 9);

        VBox card = new VBox(14, grid, actions);
        card.getStyleClass().add("card");
        card.setMaxWidth(760);

        ScrollPane detailsScroll = new ScrollPane(card);
        detailsScroll.setFitToWidth(true);
        detailsScroll.setFitToHeight(true);
        detailsScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        detailsScroll.getStyleClass().add("content-scroll");

        detailsTab.setContent(detailsScroll);
    }

    private VBox value(String label, Label field) {
        Label lbl = new Label(label);
        lbl.getStyleClass().add("pay-label");
        field.getStyleClass().add("pay-value");
        VBox box = new VBox(4, lbl, field);
        return box;
    }

    private void showDetails(Employee emp) {
        viewing = emp;
        detailEmpNo.setText(emp.getEmployeeNumber());
        detailName.setText(emp.getFullName());
        detailNationalId.setText(orDash(emp.getNationalId()));
        detailDept.setText(orDash(emp.getDepartment()));
        detailPos.setText(orDash(emp.getPosition()));
        detailDate.setText(emp.getEmploymentDate() == null ? "—" : emp.getEmploymentDate().toString());
        detailStatus.setText(emp.getEmploymentStatus() == null ? "—" : emp.getEmploymentStatus().getDisplayName());
        detailBank.setText(orDash(emp.getBankName()));
        detailBranch.setText(orDash(emp.getBankBranch()));
        detailAcc.setText(orDash(emp.getBankAccountNumber()));
        detailKra.setText(orDash(emp.getKraPin()));
        detailNssf.setText(orDash(emp.getNssfNumber()));
        detailShif.setText(orDash(emp.getShifNumber()));
        detailPhone.setText(orDash(emp.getPhone()));
        detailsTab.setText("Employee Details — " + emp.getFullName());
        switchToDetails();
    }

    private String orDash(String value) {
        return value == null || value.isBlank() ? "—" : value;
    }

    private VBox labeled(String label, javafx.scene.Node field) {
        Label lbl = new Label(label);
        if (field instanceof Region r) r.setMaxWidth(Double.MAX_VALUE);
        VBox box = new VBox(4, lbl, field);
        return box;
    }

    private void clearForm() {
        editing = null;
        empNoField.clear();
        firstNameField.clear();
        lastNameField.clear();
        nationalIdField.clear();
        deptField.clear();
        posField.clear();
        empDate.setValue(null);
        bankNameField.clear();
        bankBranchField.clear();
        bankAccField.clear();
        kraField.clear();
        nssfField.clear();
        shifField.clear();
        phoneField.clear();
        empNoField.setDisable(false);
        formTab.setText("Add Employee");
    }

    private void loadForm(Employee emp) {
        editing = emp;
        empNoField.setText(emp.getEmployeeNumber());
        empNoField.setDisable(true);
        firstNameField.setText(emp.getFirstName());
        lastNameField.setText(emp.getLastName());
        nationalIdField.setText(emp.getNationalId());
        deptField.setText(emp.getDepartment());
        posField.setText(emp.getPosition());
        empDate.setValue(emp.getEmploymentDate());
        bankNameField.setText(emp.getBankName());
        bankBranchField.setText(emp.getBankBranch());
        bankAccField.setText(emp.getBankAccountNumber());
        kraField.setText(emp.getKraPin());
        nssfField.setText(emp.getNssfNumber());
        shifField.setText(emp.getShifNumber());
        phoneField.setText(emp.getPhone());
        formTab.setText("Edit Employee");
    }

    private void save() {
        String empNo = empNoField.getText().trim();
        String firstName = firstNameField.getText().trim();
        String lastName = lastNameField.getText().trim();

        if (empNo.isEmpty() || firstName.isEmpty() || lastName.isEmpty()) {
            AlertUtil.warn("Missing fields", "Employee number, first name, and last name are required.");
            return;
        }

        if (editing == null) {
            Employee emp = new Employee();
            emp.setEmployeeNumber(empNo);
            emp.setFirstName(firstName);
            emp.setLastName(lastName);
            emp.setNationalId(nationalIdField.getText().trim());
            emp.setDepartment(deptField.getText().trim());
            emp.setPosition(posField.getText().trim());
            emp.setEmploymentDate(empDate.getValue());
            emp.setBankName(bankNameField.getText().trim());
            emp.setBankBranch(bankBranchField.getText().trim());
            emp.setBankAccountNumber(bankAccField.getText().trim());
            emp.setKraPin(kraField.getText().trim());
            emp.setNssfNumber(nssfField.getText().trim());
            emp.setShifNumber(shifField.getText().trim());
            emp.setPhone(phoneField.getText().trim());
            if (service.findByEmployeeNumber(empNo).isPresent()) {
                AlertUtil.warn("Duplicate", "An employee with number " + empNo + " already exists.");
                return;
            }
            service.createEmployee(emp);
            AlertUtil.info("Saved", "Employee " + empNo + " added.");
        } else {
            editing.setEmployeeNumber(empNo);
            editing.setFirstName(firstName);
            editing.setLastName(lastName);
            editing.setNationalId(nationalIdField.getText().trim());
            editing.setDepartment(deptField.getText().trim());
            editing.setPosition(posField.getText().trim());
            editing.setEmploymentDate(empDate.getValue());
            editing.setBankName(bankNameField.getText().trim());
            editing.setBankBranch(bankBranchField.getText().trim());
            editing.setBankAccountNumber(bankAccField.getText().trim());
            editing.setKraPin(kraField.getText().trim());
            editing.setNssfNumber(nssfField.getText().trim());
            editing.setShifNumber(shifField.getText().trim());
            editing.setPhone(phoneField.getText().trim());
            service.updateEmployee(editing);
            AlertUtil.info("Saved", "Employee details updated.");
        }
        table.refresh();
        clearForm();
        switchToList();
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

    private void switchToList() {
        tabPane.getSelectionModel().select(listTab);
    }

    private void switchToForm() {
        tabPane.getSelectionModel().select(formTab);
    }

    private void switchToDetails() {
        tabPane.getSelectionModel().select(detailsTab);
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
