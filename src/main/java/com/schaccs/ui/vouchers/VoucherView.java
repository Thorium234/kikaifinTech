package com.schaccs.ui.vouchers;

import com.schaccs.enums.PaymentMode;
import com.schaccs.model.finance.Votehead;
import com.schaccs.model.voucher.Commitment;
import com.schaccs.model.voucher.Creditor;
import com.schaccs.model.voucher.Invoice;
import com.schaccs.model.voucher.Imprest;
import com.schaccs.model.voucher.Lpo;
import com.schaccs.model.voucher.PaymentVoucher;
import com.schaccs.service.Services;
import com.schaccs.service.export.PdfExportService;
import com.schaccs.service.export.SpreadsheetExportService;
import com.schaccs.service.voucher.PaymentVoucherService;
import com.schaccs.store.FeeStructureStore;
import com.schaccs.store.VoucherStore;
import com.schaccs.ui.component.CurrencyField;
import com.schaccs.ui.layout.MainLayout;
import com.schaccs.util.AlertUtil;
import com.schaccs.util.CurrencyUtil;
import com.schaccs.util.DateUtil;
import com.schaccs.util.FileDialogMemory;
import com.schaccs.util.FileNamingUtil;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Commitments + payment vouchers. Posts through PaymentVoucherService → AccountingEngine.
 */
public class VoucherView extends VBox implements MainLayout.Refreshable {

    private final PaymentVoucherService service = Services.getInstance().voucher();
    private final VoucherStore store = VoucherStore.getInstance();
    private final SpreadsheetExportService exportService = new SpreadsheetExportService();
    private final PdfExportService pdfExportService = new PdfExportService();

    private final TextField creditorName = new TextField();
    private final TextField creditorPhone = new TextField();
    private final ComboBox<Creditor> creditorBox = new ComboBox<>();
    private final ComboBox<Votehead> voteheadBox = new ComboBox<>();
    private final CurrencyField commitAmount = new CurrencyField();
    private final TextField commitDesc = new TextField();
    private final TextField commitRef = new TextField();
    private final DatePicker commitDate = new DatePicker(LocalDate.now());

    private final TableView<Commitment> commitmentTable = new TableView<>();
    private final TableView<PaymentVoucher> voucherTable = new TableView<>();
    private final CurrencyField payAmount = new CurrencyField();
    private final ComboBox<PaymentMode> payMode = new ComboBox<>();
    private final TextField payRef = new TextField();
    private final DatePicker payDate = new DatePicker(LocalDate.now());

    private final ComboBox<Creditor> lpoCreditorBox = new ComboBox<>();
    private final ComboBox<Votehead> lpoVoteheadBox = new ComboBox<>();
    private final TextField lpoNumberField = new TextField();
    private final TextField lpoDescField = new TextField();
    private final CurrencyField lpoAmountField = new CurrencyField();
    private final DatePicker lpoDate = new DatePicker(LocalDate.now());
    private final TableView<Lpo> lpoTable = new TableView<>();
    private Lpo selectedLpo;

    private final ComboBox<Creditor> invoiceCreditorBox = new ComboBox<>();
    private final ComboBox<Votehead> invoiceVoteheadBox = new ComboBox<>();
    private final ComboBox<Lpo> invoiceLpoBox = new ComboBox<>();
    private final TextField invoiceNumberField = new TextField();
    private final TextField invoiceDescField = new TextField();
    private final CurrencyField invoiceAmountField = new CurrencyField();
    private final DatePicker invoiceDate = new DatePicker(LocalDate.now());
    private final TableView<Invoice> invoiceTable = new TableView<>();
    private Invoice selectedInvoice;

    private final TextField imprestStaffField = new TextField();
    private final ComboBox<Votehead> imprestVoteheadBox = new ComboBox<>();
    private final TextField imprestPurposeField = new TextField();
    private final CurrencyField imprestAmountField = new CurrencyField();
    private final DatePicker imprestDate = new DatePicker(LocalDate.now());
    private final CurrencyField surrenderAmountField = new CurrencyField();
    private final DatePicker surrenderDate = new DatePicker(LocalDate.now());
    private final TableView<Imprest> imprestTable = new TableView<>();
    private final Label voucherModeBadge = new Label();
    private Imprest selectedImprest;

