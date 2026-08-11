package com.schaccs.ui.payroll;

import com.schaccs.config.CurrencyConfig;
import com.schaccs.model.payroll.PayrollItem;
import com.schaccs.model.payroll.PayrollRun;
import com.schaccs.service.Services;
import com.schaccs.service.payroll.PayrollService;
import com.schaccs.ui.layout.MainLayout;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.time.Month;

/**
 * Payroll workspace. The workflow is:
 * 1. Generate a Draft run for a month/year.
 * 2. Approve the draft.
 * 3. Post the approved run to the General Ledger.
 * 4. Reverse a posted run to undo it.
 * Double-click a run (or press "View Items") to see each employee's line item.
 */
public class PayrollView extends VBox implements MainLayout.Refreshable {

    private final PayrollService service;
    private final TabPane tabPane = new TabPane();
    private final TableView<PayrollRun> runsTable = new TableView<>();
    private final TableView<PayrollItem> itemsTable = new TableView<>();

    public PayrollView() {
        this(Services.getInstance().payroll());
    }

    public PayrollView(PayrollService service) {
        this.service = service;
        setSpacing(12);
        setPadding(new Insets(16));

        Label title = new Label("Payroll Management");
        title.getStyleClass().add("section-title");

        Label sub = new Label("Pick a month and year, generate the payroll (Draft), approve it, then post it "
                + "to the General Ledger. Use Reverse to undo a posted run.");
        sub.getStyleClass().add("muted");
        sub.setWrapText(true);

        tabPane.getTabs().addAll(createProcessingTab(), createItemsTab());
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        VBox.setVgrow(tabPane, Priority.ALWAYS);

        getChildren().addAll(title, sub, tabPane);
    }

    private Tab createProcessingTab() {
        Tab tab = new Tab("Process Payroll");

        ComboBox<Month> monthBox = new ComboBox<>();
        monthBox.getItems().addAll(Month.values());
        monthBox.setValue(LocalDate.now().getMonth());
        monthBox.setPrefWidth(140);

        ComboBox<Integer> yearBox = new ComboBox<>();
        int currentYear = LocalDate.now().getYear();
        yearBox.getItems().addAll(currentYear - 1, currentYear, currentYear + 1);
        yearBox.setValue(currentYear);
        yearBox.setPrefWidth(100);

        Button generateBtn = new Button("Generate Payroll");
        generateBtn.getStyleClass().add("primary-button");
        generateBtn.setOnAction(e -> generate(monthBox.getValue(), yearBox.getValue()));

        HBox periodBox = new HBox(10, new Label("Month:"), monthBox,
                new Label("Year:"), yearBox, generateBtn);
        periodBox.setAlignment(Pos.CENTER_LEFT);

        setupRunsTable();

        Button approveBtn = new Button("Approve Selected");
        approveBtn.getStyleClass().add("success-button");
        approveBtn.setOnAction(e -> {
            PayrollRun run = selectedRun();
            if (run == null) return;
            try {
                service.approvePayroll(run.getId());
                refresh();
                showAlert(Alert.AlertType.INFORMATION, "Approved",
                        "Payroll " + run.getRunNumber() + " approved.");
            } catch (Exception ex) {
                showAlert(Alert.AlertType.ERROR, "Error", ex.getMessage());
            }
        });

        Button postBtn = new Button("Post to GL");
        postBtn.getStyleClass().add("primary-button");
        postBtn.setOnAction(e -> {
            PayrollRun run = selectedRun();
            if (run == null) return;
            try {
                service.postPayroll(run.getId());
                refresh();
                showAlert(Alert.AlertType.INFORMATION, "Posted",
                        "Payroll " + run.getRunNumber() + " posted to General Ledger.");
            } catch (Exception ex) {
                showAlert(Alert.AlertType.ERROR, "Error", ex.getMessage());
            }
        });

        Button reverseBtn = new Button("Reverse");
        reverseBtn.getStyleClass().add("danger-button");
        reverseBtn.setOnAction(e -> {
            PayrollRun run = selectedRun();
            if (run == null) return;
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                    "Reverse payroll " + run.getRunNumber() + "?\nThis will create reversal journal entries.",
                    ButtonType.YES, ButtonType.NO);
            confirm.showAndWait().ifPresent(btn -> {
                if (btn == ButtonType.YES) {
                    try {
                        service.reversePayroll(run.getId());
                        refresh();
                        showAlert(Alert.AlertType.INFORMATION, "Reversed",
                                "Payroll " + run.getRunNumber() + " reversed.");
                    } catch (Exception ex) {
                        showAlert(Alert.AlertType.ERROR, "Error", ex.getMessage());
                    }
                }
            });
        });

