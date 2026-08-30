package com.schaccs.ui.banking;

import com.schaccs.config.CurrencyConfig;
import com.schaccs.enums.AccountType;
import com.schaccs.model.finance.BankReconciliation;
import com.schaccs.model.finance.BankStatementEntry;
import com.schaccs.service.Services;
import com.schaccs.service.finance.BankReconciliationService;
import com.schaccs.service.finance.BankStatementImportService;
import com.schaccs.store.BankReconciliationStore;
import com.schaccs.store.BankStatementStore;
import com.schaccs.ui.layout.MainLayout;
import com.schaccs.util.AlertUtil;
import com.schaccs.util.CurrencyUtil;
import com.schaccs.util.DateUtil;
import com.schaccs.util.FileNamingUtil;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

public class BankReconciliationView extends VBox implements MainLayout.Refreshable {

    private static final List<AccountType> BANK_ACCOUNTS = List.of(
            AccountType.BANK_TUITION, AccountType.BANK_BOARDING,
            AccountType.BANK_INFRASTRUCTURE, AccountType.CASH_AT_BANK);

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
    private final ComboBox<AccountType> bankSelector = new ComboBox<>();
    private final TableView<BankStatementEntry> statementTable = new TableView<>();
    private final TextField receiptAdmField = new TextField();
    private final TextField receiptAmountField = new TextField();
    private final TextField chargeAmountField = new TextField();
    private final TextField sweepAmountField = new TextField();

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

        bankSelector.getItems().setAll(BANK_ACCOUNTS);
        bankSelector.setValue(AccountType.BANK_BOARDING);

