package com.schaccs.ui.procurement;

import com.schaccs.config.AppConfig;
import com.schaccs.config.CurrencyConfig;
import com.schaccs.enums.ProcurementRequestStatus;
import com.schaccs.model.procurement.ProcurementRequest;
import com.schaccs.service.Services;
import com.schaccs.service.procurement.ProcurementService;
import com.schaccs.store.ProcurementStore;
import com.schaccs.ui.layout.MainLayout;
import com.schaccs.util.AlertUtil;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public class ProcurementRequestView extends VBox implements MainLayout.Refreshable {

    private final ProcurementService procurementService = Services.getInstance().procurement();
    private final ProcurementStore store = ProcurementStore.getInstance();

    private final ComboBox<String> departmentBox = new ComboBox<>();
    private final TextField requestedByField = new TextField();
    private final TextArea descriptionArea = new TextArea();
    private final TextField quantityField = new TextField();
    private final TextField estimatedCostField = new TextField();
    private final TextArea justificationArea = new TextArea();
    private final DatePicker requiredDatePick = new DatePicker();
    private final TextField budgetAccountField = new TextField();

    private final ComboBox<ProcurementRequestStatus> statusFilter = new ComboBox<>();
    private final TableView<ProcurementRequest> table = new TableView<>();
    private final FilteredList<ProcurementRequest> filteredRequests =
            new FilteredList<>(store.getProcurementRequests(), p -> true);

    private ProcurementRequest selected;

    public ProcurementRequestView() {
        setSpacing(12);
        setPadding(new Insets(4));

        Label heading = new Label("Procurement Requests");
        heading.getStyleClass().add("section-title");

        departmentBox.getItems().setAll("Administration", "Academic", "Finance", "ICT", "Maintenance", "Transport");
        departmentBox.setPrefWidth(200);
        descriptionArea.setPrefRowCount(3);
        descriptionArea.setWrapText(true);
        justificationArea.setPrefRowCount(3);
        justificationArea.setWrapText(true);
        requiredDatePick.setPrefWidth(200);

        statusFilter.getItems().addAll(ProcurementRequestStatus.values());
        statusFilter.getItems().add(0, null);
        statusFilter.setPromptText("All Statuses");
        statusFilter.setPrefWidth(160);
        statusFilter.valueProperty().addListener((obs, o, v) -> applyFilter());

        setupTable();

        Button createBtn = new Button("Create Request");
        createBtn.getStyleClass().add("primary-button");
        createBtn.setOnAction(e -> createRequest());

        Button submitBtn = new Button("Submit for Approval");
        submitBtn.getStyleClass().add("secondary-button");
        submitBtn.setOnAction(e -> submitRequest());

        Button approveBtn = new Button("Approve");
        approveBtn.getStyleClass().add("success-button");
        approveBtn.setOnAction(e -> approveRequest());

        Button rejectBtn = new Button("Reject");
        rejectBtn.getStyleClass().add("danger-button");
        rejectBtn.setOnAction(e -> rejectRequest());

        HBox buttons = new HBox(10, createBtn, submitBtn, approveBtn, rejectBtn);
        buttons.setAlignment(Pos.CENTER_LEFT);

        GridPane form = new GridPane();
        form.setHgap(10);
        form.setVgap(8);
        form.add(voucherLabel("Department"), 0, 0);
        form.add(voucherFieldBox(departmentBox, "Requesting department"), 1, 0);
        form.add(voucherLabel("Requested By"), 0, 1);
        form.add(voucherFieldBox(requestedByField, "Name of requester"), 1, 1);
        form.add(voucherLabel("Item Description"), 0, 2);
        form.add(voucherFieldBox(descriptionArea, "Detailed description of items required"), 1, 2);
        form.add(voucherLabel("Quantity"), 0, 3);
        form.add(voucherFieldBox(quantityField, "Number of units"), 1, 3);
        form.add(voucherLabel("Estimated Cost"), 0, 4);
        form.add(voucherFieldBox(estimatedCostField, "Total estimated cost"), 1, 4);
        form.add(voucherLabel("Justification"), 0, 5);
        form.add(voucherFieldBox(justificationArea, "Business justification for this request"), 1, 5);
        form.add(voucherLabel("Required Date"), 0, 6);
        form.add(voucherFieldBox(requiredDatePick, "Date items are needed"), 1, 6);
        form.add(voucherLabel("Budget Account"), 0, 7);
        form.add(voucherFieldBox(budgetAccountField, "GL account code"), 1, 7);
        estimatedCostField.setPrefWidth(200);
        quantityField.setPrefWidth(200);

        Label formTitle = new Label("Request Details");
        formTitle.getStyleClass().add("card-title");
        VBox formCard = new VBox(10, formTitle, form, buttons);
        formCard.getStyleClass().add("card");
        formCard.setPadding(new Insets(12));

        Label tableTitle = new Label("Requests");
        tableTitle.getStyleClass().add("card-title");
        HBox filterRow = new HBox(10, statusFilter);
        filterRow.setAlignment(Pos.CENTER_LEFT);
        VBox tableCard = new VBox(10, tableTitle, filterRow, table);
        tableCard.getStyleClass().add("card");
        tableCard.setPadding(new Insets(12));
        VBox.setVgrow(table, Priority.ALWAYS);
        table.setPrefHeight(300);

        VBox content = new VBox(12, heading, formCard, tableCard);
        content.setPadding(new Insets(4));
        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        scrollPane.setPannable(true);
        scrollPane.getStyleClass().add("content-scroll");
        VBox.setVgrow(scrollPane, Priority.ALWAYS);
        getChildren().add(scrollPane);

        applyFilter();
    }

    private void setupTable() {
        TableColumn<ProcurementRequest, String> numCol = new TableColumn<>("Request No");
        numCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getRequestNumber()));
        numCol.setPrefWidth(120);

        TableColumn<ProcurementRequest, String> dateCol = new TableColumn<>("Date");
        dateCol.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getRequestDate() != null ? c.getValue().getRequestDate().toString() : ""));
        dateCol.setPrefWidth(90);

        TableColumn<ProcurementRequest, String> deptCol = new TableColumn<>("Department");
        deptCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getDepartment()));
        deptCol.setPrefWidth(110);

        TableColumn<ProcurementRequest, String> byCol = new TableColumn<>("Requested By");
        byCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getRequestedBy()));
        byCol.setPrefWidth(120);

        TableColumn<ProcurementRequest, String> descCol = new TableColumn<>("Description");
        descCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getItemDescription()));
        descCol.setPrefWidth(200);

        TableColumn<ProcurementRequest, String> costCol = new TableColumn<>("Est. Cost");
        costCol.setCellValueFactory(c -> new SimpleStringProperty(CurrencyConfig.format(c.getValue().getEstimatedCost())));
        costCol.setPrefWidth(120);

        TableColumn<ProcurementRequest, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getStatus() != null ? c.getValue().getStatus().getDisplayName() : ""));
        statusCol.setPrefWidth(100);

        @SuppressWarnings("unchecked")
        var columns = new TableColumn[]{numCol, dateCol, deptCol, byCol, descCol, costCol, statusCol};
        table.getColumns().addAll(columns);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.getSelectionModel().selectedItemProperty().addListener((obs, o, s) -> selectRequest(s));
    }

    private void applyFilter() {
        ProcurementRequestStatus status = statusFilter.getValue();
        filteredRequests.setPredicate(r -> status == null || r.getStatus() == status);
        table.setItems(filteredRequests);
    }

    private void selectRequest(ProcurementRequest r) {
        selected = r;
        if (r == null) {
            clearForm();
            return;
        }
        departmentBox.setValue(r.getDepartment());
        requestedByField.setText(r.getRequestedBy());
        descriptionArea.setText(r.getItemDescription());
        quantityField.setText(String.valueOf(r.getQuantity()));
        estimatedCostField.setText(r.getEstimatedCost() != null ? r.getEstimatedCost().toPlainString() : "");
        justificationArea.setText(r.getJustification());
        requiredDatePick.setValue(r.getRequiredDate());
        budgetAccountField.setText(r.getBudgetAccount());
    }

    private void createRequest() {
        if (!AlertUtil.confirm("Create Request", "Create new procurement request?")) return;
        ProcurementRequest r = buildFromForm();
        List<String> errors = procurementService.createRequest(r);
        if (!errors.isEmpty()) {
            AlertUtil.error("Validation Error", String.join("\n", errors));
            return;
        }
        AlertUtil.info("Success", "Request " + r.getRequestNumber() + " created.");
        clearForm();
        applyFilter();
    }

    private void submitRequest() {
        if (selected == null) {
            AlertUtil.warn("No Selection", "Select a request to submit.");
            return;
        }
        if (!AlertUtil.confirm("Submit", "Submit request " + selected.getRequestNumber() + " for approval?")) return;
        List<String> errors = procurementService.submitRequest(selected);
        if (!errors.isEmpty()) {
            AlertUtil.error("Error", String.join("\n", errors));
            return;
        }
        AlertUtil.info("Submitted", "Request submitted for approval.");
        applyFilter();
    }

    private void approveRequest() {
        if (selected == null) {
            AlertUtil.warn("No Selection", "Select a request to approve.");
            return;
        }
        String approver = AppConfig.getInstance().getCurrentUser();
        String role = AppConfig.getInstance().getCurrentUserRole();
        if (!AlertUtil.confirm("Approve", "Approve request " + selected.getRequestNumber() + "?")) return;
        List<String> errors = procurementService.approveRequest(selected, approver, role, null);
        if (!errors.isEmpty()) {
            AlertUtil.error("Error", String.join("\n", errors));
            return;
        }
        AlertUtil.info("Approved", "Request approved.");
        applyFilter();
    }

    private void rejectRequest() {
        if (selected == null) {
            AlertUtil.warn("No Selection", "Select a request to reject.");
            return;
        }
        String approver = AppConfig.getInstance().getCurrentUser();
        String role = AppConfig.getInstance().getCurrentUserRole();
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Reject Request");
        dialog.setHeaderText("Provide rejection reason for " + selected.getRequestNumber());
        dialog.setContentText("Reason:");
        dialog.showAndWait().ifPresent(reason -> {
            List<String> errors = procurementService.rejectRequest(selected, approver, role, reason);
            if (!errors.isEmpty()) {
                AlertUtil.error("Error", String.join("\n", errors));
                return;
            }
            AlertUtil.info("Rejected", "Request rejected.");
            applyFilter();
        });
    }

    private ProcurementRequest buildFromForm() {
        ProcurementRequest r = new ProcurementRequest();
        r.setDepartment(departmentBox.getValue());
        r.setRequestedBy(requestedByField.getText());
        r.setItemDescription(descriptionArea.getText());
        try {
            r.setQuantity(Integer.parseInt(quantityField.getText().trim()));
        } catch (NumberFormatException e) {
            r.setQuantity(0);
        }
        try {
            r.setEstimatedCost(CurrencyConfig.money(estimatedCostField.getText().trim()));
        } catch (NumberFormatException e) {
            r.setEstimatedCost(CurrencyConfig.zero());
        }
        r.setJustification(justificationArea.getText());
        r.setRequiredDate(requiredDatePick.getValue());
        r.setBudgetAccount(budgetAccountField.getText());
        return r;
    }

    private void clearForm() {
        departmentBox.setValue(null);
        requestedByField.clear();
        descriptionArea.clear();
        quantityField.clear();
        estimatedCostField.clear();
        justificationArea.clear();
        requiredDatePick.setValue(null);
        budgetAccountField.clear();
    }

    private static Label voucherLabel(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("field-label");
        return l;
    }

    private static VBox voucherFieldBox(javafx.scene.control.Control field, String hint) {
        VBox box = new VBox(2);
        box.getChildren().addAll(field, new Label(hint));
        return box;
    }

    @Override
    public void refresh() {
        applyFilter();
        table.refresh();
    }
}