        Button recalcBtn = new Button("Recalculate");
        recalcBtn.getStyleClass().add("secondary-button");
        recalcBtn.setOnAction(e -> {
            PayrollRun run = selectedRun();
            if (run == null) return;
            try {
                service.recalculatePayroll(run.getId());
                refresh();
                showAlert(Alert.AlertType.INFORMATION, "Recalculated",
                        "Payroll " + run.getRunNumber() + " recalculated.");
            } catch (Exception ex) {
                showAlert(Alert.AlertType.ERROR, "Error", ex.getMessage());
            }
        });

        Button viewItemsBtn = new Button("View Items");
        viewItemsBtn.getStyleClass().add("secondary-button");
        viewItemsBtn.setOnAction(e -> {
            PayrollRun run = selectedRun();
            if (run == null) return;
            loadItems(run);
            tabPane.getSelectionModel().select(1);
        });

        HBox actions = new HBox(8, approveBtn, postBtn, reverseBtn, recalcBtn, viewItemsBtn);
        actions.setAlignment(Pos.CENTER_LEFT);

        VBox content = new VBox(12, periodBox, runsTable, actions);
        content.setPadding(new Insets(12));
        VBox.setVgrow(runsTable, Priority.ALWAYS);

        tab.setContent(content);
        return tab;
    }

    private Tab createItemsTab() {
        Tab tab = new Tab("Payroll Items");
        setupItemsTable();
        VBox box = new VBox(12, itemsTable);
        box.setPadding(new Insets(12));
        VBox.setVgrow(itemsTable, Priority.ALWAYS);
        tab.setContent(box);
        return tab;
    }

    private void setupRunsTable() {
        TableColumn<PayrollRun, String> runNoCol = new TableColumn<>("Run #");
        runNoCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getRunNumber()));
        runNoCol.setPrefWidth(140);

        TableColumn<PayrollRun, String> periodCol = new TableColumn<>("Period");
        periodCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getPeriodLabel()));
        periodCol.setPrefWidth(80);

        TableColumn<PayrollRun, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(d -> new SimpleStringProperty(
                d.getValue().getStatus() != null ? d.getValue().getStatus().getDisplayName() : ""));
        statusCol.setPrefWidth(130);

        TableColumn<PayrollRun, String> empCountCol = new TableColumn<>("Employees");
        empCountCol.setCellValueFactory(d -> new SimpleStringProperty(String.valueOf(d.getValue().getEmployeeCount())));
        empCountCol.setPrefWidth(90);

        TableColumn<PayrollRun, String> grossCol = new TableColumn<>("Gross Pay");
        grossCol.setCellValueFactory(d -> new SimpleStringProperty(CurrencyConfig.format(d.getValue().getTotalGrossPay())));
        grossCol.setPrefWidth(130);

        TableColumn<PayrollRun, String> deductionsCol = new TableColumn<>("Deductions");
        deductionsCol.setCellValueFactory(d -> new SimpleStringProperty(CurrencyConfig.format(d.getValue().getTotalDeductions())));
        deductionsCol.setPrefWidth(130);

        TableColumn<PayrollRun, String> netCol = new TableColumn<>("Net Pay");
        netCol.setCellValueFactory(d -> new SimpleStringProperty(CurrencyConfig.format(d.getValue().getTotalNetPay())));
        netCol.setPrefWidth(130);

        TableColumn<PayrollRun, String> preparedCol = new TableColumn<>("Prepared By");
        preparedCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getPreparedBy()));
        preparedCol.setPrefWidth(110);

        TableColumn<PayrollRun, String> postedCol = new TableColumn<>("Posted By");
        postedCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getPostedBy()));
        postedCol.setPrefWidth(110);

        runsTable.getColumns().addAll(runNoCol, periodCol, statusCol, empCountCol,
                grossCol, deductionsCol, netCol, preparedCol, postedCol);
        runsTable.setItems(service.getStore().getPayrollRuns());
        runsTable.setPlaceholder(new Label("No payroll runs yet. Pick a month/year above and press Generate."));
        runsTable.setRowFactory(tv -> {
            TableRow<PayrollRun> row = new TableRow<>();
            row.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !row.isEmpty()) {
                    loadItems(row.getItem());
                    tabPane.getSelectionModel().select(1);
                }
            });
            return row;
        });
    }

    private void setupItemsTable() {
        TableColumn<PayrollItem, String> empNoCol = new TableColumn<>("Emp #");
        empNoCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getEmployeeNumber()));
        empNoCol.setPrefWidth(90);

        TableColumn<PayrollItem, String> nameCol = new TableColumn<>("Employee");
        nameCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getEmployeeName()));
        nameCol.setPrefWidth(160);

        TableColumn<PayrollItem, String> deptCol = new TableColumn<>("Department");
        deptCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getDepartment()));
        deptCol.setPrefWidth(120);

        TableColumn<PayrollItem, String> basicCol = new TableColumn<>("Basic Salary");
        basicCol.setCellValueFactory(d -> new SimpleStringProperty(CurrencyConfig.format(d.getValue().getBasicSalary())));
        basicCol.setPrefWidth(120);

        TableColumn<PayrollItem, String> allowancesCol = new TableColumn<>("Allowances");
        allowancesCol.setCellValueFactory(d -> new SimpleStringProperty(CurrencyConfig.format(d.getValue().getTotalAllowances())));
        allowancesCol.setPrefWidth(120);

        TableColumn<PayrollItem, String> grossCol = new TableColumn<>("Gross Pay");
        grossCol.setCellValueFactory(d -> new SimpleStringProperty(CurrencyConfig.format(d.getValue().getGrossPay())));
        grossCol.setPrefWidth(120);

        TableColumn<PayrollItem, String> payeCol = new TableColumn<>("PAYE");
        payeCol.setCellValueFactory(d -> new SimpleStringProperty(CurrencyConfig.format(d.getValue().getPaye())));
        payeCol.setPrefWidth(100);

        TableColumn<PayrollItem, String> nssfCol = new TableColumn<>("NSSF");
        nssfCol.setCellValueFactory(d -> new SimpleStringProperty(CurrencyConfig.format(d.getValue().getNssf())));
        nssfCol.setPrefWidth(100);

        TableColumn<PayrollItem, String> shifCol = new TableColumn<>("SHIF");
        shifCol.setCellValueFactory(d -> new SimpleStringProperty(CurrencyConfig.format(d.getValue().getShif())));
        shifCol.setPrefWidth(100);

        TableColumn<PayrollItem, String> totalDedCol = new TableColumn<>("Total Deductions");
        totalDedCol.setCellValueFactory(d -> new SimpleStringProperty(CurrencyConfig.format(d.getValue().getTotalDeductions())));
        totalDedCol.setPrefWidth(130);

        TableColumn<PayrollItem, String> netCol = new TableColumn<>("Net Pay");
        netCol.setCellValueFactory(d -> new SimpleStringProperty(CurrencyConfig.format(d.getValue().getNetPay())));
        netCol.setPrefWidth(130);

        itemsTable.getColumns().addAll(empNoCol, nameCol, deptCol, basicCol, allowancesCol,
                grossCol, payeCol, nssfCol, shifCol, totalDedCol, netCol);
        itemsTable.setPlaceholder(new Label("Select a payroll run and press \"View Items\" to see line items."));
    }

    private PayrollRun selectedRun() {
        PayrollRun run = runsTable.getSelectionModel().getSelectedItem();
        if (run == null) {
            showAlert(Alert.AlertType.WARNING, "No Selection", "Select a payroll run first.");
        }
        return run;
    }

    private void generate(Month month, Integer year) {
        if (month == null || year == null) return;
        try {
            PayrollRun run = service.generatePayroll(month.getValue(), year);
            refresh();
            showAlert(Alert.AlertType.INFORMATION, "Success",
                    "Payroll generated: " + run.getRunNumber()
                            + "\nEmployees: " + run.getEmployeeCount()
                            + "\nTotal Net Pay: " + CurrencyConfig.format(run.getTotalNetPay()));
        } catch (IllegalStateException ex) {
            showAlert(Alert.AlertType.WARNING, "Error", ex.getMessage());
        }
    }

    private void loadItems(PayrollRun run) {
        itemsTable.setItems(FXCollections.observableArrayList(service.findItemsByRunId(run.getId())));
    }

    private void showAlert(Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type, message, ButtonType.OK);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.showAndWait();
    }

    @Override
    public void refresh() {
        runsTable.refresh();
        itemsTable.refresh();
    }
}
