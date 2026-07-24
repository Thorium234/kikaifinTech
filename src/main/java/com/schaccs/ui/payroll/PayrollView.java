package com.schaccs.ui.payroll;

import com.schaccs.config.CurrencyConfig;
import com.schaccs.model.payroll.PayrollItem;
import com.schaccs.model.payroll.PayrollRun;
import com.schaccs.service.Services;
import com.schaccs.service.payroll.PayrollService;
import com.schaccs.ui.layout.MainLayout;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.time.LocalDate;
import java.time.Month;
import java.util.List;

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
        getStyleClass().add("view-container");
        setSpacing(12);
        setPadding(new Insets(16));

        Label title = new Label("Payroll Management");
        title.getStyleClass().add("view-title");
        getChildren().add(title);

        tabPane.getTabs().addAll(createProcessingTab(), createRunsTab(), createItemsTab());
        VBox.setVgrow(tabPane, Priority.ALWAYS);
        getChildren().add(tabPane);
    }

    private Tab createProcessingTab() {
        Tab tab = new Tab("Process Payroll");

        VBox content = new VBox(12);
        content.setPadding(new Insets(12));

        // Period selector
        HBox periodBox = new HBox(10);
        periodBox.setAlignment(Pos.CENTER_LEFT);

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
        generateBtn.getStyleClass().add("btn-primary");
        generateBtn.setOnAction(e -> {
            try {
                PayrollRun run = service.generatePayroll(monthBox.getValue().getValue(), yearBox.getValue());
                refresh();
                showAlert(Alert.AlertType.INFORMATION, "Success",
                        "Payroll generated: " + run.getRunNumber()
                                + "\nEmployees: " + run.getEmployeeCount()
                                + "\nTotal Net Pay: " + CurrencyConfig.format(run.getTotalNetPay()));
            } catch (IllegalStateException ex) {
                showAlert(Alert.AlertType.WARNING, "Error", ex.getMessage());
            }
        });

        periodBox.getChildren().addAll(
                new Label("Month:"), monthBox,
                new Label("Year:"), yearBox,
                generateBtn);

        content.getChildren().add(periodBox);

        // Payroll runs summary table
        buildRunsSummaryTable();

        // Action buttons
        HBox actionBox = new HBox(8);
        actionBox.setAlignment(Pos.CENTER_LEFT);

        Button approveBtn = new Button("Approve Selected");
        approveBtn.getStyleClass().add("btn-success");
        approveBtn.setOnAction(e -> {
            PayrollRun selected = runsTable.getSelectionModel().getSelectedItem();
            if (selected == null) {
                showAlert(Alert.AlertType.WARNING, "No Selection", "Please select a payroll run.");
                return;
            }
            try {
                service.approvePayroll(selected.getId());
                refresh();
                showAlert(Alert.AlertType.INFORMATION, "Approved", "Payroll " + selected.getRunNumber() + " approved.");
            } catch (Exception ex) {
                showAlert(Alert.AlertType.ERROR, "Error", ex.getMessage());
            }
        });

        Button postBtn = new Button("Post to GL");
        postBtn.getStyleClass().add("btn-primary");
        postBtn.setOnAction(e -> {
            PayrollRun selected = runsTable.getSelectionModel().getSelectedItem();
            if (selected == null) {
                showAlert(Alert.AlertType.WARNING, "No Selection", "Please select a payroll run.");
                return;
            }
            try {
                service.postPayroll(selected.getId());
                refresh();
                showAlert(Alert.AlertType.INFORMATION, "Posted",
                        "Payroll " + selected.getRunNumber() + " posted to General Ledger.");
            } catch (Exception ex) {
                showAlert(Alert.AlertType.ERROR, "Error", ex.getMessage());
            }
        });

        Button reverseBtn = new Button("Reverse");
        reverseBtn.getStyleClass().add("btn-danger");
        reverseBtn.setOnAction(e -> {
            PayrollRun selected = runsTable.getSelectionModel().getSelectedItem();
            if (selected == null) {
                showAlert(Alert.AlertType.WARNING, "No Selection", "Please select a payroll run.");
                return;
            }
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                    "Reverse payroll " + selected.getRunNumber() + "?\nThis will create reversal journal entries.",
                    ButtonType.YES, ButtonType.NO);
            confirm.showAndWait().ifPresent(btn -> {
                if (btn == ButtonType.YES) {
                    try {
                        service.reversePayroll(selected.getId());
                        refresh();
                        showAlert(Alert.AlertType.INFORMATION, "Reversed",
                                "Payroll " + selected.getRunNumber() + " reversed.");
                    } catch (Exception ex) {
                        showAlert(Alert.AlertType.ERROR, "Error", ex.getMessage());
                    }
                }
            });
        });

        Button recalcBtn = new Button("Recalculate");
        recalcBtn.getStyleClass().add("btn-secondary");
        recalcBtn.setOnAction(e -> {
            PayrollRun selected = runsTable.getSelectionModel().getSelectedItem();
            if (selected == null) {
                showAlert(Alert.AlertType.WARNING, "No Selection", "Please select a payroll run.");
                return;
            }
            try {
                service.recalculatePayroll(selected.getId());
                refresh();
                showAlert(Alert.AlertType.INFORMATION, "Recalculated",
                        "Payroll " + selected.getRunNumber() + " recalculated.");
            } catch (Exception ex) {
                showAlert(Alert.AlertType.ERROR, "Error", ex.getMessage());
            }
        });

        Button viewItemsBtn = new Button("View Items");
        viewItemsBtn.getStyleClass().add("btn-secondary");
        viewItemsBtn.setOnAction(e -> {
            PayrollRun selected = runsTable.getSelectionModel().getSelectedItem();
            if (selected == null) {
                showAlert(Alert.AlertType.WARNING, "No Selection", "Please select a payroll run.");
                return;
            }
            loadItems(selected.getId());
            tabPane.getSelectionModel().select(2); // Switch to Items tab
        });

        actionBox.getChildren().addAll(approveBtn, postBtn, reverseBtn, recalcBtn, viewItemsBtn);
        content.getChildren().addAll(runsTable, actionBox);

        tab.setContent(content);
        return tab;
    }

    private Tab createRunsTab() {
        Tab tab = new Tab("All Payroll Runs");
        buildRunsSummaryTable();
        VBox box = new VBox(12, runsTable);
        box.setPadding(new Insets(12));
        tab.setContent(box);
        return tab;
    }

    private Tab createItemsTab() {
        Tab tab = new Tab("Payroll Items");
        buildItemsTable();
        VBox box = new VBox(12, itemsTable);
        box.setPadding(new Insets(12));
        tab.setContent(box);
        return tab;
    }

    @SuppressWarnings("unchecked")
    private void buildRunsSummaryTable() {
        runsTable.getColumns().clear();

        TableColumn<PayrollRun, String> runNoCol = new TableColumn<>("Run #");
        runNoCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getRunNumber()));
        runNoCol.setPrefWidth(140);

        TableColumn<PayrollRun, String> periodCol = new TableColumn<>("Period");
        periodCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getPeriodLabel()));
        periodCol.setPrefWidth(90);

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
        runsTable.setPlaceholder(new Label("No payroll runs yet. Use 'Process Payroll' tab to generate."));
        runsTable.setPrefHeight(400);
    }

    @SuppressWarnings("unchecked")
    private void buildItemsTable() {
        itemsTable.getColumns().clear();

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
        itemsTable.setPlaceholder(new Label("Select a payroll run to view items."));
        itemsTable.setPrefHeight(400);
    }

    private void loadItems(String runId) {
        List<PayrollItem> items = service.findItemsByRunId(runId);
        itemsTable.setItems(FXCollections.observableArrayList(items));
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