        TableColumn<BankStatementEntry, String> sd = new TableColumn<>("Date");
        sd.setCellValueFactory(c -> new SimpleStringProperty(DateUtil.format(c.getValue().getStatementDate())));
        TableColumn<BankStatementEntry, String> sdesc = new TableColumn<>("Description");
        sdesc.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getDescription()));
        TableColumn<BankStatementEntry, String> sref = new TableColumn<>("Reference");
        sref.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getReference()));
        TableColumn<BankStatementEntry, String> sout = new TableColumn<>("Withdrawal");
        sout.setCellValueFactory(c -> new SimpleStringProperty(CurrencyUtil.format(c.getValue().getDebit())));
        TableColumn<BankStatementEntry, String> sin = new TableColumn<>("Deposit");
        sin.setCellValueFactory(c -> new SimpleStringProperty(CurrencyUtil.format(c.getValue().getCredit())));
        TableColumn<BankStatementEntry, String> srec = new TableColumn<>("Matched");
        srec.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().isReconciled() ? "Yes" : "No"));
        @SuppressWarnings("unchecked")
        var stmtCols = new TableColumn[]{sd, sdesc, sref, sout, sin, srec};
        statementTable.getColumns().addAll(stmtCols);
        statementTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        VBox.setVgrow(statementTable, Priority.ALWAYS);

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
        form.add(new Label("Bank Account"), 0, row);
        form.add(bankSelector, 1, row++);
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
        Button pdfBtn = new Button("Export PDF Statement");
        pdfBtn.getStyleClass().add("secondary-button");
        pdfBtn.setOnAction(e -> exportPdfStatement());

        Button importBtn = new Button("Import Statement");
        importBtn.getStyleClass().add("secondary-button");
        importBtn.setOnAction(e -> importStatement());
        Button autoMatchBtn = new Button("Auto-Match");
        autoMatchBtn.getStyleClass().add("secondary-button");
        autoMatchBtn.setOnAction(e -> autoMatch());
        Button markClearedBtn = new Button("Mark Selected Cleared");
        markClearedBtn.getStyleClass().add("secondary-button");
        markClearedBtn.setOnAction(e -> markSelectedCleared());

        Button receiptBtn = new Button("Generate Receipt from Statement");
        receiptBtn.getStyleClass().add("secondary-button");
        receiptBtn.setOnAction(e -> generateReceiptFromStatement());

        Button sweepBtn = new Button("M-Pesa In-Transit Sweep");
        sweepBtn.getStyleClass().add("secondary-button");
        sweepBtn.setOnAction(e -> mpesaSweep());
        HBox sweepBar = new HBox(8, new Label("Sweep Amt:"), sweepAmountField, sweepBtn);

        Button chargeBtn = new Button("Post Bank Charges");
        chargeBtn.getStyleClass().add("secondary-button");
        chargeBtn.setOnAction(e -> postBankCharges());
        HBox chargesBar = new HBox(8, new Label("Charge Amt:"), chargeAmountField, chargeBtn);

        HBox receiptBar = new HBox(8,
                new Label("Adm No:"), receiptAdmField,
                new Label("Amt:"), receiptAmountField,
                receiptBtn);

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
        sweepAmountField.setPrefWidth(90);
        chargeAmountField.setPrefWidth(90);
        receiptAdmField.setPrefWidth(120);
        receiptAmountField.setPrefWidth(90);

        VBox content = new VBox(10, heading, sub,
                new HBox(10, createBtn, finalizeBtn, exportBtn, pdfBtn),
                listTable,
                new Label("Reconciliation Items"),
                itemsTable,
                new HBox(10, markClearedBtn),
                itemForm,
                new Label("Bank Statement (imported)"),
                statementTable,
                new HBox(10, importBtn, autoMatchBtn),
                receiptBar,
                sweepBar,
                chargesBar,
                form,
                notesArea);
        content.getStyleClass().add("card");
        VBox.setVgrow(itemsTable, Priority.ALWAYS);
        VBox.setVgrow(listTable, Priority.SOMETIMES);
        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        scrollPane.setPannable(true);
        scrollPane.getStyleClass().add("content-scroll");
        VBox.setVgrow(scrollPane, Priority.ALWAYS);
        getChildren().add(scrollPane);
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
        AccountType account = bankSelector.getValue();
        if (account == null) {
            AlertUtil.warn("Select account", "Choose a bank account to reconcile.");
            return;
        }
        BankReconciliation rec = service.createReconciliation(
                account, date, stmtBal, notesArea.getText().trim());
        rec.getItems().clear();
        rec.getItems().addAll(service.calculateUnclearedItems(rec, account));
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
        chooser.setInitialFileName(FileNamingUtil.suggest("reconciliation-" + rec.getId() + ".csv"));
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

    private void importStatement() {
        javafx.stage.FileChooser chooser = new javafx.stage.FileChooser();
        chooser.setTitle("Import Bank Statement");
        chooser.getExtensionFilters().addAll(
                new javafx.stage.FileChooser.ExtensionFilter("Excel files", "*.xlsx"),
                new javafx.stage.FileChooser.ExtensionFilter("CSV files", "*.csv"));
        java.io.File file = chooser.showOpenDialog(getScene() != null ? getScene().getWindow() : null);
        if (file == null) return;
        try {
            List<BankStatementEntry> entries = new BankStatementImportService().importFile(file.toPath());
            BankStatementStore.getInstance().replaceAll(entries);
            statementTable.getItems().setAll(entries);
            AlertUtil.info("Imported", "Imported " + entries.size() + " statement rows from:\n" + file.getAbsolutePath());
        } catch (Exception e) {
            AlertUtil.error("Import failed", e.getMessage());
        }
    }

    private void autoMatch() {
        BankReconciliation rec = listTable.getSelectionModel().getSelectedItem();
        if (rec == null) {
            AlertUtil.warn("Select reconciliation", "Select a reconciliation first.");
            return;
        }
        AccountType account = bankSelector.getValue();
        if (account == null) account = AccountType.BANK_BOARDING;
        try {
            List<BankStatementEntry> entries = service.currentStatementEntries();
            int matched = service.autoMatchFromStatement(rec, account, entries);
            itemsTable.getItems().setAll(rec.getItems());
            statementTable.getItems().setAll(entries);
            listTable.refresh();
            AlertUtil.info("Auto-match complete", "Matched " + matched + " items against the statement.");
        } catch (Exception e) {
            AlertUtil.error("Auto-match failed", e.getMessage());
        }
    }

    private void markSelectedCleared() {
        BankReconciliation rec = listTable.getSelectionModel().getSelectedItem();
        if (rec == null) {
            AlertUtil.warn("Select reconciliation", "Select a reconciliation first.");
            return;
        }
        BankReconciliation.ReconciliationItem item = itemsTable.getSelectionModel().getSelectedItem();
        if (item == null) {
            AlertUtil.warn("Select item", "Select a cashbook item to mark cleared.");
            return;
        }
        BankStatementEntry stmt = statementTable.getSelectionModel().getSelectedItem();
        try {
            String user = com.schaccs.config.AppConfig.getInstance().getCurrentUser();
            if (stmt != null) {
                service.pairItemToStatement(rec, item.getId(), stmt,
                        LocalDate.now().toString());
            } else {
                service.markItemCleared(rec, item, LocalDate.now(), "MANUAL", null, user);
            }
            itemsTable.getItems().setAll(rec.getItems());
            if (stmt != null) statementTable.getItems().setAll(BankStatementStore.getInstance().getEntries());
            listTable.refresh();
        } catch (Exception e) {
            AlertUtil.error("Could not clear item", e.getMessage());
        }
    }

    private void generateReceiptFromStatement() {
        String adm = receiptAdmField.getText().trim();
        if (adm.isEmpty()) {
            AlertUtil.warn("Missing admission", "Enter the student admission number.");
            return;
        }
        var maybe = com.schaccs.store.StudentStore.getInstance().findByAdmissionNumber(adm);
        if (maybe.isEmpty()) {
            AlertUtil.warn("Student not found", "No student with admission number " + adm);
            return;
        }
        try {
            BigDecimal amt = CurrencyConfig.money(receiptAmountField.getText().trim());
            var result = service.generateReceiptFromStatement(maybe.get(), amt, LocalDate.now(),
                    "STMT-" + System.currentTimeMillis(), "Direct bank credit from statement");
            if (result.isSuccess()) {
                AlertUtil.info("Receipt posted", "Receipt raised for " + adm + " of " + CurrencyUtil.format(amt));
            } else {
                AlertUtil.error("Could not post receipt", String.join("\n", result.getErrors()));
            }
        } catch (Exception e) {
            AlertUtil.error("Receipt failed", e.getMessage());
        }
    }

    private void mpesaSweep() {
        AccountType bank = bankSelector.getValue();
        if (bank == null) bank = AccountType.BANK_BOARDING;
        try {
            BigDecimal amt = CurrencyConfig.money(sweepAmountField.getText().trim());
            service.sweepMpesaClearing(bank, LocalDate.now(), "SWEEP-" + System.currentTimeMillis(),
                    amt, "M-Pesa Pay Bill bulk transfer");
            AlertUtil.info("Sweep posted", "M-Pesa in-transit cleared into " + bank.getDisplayName());
            refresh();
        } catch (Exception e) {
            AlertUtil.error("Sweep failed", e.getMessage());
        }
    }

    private void postBankCharges() {
        AccountType bank = bankSelector.getValue();
        if (bank == null) bank = AccountType.BANK_BOARDING;
        try {
            BigDecimal amt = CurrencyConfig.money(chargeAmountField.getText().trim());
            service.postBankCharges(bank, LocalDate.now(), "BCH-" + System.currentTimeMillis(),
                    "Ledger fees (direct debit)", amt);
            AlertUtil.info("Charge posted", "Bank charges of " + CurrencyUtil.format(amt) + " posted.");
            refresh();
        } catch (Exception e) {
            AlertUtil.error("Charge failed", e.getMessage());
        }
    }

    private void exportPdfStatement() {
        BankReconciliation rec = listTable.getSelectionModel().getSelectedItem();
        if (rec == null) {
            AlertUtil.warn("Select reconciliation", "Select a reconciliation first.");
            return;
        }
        javafx.stage.FileChooser chooser = new javafx.stage.FileChooser();
        chooser.setTitle("Export Bank Reconciliation Statement (PDF)");
        chooser.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter("PDF files", "*.pdf"));
        chooser.setInitialFileName(FileNamingUtil.suggest("bank-reconciliation-statement.pdf"));
        java.io.File file = chooser.showSaveDialog(getScene() != null ? getScene().getWindow() : null);
        if (file == null) return;
        try {
            BigDecimal deposits = service.getDepositsInTransit(rec);
            BigDecimal cheques = service.getUnpresentedCheques(rec);
            com.schaccs.service.export.BankReconciliationPdfExporter exporter =
                    new com.schaccs.service.export.BankReconciliationPdfExporter();
            exporter.export(file.toPath(), rec, bankName(rec), deposits, cheques);
            AlertUtil.info("PDF exported", "Bank Reconciliation Statement saved to:\n" + file.getAbsolutePath());
        } catch (Exception e) {
            AlertUtil.error("PDF export failed", e.getMessage());
        }
    }

    private String bankName(BankReconciliation rec) {
        try {
            if (rec.getBankAccountType() != null) {
                return AccountType.valueOf(rec.getBankAccountType()).getDisplayName();
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    @Override
    public void refresh() {
        listTable.getItems().setAll(store.getReconciliations());
        AccountType bank = bankSelector.getValue();
        if (bank == null) bank = AccountType.BANK_BOARDING;
        BigDecimal bookBalance = com.schaccs.store.LedgerStore.getInstance().getAccountBalance(bank);
        bookBalanceField.setText(CurrencyUtil.formatPlain(bookBalance));
        statementTable.getItems().setAll(BankStatementStore.getInstance().getEntries());
    }
}
