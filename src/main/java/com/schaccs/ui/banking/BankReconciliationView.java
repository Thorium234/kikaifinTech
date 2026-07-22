package com.schaccs.ui.banking;

import com.schaccs.config.CurrencyConfig;
import com.schaccs.model.finance.BankReconciliation;
import com.schaccs.service.Services;
import com.schaccs.service.finance.BankReconciliationService;
import com.schaccs.store.BankReconciliationStore;
import com.schaccs.ui.layout.MainLayout;
import com.schaccs.util.AlertUtil;
import com.schaccs.util.CurrencyUtil;
import com.schaccs.util.DateUtil;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.time.LocalDate;

public class BankReconciliationView extends VBox implements MainLayout.Refreshable {

    private final BankReconciliationService service = Services.getInstance().bankReconciliation();
    private final BankReconciliationStore store = BankReconciliationStore.getInstance();

    private final TableView<BankReconciliation> listTable = new TableView<>();
    private final TextField statementBalanceField = new TextField();
    private final TextField bookBalanceField = new TextField();
    private final DatePicker statementDate = new DatePicker(LocalDate.now());
    private final TextArea notesArea = new TextArea();
    private final TableView<BankReconciliation.ReconciliationItem> itemsTable = new TableView<>();
    private final TextField itemTypeField = new TextField();
    private final TextField itemRefField = new TextField();
    private final TextField itemDescField = new TextField();
    private final TextField itemAmountField = new TextField();

