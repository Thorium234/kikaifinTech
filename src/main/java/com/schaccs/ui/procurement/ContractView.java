package com.schaccs.ui.procurement;

import com.schaccs.config.CurrencyConfig;
import com.schaccs.enums.ContractStatus;
import com.schaccs.model.procurement.Contract;
import com.schaccs.model.procurement.ContractMilestone;
import com.schaccs.model.procurement.Supplier;
import com.schaccs.service.Services;
import com.schaccs.service.procurement.ContractService;
import com.schaccs.store.ProcurementStore;
import com.schaccs.ui.layout.MainLayout;
import com.schaccs.util.AlertUtil;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

public class ContractView extends VBox implements MainLayout.Refreshable {

    private final ContractService contractService = Services.getInstance().contract();
    private final ProcurementStore store = ProcurementStore.getInstance();

    private final TabPane tabPane = new TabPane();

    // --- Contracts Tab fields ---
    private final ComboBox<Supplier> supplierBox = new ComboBox<>();
    private final DatePicker startDatePick = new DatePicker();
    private final DatePicker endDatePick = new DatePicker();
    private final TextField contractValueField = new TextField();
    private final TextArea deliverablesArea = new TextArea();
    private final TextArea contractNotesArea = new TextArea();
    private final TableView<Contract> contractTable = new TableView<>();
    private final FilteredList<Contract> filteredContracts = new FilteredList<>(store.getContracts(), p -> true);
    private Contract selectedContract;

    // --- Milestones Tab fields ---
    private final ComboBox<Contract> milestoneContractBox = new ComboBox<>();
    private final TextField milestoneTitleField = new TextField();
    private final TextArea milestoneDescArea = new TextArea();
    private final DatePicker milestoneDuePick = new DatePicker();
    private final TextField milestoneAmountField = new TextField();
    private final TableView<ContractMilestone> milestoneTable = new TableView<>();

    public ContractView() {
        setSpacing(12);
        setPadding(new Insets(4));

        Label heading = new Label("Contract Management");
        heading.getStyleClass().add("section-title");

        tabPane.getTabs().addAll(buildContractsTab(), buildMilestonesTab());
        VBox.setVgrow(tabPane, Priority.ALWAYS);

        VBox content = new VBox(12, heading, tabPane);
        content.setPadding(new Insets(4));
        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        scrollPane.setPannable(true);
        scrollPane.getStyleClass().add("content-scroll");
        VBox.setVgrow(scrollPane, Priority.ALWAYS);
        getChildren().add(scrollPane);
    }

    // ========== CONTRACTS TAB ==========

    private Tab buildContractsTab() {
        supplierBox.getItems().addAll(store.getSuppliers());
        supplierBox.setPrefWidth(280);
        deliverablesArea.setPrefRowCount(3);
        deliverablesArea.setWrapText(true);
        contractNotesArea.setPrefRowCount(2);
        contractNotesArea.setWrapText(true);

        setupContractTable();

        Button createBtn = new Button("Create Contract");
        createBtn.getStyleClass().add("primary-button");
        createBtn.setOnAction(e -> createContract());

        Button activateBtn = new Button("Activate");
        activateBtn.getStyleClass().add("success-button");
        activateBtn.setOnAction(e -> activateContract());

        Button completeBtn = new Button("Complete");
        completeBtn.getStyleClass().add("secondary-button");
        completeBtn.setOnAction(e -> completeContract());

        Button extendBtn = new Button("Extend");
        extendBtn.getStyleClass().add("secondary-button");
        extendBtn.setOnAction(e -> extendContract());

        Button terminateBtn = new Button("Terminate");
        terminateBtn.getStyleClass().add("danger-button");
        terminateBtn.setOnAction(e -> terminateContract());

        FlowPane buttons = new FlowPane(10, 10, createBtn, activateBtn, completeBtn, extendBtn, terminateBtn);
        buttons.setAlignment(Pos.CENTER_LEFT);

        GridPane form = new GridPane();
        form.setHgap(10);
        form.setVgap(8);
        form.add(voucherLabel("Supplier"), 0, 0);
        form.add(voucherFieldBox(supplierBox, "Select supplier"), 1, 0);
        form.add(voucherLabel("Start Date"), 0, 1);
        form.add(voucherFieldBox(startDatePick, "Contract start date"), 1, 1);
        form.add(voucherLabel("End Date"), 0, 2);
        form.add(voucherFieldBox(endDatePick, "Contract end date"), 1, 2);
        form.add(voucherLabel("Contract Value"), 0, 3);
        form.add(voucherFieldBox(contractValueField, "Total contract value"), 1, 3);
        form.add(voucherLabel("Deliverables"), 0, 4);
        form.add(voucherFieldBox(deliverablesArea, "Description of deliverables"), 1, 4);
        form.add(voucherLabel("Notes"), 0, 5);
        form.add(voucherFieldBox(contractNotesArea, "Additional notes"), 1, 5);
        contractValueField.setPrefWidth(200);

        Label formTitle = new Label("Contract Details");
        formTitle.getStyleClass().add("card-title");
        VBox formCard = new VBox(10, formTitle, form, buttons);
        formCard.getStyleClass().add("card");
        formCard.setPadding(new Insets(12));

        Label tableTitle = new Label("Contracts");
        tableTitle.getStyleClass().add("card-title");
        VBox tableCard = new VBox(10, tableTitle, contractTable);
        tableCard.getStyleClass().add("card");
        tableCard.setPadding(new Insets(12));
        VBox.setVgrow(contractTable, Priority.ALWAYS);
        contractTable.setPrefHeight(300);

        VBox tabContent = new VBox(12, formCard, tableCard);
        tabContent.setPadding(new Insets(8));
        ScrollPane sp = new ScrollPane(tabContent);
        sp.setFitToWidth(true);
        sp.setPannable(true);

        Tab tab = new Tab("Contracts", sp);
        tab.setClosable(false);
        return tab;
    }