    public VoucherView() {
        setSpacing(12);
        setPadding(new Insets(4));

        Label heading = new Label("Payment Vouchers & Commitments");
        heading.getStyleClass().add("section-title");
        Label badge = new Label("Expenditure Control Workspace");
        badge.getStyleClass().add("voucher-header-badge");

        Label note = new Label("Record supplier commitments, then pay via voucher. All payments post through AccountingEngine.");
        note.getStyleClass().addAll("muted", "voucher-subtitle");
        note.setWrapText(true);

        HBox top = new HBox(16, buildCreditorCard(), buildCommitmentForm());
        top.setFillHeight(true);
        HBox.setHgrow(top.getChildren().get(1), Priority.ALWAYS);

        setupCommitmentTable();
        setupVoucherTable();

        HBox commitmentHeader = new HBox(10, new Label("Open Commitments"), exportButton("Export Commitments", this::exportCommitments));
        commitmentHeader.getStyleClass().add("voucher-section-header");
        VBox commitCard = new VBox(10, commitmentHeader, commitmentTable, buildPayBar());
        commitCard.getStyleClass().addAll("card", "voucher-table-card");
        VBox.setVgrow(commitmentTable, Priority.ALWAYS);

        HBox voucherHeader = new HBox(10, new Label("Paid Vouchers"), exportButton("Export Vouchers", this::exportVouchers));
        voucherHeader.getStyleClass().add("voucher-section-header");
        VBox voucherCard = new VBox(10, voucherHeader, voucherTable);
        voucherCard.getStyleClass().addAll("card", "voucher-table-card");
        VBox.setVgrow(voucherTable, Priority.ALWAYS);

        HBox lower = new HBox(16, commitCard, voucherCard);
        lower.setFillHeight(true);
        HBox.setHgrow(commitCard, Priority.ALWAYS);
        HBox.setHgrow(voucherCard, Priority.ALWAYS);
        VBox.setVgrow(lower, Priority.ALWAYS);

        setupLpoTable();
        setupInvoiceTable();
        setupImprestTable();
        TabPane extraTabs = new TabPane(
                new Tab("LPOs", wrapTabContent(buildLpoTab())),
                new Tab("Invoices", wrapTabContent(buildInvoiceTab())),
                new Tab("Imprests", wrapTabContent(buildImprestTab()))
        );
        extraTabs.getTabs().forEach(t -> t.setClosable(false));
        VBox.setVgrow(extraTabs, Priority.ALWAYS);

        HBox exportBar = new HBox(10,
                exportButton("Export Creditors", this::exportCreditors),
                exportButton("Export LPOs", this::exportLpos),
                exportButton("Export Invoices", this::exportInvoices),
                exportButton("Export Imprests", this::exportImprests));
        exportBar.getStyleClass().add("voucher-toolbar");
        exportBar.setAlignment(Pos.CENTER_LEFT);

        ScrollPane lowerScroll = new ScrollPane(lower);
        lowerScroll.setFitToWidth(true);
        lowerScroll.setFitToHeight(true);
        lowerScroll.setPannable(true);
        lowerScroll.getStyleClass().add("inline-scroll-pane");
        VBox.setVgrow(lowerScroll, Priority.ALWAYS);

        VBox headerCard = new VBox(8, badge, heading, note, exportBar);
        headerCard.getStyleClass().addAll("card", "voucher-header-card");
        voucherModeBadge.getStyleClass().addAll("voucher-mode-badge", "voucher-mode-ready");
        voucherModeBadge.setText("Ready for Commitment and Voucher Processing");

        VBox content = new VBox(12, headerCard, voucherModeBadge, top, lowerScroll, extraTabs);
        content.setPadding(new Insets(4));
        ScrollPane mainScroll = new ScrollPane(content);
        mainScroll.setFitToWidth(true);
        mainScroll.setPannable(true);
        mainScroll.getStyleClass().add("content-scroll");
        VBox.setVgrow(mainScroll, Priority.ALWAYS);
        getChildren().add(mainScroll);
        refresh();
    }

    private VBox buildCreditorCard() {
        Label t = new Label("Add Creditor");
        t.getStyleClass().add("section-title");
        Label sub = new Label("Maintain supplier master data before creating commitments.");
        sub.getStyleClass().add("muted");
        creditorName.setPromptText("Supplier name");
        creditorPhone.setPromptText("Phone");
        Button add = new Button("Add Creditor");
        add.getStyleClass().add("primary-button");
        add.setOnAction(e -> {
            if (creditorName.getText() == null || creditorName.getText().isBlank()) {
                AlertUtil.warn("Validation", "Creditor name is required.");
                return;
            }
            service.addCreditor(creditorName.getText().trim(), creditorPhone.getText().trim(), null);
            creditorName.clear();
            creditorPhone.clear();
            refresh();
            AlertUtil.info("Saved", "Creditor added.");
        });
        VBox box = new VBox(10,
                t,
                sub,
                voucherFieldBlock("Creditor Name", creditorName, "Official supplier or service provider name."),
                voucherFieldBlock("Phone", creditorPhone, "Primary supplier contact number."),
                add);
        box.getStyleClass().addAll("card", "voucher-form-card");
        box.setPrefWidth(260);
        return box;
    }

    private VBox buildCommitmentForm() {
        Label t = new Label("New Commitment");
        t.getStyleClass().add("section-title");
        Label sub = new Label("Capture approved commitments before actual payment is posted.");
        sub.getStyleClass().add("muted");

        creditorBox.setItems(store.getCreditors());
        creditorBox.setPrefWidth(220);
        voteheadBox.setItems(FeeStructureStore.getInstance().getVoteheads());
        voteheadBox.setPrefWidth(220);
        commitDesc.setPromptText("Description / invoice");
        commitRef.setPromptText("LPO / Invoice ref");

        Button create = new Button("Create Commitment");
        create.getStyleClass().add("success-button");
        create.setOnAction(e -> {
            List<String> errors = service.createCommitment(
                    creditorBox.getValue(),
                    voteheadBox.getValue(),
                    commitAmount.getAmount(),
                    commitDesc.getText(),
                    commitRef.getText(),
                    commitDate.getValue());
            if (!errors.isEmpty()) {
                AlertUtil.warn("Validation", String.join("\n", errors));
                return;
            }
            commitAmount.clear();
            commitDesc.clear();
            commitRef.clear();
            refresh();
            AlertUtil.info("Saved", "Commitment recorded.");
        });

        GridPane g = new GridPane();
        g.getStyleClass().add("voucher-form-grid");
        makeVoucherForm(g);
        g.add(voucherLabel("Creditor"), 0, 0);
        g.add(voucherFieldBox(creditorBox, "Choose the supplier to be committed."), 1, 0);
        g.add(voucherLabel("Votehead"), 0, 1);
        g.add(voucherFieldBox(voteheadBox, "Select the expenditure votehead to be debited."), 1, 1);
        g.add(voucherLabel("Amount"), 0, 2);
        g.add(voucherFieldBox(commitAmount, "Enter the approved commitment amount."), 1, 2);
        g.add(voucherLabel("Reference"), 0, 3);
        g.add(voucherFieldBox(commitRef, "LPO, invoice, or approval reference."), 1, 3);
        g.add(voucherLabel("Description"), 0, 4);
        g.add(voucherFieldBox(commitDesc, "Describe the goods, services, or payment purpose."), 1, 4);
        g.add(voucherLabel("Date"), 0, 5);
        g.add(voucherFieldBox(commitDate, "Commitment creation date."), 1, 5);

        VBox box = new VBox(10, t, sub, g, create);
        box.getStyleClass().addAll("card", "voucher-form-card");
        return box;
    }

