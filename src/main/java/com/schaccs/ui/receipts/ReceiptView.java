package com.schaccs.ui.receipts;

import com.schaccs.config.AppConfig;
import com.schaccs.enums.PaymentMode;
import com.schaccs.model.fee.FeeAllocation;
import com.schaccs.model.receipt.Receipt;
import com.schaccs.model.student.Student;
import com.schaccs.model.student.StudentFeeLedger;
import com.schaccs.service.Services;
import com.schaccs.service.export.PdfExportService;
import com.schaccs.service.receipt.ReceiptService;
import com.schaccs.store.StudentStore;
import com.schaccs.ui.component.CurrencyField;
import com.schaccs.ui.component.SearchBar;
import com.schaccs.ui.layout.MainLayout;
import com.schaccs.util.AlertUtil;
import com.schaccs.util.CurrencyUtil;
import com.schaccs.util.PrintUtil;
import javafx.application.Platform;
import com.schaccs.util.ReceiptPrinter;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class ReceiptView extends VBox implements MainLayout.Refreshable {

    private final ReceiptService receiptService = Services.getInstance().receipt();
    private final StudentStore studentStore = StudentStore.getInstance();
    private final PdfExportService pdfExportService = new PdfExportService();

    private final SearchBar searchBar = new SearchBar("Search student by admission no or name…");
    private final TableView<Student> studentTable = new TableView<>();
    private final Label studentSummary = new Label("Select a student");
    private final Label balanceLabel = new Label();
    private final CurrencyField amountField = new CurrencyField();
    private final ComboBox<PaymentMode> modeBox = new ComboBox<>();
    private final TextField refField = new TextField();
    private final DatePicker datePicker = new DatePicker(LocalDate.now());
    private final TableView<FeeAllocation> allocationTable = new TableView<>();
    private final TextArea previewArea = new TextArea();
    private final Label receiptModeBadge = new Label();
    private final Label paymentHint = new Label();

    private Student selected;
    private Receipt lastReceipt;

    public ReceiptView() {
        setSpacing(12);
        setPadding(new Insets(4));

        Label heading = new Label("Receipting — Automatic Votehead Allocation");
        heading.getStyleClass().add("section-title");
        Label badge = new Label("Student Fee Collection Workspace");
        badge.getStyleClass().add("receipt-header-badge");
        Label subHeading = new Label("Search a student, review outstanding balances, preview allocation, then post an official receipt.");
        subHeading.getStyleClass().addAll("muted", "receipt-subtitle");

        Label policy = new Label("School policy: No cash except bank pay-in slip approved by the Principal.");
        policy.getStyleClass().add("policy-banner");
        policy.setWrapText(true);
        policy.setMaxWidth(Double.MAX_VALUE);

        setupStudentTable();
        searchBar.textProperty().addListener((obs, o, q) -> filterStudents(q));

        Label searchTitle = new Label("Student Search & Selection");
        searchTitle.getStyleClass().add("section-title");
        Label searchHint = new Label("Use admission number, student name, or class to quickly find a learner.");
        searchHint.getStyleClass().add("muted");
        VBox searchCard = new VBox(10, searchTitle, searchHint, searchBar, studentTable);
        searchCard.getStyleClass().addAll("card", "receipt-search-card");
        studentTable.setPrefHeight(200);

        studentSummary.getStyleClass().add("receipt-student-summary");
        balanceLabel.getStyleClass().addAll("muted", "receipt-balance-summary");
        receiptModeBadge.getStyleClass().add("receipt-mode-badge");
        paymentHint.getStyleClass().addAll("muted", "receipt-payment-hint");
        paymentHint.setText("Select a student and enter an amount to preview exact votehead allocation before posting.");

        modeBox.getItems().setAll(PaymentMode.allowedModes());
        modeBox.setValue(PaymentMode.BANK_SLIP);
        refField.setPromptText("Bank slip / M-Pesa / cheque reference");

        amountField.textProperty().addListener((obs, o, n) -> previewAllocation());

        Button previewBtn = new Button("Preview Allocation");
        previewBtn.getStyleClass().add("secondary-button");
        previewBtn.setOnAction(e -> previewAllocation());

        Button receiveBtn = new Button("Receive Payment");
        receiveBtn.getStyleClass().add("success-button");
        receiveBtn.setOnAction(e -> receive());

        Button printBtn = new Button("Print Preview");
        printBtn.getStyleClass().add("primary-button");
        printBtn.setOnAction(e -> printPreview());

        Button pdfBtn = new Button("Export Receipt PDF");
        pdfBtn.getStyleClass().add("secondary-button");
        pdfBtn.setOnAction(e -> exportReceiptPdf());

        GridPane form = new GridPane();
        form.getStyleClass().add("receipt-form-grid");
        form.setHgap(10);
        form.setVgap(10);
        form.add(receiptLabel("Amount (KSh)"), 0, 0);
        form.add(receiptFieldBox(amountField, "Enter the full amount received from the student."), 1, 0);
        form.add(receiptLabel("Payment Mode"), 0, 1);
        form.add(receiptFieldBox(modeBox, "Use approved non-cash collection channels."), 1, 1);
        form.add(receiptLabel("Reference"), 0, 2);
        form.add(receiptFieldBox(refField, "Bank slip, cheque, EFT, or M-Pesa transaction reference."), 1, 2);
        form.add(receiptLabel("Date"), 0, 3);
        form.add(receiptFieldBox(datePicker, "Posting date for this receipt."), 1, 3);
        amountField.setPrefWidth(200);
        modeBox.setPrefWidth(200);
        refField.setPrefWidth(200);

        HBox actions = new HBox(10, previewBtn, receiveBtn, printBtn, pdfBtn);
        actions.getStyleClass().add("receipt-action-bar");
        actions.setAlignment(Pos.CENTER_LEFT);

        setupAllocationTable();

        Label allocationTitle = new Label("Automatic Votehead Distribution");
        allocationTitle.getStyleClass().add("section-title");
        VBox payCard = new VBox(12, receiptModeBadge, studentSummary, balanceLabel, new Separator(), paymentHint, form, actions,
                allocationTitle, allocationTable);
        payCard.getStyleClass().addAll("card", "receipt-pay-card");
        VBox.setVgrow(allocationTable, Priority.SOMETIMES);

        previewArea.setEditable(false);
        previewArea.setPrefRowCount(16);
        previewArea.setStyle("-fx-font-family: monospace; -fx-font-size: 12px;");
        Label previewTitle = new Label("Official Receipt Preview");
        previewTitle.getStyleClass().add("section-title");
        Label previewHint = new Label("Preview the official receipt wording before printing or exporting PDF.");
        previewHint.getStyleClass().add("muted");
        VBox previewCard = new VBox(10, previewTitle, previewHint, previewArea);
        previewCard.getStyleClass().addAll("card", "receipt-preview-card");
        previewCard.setPrefWidth(420);
        VBox.setVgrow(previewArea, Priority.ALWAYS);

        HBox lower = new HBox(16, payCard, previewCard);
        lower.setFillHeight(true);
        HBox.setHgrow(payCard, Priority.ALWAYS);
        HBox.setHgrow(previewCard, Priority.ALWAYS);
        previewCard.setMinWidth(320);
        VBox.setVgrow(lower, Priority.ALWAYS);

        VBox headerCard = new VBox(8, badge, heading, subHeading, policy);
        headerCard.getStyleClass().addAll("card", "receipt-header-card");
        getChildren().addAll(headerCard, searchCard, lower);
        updateReceiptMode();
        filterStudents("");
    }

    private void setupStudentTable() {
        TableColumn<Student, String> adm = new TableColumn<>("Adm No");
        adm.setCellValueFactory(c -> c.getValue().admissionNumberProperty());
        adm.setPrefWidth(100);

        TableColumn<Student, String> name = new TableColumn<>("Name");
        name.setCellValueFactory(c -> c.getValue().nameProperty());
        name.setPrefWidth(180);

        TableColumn<Student, String> cls = new TableColumn<>("Class");
        cls.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getClassLabel()));
        cls.setPrefWidth(80);

        TableColumn<Student, String> bal = new TableColumn<>("Balance");
        bal.setCellValueFactory(c -> {
            StudentFeeLedger ledger = studentStore.getLedger(c.getValue().getId());
            return new SimpleStringProperty(CurrencyUtil.format(ledger.getBalance()));
        });
        bal.setPrefWidth(120);

        studentTable.getColumns().addAll(adm, name, cls, bal);
        studentTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        studentTable.getSelectionModel().selectedItemProperty().addListener((obs, o, s) -> selectStudent(s));
    }

    private void setupAllocationTable() {
        TableColumn<FeeAllocation, String> vh = new TableColumn<>("Vote Head");
        vh.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getVoteheadName()));
        vh.setPrefWidth(160);

        TableColumn<FeeAllocation, String> due = new TableColumn<>("Due");
        due.setCellValueFactory(c -> new SimpleStringProperty(CurrencyUtil.format(c.getValue().getOutstandingBefore())));
        due.setPrefWidth(100);

        TableColumn<FeeAllocation, String> alloc = new TableColumn<>("Allocated");
        alloc.setCellValueFactory(c -> new SimpleStringProperty(CurrencyUtil.format(c.getValue().getAllocated())));
        alloc.setPrefWidth(100);

        TableColumn<FeeAllocation, String> after = new TableColumn<>("Remaining");
        after.setCellValueFactory(c -> new SimpleStringProperty(CurrencyUtil.format(c.getValue().getOutstandingAfter())));
        after.setPrefWidth(100);

        allocationTable.getColumns().addAll(vh, due, alloc, after);
        allocationTable.setPrefHeight(180);
        allocationTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
    }

    private void filterStudents(String q) {
        studentTable.setItems(studentStore.search(q));
    }

    private void selectStudent(Student s) {
        selected = s;
        if (s == null) {
            studentSummary.setText("Select a student to begin receipting");
            balanceLabel.setText("");
            allocationTable.getItems().clear();
            updateReceiptMode();
            return;
        }
        StudentFeeLedger ledger = studentStore.getLedger(s.getId());
        studentSummary.setText(s.getAdmissionNumber() + " — " + s.getName() + " (" + s.getClassLabel() + ")");
        balanceLabel.setText("Outstanding balance: " + CurrencyUtil.format(ledger.getBalance())
                + "  |  Charged: " + CurrencyUtil.format(ledger.getTotalCharged())
                + "  |  Paid: " + CurrencyUtil.format(ledger.getTotalPaid())
                + (ledger.getArrears().compareTo(BigDecimal.ZERO) > 0
                ? "  |  Arrears: " + CurrencyUtil.format(ledger.getArrears()) : ""));
        updateReceiptMode();
        previewAllocation();
        studentTable.refresh();
    }

    private void previewAllocation() {
        if (selected == null) {
            return;
        }
        BigDecimal amount = amountField.getAmount();
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            allocationTable.getItems().clear();
            return;
        }
        List<FeeAllocation> allocs = receiptService.previewAllocation(selected, amount);
        allocationTable.getItems().setAll(allocs);
        paymentHint.setText(allocs.isEmpty()
                ? "No allocatable balances found for the entered amount."
                : "Allocation preview updated. Review votehead distribution before posting.");
    }

    private void receive() {
        if (selected == null) {
            AlertUtil.warn("No student", "Search and select a student first.");
            return;
        }
        BigDecimal amount = amountField.getAmount();
        ReceiptService.Result result = receiptService.receivePayment(
                selected, amount, modeBox.getValue(), refField.getText(),
                datePicker.getValue(), null);

        if (!result.isSuccess()) {
            AlertUtil.warn("Cannot receive payment", String.join("\n", result.getErrors()));
            return;
        }

        Receipt receipt = result.getReceipt();
        lastReceipt = receipt;
        allocationTable.getItems().setAll(result.getAllocations());
        previewArea.setText(ReceiptPrinter.format(receipt));
        AlertUtil.info("Payment received",
                "Receipt No. " + receipt.getReceiptNumberDisplay() + " for "
                        + CurrencyUtil.format(receipt.getAmount()) + " posted successfully.");

        amountField.clear();
        refField.clear();
        paymentHint.setText("Receipt posted successfully.");
        selectStudent(selected);
        studentTable.refresh();

        PrintUtil.printText("Official Fee Receipt — " + receipt.getReceiptNumberDisplay(),
                ReceiptPrinter.format(receipt),
                getScene() != null ? getScene().getWindow() : null);
    }

    private void exportReceiptPdf() {
        if (lastReceipt == null && (previewArea.getText() == null || previewArea.getText().isBlank())) {
            AlertUtil.warn("No receipt", "Post a payment first, then export the PDF.");
            return;
        }
        Receipt exportReceipt = lastReceipt;
        if (exportReceipt == null) {
            exportReceipt = new Receipt();
            exportReceipt.setReceiptNumber(0);
            exportReceipt.setDate(datePicker.getValue());
            exportReceipt.setStudentId(selected != null ? selected.getId() : null);
            exportReceipt.setAdmissionNumber(selected != null ? selected.getAdmissionNumber() : "");
            exportReceipt.setStudentName(selected != null ? selected.getName() : "");
            exportReceipt.setClassLabel(selected != null ? selected.getClassLabel() : "");
            exportReceipt.setAmount(amountField.getAmount());
            exportReceipt.setPaymentMode(modeBox.getValue());
            exportReceipt.setBankReference(refField.getText());
            exportReceipt.setReceivedBy(AppConfig.getInstance().getCurrentUser());
            allocationTable.getItems().forEach(a -> {
                com.schaccs.model.receipt.ReceiptLine line = new com.schaccs.model.receipt.ReceiptLine();
                line.setVoteheadCode(a.getVoteheadCode());
                line.setVoteheadName(a.getVoteheadName());
                line.setAmount(a.getAllocated());
                exportReceipt.addLine(line);
            });
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export Receipt PDF");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF files", "*.pdf"));
        chooser.setInitialFileName(selected != null ? "receipt-" + selected.getAdmissionNumber() + ".pdf" : "receipt.pdf");
        File file = chooser.showSaveDialog(getScene() != null ? getScene().getWindow() : null);
        if (file == null) return;
        File finalFile = file;
        Receipt finalReceipt = exportReceipt;
        CompletableFuture.runAsync(() -> {
            try {
                pdfExportService.exportReceipt(finalFile.toPath(), finalReceipt);
                Platform.runLater(() -> AlertUtil.info("Export complete", "Receipt PDF exported to:\n" + finalFile.getAbsolutePath()));
            } catch (IOException e) {
                Platform.runLater(() -> AlertUtil.error("Export failed", e.getMessage()));
            }
        });
    }



    private void printPreview() {
        String content = previewArea.getText();
        if (content == null || content.isBlank()) {
            AlertUtil.warn("No receipt", "Generate or select a receipt preview first.");
            return;
        }
        boolean printed = PrintUtil.printText("Receipt Preview", content,
                getScene() != null ? getScene().getWindow() : null);
        if (!printed) {
            AlertUtil.warn("Print cancelled", "No receipt was printed.");
        }
    }

    private Label receiptLabel(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("receipt-form-label");
        return label;
    }

    private VBox receiptFieldBox(javafx.scene.Node field, String hint) {
        Label hintLabel = new Label(hint);
        hintLabel.getStyleClass().addAll("muted", "receipt-field-hint");
        VBox box = new VBox(4, field, hintLabel);
        box.getStyleClass().add("receipt-field-box");
        if (field instanceof javafx.scene.layout.Region region) {
            region.setMaxWidth(Double.MAX_VALUE);
        }
        return box;
    }

    private void updateReceiptMode() {
        if (selected == null) {
            receiptModeBadge.setText("Awaiting Student Selection");
            receiptModeBadge.getStyleClass().removeAll("receipt-mode-ready", "receipt-mode-active");
            receiptModeBadge.getStyleClass().add("receipt-mode-ready");
        } else {
            receiptModeBadge.setText("Ready to Receipt Selected Student");
            receiptModeBadge.getStyleClass().removeAll("receipt-mode-ready", "receipt-mode-active");
            receiptModeBadge.getStyleClass().add("receipt-mode-active");
        }
    }

    @Override
    public void refresh() {
        filterStudents(searchBar.getText());
        if (selected != null) {
            studentStore.findById(selected.getId()).ifPresent(this::selectStudent);
        }
        studentTable.refresh();
    }
}