    private void setupContractTable() {
        TableColumn<Contract, String> numCol = new TableColumn<>("Contract No");
        numCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getContractNumber()));
        numCol.setPrefWidth(120);

        TableColumn<Contract, String> supplierCol = new TableColumn<>("Supplier");
        supplierCol.setCellValueFactory(c -> {
            String sid = c.getValue().getSupplierId();
            return store.findSupplierById(sid)
                    .map(s -> new SimpleStringProperty(s.getBusinessName()))
                    .orElse(new SimpleStringProperty(sid));
        });
        supplierCol.setPrefWidth(150);

        TableColumn<Contract, String> valueCol = new TableColumn<>("Value");
        valueCol.setCellValueFactory(c -> new SimpleStringProperty(CurrencyConfig.format(c.getValue().getContractValue())));
        valueCol.setPrefWidth(120);

        TableColumn<Contract, String> startCol = new TableColumn<>("Start");
        startCol.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getStartDate() != null ? c.getValue().getStartDate().toString() : ""));
        startCol.setPrefWidth(90);

        TableColumn<Contract, String> endCol = new TableColumn<>("End");
        endCol.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getEndDate() != null ? c.getValue().getEndDate().toString() : ""));
        endCol.setPrefWidth(90);

        TableColumn<Contract, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getStatus() != null ? c.getValue().getStatus().getDisplayName() : ""));
        statusCol.setPrefWidth(90);

        TableColumn<Contract, String> daysCol = new TableColumn<>("Days Remaining");
        daysCol.setCellValueFactory(c -> {
            long days = c.getValue().getDaysRemaining();
            return new SimpleStringProperty(days > 0 ? String.valueOf(days) : "Expired");
        });
        daysCol.setPrefWidth(100);

        @SuppressWarnings("unchecked")
        var columns = new TableColumn[]{numCol, supplierCol, valueCol, startCol, endCol, statusCol, daysCol};
        contractTable.getColumns().addAll(columns);
        contractTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        contractTable.setItems(filteredContracts);
        contractTable.getSelectionModel().selectedItemProperty().addListener((obs, o, c) -> selectContract(c));
    }

    private void selectContract(Contract c) {
        selectedContract = c;
        if (c == null) {
            clearContractForm();
            return;
        }
        supplierBox.getItems().stream()
                .filter(s -> s.getId().equals(c.getSupplierId()))
                .findFirst().ifPresent(supplierBox::setValue);
        startDatePick.setValue(c.getStartDate());
        endDatePick.setValue(c.getEndDate());
        contractValueField.setText(c.getContractValue() != null ? c.getContractValue().toPlainString() : "");
        deliverablesArea.setText(c.getDeliverables());
        contractNotesArea.setText(c.getNotes());
    }

    private void createContract() {
        if (!AlertUtil.confirm("Create Contract", "Create new contract?")) return;
        Contract c = buildContractFromForm();
        List<String> errors = contractService.createContract(c);
        if (!errors.isEmpty()) {
            AlertUtil.error("Validation Error", String.join("\n", errors));
            return;
        }
        AlertUtil.info("Success", "Contract " + c.getContractNumber() + " created.");
        clearContractForm();
        contractTable.refresh();
    }

    private void activateContract() {
        if (selectedContract == null) {
            AlertUtil.warn("No Selection", "Select a contract to activate.");
            return;
        }
        if (!AlertUtil.confirm("Activate", "Activate contract " + selectedContract.getContractNumber() + "?")) return;
        List<String> errors = contractService.activateContract(selectedContract);
        if (!errors.isEmpty()) {
            AlertUtil.error("Error", String.join("\n", errors));
            return;
        }
        AlertUtil.info("Activated", "Contract activated.");
        contractTable.refresh();
    }

    private void completeContract() {
        if (selectedContract == null) {
            AlertUtil.warn("No Selection", "Select a contract to complete.");
            return;
        }
        if (!AlertUtil.confirm("Complete", "Mark contract " + selectedContract.getContractNumber() + " as completed?")) return;
        List<String> errors = contractService.completeContract(selectedContract);
        if (!errors.isEmpty()) {
            AlertUtil.error("Error", String.join("\n", errors));
            return;
        }
        AlertUtil.info("Completed", "Contract completed.");
        contractTable.refresh();
    }

    private void extendContract() {
        if (selectedContract == null) {
            AlertUtil.warn("No Selection", "Select a contract to extend.");
            return;
        }
        DatePicker extPick = new DatePicker(LocalDate.now().plusMonths(3));
        extPick.setPromptText("New end date");
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("Extend Contract");
        dialog.setHeaderText("Select new end date for " + selectedContract.getContractNumber());
        dialog.getDialogPane().setContent(new VBox(8, new Label("New End Date:"), extPick));
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dialog.setResultConverter(btn -> btn == ButtonType.OK ? extPick.getValue().toString() : null);
        dialog.showAndWait().ifPresent(dateStr -> {
            LocalDate newEnd = LocalDate.parse(dateStr);
            List<String> errors = contractService.extendContract(selectedContract, newEnd);
            if (!errors.isEmpty()) {
                AlertUtil.error("Error", String.join("\n", errors));
                return;
            }
            AlertUtil.info("Extended", "Contract extended to " + newEnd + ".");
            contractTable.refresh();
        });
    }

    private void terminateContract() {
        if (selectedContract == null) {
            AlertUtil.warn("No Selection", "Select a contract to terminate.");
            return;
        }
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Terminate Contract");
        dialog.setHeaderText("Provide termination reason for " + selectedContract.getContractNumber());
        dialog.setContentText("Reason:");
        dialog.showAndWait().ifPresent(reason -> {
            List<String> errors = contractService.terminateContract(selectedContract, reason);
            if (!errors.isEmpty()) {
                AlertUtil.error("Error", String.join("\n", errors));
                return;
            }
            AlertUtil.info("Terminated", "Contract terminated.");
            contractTable.refresh();
        });
    }

    private Contract buildContractFromForm() {
        Contract c = new Contract();
        Supplier s = supplierBox.getValue();
        if (s != null) c.setSupplierId(s.getId());
        c.setStartDate(startDatePick.getValue());
        c.setEndDate(endDatePick.getValue());
        try {
            c.setContractValue(CurrencyConfig.money(contractValueField.getText().trim()));
        } catch (NumberFormatException e) {
            c.setContractValue(CurrencyConfig.zero());
        }
        c.setDeliverables(deliverablesArea.getText());
        c.setNotes(contractNotesArea.getText());
        return c;
    }

    private void clearContractForm() {
        supplierBox.setValue(null);
        startDatePick.setValue(null);
        endDatePick.setValue(null);
        contractValueField.clear();
        deliverablesArea.clear();
        contractNotesArea.clear();
    }

    // ========== MILESTONES TAB ==========

    private Tab buildMilestonesTab() {
        milestoneContractBox.getItems().addAll(store.getContracts());
        milestoneContractBox.setPrefWidth(280);
        milestoneContractBox.valueProperty().addListener((obs, o, c) -> refreshMilestoneTable());

        milestoneDescArea.setPrefRowCount(2);
        milestoneDescArea.setWrapText(true);

        setupMilestoneTable();

        Button addBtn = new Button("Add Milestone");
        addBtn.getStyleClass().add("primary-button");
        addBtn.setOnAction(e -> addMilestone());

        Button completeBtn = new Button("Complete Milestone");
        completeBtn.getStyleClass().add("success-button");
        completeBtn.setOnAction(e -> completeMilestone());

        HBox buttons = new HBox(10, addBtn, completeBtn);
        buttons.setAlignment(Pos.CENTER_LEFT);

        GridPane form = new GridPane();
        form.setHgap(10);
        form.setVgap(8);
        form.add(voucherLabel("Select Contract"), 0, 0);
        form.add(voucherFieldBox(milestoneContractBox, "Choose a contract"), 1, 0);
        form.add(voucherLabel("Title"), 0, 1);
        form.add(voucherFieldBox(milestoneTitleField, "Milestone title"), 1, 1);
        form.add(voucherLabel("Description"), 0, 2);
        form.add(voucherFieldBox(milestoneDescArea, "Milestone description"), 1, 2);
        form.add(voucherLabel("Due Date"), 0, 3);
        form.add(voucherFieldBox(milestoneDuePick, "Due date"), 1, 3);
        form.add(voucherLabel("Amount"), 0, 4);
        form.add(voucherFieldBox(milestoneAmountField, "Milestone amount"), 1, 4);
        milestoneTitleField.setPrefWidth(280);
        milestoneAmountField.setPrefWidth(200);

        Label formTitle = new Label("Milestone Entry");
        formTitle.getStyleClass().add("card-title");
        VBox formCard = new VBox(10, formTitle, form, buttons);
        formCard.getStyleClass().add("card");
        formCard.setPadding(new Insets(12));

        Label tableTitle = new Label("Milestones");
        tableTitle.getStyleClass().add("card-title");
        VBox tableCard = new VBox(10, tableTitle, milestoneTable);
        tableCard.getStyleClass().add("card");
        tableCard.setPadding(new Insets(12));
        VBox.setVgrow(milestoneTable, Priority.ALWAYS);
        milestoneTable.setPrefHeight(250);

        VBox tabContent = new VBox(12, formCard, tableCard);
        tabContent.setPadding(new Insets(8));
        ScrollPane sp = new ScrollPane(tabContent);
        sp.setFitToWidth(true);
        sp.setPannable(true);

        Tab tab = new Tab("Milestones", sp);
        tab.setClosable(false);
        return tab;
    }

    private void refreshMilestoneTable() {
        Contract c = milestoneContractBox.getValue();
        if (c == null) {
            milestoneTable.getItems().clear();
            return;
        }
        milestoneTable.setItems(store.milestonesForContract(c.getId()));
    }

    private void setupMilestoneTable() {
        TableColumn<ContractMilestone, String> titleCol = new TableColumn<>("Title");
        titleCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getTitle()));
        titleCol.setPrefWidth(180);

        TableColumn<ContractMilestone, String> dueCol = new TableColumn<>("Due Date");
        dueCol.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getDueDate() != null ? c.getValue().getDueDate().toString() : ""));
        dueCol.setPrefWidth(100);

        TableColumn<ContractMilestone, String> amountCol = new TableColumn<>("Amount");
        amountCol.setCellValueFactory(c -> new SimpleStringProperty(CurrencyConfig.format(c.getValue().getAmount())));
        amountCol.setPrefWidth(120);

        TableColumn<ContractMilestone, String> completedCol = new TableColumn<>("Completed");
        completedCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().isCompleted() ? "Yes" : "No"));
        completedCol.setPrefWidth(80);

        TableColumn<ContractMilestone, String> overdueCol = new TableColumn<>("Overdue");
        overdueCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().isOverdue() ? "Yes" : ""));
        overdueCol.setPrefWidth(60);

        @SuppressWarnings("unchecked")
        var columns = new TableColumn[]{titleCol, dueCol, amountCol, completedCol, overdueCol};
        milestoneTable.getColumns().addAll(columns);
        milestoneTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
    }

    private void addMilestone() {
        Contract c = milestoneContractBox.getValue();
        if (c == null) {
            AlertUtil.warn("No Selection", "Select a contract first.");
            return;
        }
        if (!AlertUtil.confirm("Add Milestone", "Add milestone to contract " + c.getContractNumber() + "?")) return;
        ContractMilestone m = new ContractMilestone();
        m.setContractId(c.getId());
        m.setTitle(milestoneTitleField.getText());
        m.setDescription(milestoneDescArea.getText());
        m.setDueDate(milestoneDuePick.getValue());
        try {
            m.setAmount(CurrencyConfig.money(milestoneAmountField.getText().trim()));
        } catch (NumberFormatException e) {
            m.setAmount(CurrencyConfig.zero());
        }
        List<String> errors = contractService.addMilestone(m);
        if (!errors.isEmpty()) {
            AlertUtil.error("Validation Error", String.join("\n", errors));
            return;
        }
        AlertUtil.info("Success", "Milestone added.");
        milestoneTitleField.clear();
        milestoneDescArea.clear();
        milestoneDuePick.setValue(null);
        milestoneAmountField.clear();
        refreshMilestoneTable();
    }

    private void completeMilestone() {
        ContractMilestone m = milestoneTable.getSelectionModel().getSelectedItem();
        if (m == null) {
            AlertUtil.warn("No Selection", "Select a milestone to complete.");
            return;
        }
        if (!AlertUtil.confirm("Complete Milestone", "Mark \"" + m.getTitle() + "\" as completed?")) return;
        List<String> errors = contractService.completeMilestone(m);
        if (!errors.isEmpty()) {
            AlertUtil.error("Error", String.join("\n", errors));
            return;
        }
        AlertUtil.info("Completed", "Milestone marked as completed.");
        refreshMilestoneTable();
    }

    // ========== COMMON ==========

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
        contractTable.refresh();
        supplierBox.getItems().setAll(store.getSuppliers());
        milestoneContractBox.getItems().setAll(store.getContracts());
        refreshMilestoneTable();
    }
}