    private void setupCommitmentTable() {
        TableColumn<Commitment, String> date = new TableColumn<>("Date");
        date.setCellValueFactory(c -> new SimpleStringProperty(DateUtil.format(c.getValue().getDate())));
        TableColumn<Commitment, String> cred = new TableColumn<>("Creditor");
        cred.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getCreditorName()));
        TableColumn<Commitment, String> vh = new TableColumn<>("Votehead");
        vh.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getVoteheadName()));
        TableColumn<Commitment, String> amt = new TableColumn<>("Amount");
        amt.setCellValueFactory(c -> new SimpleStringProperty(CurrencyUtil.format(c.getValue().getAmount())));
        TableColumn<Commitment, String> out = new TableColumn<>("Outstanding");
        out.setCellValueFactory(c -> new SimpleStringProperty(CurrencyUtil.format(c.getValue().getOutstanding())));
        TableColumn<Commitment, String> st = new TableColumn<>("Status");
        st.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getStatus()));
        @SuppressWarnings("unchecked")
        var columns1 = new TableColumn[]{date, cred, vh, amt, out, st};
        commitmentTable.getColumns().addAll(columns1);
        commitmentTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        commitmentTable.setPrefHeight(220);
        commitmentTable.getSelectionModel().selectedItemProperty().addListener((obs, o, c) -> {
            if (c != null) {
                payAmount.setAmount(c.getOutstanding());
            }
        });
    }

    private Pane buildPayBar() {
        payMode.getItems().setAll(PaymentMode.allowedModes());
        payMode.setValue(PaymentMode.BANK_SLIP);
        payRef.setPromptText("Bank ref / cheque no");
        payAmount.setPrefWidth(120);

        Button pay = new Button("Pay Selected (Post Voucher)");
        pay.getStyleClass().add("success-button");
        pay.setOnAction(e -> {
            Commitment c = commitmentTable.getSelectionModel().getSelectedItem();
            List<String> errors = service.payVoucher(
                    c, payAmount.getAmount(), payMode.getValue(),
                    payRef.getText(), payDate.getValue(), null);
            if (!errors.isEmpty()) {
                AlertUtil.warn("Cannot pay", String.join("\n", errors));
                return;
            }
            payRef.clear();
            refresh();
            AlertUtil.info("Paid", "Payment voucher posted to the ledger.");
        });

        FlowPane bar = new FlowPane(10, 10,
                new Label("Pay:"), payAmount,
                new Label("Mode:"), payMode,
                new Label("Ref:"), payRef,
                new Label("Date:"), payDate,
                pay);
        bar.getStyleClass().add("voucher-pay-bar");
        bar.setAlignment(Pos.CENTER_LEFT);
        return bar;
    }

    private void setupVoucherTable() {
        TableColumn<PaymentVoucher, String> num = new TableColumn<>("PV #");
        num.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getVoucherNumberDisplay()));
        TableColumn<PaymentVoucher, String> date = new TableColumn<>("Date");
        date.setCellValueFactory(c -> new SimpleStringProperty(DateUtil.format(c.getValue().getDate())));
        TableColumn<PaymentVoucher, String> cred = new TableColumn<>("Creditor");
        cred.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getCreditorName()));
        TableColumn<PaymentVoucher, String> vh = new TableColumn<>("Votehead");
        vh.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getVoteheadName()));
        TableColumn<PaymentVoucher, String> amt = new TableColumn<>("Amount");
        amt.setCellValueFactory(c -> new SimpleStringProperty(CurrencyUtil.format(c.getValue().getAmount())));
        TableColumn<PaymentVoucher, String> st = new TableColumn<>("Status");
        st.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getStatus() != null ? c.getValue().getStatus().getDisplayName() : ""));
        @SuppressWarnings("unchecked")
        var columns2 = new TableColumn[]{num, date, cred, vh, amt, st};
        voucherTable.getColumns().addAll(columns2);
        voucherTable.setItems(store.getVouchers());
        voucherTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        voucherTable.setPrefHeight(220);
    }

    private VBox buildLpoTab() {
        lpoCreditorBox.setItems(store.getCreditors());
        lpoVoteheadBox.setItems(FeeStructureStore.getInstance().getVoteheads());
        lpoNumberField.setPromptText("LPO number");
        lpoDescField.setPromptText("Description");
        Button create = new Button("Create LPO");
        create.getStyleClass().add("success-button");
        create.setOnAction(e -> {
            List<String> errors = service.createLpo(lpoCreditorBox.getValue(), lpoVoteheadBox.getValue(),
                    lpoAmountField.getAmount(), lpoNumberField.getText(), lpoDescField.getText(), lpoDate.getValue());
            if (!errors.isEmpty()) {
                AlertUtil.warn("Validation", String.join("\n", errors));
                return;
            }
            clearLpoForm();
            refresh();
        });
        Button update = new Button("Update Selected");
        update.getStyleClass().add("primary-button");
        update.setOnAction(e -> {
            List<String> errors = service.updateLpo(selectedLpo, lpoCreditorBox.getValue(), lpoVoteheadBox.getValue(),
                    lpoAmountField.getAmount(), lpoNumberField.getText(), lpoDescField.getText(), lpoDate.getValue());
            if (!errors.isEmpty()) {
                AlertUtil.warn("Validation", String.join("\n", errors));
                return;
            }
            clearLpoForm();
            refresh();
            AlertUtil.info("Updated", "LPO updated.");
        });
        Button cancel = new Button("Cancel Selected");
        cancel.getStyleClass().add("secondary-button");
        cancel.setOnAction(e -> cancelSelectedLpo());
        Button delete = new Button("Delete Selected");
        delete.getStyleClass().add("secondary-button");
        delete.setOnAction(e -> deleteSelectedLpo());
        GridPane form = new GridPane();
        form.getStyleClass().add("voucher-form-grid");
        makeVoucherForm(form);
        form.add(voucherLabel("Creditor"), 0, 0);
        form.add(voucherFieldBox(lpoCreditorBox, "Supplier for the purchase order."), 1, 0);
        form.add(voucherLabel("Votehead"), 0, 1);
        form.add(voucherFieldBox(lpoVoteheadBox, "Budget line against which this LPO is committed."), 1, 1);
        form.add(voucherLabel("LPO Number"), 0, 2);
        form.add(voucherFieldBox(lpoNumberField, "Official LPO reference number."), 1, 2);
        form.add(voucherLabel("Amount"), 0, 3);
        form.add(voucherFieldBox(lpoAmountField, "Total LPO value."), 1, 3);
        form.add(voucherLabel("Description"), 0, 4);
        form.add(voucherFieldBox(lpoDescField, "Describe the procurement request."), 1, 4);
        form.add(voucherLabel("Date"), 0, 5);
        form.add(voucherFieldBox(lpoDate, "LPO issue date."), 1, 5);
        FlowPane actions = new FlowPane(10, 10, create, update, cancel, delete, exportButton("Export PDF", this::exportLposPdf));
        actions.getStyleClass().add("voucher-action-bar");
        return new VBox(10, sectionTitle("LPO Management", "Create, edit, cancel, or export local purchase orders."), form, actions, new Separator(), lpoTable);
    }

    private VBox buildInvoiceTab() {
        invoiceCreditorBox.setItems(store.getCreditors());
        invoiceVoteheadBox.setItems(FeeStructureStore.getInstance().getVoteheads());
        invoiceLpoBox.setItems(store.getLpos());
        invoiceNumberField.setPromptText("Invoice number");
        invoiceDescField.setPromptText("Description");
        Button create = new Button("Create Invoice");
        create.getStyleClass().add("success-button");
        create.setOnAction(e -> {
            List<String> errors = service.createInvoice(invoiceCreditorBox.getValue(), invoiceVoteheadBox.getValue(),
                    invoiceAmountField.getAmount(), invoiceNumberField.getText(), invoiceDescField.getText(),
                    invoiceDate.getValue(), invoiceLpoBox.getValue());
            if (!errors.isEmpty()) {
                AlertUtil.warn("Validation", String.join("\n", errors));
                return;
            }
            clearInvoiceForm();
            refresh();
        });
        Button update = new Button("Update Selected");
        update.getStyleClass().add("primary-button");
        update.setOnAction(e -> {
            List<String> errors = service.updateInvoice(selectedInvoice, invoiceCreditorBox.getValue(), invoiceVoteheadBox.getValue(),
                    invoiceAmountField.getAmount(), invoiceNumberField.getText(), invoiceDescField.getText(),
                    invoiceDate.getValue(), invoiceLpoBox.getValue());
            if (!errors.isEmpty()) {
                AlertUtil.warn("Validation", String.join("\n", errors));
                return;
            }
            clearInvoiceForm();
            refresh();
            AlertUtil.info("Updated", "Invoice updated.");
        });
        Button cancel = new Button("Cancel Selected");
        cancel.getStyleClass().add("secondary-button");
        cancel.setOnAction(e -> cancelSelectedInvoice());
        Button delete = new Button("Delete Selected");
        delete.getStyleClass().add("secondary-button");
        delete.setOnAction(e -> deleteSelectedInvoice());
        GridPane form = new GridPane();
        form.getStyleClass().add("voucher-form-grid");
        makeVoucherForm(form);
        form.add(voucherLabel("Creditor"), 0, 0);
        form.add(voucherFieldBox(invoiceCreditorBox, "Supplier issuing the invoice."), 1, 0);
        form.add(voucherLabel("Votehead"), 0, 1);
        form.add(voucherFieldBox(invoiceVoteheadBox, "Expense votehead to be charged."), 1, 1);
        form.add(voucherLabel("LPO"), 0, 2);
        form.add(voucherFieldBox(invoiceLpoBox, "Optional linked LPO record."), 1, 2);
        form.add(voucherLabel("Invoice Number"), 0, 3);
        form.add(voucherFieldBox(invoiceNumberField, "Supplier invoice reference number."), 1, 3);
        form.add(voucherLabel("Amount"), 0, 4);
        form.add(voucherFieldBox(invoiceAmountField, "Gross invoice amount."), 1, 4);
        form.add(voucherLabel("Description"), 0, 5);
        form.add(voucherFieldBox(invoiceDescField, "Short description of the invoice."), 1, 5);
        form.add(voucherLabel("Date"), 0, 6);
        form.add(voucherFieldBox(invoiceDate, "Invoice date."), 1, 6);
        FlowPane actions = new FlowPane(10, 10, create, update, cancel, delete, exportButton("Export PDF", this::exportInvoicesPdf));
        actions.getStyleClass().add("voucher-action-bar");
        return new VBox(10, sectionTitle("Invoice Management", "Capture supplier invoices and track payable status."), form, actions, new Separator(), invoiceTable);
    }

    private ScrollPane wrapTabContent(VBox content) {
        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(true);
        scrollPane.setPannable(true);
        scrollPane.getStyleClass().add("inline-scroll-pane");
        return scrollPane;
    }

    private VBox buildImprestTab() {
        imprestVoteheadBox.setItems(FeeStructureStore.getInstance().getVoteheads());
        imprestStaffField.setPromptText("Staff name");
        imprestPurposeField.setPromptText("Purpose");
        Button issue = new Button("Issue Imprest");
        issue.getStyleClass().add("success-button");
        issue.setOnAction(e -> {
            List<String> errors = service.createImprest(imprestStaffField.getText(), imprestVoteheadBox.getValue(),
                    imprestAmountField.getAmount(), imprestPurposeField.getText(), imprestDate.getValue());
            if (!errors.isEmpty()) {
                AlertUtil.warn("Validation", String.join("\n", errors));
                return;
            }
            clearImprestForm();
            refresh();
        });
        Button surrender = new Button("Surrender Selected");
        surrender.getStyleClass().add("secondary-button");
        surrender.setOnAction(e -> {
            Imprest imp = selectedImprest;
            List<String> errors = service.surrenderImprest(imp, surrenderAmountField.getAmount(), surrenderDate.getValue());
            if (!errors.isEmpty()) {
                AlertUtil.warn("Validation", String.join("\n", errors));
                return;
            }
            surrenderAmountField.clear();
            refresh();
        });
        Button update = new Button("Update Selected");
        update.getStyleClass().add("primary-button");
        update.setOnAction(e -> {
            List<String> errors = service.updateImprest(selectedImprest, imprestStaffField.getText(), imprestVoteheadBox.getValue(),
                    imprestAmountField.getAmount(), imprestPurposeField.getText(), imprestDate.getValue());
            if (!errors.isEmpty()) {
                AlertUtil.warn("Validation", String.join("\n", errors));
                return;
            }
            clearImprestForm();
            refresh();
            AlertUtil.info("Updated", "Imprest updated.");
        });
        Button delete = new Button("Delete Selected");
        delete.getStyleClass().add("secondary-button");
        delete.setOnAction(e -> deleteSelectedImprest());
        GridPane form = new GridPane();
        form.getStyleClass().add("voucher-form-grid");
        makeVoucherForm(form);
        form.add(voucherLabel("Staff"), 0, 0);
        form.add(voucherFieldBox(imprestStaffField, "Staff member receiving the imprest."), 1, 0);
        form.add(voucherLabel("Votehead"), 0, 1);
        form.add(voucherFieldBox(imprestVoteheadBox, "Expense votehead funding the imprest."), 1, 1);
        form.add(voucherLabel("Amount"), 0, 2);
        form.add(voucherFieldBox(imprestAmountField, "Amount issued to staff."), 1, 2);
        form.add(voucherLabel("Purpose"), 0, 3);
        form.add(voucherFieldBox(imprestPurposeField, "Reason for the imprest issue."), 1, 3);
        form.add(voucherLabel("Date"), 0, 4);
        form.add(voucherFieldBox(imprestDate, "Imprest issue date."), 1, 4);
        form.add(voucherLabel("Surrender Amount"), 0, 5);
        form.add(voucherFieldBox(surrenderAmountField, "Amount surrendered back or accounted for."), 1, 5);
        form.add(voucherLabel("Surrender Date"), 0, 6);
        form.add(voucherFieldBox(surrenderDate, "Date of surrender/accountability."), 1, 6);
        FlowPane actions = new FlowPane(10, 10, issue, update, delete, surrender, exportButton("Export PDF", this::exportImprestsPdf));
        actions.getStyleClass().add("voucher-action-bar");
        return new VBox(10, sectionTitle("Imprest Management", "Issue, update, surrender, and export staff imprests."), form, actions, new Separator(), imprestTable);
    }

    private void setupLpoTable() {
        TableColumn<Lpo, String> num = new TableColumn<>("LPO #");
        num.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getLpoNumber()));
        TableColumn<Lpo, String> date = new TableColumn<>("Date");
        date.setCellValueFactory(c -> new SimpleStringProperty(DateUtil.format(c.getValue().getDate())));
        TableColumn<Lpo, String> creditor = new TableColumn<>("Creditor");
        creditor.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getCreditorName()));
        TableColumn<Lpo, String> amount = new TableColumn<>("Amount");
        amount.setCellValueFactory(c -> new SimpleStringProperty(CurrencyUtil.format(c.getValue().getAmount())));
        TableColumn<Lpo, String> status = new TableColumn<>("Status");
        status.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getStatus()));
        @SuppressWarnings("unchecked")
        var columns3 = new TableColumn[]{num, date, creditor, amount, status};
        lpoTable.getColumns().addAll(columns3);
        lpoTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        lpoTable.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, value) -> {
            selectedLpo = value;
            if (value != null) {
                loadLpoForm(value);
            }
        });
    }

    private void setupInvoiceTable() {
        TableColumn<Invoice, String> num = new TableColumn<>("Invoice #");
        num.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getInvoiceNumber()));
        TableColumn<Invoice, String> date = new TableColumn<>("Date");
        date.setCellValueFactory(c -> new SimpleStringProperty(DateUtil.format(c.getValue().getDate())));
        TableColumn<Invoice, String> creditor = new TableColumn<>("Creditor");
        creditor.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getCreditorName()));
        TableColumn<Invoice, String> amount = new TableColumn<>("Amount");
        amount.setCellValueFactory(c -> new SimpleStringProperty(CurrencyUtil.format(c.getValue().getAmount())));
        TableColumn<Invoice, String> status = new TableColumn<>("Status");
        status.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getStatus()));
        @SuppressWarnings("unchecked")
        var columns4 = new TableColumn[]{num, date, creditor, amount, status};
        invoiceTable.getColumns().addAll(columns4);
        invoiceTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        invoiceTable.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, value) -> {
            selectedInvoice = value;
            if (value != null) {
                loadInvoiceForm(value);
            }
        });
    }

    private void setupImprestTable() {
        TableColumn<Imprest, String> staff = new TableColumn<>("Staff");
        staff.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getStaffName()));
        TableColumn<Imprest, String> date = new TableColumn<>("Date");
        date.setCellValueFactory(c -> new SimpleStringProperty(DateUtil.format(c.getValue().getDate())));
        TableColumn<Imprest, String> amount = new TableColumn<>("Amount");
        amount.setCellValueFactory(c -> new SimpleStringProperty(CurrencyUtil.format(c.getValue().getAmount())));
        TableColumn<Imprest, String> surrendered = new TableColumn<>("Surrendered");
        surrendered.setCellValueFactory(c -> new SimpleStringProperty(CurrencyUtil.format(c.getValue().getSurrenderedAmount())));
        TableColumn<Imprest, String> status = new TableColumn<>("Status");
        status.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getStatus()));
        @SuppressWarnings("unchecked")
        var columns5 = new TableColumn[]{staff, date, amount, surrendered, status};
        imprestTable.getColumns().addAll(columns5);
        imprestTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        imprestTable.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, value) -> {
            selectedImprest = value;
            if (value != null) {
                loadImprestForm(value);
            }
        });
    }

    private void exportLposPdf() {
        exportPdfTable("Local Purchase Orders", "lpos.pdf",
                List.of("LPO Number", "Date", "Creditor", "Votehead", "Amount", "Status", "Description"),
                store.getLpos().stream().map(l -> List.of(
                        safe(l.getLpoNumber()),
                        DateUtil.format(l.getDate()),
                        safe(l.getCreditorName()),
                        safe(l.getVoteheadName()),
                        CurrencyUtil.formatPlain(l.getAmount()),
                        safe(l.getStatus()),
                        safe(l.getDescription())
                )).toList());
    }

    private void exportInvoicesPdf() {
        exportPdfTable("Supplier Invoices", "invoices.pdf",
                List.of("Invoice Number", "Date", "Creditor", "Votehead", "Amount", "Status", "Description", "LPO ID"),
                store.getInvoices().stream().map(i -> List.of(
                        safe(i.getInvoiceNumber()),
                        DateUtil.format(i.getDate()),
                        safe(i.getCreditorName()),
                        safe(i.getVoteheadName()),
                        CurrencyUtil.formatPlain(i.getAmount()),
                        safe(i.getStatus()),
                        safe(i.getDescription()),
                        safe(i.getLpoId())
                )).toList());
    }

    private void exportImprestsPdf() {
        exportPdfTable("Imprest Register", "imprests.pdf",
                List.of("Staff Name", "Date", "Votehead", "Amount", "Status", "Purpose", "Surrendered", "Surrender Date"),
                store.getImprests().stream().map(i -> List.of(
                        safe(i.getStaffName()),
                        DateUtil.format(i.getDate()),
                        safe(i.getVoteheadName()),
                        CurrencyUtil.formatPlain(i.getAmount()),
                        safe(i.getStatus()),
                        safe(i.getPurpose()),
                        CurrencyUtil.formatPlain(i.getSurrenderedAmount()),
                        i.getSurrenderDate() != null ? DateUtil.format(i.getSurrenderDate()) : ""
                )).toList());
    }



    private void exportPdfTable(String title, String initialName, List<String> headers, List<List<String>> rows) {
        FileChooser chooser = new FileChooser();
        FileDialogMemory.applyTo(chooser);
        chooser.setTitle(title);
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF files", "*.pdf"));
        chooser.setInitialFileName(FileNamingUtil.suggest(initialName));
        File file = chooser.showSaveDialog(getScene() != null ? getScene().getWindow() : null);
        if (file == null) {
            return;
        }
        FileDialogMemory.remember(file);
        try {
            pdfExportService.exportTable(file.toPath(), title, headers, rows);
            AlertUtil.info("Export complete", "PDF exported to:\n" + file.getAbsolutePath());
        } catch (IOException e) {
            AlertUtil.error("Export failed", e.getMessage());
        }
    }

    private Button exportButton(String text, Runnable action) {
        Button button = new Button(text);
        button.getStyleClass().add("secondary-button");
        button.setOnAction(e -> action.run());
        return button;
    }

    private void exportCreditors() {
        File file = chooseSaveFile("Export Creditors", "creditors.csv");
        if (file == null) {
            return;
        }
        try {
            List<String> headers = List.of("Name", "Phone", "Description");
            List<List<String>> rows = store.getCreditors().stream().map(c -> List.of(
                    safe(c.getName()),
                    safe(c.getPhone()),
                    safe(c.getDescription())
            )).toList();
            exportService.export(file.toPath(), "Creditors", headers, rows);
            AlertUtil.info("Export complete", "Creditors exported to:\n" + file.getAbsolutePath());
        } catch (IOException e) {
            AlertUtil.error("Export failed", e.getMessage());
        }
    }

    private void exportLpos() {
        File file = chooseSaveFile("Export LPOs", "lpos.csv");
        if (file == null) {
            return;
        }
        try {
            List<String> headers = List.of("LPO Number", "Date", "Creditor", "Votehead", "Amount", "Status", "Description");
            List<List<String>> rows = store.getLpos().stream().map(l -> List.of(
                    safe(l.getLpoNumber()),
                    DateUtil.format(l.getDate()),
                    safe(l.getCreditorName()),
                    safe(l.getVoteheadName()),
                    CurrencyUtil.formatPlain(l.getAmount()),
                    safe(l.getStatus()),
                    safe(l.getDescription())
            )).toList();
            exportService.export(file.toPath(), "LPOs", headers, rows);
            AlertUtil.info("Export complete", "LPOs exported to:\n" + file.getAbsolutePath());
        } catch (IOException e) {
            AlertUtil.error("Export failed", e.getMessage());
        }
    }

    private void exportInvoices() {
        File file = chooseSaveFile("Export Invoices", "invoices.csv");
        if (file == null) {
            return;
        }
        try {
            List<String> headers = List.of("Invoice Number", "Date", "Creditor", "Votehead", "Amount", "Status", "Description", "LPO ID");
            List<List<String>> rows = store.getInvoices().stream().map(i -> List.of(
                    safe(i.getInvoiceNumber()),
                    DateUtil.format(i.getDate()),
                    safe(i.getCreditorName()),
                    safe(i.getVoteheadName()),
                    CurrencyUtil.formatPlain(i.getAmount()),
                    safe(i.getStatus()),
                    safe(i.getDescription()),
                    safe(i.getLpoId())
            )).toList();
            exportService.export(file.toPath(), "Invoices", headers, rows);
            AlertUtil.info("Export complete", "Invoices exported to:\n" + file.getAbsolutePath());
        } catch (IOException e) {
            AlertUtil.error("Export failed", e.getMessage());
        }
    }

    private void exportImprests() {
        File file = chooseSaveFile("Export Imprests", "imprests.csv");
        if (file == null) {
            return;
        }
        try {
            List<String> headers = List.of("Staff Name", "Date", "Votehead", "Amount", "Status", "Purpose", "Surrendered Amount", "Surrender Date");
            List<List<String>> rows = store.getImprests().stream().map(i -> List.of(
                    safe(i.getStaffName()),
                    DateUtil.format(i.getDate()),
                    safe(i.getVoteheadName()),
                    CurrencyUtil.formatPlain(i.getAmount()),
                    safe(i.getStatus()),
                    safe(i.getPurpose()),
                    CurrencyUtil.formatPlain(i.getSurrenderedAmount()),
                    i.getSurrenderDate() != null ? DateUtil.format(i.getSurrenderDate()) : ""
            )).toList();
            exportService.export(file.toPath(), "Imprests", headers, rows);
            AlertUtil.info("Export complete", "Imprests exported to:\n" + file.getAbsolutePath());
        } catch (IOException e) {
            AlertUtil.error("Export failed", e.getMessage());
        }
    }

    private void exportCommitments() {
        File file = chooseSaveFile("Export Commitments", "commitments.csv");
        if (file == null) {
            return;
        }
        try {
            List<String> headers = List.of("Date", "Creditor", "Votehead", "Amount", "Paid", "Outstanding", "Status", "Reference", "Description");
            List<List<String>> rows = store.getCommitments().stream().map(c -> List.of(
                    DateUtil.format(c.getDate()),
                    safe(c.getCreditorName()),
                    safe(c.getVoteheadName()),
                    CurrencyUtil.formatPlain(c.getAmount()),
                    CurrencyUtil.formatPlain(c.getAmountPaid()),
                    CurrencyUtil.formatPlain(c.getOutstanding()),
                    safe(c.getStatus()),
                    safe(c.getReference()),
                    safe(c.getDescription())
            )).toList();
            exportService.export(file.toPath(), "Commitments", headers, rows);
            AlertUtil.info("Export complete", "Commitments exported to:\n" + file.getAbsolutePath());
        } catch (IOException e) {
            AlertUtil.error("Export failed", e.getMessage());
        }
    }

    private void exportVouchers() {
        File file = chooseSaveFile("Export Vouchers", "vouchers.csv");
        if (file == null) {
            return;
        }
        try {
            List<String> headers = List.of("Voucher Number", "Date", "Creditor", "Votehead", "Amount", "Status", "Payment Mode", "Reference", "Prepared By", "Approved By");
            List<List<String>> rows = store.getVouchers().stream().map(v -> List.of(
                    v.getVoucherNumberDisplay(),
                    DateUtil.format(v.getDate()),
                    safe(v.getCreditorName()),
                    safe(v.getVoteheadName()),
                    CurrencyUtil.formatPlain(v.getAmount()),
                    v.getStatus() != null ? v.getStatus().getDisplayName() : "",
                    v.getPaymentMode() != null ? v.getPaymentMode().getDisplayName() : "",
                    safe(v.getBankReference()),
                    safe(v.getPreparedBy()),
                    safe(v.getApprovedBy())
            )).toList();
            exportService.export(file.toPath(), "Vouchers", headers, rows);
            AlertUtil.info("Export complete", "Vouchers exported to:\n" + file.getAbsolutePath());
        } catch (IOException e) {
            AlertUtil.error("Export failed", e.getMessage());
        }
    }

    private File chooseSaveFile(String title, String initialName) {
        FileChooser chooser = new FileChooser();
        FileDialogMemory.applyTo(chooser);
        chooser.setTitle(title);
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("CSV files", "*.csv"),
                new FileChooser.ExtensionFilter("Excel files", "*.xlsx")
        );
        chooser.setInitialFileName(FileNamingUtil.suggest(initialName));
        File file = chooser.showSaveDialog(getScene() != null ? getScene().getWindow() : null);
        FileDialogMemory.remember(file);
        return file;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private void loadLpoForm(Lpo lpo) {
        lpoCreditorBox.setValue(findCreditor(lpo.getCreditorId()).orElse(null));
        lpoVoteheadBox.setValue(findVotehead(lpo.getVoteheadCode()).orElse(null));
        lpoNumberField.setText(lpo.getLpoNumber());
        lpoDescField.setText(lpo.getDescription());
        lpoAmountField.setAmount(lpo.getAmount());
        lpoDate.setValue(lpo.getDate());
    }

    private void clearLpoForm() {
        selectedLpo = null;
        lpoTable.getSelectionModel().clearSelection();
        lpoCreditorBox.setValue(null);
        lpoVoteheadBox.setValue(null);
        lpoNumberField.clear();
        lpoDescField.clear();
        lpoAmountField.clear();
        lpoDate.setValue(LocalDate.now());
    }

    private void cancelSelectedLpo() {
        if (selectedLpo == null) {
            AlertUtil.warn("Selection required", "Select an LPO to cancel.");
            return;
        }
        if (!AlertUtil.confirm("Cancel LPO", "Cancel selected LPO " + safe(selectedLpo.getLpoNumber()) + "?")) {
            return;
        }
        List<String> errors = service.cancelLpo(selectedLpo);
        if (!errors.isEmpty()) {
            AlertUtil.warn("Cannot cancel", String.join("\n", errors));
            return;
        }
        clearLpoForm();
        refresh();
        AlertUtil.info("Cancelled", "LPO cancelled.");
    }

    private void deleteSelectedLpo() {
        if (selectedLpo == null) {
            AlertUtil.warn("Selection required", "Select an LPO to delete.");
            return;
        }
        if (!AlertUtil.confirm("Delete LPO", "Delete selected LPO " + safe(selectedLpo.getLpoNumber()) + "? This cannot be undone.")) {
            return;
        }
        List<String> errors = service.deleteLpo(selectedLpo);
        if (!errors.isEmpty()) {
            AlertUtil.warn("Cannot delete", String.join("\n", errors));
            return;
        }
        clearLpoForm();
        refresh();
        AlertUtil.info("Deleted", "LPO deleted.");
    }

    private void loadInvoiceForm(Invoice invoice) {
        invoiceCreditorBox.setValue(findCreditor(invoice.getCreditorId()).orElse(null));
        invoiceVoteheadBox.setValue(findVotehead(invoice.getVoteheadCode()).orElse(null));
        invoiceLpoBox.setValue(findLpo(invoice.getLpoId()).orElse(null));
        invoiceNumberField.setText(invoice.getInvoiceNumber());
        invoiceDescField.setText(invoice.getDescription());
        invoiceAmountField.setAmount(invoice.getAmount());
        invoiceDate.setValue(invoice.getDate());
    }

    private void clearInvoiceForm() {
        selectedInvoice = null;
        invoiceTable.getSelectionModel().clearSelection();
        invoiceCreditorBox.setValue(null);
        invoiceVoteheadBox.setValue(null);
        invoiceLpoBox.setValue(null);
        invoiceNumberField.clear();
        invoiceDescField.clear();
        invoiceAmountField.clear();
        invoiceDate.setValue(LocalDate.now());
    }

    private void cancelSelectedInvoice() {
        if (selectedInvoice == null) {
            AlertUtil.warn("Selection required", "Select an invoice to cancel.");
            return;
        }
        if (!AlertUtil.confirm("Cancel Invoice", "Cancel selected invoice " + safe(selectedInvoice.getInvoiceNumber()) + "?")) {
            return;
        }
        List<String> errors = service.cancelInvoice(selectedInvoice);
        if (!errors.isEmpty()) {
            AlertUtil.warn("Cannot cancel", String.join("\n", errors));
            return;
        }
        clearInvoiceForm();
        refresh();
        AlertUtil.info("Cancelled", "Invoice cancelled.");
    }

    private void deleteSelectedInvoice() {
        if (selectedInvoice == null) {
            AlertUtil.warn("Selection required", "Select an invoice to delete.");
            return;
        }
        if (!AlertUtil.confirm("Delete Invoice", "Delete selected invoice " + safe(selectedInvoice.getInvoiceNumber()) + "? This cannot be undone.")) {
            return;
        }
        List<String> errors = service.deleteInvoice(selectedInvoice);
        if (!errors.isEmpty()) {
            AlertUtil.warn("Cannot delete", String.join("\n", errors));
            return;
        }
        clearInvoiceForm();
        refresh();
        AlertUtil.info("Deleted", "Invoice deleted.");
    }

    private void loadImprestForm(Imprest imprest) {
        imprestStaffField.setText(imprest.getStaffName());
        imprestVoteheadBox.setValue(findVotehead(imprest.getVoteheadCode()).orElse(null));
        imprestPurposeField.setText(imprest.getPurpose());
        imprestAmountField.setAmount(imprest.getAmount());
        imprestDate.setValue(imprest.getDate());
        surrenderAmountField.setAmount(imprest.getSurrenderedAmount());
        surrenderDate.setValue(imprest.getSurrenderDate() != null ? imprest.getSurrenderDate() : LocalDate.now());
    }

    private void clearImprestForm() {
        selectedImprest = null;
        imprestTable.getSelectionModel().clearSelection();
        imprestStaffField.clear();
        imprestVoteheadBox.setValue(null);
        imprestPurposeField.clear();
        imprestAmountField.clear();
        imprestDate.setValue(LocalDate.now());
        surrenderAmountField.clear();
        surrenderDate.setValue(LocalDate.now());
    }

    private void deleteSelectedImprest() {
        if (selectedImprest == null) {
            AlertUtil.warn("Selection required", "Select an imprest to delete.");
            return;
        }
        if (!AlertUtil.confirm("Delete Imprest", "Delete selected imprest for " + safe(selectedImprest.getStaffName()) + "? This cannot be undone.")) {
            return;
        }
        List<String> errors = service.deleteImprest(selectedImprest);
        if (!errors.isEmpty()) {
            AlertUtil.warn("Cannot delete", String.join("\n", errors));
            return;
        }
        clearImprestForm();
        refresh();
        AlertUtil.info("Deleted", "Imprest deleted.");
    }

    private VBox sectionTitle(String title, String hint) {
        Label heading = new Label(title);
        heading.getStyleClass().add("section-title");
        Label sub = new Label(hint);
        sub.getStyleClass().add("muted");
        sub.setWrapText(true);
        return new VBox(4, heading, sub);
    }

    private Label voucherLabel(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("voucher-form-label");
        return label;
    }

    private void makeVoucherForm(GridPane grid) {
        grid.setHgap(10);
        grid.setVgap(8);
        ColumnConstraints labelCol = new ColumnConstraints();
        labelCol.setPrefWidth(90);
        ColumnConstraints fieldCol = new ColumnConstraints();
        fieldCol.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(labelCol, fieldCol);
    }

    private VBox voucherFieldBox(javafx.scene.Node field, String hint) {
        Label hintLabel = new Label(hint);
        hintLabel.getStyleClass().addAll("muted", "voucher-field-hint");
        VBox box = new VBox(4, field, hintLabel);
        box.getStyleClass().add("voucher-field-box");
        if (field instanceof javafx.scene.layout.Region region) {
            region.setMaxWidth(Double.MAX_VALUE);
        }
        return box;
    }

    private Optional<Creditor> findCreditor(String creditorId) {
        return store.getCreditors().stream().filter(c -> c.getId().equals(creditorId)).findFirst();
    }

    private Optional<Votehead> findVotehead(String voteheadCode) {
        return FeeStructureStore.getInstance().getVoteheads().stream().filter(v -> v.getCode().equals(voteheadCode)).findFirst();
    }

    private Optional<Lpo> findLpo(String lpoId) {
        return store.getLpos().stream().filter(l -> l.getId().equals(lpoId)).findFirst();
    }

    @Override
    public void refresh() {
        payMode.getItems().setAll(PaymentMode.allowedModes());
        if (!payMode.getItems().contains(payMode.getValue())) {
            payMode.setValue(payMode.getItems().isEmpty() ? null : payMode.getItems().get(0));
        }
        creditorBox.setItems(store.getCreditors());
        voteheadBox.setItems(FeeStructureStore.getInstance().getVoteheads());
        lpoCreditorBox.setItems(store.getCreditors());
        invoiceCreditorBox.setItems(store.getCreditors());
        lpoVoteheadBox.setItems(FeeStructureStore.getInstance().getVoteheads());
        invoiceVoteheadBox.setItems(FeeStructureStore.getInstance().getVoteheads());
        imprestVoteheadBox.setItems(FeeStructureStore.getInstance().getVoteheads());
        invoiceLpoBox.setItems(store.getLpos());
        commitmentTable.getItems().setAll(store.getCommitments());
        voucherTable.setItems(store.getVouchers());
        lpoTable.setItems(store.getLpos());
        invoiceTable.setItems(store.getInvoices());
        imprestTable.setItems(store.getImprests());
        voucherTable.refresh();
        lpoTable.refresh();
        invoiceTable.refresh();
        imprestTable.refresh();
    }

    private VBox voucherFieldBlock(String label, TextField field, String hint) {
        VBox box = new VBox(4, new Label(label), field, new Label(hint));
        box.getStyleClass().add("voucher-field-block");
        return box;
    }
}