    public BankReconciliationView() {
        setSpacing(12);
        setPadding(new Insets(4));

        Label heading = new Label("Bank Reconciliation");
        heading.getStyleClass().add("section-title");
        Label sub = new Label("Compare book balances against bank statements and identify uncleared items.");
        sub.getStyleClass().add("muted");

        TableColumn<BankReconciliation, String> dateCol = new TableColumn<>("Statement Date");
        dateCol.setCellValueFactory(c -> new SimpleStringProperty(DateUtil.format(c.getValue().getStatementDate())));
        TableColumn<BankReconciliation, String> bookCol = new TableColumn<>("Book Balance");
        bookCol.setCellValueFactory(c -> new SimpleStringProperty(CurrencyUtil.format(c.getValue().getBookBalance())));
        TableColumn<BankReconciliation, String> stmtCol = new TableColumn<>("Statement Balance");
        stmtCol.setCellValueFactory(c -> new SimpleStringProperty(CurrencyUtil.format(c.getValue().getStatementBalance())));
        TableColumn<BankReconciliation, String> diffCol = new TableColumn<>("Difference");
        diffCol.setCellValueFactory(c -> new SimpleStringProperty(CurrencyUtil.format(c.getValue().getDifference())));
        TableColumn<BankReconciliation, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getStatus()));
        @SuppressWarnings("unchecked")
        var columns1 = new TableColumn[]{dateCol, bookCol, stmtCol, diffCol, statusCol};
        listTable.getColumns().addAll(columns1);
        listTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        TableColumn<BankReconciliation.ReconciliationItem, String> itType = new TableColumn<>("Type");
        itType.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getType()));
        TableColumn<BankReconciliation.ReconciliationItem, String> itRef = new TableColumn<>("Reference");
        itRef.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getReference()));
        TableColumn<BankReconciliation.ReconciliationItem, String> itDesc = new TableColumn<>("Description");
        itDesc.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getDescription()));
        TableColumn<BankReconciliation.ReconciliationItem, String> itAmt = new TableColumn<>("Amount");
        itAmt.setCellValueFactory(c -> new SimpleStringProperty(CurrencyUtil.format(c.getValue().getAmount())));
        TableColumn<BankReconciliation.ReconciliationItem, String> itCleared = new TableColumn<>("Cleared");
        itCleared.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().isCleared() ? "Yes" : "No"));
        @SuppressWarnings("unchecked")
        var columns2 = new TableColumn[]{itType, itRef, itDesc, itAmt, itCleared};
        itemsTable.getColumns().addAll(columns2);
        itemsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        VBox.setVgrow(itemsTable, Priority.ALWAYS);

        listTable.getSelectionModel().selectedItemProperty().addListener((obs, o, rec) -> {
            if (rec != null) {
                itemsTable.getItems().setAll(rec.getItems());
                statementBalanceField.setText(CurrencyUtil.formatPlain(rec.getStatementBalance()));
                bookBalanceField.setText(CurrencyUtil.formatPlain(rec.getBookBalance()));
                statementDate.setValue(rec.getStatementDate());
                notesArea.setText(rec.getNotes());
            }
        });

        GridPane form = new GridPane();
        form.setHgap(10);
        form.setVgap(8);
        int row = 0;
        form.add(new Label("Statement Date"), 0, row);
        form.add(statementDate, 1, row++);
        form.add(new Label("Statement Balance"), 0, row);
        form.add(statementBalanceField, 1, row++);
        form.add(new Label("Book Balance"), 0, row);
        bookBalanceField.setEditable(false);
        form.add(bookBalanceField, 1, row++);
        form.add(new Label("Notes"), 0, row);
        notesArea.setPrefRowCount(3);
        form.add(notesArea, 1, row++);

        Button createBtn = new Button("New Reconciliation");
        createBtn.getStyleClass().add("primary-button");
        createBtn.setOnAction(e -> createReconciliation());
        Button addItemBtn = new Button("Add Item");
        addItemBtn.getStyleClass().add("secondary-button");
        addItemBtn.setOnAction(e -> addItem());
        Button finalizeBtn = new Button("Finalize Reconciliation");
        finalizeBtn.getStyleClass().add("success-button");
        finalizeBtn.setOnAction(e -> finalizeReconciliation());
        Button exportBtn = new Button("Export CSV");
        exportBtn.getStyleClass().add("secondary-button");
        exportBtn.setOnAction(e -> exportReconciliation());

        HBox itemForm = new HBox(8,
                new Label("Type:"), itemTypeField,
                new Label("Ref:"), itemRefField,
                new Label("Desc:"), itemDescField,
                new Label("Amount:"), itemAmountField,
                addItemBtn);
        itemTypeField.setPrefWidth(100);
        itemRefField.setPrefWidth(100);
        itemDescField.setPrefWidth(150);
        itemAmountField.setPrefWidth(100);

        VBox card = new VBox(10, heading, sub,
                new HBox(10, createBtn, finalizeBtn, exportBtn),
                listTable,
                new Label("Reconciliation Items"),
                itemsTable,
                itemForm,
                form,
                notesArea);
        card.getStyleClass().add("card");
        VBox.setVgrow(itemsTable, Priority.ALWAYS);
        VBox.setVgrow(listTable, Priority.SOMETIMES);
        getChildren().add(card);
        refresh();
    }

    private void createReconciliation() {
        LocalDate date = statementDate.getValue();
        if (date == null) {
            AlertUtil.warn("Missing date", "Select a statement date.");
            return;
        }
        BigDecimal stmtBal;
        try {
            stmtBal = CurrencyConfig.money(statementBalanceField.getText().trim());
        } catch (Exception e) {
            AlertUtil.warn("Invalid balance", "Enter a valid statement balance.");
            return;
        }
        BankReconciliation rec = service.createReconciliation(date, stmtBal, notesArea.getText().trim());
        rec.getItems().clear();
        rec.getItems().addAll(service.calculateUnclearedItems(rec));
        itemsTable.getItems().setAll(rec.getItems());
        bookBalanceField.setText(CurrencyUtil.formatPlain(rec.getBookBalance()));
        listTable.getItems().setAll(store.getReconciliations());
        listTable.getSelectionModel().select(rec);
        AlertUtil.info("Created", "Reconciliation created with " + rec.getItems().size() + " uncleared items.");
    }

    private void addItem() {
        BankReconciliation rec = listTable.getSelectionModel().getSelectedItem();
        if (rec == null) {
            AlertUtil.warn("Select reconciliation", "Select a reconciliation first.");
            return;
        }
        try {
            BankReconciliation.ReconciliationItem item = new BankReconciliation.ReconciliationItem();
            item.setType(itemTypeField.getText().trim());
            item.setReference(itemRefField.getText().trim());
            item.setDescription(itemDescField.getText().trim());
            item.setAmount(CurrencyConfig.money(itemAmountField.getText().trim()));
            service.addItem(rec, item);
            itemsTable.getItems().setAll(rec.getItems());
            itemTypeField.clear();
            itemRefField.clear();
            itemDescField.clear();
            itemAmountField.clear();
        } catch (Exception e) {
            AlertUtil.warn("Invalid item", "Check item fields.");
        }
    }

    private void finalizeReconciliation() {
        BankReconciliation rec = listTable.getSelectionModel().getSelectedItem();
        if (rec == null) {
            AlertUtil.warn("Select reconciliation", "Select a reconciliation first.");
            return;
        }
        service.finalizeReconciliation(rec);
        itemsTable.getItems().setAll(rec.getItems());
        listTable.refresh();
        AlertUtil.info("Finalized", "Reconciliation " + rec.getId() + " finalized with difference " + CurrencyUtil.format(rec.getDifference()));
    }

    private void exportReconciliation() {
        BankReconciliation rec = listTable.getSelectionModel().getSelectedItem();
        if (rec == null) {
            AlertUtil.warn("Select reconciliation", "Select a reconciliation first.");
            return;
        }
        javafx.stage.FileChooser chooser = new javafx.stage.FileChooser();
        chooser.setTitle("Export Reconciliation");
        chooser.getExtensionFilters().addAll(
                new javafx.stage.FileChooser.ExtensionFilter("CSV files", "*.csv"),
                new javafx.stage.FileChooser.ExtensionFilter("Excel files", "*.xlsx"));
        chooser.setInitialFileName("reconciliation-" + rec.getId() + ".csv");
        java.io.File file = chooser.showSaveDialog(getScene() != null ? getScene().getWindow() : null);
        if (file == null) return;
        try {
            com.schaccs.service.export.SpreadsheetExportService exportService = new com.schaccs.service.export.SpreadsheetExportService();
            java.util.List<String> headers = java.util.List.of("Type", "Reference", "Description", "Amount", "Cleared");
            java.util.List<java.util.List<String>> rows = rec.getItems().stream().map(item -> java.util.List.of(
                    item.getType(), item.getReference(), item.getDescription(),
                    CurrencyUtil.formatPlain(item.getAmount()),
                    item.isCleared() ? "Yes" : "No")).toList();
            exportService.export(file.toPath(), "Reconciliation", headers, rows);
            AlertUtil.info("Export complete", "Reconciliation exported to:\n" + file.getAbsolutePath());
        } catch (java.io.IOException e) {
            AlertUtil.error("Export failed", e.getMessage());
        }
    }

    @Override
    public void refresh() {
        listTable.getItems().setAll(store.getReconciliations());
        BigDecimal bookBalance = com.schaccs.store.LedgerStore.getInstance()
                .getAccountBalance(com.schaccs.enums.AccountType.SCHOOL_FUND);
        bookBalanceField.setText(CurrencyUtil.formatPlain(bookBalance));
    }
}
