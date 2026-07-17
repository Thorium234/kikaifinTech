package com.schaccs.ui.reports;

import com.schaccs.enums.AcademicTerm;
import com.schaccs.model.receipt.Receipt;
import com.schaccs.model.report.AgeingBucket;
import com.schaccs.model.report.CollectionSummary;
import com.schaccs.model.report.TrialBalanceRow;
import com.schaccs.model.report.VoteheadSummary;
import com.schaccs.model.student.Student;
import com.schaccs.model.student.StudentBalance;
import com.schaccs.service.Services;
import com.schaccs.service.export.PdfExportService;
import com.schaccs.service.export.ReportPackExportService;
import com.schaccs.service.export.SpreadsheetExportService;
import com.schaccs.service.finance.AccountingService;
import com.schaccs.service.receipt.ReceiptService;
import com.schaccs.service.report.ReportService;
import com.schaccs.store.ReceiptStore;
import com.schaccs.store.StudentStore;
import com.schaccs.ui.layout.MainLayout;
import com.schaccs.util.AlertUtil;
import com.schaccs.util.CurrencyUtil;
import com.schaccs.util.DateUtil;
import com.schaccs.util.PrintUtil;
import com.schaccs.util.ReceiptPrinter;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

public class ReportsView extends VBox implements MainLayout.Refreshable {

    private final ReportService reportService = Services.getInstance().report();
    private final AccountingService accountingService = Services.getInstance().accounting();
    private final SpreadsheetExportService exportService = new SpreadsheetExportService();
    private final PdfExportService pdfExportService = new PdfExportService();
    private final ReportPackExportService reportPackExportService = new ReportPackExportService(exportService, reportService);

    private final TableView<StudentBalance> balancesTable = new TableView<>();
    private final TableView<StudentBalance> defaultersTable = new TableView<>();
    private final TableView<CollectionSummary> dailyTable = new TableView<>();
    private final TableView<VoteheadSummary> voteheadTable = new TableView<>();
    private final TableView<TrialBalanceRow> trialTable = new TableView<>();
    private final TableView<Receipt> reprintTable = new TableView<>();
    private final TextArea statementArea = new TextArea();
    private final TextArea reprintPreview = new TextArea();
    private final ComboBox<Student> studentBox = new ComboBox<>();
    private final DatePicker dailyDate = new DatePicker(LocalDate.now());
    private final ComboBox<AcademicTerm> termBox = new ComboBox<>();

    public ReportsView() {
        setSpacing(12);
        setPadding(new Insets(4));

        Label heading = new Label("Reports");
        heading.getStyleClass().add("section-title");

        TabPane tabs = new TabPane();
        tabs.getTabs().addAll(
                tab("Fee Balances", buildBalances()),
                tab("Defaulters", buildDefaulters()),
                tab("Daily Collection", buildDaily()),
                tab("Votehead Summary", buildVotehead()),
                tab("Student Statement", buildStatement()),
                tab("Receipt Reprint", buildReprint()),
                tab("Ageing", buildAgeing()),
                tab("Trial Balance", buildTrial())
        );
        VBox.setVgrow(tabs, Priority.ALWAYS);

        Button reportPackBtn = new Button("Export Full Report Pack");
        reportPackBtn.getStyleClass().add("secondary-button");
        reportPackBtn.setOnAction(e -> exportReportPack());
        getChildren().addAll(heading, reportPackBtn, tabs);
        refresh();
    }

    private Tab tab(String title, VBox content) {
        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(true);
        scrollPane.setPannable(true);
        scrollPane.getStyleClass().add("inline-scroll-pane");
        Tab t = new Tab(title, scrollPane);
        t.setClosable(false);
        return t;
    }

    private VBox buildBalances() {
        setupBalanceColumns(balancesTable);
        termBox.getItems().setAll(AcademicTerm.TERM_1, AcademicTerm.TERM_2, AcademicTerm.TERM_3);
        termBox.setPromptText("All terms");
        Button refresh = new Button("Refresh");
        refresh.getStyleClass().add("secondary-button");
        refresh.setOnAction(e -> balancesTable.getItems().setAll(reportService.feeBalances(termBox.getValue())));
        Button export = new Button("Export");
        export.getStyleClass().add("secondary-button");
        export.setOnAction(e -> exportStudentBalances(balancesTable.getItems(), "fee-balances"));
        Button pdf = new Button("PDF");
        pdf.getStyleClass().add("secondary-button");
        pdf.setOnAction(e -> exportStudentBalancesPdf(balancesTable.getItems(), "Fee Balances", "fee-balances.pdf"));
        HBox bar = new HBox(10, new Label("Term:"), termBox, refresh, export, pdf);
        bar.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        VBox box = new VBox(8, bar, balancesTable);
        box.setPadding(new Insets(10));
        VBox.setVgrow(balancesTable, Priority.ALWAYS);
        return box;
    }

    private VBox buildDefaulters() {
        setupBalanceColumns(defaultersTable);
        Button refresh = new Button("Refresh");
        refresh.getStyleClass().add("secondary-button");
        refresh.setOnAction(e -> defaultersTable.getItems().setAll(reportService.defaulters(termBox.getValue(), null)));
        Button rollover = new Button("Roll over arrears");
        rollover.getStyleClass().add("danger-button");
        rollover.setOnAction(e -> rolloverArrears());
        Button export = new Button("Export");
        export.getStyleClass().add("secondary-button");
        export.setOnAction(e -> exportStudentBalances(defaultersTable.getItems(), "defaulters"));
        Button pdf = new Button("PDF");
        pdf.getStyleClass().add("secondary-button");
        pdf.setOnAction(e -> exportStudentBalancesPdf(defaultersTable.getItems(), "Defaulters", "defaulters.pdf"));
        HBox bar = new HBox(10, new Label("Term:"), termBox, refresh, export, pdf, rollover);
        bar.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        VBox box = new VBox(8, bar, defaultersTable);
        box.setPadding(new Insets(10));
        VBox.setVgrow(defaultersTable, Priority.ALWAYS);
        return box;
    }

    private void setupBalanceColumns(TableView<StudentBalance> table) {
        TableColumn<StudentBalance, String> adm = new TableColumn<>("Adm No");
        adm.setCellValueFactory(c -> c.getValue().admissionNumberProperty());
        TableColumn<StudentBalance, String> name = new TableColumn<>("Name");
        name.setCellValueFactory(c -> c.getValue().studentNameProperty());
        TableColumn<StudentBalance, String> cls = new TableColumn<>("Class");
        cls.setCellValueFactory(c -> c.getValue().classLabelProperty());
        TableColumn<StudentBalance, String> charged = new TableColumn<>("Charged");
        charged.setCellValueFactory(c -> new SimpleStringProperty(CurrencyUtil.format(c.getValue().getTotalCharged())));
        TableColumn<StudentBalance, String> paid = new TableColumn<>("Paid");
        paid.setCellValueFactory(c -> new SimpleStringProperty(CurrencyUtil.format(c.getValue().getTotalPaid())));
        TableColumn<StudentBalance, String> arrears = new TableColumn<>("Arrears");
        arrears.setCellValueFactory(c -> new SimpleStringProperty(CurrencyUtil.format(c.getValue().getArrears())));
        TableColumn<StudentBalance, String> bal = new TableColumn<>("Balance");
        bal.setCellValueFactory(c -> new SimpleStringProperty(CurrencyUtil.format(c.getValue().getBalance())));
        table.getColumns().addAll(adm, name, cls, charged, paid, arrears, bal);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
    }

    private VBox buildDaily() {
        TableColumn<CollectionSummary, String> date = new TableColumn<>("Date");
        date.setCellValueFactory(c -> new SimpleStringProperty(DateUtil.format(c.getValue().getDate())));
        TableColumn<CollectionSummary, String> mode = new TableColumn<>("Mode");
        mode.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getPaymentMode().getDisplayName()));
        TableColumn<CollectionSummary, String> count = new TableColumn<>("Receipts");
        count.setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue().getReceiptCount())));
        TableColumn<CollectionSummary, String> total = new TableColumn<>("Total");
        total.setCellValueFactory(c -> new SimpleStringProperty(CurrencyUtil.format(c.getValue().getTotalAmount())));
        dailyTable.getColumns().addAll(date, mode, count, total);
        dailyTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        Button load = new Button("Load");
        load.getStyleClass().add("primary-button");
        load.setOnAction(e -> dailyTable.getItems().setAll(reportService.dailyCollection(dailyDate.getValue())));
        Button export = new Button("Export");
        export.getStyleClass().add("secondary-button");
        export.setOnAction(e -> exportDailyCollection());
        Button pdf = new Button("PDF");
        pdf.getStyleClass().add("secondary-button");
        pdf.setOnAction(e -> exportDailyCollectionPdf());
        HBox bar = new HBox(10, new Label("Date:"), dailyDate, load, export, pdf);
        VBox box = new VBox(8, bar, dailyTable);
        box.setPadding(new Insets(10));
        VBox.setVgrow(dailyTable, Priority.ALWAYS);
        return box;
    }

    private VBox buildVotehead() {
        TableColumn<VoteheadSummary, String> code = new TableColumn<>("Code");
        code.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getVoteheadCode()));
        TableColumn<VoteheadSummary, String> name = new TableColumn<>("Vote Head");
        name.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getVoteheadName()));
        TableColumn<VoteheadSummary, String> charged = new TableColumn<>("Charged");
        charged.setCellValueFactory(c -> new SimpleStringProperty(CurrencyUtil.format(c.getValue().getCharged())));
        TableColumn<VoteheadSummary, String> coll = new TableColumn<>("Collected");
        coll.setCellValueFactory(c -> new SimpleStringProperty(CurrencyUtil.format(c.getValue().getCollected())));
        TableColumn<VoteheadSummary, String> out = new TableColumn<>("Outstanding");
        out.setCellValueFactory(c -> new SimpleStringProperty(CurrencyUtil.format(c.getValue().getOutstanding())));
        voteheadTable.getColumns().addAll(code, name, charged, coll, out);
        voteheadTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        Button refresh = new Button("Refresh");
        refresh.getStyleClass().add("secondary-button");
        refresh.setOnAction(e -> voteheadTable.getItems().setAll(reportService.voteheadSummaries()));
        Button export = new Button("Export");
        export.getStyleClass().add("secondary-button");
        export.setOnAction(e -> exportVoteheadSummary());
        Button pdf = new Button("PDF");
        pdf.getStyleClass().add("secondary-button");
        pdf.setOnAction(e -> exportVoteheadSummaryPdf());
        VBox box = new VBox(8, new HBox(10, refresh, export, pdf), voteheadTable);
        box.setPadding(new Insets(10));
        VBox.setVgrow(voteheadTable, Priority.ALWAYS);
        return box;
    }

    private VBox buildStatement() {
        studentBox.setItems(StudentStore.getInstance().getStudents());
        studentBox.setPrefWidth(320);
        Button load = new Button("Generate Statement");
        load.getStyleClass().add("primary-button");
        load.setOnAction(e -> {
            Student s = studentBox.getValue();
            if (s == null) {
                return;
            }
            StudentBalance bal = reportService.studentStatement(s);
            StringBuilder sb = new StringBuilder();
            sb.append("STUDENT FEE STATEMENT\n");
            sb.append("=====================\n");
            sb.append("Student: ").append(s.getName()).append(" (").append(s.getAdmissionNumber()).append(")\n");
            sb.append("Class:   ").append(s.getClassLabel()).append("\n");
            sb.append("Charged: ").append(CurrencyUtil.format(bal.getTotalCharged())).append("\n");
            sb.append("Paid:    ").append(CurrencyUtil.format(bal.getTotalPaid())).append("\n");
            sb.append("Arrears: ").append(CurrencyUtil.format(bal.getArrears())).append("\n");
            sb.append("Balance: ").append(CurrencyUtil.format(bal.getBalance())).append("\n\n");
            sb.append("Receipts:\n");
            for (Receipt r : reportService.studentReceipts(s)) {
                sb.append(String.format("  #%s  %s  %s  %s%n",
                        r.getReceiptNumberDisplay(),
                        DateUtil.format(r.getDate()),
                        CurrencyUtil.format(r.getAmount()),
                        r.getPaymentMode().getDisplayName()));
            }
            statementArea.setText(sb.toString());
        });
        statementArea.setEditable(false);
        statementArea.setStyle("-fx-font-family: monospace;");
        Button print = new Button("Print Statement");
        print.getStyleClass().add("secondary-button");
        print.setOnAction(e -> printStatement());
        Button export = new Button("Export Statement");
        export.getStyleClass().add("secondary-button");
        export.setOnAction(e -> exportStatement());
        Button pdf = new Button("PDF Statement");
        pdf.getStyleClass().add("secondary-button");
        pdf.setOnAction(e -> exportStatementPdf());
        HBox bar = new HBox(10, new Label("Student:"), studentBox, load, print, export, pdf);
        VBox box = new VBox(8, bar, statementArea);
        box.setPadding(new Insets(10));
        VBox.setVgrow(statementArea, Priority.ALWAYS);
        return box;
    }

    private VBox buildReprint() {
        TableColumn<Receipt, String> num = new TableColumn<>("Receipt #");
        num.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getReceiptNumberDisplay()));
        TableColumn<Receipt, String> student = new TableColumn<>("Student");
        student.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getStudentName()));
        TableColumn<Receipt, String> amount = new TableColumn<>("Amount");
        amount.setCellValueFactory(c -> new SimpleStringProperty(CurrencyUtil.format(c.getValue().getAmount())));
        TableColumn<Receipt, String> date = new TableColumn<>("Date");
        date.setCellValueFactory(c -> new SimpleStringProperty(DateUtil.format(c.getValue().getDate())));
        reprintTable.getColumns().addAll(num, student, amount, date);
        reprintTable.setItems(ReceiptStore.getInstance().getReceipts());
        reprintTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        reprintTable.getSelectionModel().selectedItemProperty().addListener((obs, o, r) -> {
            if (r != null) {
                reprintPreview.setText(ReceiptPrinter.format(r));
            }
        });
        reprintPreview.setEditable(false);
        reprintPreview.setStyle("-fx-font-family: monospace; -fx-font-size: 12px;");
        Button exportBtn = new Button("Export Selected");
        exportBtn.getStyleClass().add("secondary-button");
        exportBtn.setOnAction(e -> exportSelectedReceipt());
        Button pdfBtn = new Button("PDF Selected");
        pdfBtn.getStyleClass().add("secondary-button");
        pdfBtn.setOnAction(e -> exportSelectedReceiptPdf());
        Button printBtn = new Button("Print Selected");
        printBtn.getStyleClass().add("primary-button");
        printBtn.setOnAction(e -> printSelectedReceipt());
        Button reverseBtn = new Button("Reverse Selected");
        reverseBtn.getStyleClass().add("danger-button");
        reverseBtn.setOnAction(e -> reverseReceipt());
        HBox body = new HBox(12, reprintTable, new VBox(8, exportBtn, pdfBtn, printBtn, reverseBtn, reprintPreview));
        HBox.setHgrow(reprintTable, Priority.ALWAYS);
        HBox.setHgrow(reprintPreview, Priority.ALWAYS);
        VBox box = new VBox(8, body);
        box.setPadding(new Insets(10));
        VBox.setVgrow(body, Priority.ALWAYS);
        return box;
    }

    private void printSelectedReceipt() {
        Receipt r = reprintTable.getSelectionModel().getSelectedItem();
        if (r == null) {
            AlertUtil.warn("Select receipt", "Select a receipt to print.");
            return;
        }
        boolean printed = PrintUtil.printText("Receipt " + r.getReceiptNumberDisplay(),
                ReceiptPrinter.format(r), getScene() != null ? getScene().getWindow() : null);
        if (!printed) {
            AlertUtil.warn("Print cancelled", "No receipt was printed.");
        }
    }

    private void reverseReceipt() {
        Receipt r = reprintTable.getSelectionModel().getSelectedItem();
        if (r == null) {
            AlertUtil.warn("Select receipt", "Select a receipt to reverse.");
            return;
        }
        if (r.isReversed()) {
            AlertUtil.warn("Already reversed", "Receipt " + r.getReceiptNumberDisplay() + " is already reversed.");
            return;
        }
        if (!AlertUtil.confirm("Confirm reversal",
                "Reverse receipt " + r.getReceiptNumberDisplay() + " for "
                        + CurrencyUtil.format(r.getAmount()) + "? A contra entry will be posted.")) {
            return;
        }
        ReceiptService.Result result = Services.getInstance().receipt().reverseReceipt(r, "Manual reversal");
        if (!result.isSuccess()) {
            AlertUtil.warn("Cannot reverse", String.join("\n", result.getErrors()));
            return;
        }
        AlertUtil.info("Reversed", "Receipt " + r.getReceiptNumberDisplay() + " reversed.");
        refresh();
    }

    private VBox buildTrial() {
        TableColumn<TrialBalanceRow, String> acct = new TableColumn<>("Account");
        acct.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getAccountName()));
        TableColumn<TrialBalanceRow, String> debit = new TableColumn<>("Debit");
        debit.setCellValueFactory(c -> new SimpleStringProperty(CurrencyUtil.format(c.getValue().getDebit())));
        TableColumn<TrialBalanceRow, String> credit = new TableColumn<>("Credit");
        credit.setCellValueFactory(c -> new SimpleStringProperty(CurrencyUtil.format(c.getValue().getCredit())));
        trialTable.getColumns().addAll(acct, debit, credit);
        trialTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        Button refresh = new Button("Refresh");
        refresh.getStyleClass().add("secondary-button");
        refresh.setOnAction(e -> trialTable.getItems().setAll(reportService.trialBalance()));
        Button export = new Button("Export Trial Balance");
        export.getStyleClass().add("secondary-button");
        export.setOnAction(e -> exportTrialBalance());
        Button pdf = new Button("PDF Trial Balance");
        pdf.getStyleClass().add("secondary-button");
        pdf.setOnAction(e -> exportTrialBalancePdf());
        Button exportLedger = new Button("Export Ledger Transactions");
        exportLedger.getStyleClass().add("secondary-button");
        exportLedger.setOnAction(e -> exportLedgerTransactions());
        Button pdfLedger = new Button("PDF Ledger");
        pdfLedger.getStyleClass().add("secondary-button");
        pdfLedger.setOnAction(e -> exportLedgerTransactionsPdf());
        VBox box = new VBox(8, new HBox(10, refresh, export, pdf, exportLedger, pdfLedger), trialTable);
        box.setPadding(new Insets(10));
        VBox.setVgrow(trialTable, Priority.ALWAYS);
        return box;
    }

    private void rolloverArrears() {
        if (!AlertUtil.confirm("Confirm", "Move all active students' current outstanding balances into arrears?")) {
            return;
        }
        Services.getInstance().arrears().rolloverAll();
        com.schaccs.repository.PersistenceService.getInstance().saveAll();
        AlertUtil.info("Done", "Arrears rolled over for active students.");
        refresh();
    }

    private VBox buildAgeing() {
        TableView<AgeingBucket> table = new TableView<>();
        TableColumn<AgeingBucket, String> bucket = new TableColumn<>("Ageing Bucket");
        bucket.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getLabel()));
        bucket.setPrefWidth(160);
        TableColumn<AgeingBucket, String> amt = new TableColumn<>("Outstanding");
        amt.setCellValueFactory(c -> new SimpleStringProperty(CurrencyUtil.format(c.getValue().getAmount())));
        amt.setPrefWidth(140);
        TableColumn<AgeingBucket, String> cnt = new TableColumn<>("Students");
        cnt.setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue().getStudents())));
        cnt.setPrefWidth(100);
        table.getColumns().addAll(bucket, amt, cnt);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        Button refresh = new Button("Refresh");
        refresh.getStyleClass().add("secondary-button");
        refresh.setOnAction(e -> table.getItems().setAll(reportService.ageing()));
        Button export = new Button("Export");
        export.getStyleClass().add("secondary-button");
        export.setOnAction(e -> exportAgeing(table));
        Button pdf = new Button("PDF");
        pdf.getStyleClass().add("secondary-button");
        pdf.setOnAction(e -> exportAgeingPdf(table));
        VBox box = new VBox(8, new HBox(10, refresh, export, pdf), table);
        box.setPadding(new Insets(10));
        VBox.setVgrow(table, Priority.ALWAYS);
        return box;
    }

    private void printStatement() {
        String content = statementArea.getText();
        if (content == null || content.isBlank()) {
            AlertUtil.warn("No statement", "Generate a statement first.");
            return;
        }
        boolean printed = PrintUtil.printText("Student Statement", content,
                getScene() != null ? getScene().getWindow() : null);
        if (!printed) {
            AlertUtil.warn("Print cancelled", "No statement was printed.");
        }
    }

    private void exportStatement() {
        String content = statementArea.getText();
        if (content == null || content.isBlank()) {
            AlertUtil.warn("No statement", "Generate a statement first.");
            return;
        }
        File file = chooseSaveFile("Export Statement", "student-statement.csv");
        if (file == null) {
            return;
        }
        try {
            List<String> headers = List.of("Statement");
            List<List<String>> rows = content.lines().map(line -> List.of(line)).toList();
            exportService.export(file.toPath(), "Statement", headers, rows);
            AlertUtil.info("Export complete", "Statement exported to:\n" + file.getAbsolutePath());
        } catch (IOException e) {
            AlertUtil.error("Export failed", e.getMessage());
        }
    }

    private void exportSelectedReceiptPdf() {
        Receipt r = reprintTable.getSelectionModel().getSelectedItem();
        if (r == null) {
            AlertUtil.warn("Select receipt", "Select a receipt to export.");
            return;
        }
        File file = choosePdfFile("Export Receipt PDF", "receipt-" + r.getReceiptNumberDisplay() + ".pdf");
        if (file == null) {
            return;
        }
        try {
            pdfExportService.exportTable(file.toPath(), "Official Receipt", List.of("Field", "Value"), receiptRows(r));
            AlertUtil.info("Export complete", "Receipt exported to:\n" + file.getAbsolutePath());
        } catch (IOException e) {
            AlertUtil.error("Export failed", e.getMessage());
        }
    }

    private void exportSelectedReceipt() {
        Receipt r = reprintTable.getSelectionModel().getSelectedItem();
        if (r == null) {
            AlertUtil.warn("Select receipt", "Select a receipt to export.");
            return;
        }
        File file = chooseSaveFile("Export Receipt", "receipt-" + r.getReceiptNumberDisplay() + ".csv");
        if (file == null) {
            return;
        }
        try {
            List<String> headers = List.of("Receipt Text");
            List<List<String>> rows = ReceiptPrinter.format(r).lines().map(line -> List.of(line)).toList();
            exportService.export(file.toPath(), "Receipt", headers, rows);
            AlertUtil.info("Export complete", "Receipt exported to:\n" + file.getAbsolutePath());
        } catch (IOException e) {
            AlertUtil.error("Export failed", e.getMessage());
        }
    }

    private void exportStudentBalances(List<StudentBalance> balances, String baseName) {
        File file = chooseSaveFile("Export " + baseName, baseName + ".csv");
        if (file == null) {
            return;
        }
        try {
            List<String> headers = List.of("Admission Number", "Name", "Class", "Charged", "Paid", "Arrears", "Balance");
            List<List<String>> rows = balances.stream().map(b -> List.of(
                    b.getAdmissionNumber(),
                    b.getStudentName(),
                    b.getClassLabel(),
                    CurrencyUtil.formatPlain(b.getTotalCharged()),
                    CurrencyUtil.formatPlain(b.getTotalPaid()),
                    CurrencyUtil.formatPlain(b.getArrears()),
                    CurrencyUtil.formatPlain(b.getBalance())
            )).toList();
            exportService.export(file.toPath(), "Balances", headers, rows);
            AlertUtil.info("Export complete", "Data exported to:\n" + file.getAbsolutePath());
        } catch (IOException e) {
            AlertUtil.error("Export failed", e.getMessage());
        }
    }

    private void exportDailyCollection() {
        File file = chooseSaveFile("Export Daily Collection", "daily-collection.csv");
        if (file == null) {
            return;
        }
        try {
            List<String> headers = List.of("Date", "Payment Mode", "Receipts", "Total Amount");
            List<List<String>> rows = dailyTable.getItems().stream().map(d -> List.of(
                    DateUtil.format(d.getDate()),
                    d.getPaymentMode().getDisplayName(),
                    String.valueOf(d.getReceiptCount()),
                    CurrencyUtil.formatPlain(d.getTotalAmount())
            )).toList();
            exportService.export(file.toPath(), "Daily Collection", headers, rows);
            AlertUtil.info("Export complete", "Data exported to:\n" + file.getAbsolutePath());
        } catch (IOException e) {
            AlertUtil.error("Export failed", e.getMessage());
        }
    }

    private void exportVoteheadSummary() {
        File file = chooseSaveFile("Export Votehead Summary", "votehead-summary.csv");
        if (file == null) {
            return;
        }
        try {
            List<String> headers = List.of("Code", "Vote Head", "Charged", "Collected", "Outstanding");
            List<List<String>> rows = voteheadTable.getItems().stream().map(v -> List.of(
                    v.getVoteheadCode(),
                    v.getVoteheadName(),
                    CurrencyUtil.formatPlain(v.getCharged()),
                    CurrencyUtil.formatPlain(v.getCollected()),
                    CurrencyUtil.formatPlain(v.getOutstanding())
            )).toList();
            exportService.export(file.toPath(), "Voteheads", headers, rows);
            AlertUtil.info("Export complete", "Data exported to:\n" + file.getAbsolutePath());
        } catch (IOException e) {
            AlertUtil.error("Export failed", e.getMessage());
        }
    }

    private void exportTrialBalance() {
        File file = chooseSaveFile("Export Trial Balance", "trial-balance.csv");
        if (file == null) {
            return;
        }
        try {
            List<String> headers = List.of("Account", "Debit", "Credit");
            List<List<String>> rows = trialTable.getItems().stream().map(t -> List.of(
                    t.getAccountName(),
                    CurrencyUtil.formatPlain(t.getDebit()),
                    CurrencyUtil.formatPlain(t.getCredit())
            )).toList();
            exportService.export(file.toPath(), "Trial Balance", headers, rows);
            AlertUtil.info("Export complete", "Data exported to:\n" + file.getAbsolutePath());
        } catch (IOException e) {
            AlertUtil.error("Export failed", e.getMessage());
        }
    }

    private void exportAgeing(TableView<AgeingBucket> table) {
        File file = chooseSaveFile("Export Ageing", "ageing.csv");
        if (file == null) {
            return;
        }
        try {
            List<String> headers = List.of("Ageing Bucket", "Outstanding", "Students");
            List<List<String>> rows = table.getItems().stream().map(a -> List.of(
                    a.getLabel(),
                    CurrencyUtil.formatPlain(a.getAmount()),
                    String.valueOf(a.getStudents())
            )).toList();
            exportService.export(file.toPath(), "Ageing", headers, rows);
            AlertUtil.info("Export complete", "Data exported to:\n" + file.getAbsolutePath());
        } catch (IOException e) {
            AlertUtil.error("Export failed", e.getMessage());
        }
    }

    private void exportStudentBalancesPdf(List<StudentBalance> balances, String title, String initialName) {
        File file = choosePdfFile("Export " + title + " PDF", initialName);
        if (file == null) {
            return;
        }
        try {
            List<String> headers = List.of("Admission Number", "Name", "Class", "Charged", "Paid", "Arrears", "Balance");
            List<List<String>> rows = balances.stream().map(b -> List.of(
                    b.getAdmissionNumber(),
                    b.getStudentName(),
                    b.getClassLabel(),
                    CurrencyUtil.formatPlain(b.getTotalCharged()),
                    CurrencyUtil.formatPlain(b.getTotalPaid()),
                    CurrencyUtil.formatPlain(b.getArrears()),
                    CurrencyUtil.formatPlain(b.getBalance())
            )).toList();
            pdfExportService.exportTable(file.toPath(), title, headers, rows);
            AlertUtil.info("Export complete", "PDF exported to:\n" + file.getAbsolutePath());
        } catch (IOException e) {
            AlertUtil.error("Export failed", e.getMessage());
        }
    }

    private void exportDailyCollectionPdf() {
        File file = choosePdfFile("Export Daily Collection PDF", "daily-collection.pdf");
        if (file == null) {
            return;
        }
        try {
            List<String> headers = List.of("Date", "Payment Mode", "Receipts", "Total Amount");
            List<List<String>> rows = dailyTable.getItems().stream().map(d -> List.of(
                    DateUtil.format(d.getDate()),
                    d.getPaymentMode().getDisplayName(),
                    String.valueOf(d.getReceiptCount()),
                    CurrencyUtil.formatPlain(d.getTotalAmount())
            )).toList();
            pdfExportService.exportTable(file.toPath(), "Daily Collection", headers, rows);
            AlertUtil.info("Export complete", "PDF exported to:\n" + file.getAbsolutePath());
        } catch (IOException e) {
            AlertUtil.error("Export failed", e.getMessage());
        }
    }

    private void exportVoteheadSummaryPdf() {
        File file = choosePdfFile("Export Votehead Summary PDF", "votehead-summary.pdf");
        if (file == null) {
            return;
        }
        try {
            List<String> headers = List.of("Code", "Vote Head", "Charged", "Collected", "Outstanding");
            List<List<String>> rows = voteheadTable.getItems().stream().map(v -> List.of(
                    v.getVoteheadCode(),
                    v.getVoteheadName(),
                    CurrencyUtil.formatPlain(v.getCharged()),
                    CurrencyUtil.formatPlain(v.getCollected()),
                    CurrencyUtil.formatPlain(v.getOutstanding())
            )).toList();
            pdfExportService.exportTable(file.toPath(), "Votehead Summary", headers, rows);
            AlertUtil.info("Export complete", "PDF exported to:\n" + file.getAbsolutePath());
        } catch (IOException e) {
            AlertUtil.error("Export failed", e.getMessage());
        }
    }

    private void exportStatementPdf() {
        Student s = studentBox.getValue();
        String content = statementArea.getText();
        if (s == null || content == null || content.isBlank()) {
            AlertUtil.warn("No statement", "Generate a statement first.");
            return;
        }
        File file = choosePdfFile("Export Statement PDF", "student-statement-" + s.getAdmissionNumber() + ".pdf");
        if (file == null) {
            return;
        }
        try {
            StudentBalance bal = reportService.studentStatement(s);
            List<List<String>> rows = new java.util.ArrayList<>();
            rows.add(List.of("Student", s.getName()));
            rows.add(List.of("Admission Number", s.getAdmissionNumber()));
            rows.add(List.of("Class", s.getClassLabel()));
            rows.add(List.of("Charged", CurrencyUtil.formatPlain(bal.getTotalCharged())));
            rows.add(List.of("Paid", CurrencyUtil.formatPlain(bal.getTotalPaid())));
            rows.add(List.of("Arrears", CurrencyUtil.formatPlain(bal.getArrears())));
            rows.add(List.of("Balance", CurrencyUtil.formatPlain(bal.getBalance())));
            for (Receipt r : reportService.studentReceipts(s)) {
                rows.add(List.of("Receipt #" + r.getReceiptNumberDisplay(), DateUtil.format(r.getDate()) + " | "
                        + CurrencyUtil.formatPlain(r.getAmount()) + " | " + r.getPaymentMode().getDisplayName()));
            }
            pdfExportService.exportTable(file.toPath(), "Student Statement", List.of("Field", "Value"), rows);
            AlertUtil.info("Export complete", "PDF exported to:\n" + file.getAbsolutePath());
        } catch (IOException e) {
            AlertUtil.error("Export failed", e.getMessage());
        }
    }

    private void exportTrialBalancePdf() {
        File file = choosePdfFile("Export Trial Balance PDF", "trial-balance.pdf");
        if (file == null) {
            return;
        }
        try {
            List<String> headers = List.of("Account", "Debit", "Credit");
            List<List<String>> rows = trialTable.getItems().stream().map(t -> List.of(
                    t.getAccountName(),
                    CurrencyUtil.formatPlain(t.getDebit()),
                    CurrencyUtil.formatPlain(t.getCredit())
            )).toList();
            pdfExportService.exportTable(file.toPath(), "Trial Balance", headers, rows);
            AlertUtil.info("Export complete", "PDF exported to:\n" + file.getAbsolutePath());
        } catch (IOException e) {
            AlertUtil.error("Export failed", e.getMessage());
        }
    }

    private void exportAgeingPdf(TableView<AgeingBucket> table) {
        File file = choosePdfFile("Export Ageing PDF", "ageing.pdf");
        if (file == null) {
            return;
        }
        try {
            List<String> headers = List.of("Ageing Bucket", "Outstanding", "Students");
            List<List<String>> rows = table.getItems().stream().map(a -> List.of(
                    a.getLabel(),
                    CurrencyUtil.formatPlain(a.getAmount()),
                    String.valueOf(a.getStudents())
            )).toList();
            pdfExportService.exportTable(file.toPath(), "Ageing", headers, rows);
            AlertUtil.info("Export complete", "PDF exported to:\n" + file.getAbsolutePath());
        } catch (IOException e) {
            AlertUtil.error("Export failed", e.getMessage());
        }
    }

    private void exportLedgerTransactionsPdf() {
        File file = choosePdfFile("Export Ledger Transactions PDF", "ledger-transactions.pdf");
        if (file == null) {
            return;
        }
        try {
            List<String> headers = List.of("Date", "Type", "Account", "Votehead Code", "Reference", "Description", "Debit", "Credit", "Student ID", "Receipt ID", "Voucher ID", "Created By");
            List<List<String>> rows = accountingService.transactions().stream().map(t -> List.of(
                    DateUtil.format(t.getDate()),
                    t.getType() != null ? t.getType().name() : "",
                    t.getAccountType() != null ? t.getAccountType().getDisplayName() : "",
                    safe(t.getVoteheadCode()),
                    safe(t.getReference()),
                    safe(t.getDescription()),
                    CurrencyUtil.formatPlain(t.getDebit()),
                    CurrencyUtil.formatPlain(t.getCredit()),
                    safe(t.getStudentId()),
                    safe(t.getReceiptId()),
                    safe(t.getVoucherId()),
                    safe(t.getCreatedBy())
            )).toList();
            pdfExportService.exportTable(file.toPath(), "Ledger Transactions", headers, rows);
            AlertUtil.info("Export complete", "PDF exported to:\n" + file.getAbsolutePath());
        } catch (IOException e) {
            AlertUtil.error("Export failed", e.getMessage());
        }
    }

    private List<List<String>> receiptRows(Receipt r) {
        List<List<String>> rows = new java.util.ArrayList<>();
        rows.add(List.of("Receipt Number", r.getReceiptNumberDisplay()));
        rows.add(List.of("Date", DateUtil.format(r.getDate())));
        rows.add(List.of("Student", safe(r.getStudentName())));
        rows.add(List.of("Admission Number", safe(r.getAdmissionNumber())));
        rows.add(List.of("Class", safe(r.getClassLabel())));
        rows.add(List.of("Amount", CurrencyUtil.formatPlain(r.getAmount())));
        rows.add(List.of("Payment Mode", r.getPaymentMode() != null ? r.getPaymentMode().getDisplayName() : ""));
        rows.add(List.of("Reference", safe(r.getBankReference())));
        rows.add(List.of("Received By", safe(r.getReceivedBy())));
        rows.add(List.of("Status", r.isReversed() ? "REVERSED" : "POSTED"));
        r.getLines().forEach(line -> rows.add(List.of("Line - " + safe(line.getVoteheadName()), CurrencyUtil.formatPlain(line.getAmount()))));
        return rows;
    }

    private void exportReportPack() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export Full Report Pack");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Excel files", "*.xlsx"));
        chooser.setInitialFileName("report-pack.xlsx");
        File file = chooser.showSaveDialog(getScene() != null ? getScene().getWindow() : null);
        if (file == null) {
            return;
        }
        try {
            reportPackExportService.exportFullReportPack(file.toPath(), dailyDate.getValue());
            AlertUtil.info("Export complete", "Report pack exported to:\n" + file.getAbsolutePath());
        } catch (IOException e) {
            AlertUtil.error("Export failed", e.getMessage());
        }
    }

    private void exportLedgerTransactions() {
        File file = chooseSaveFile("Export Ledger Transactions", "ledger-transactions.csv");
        if (file == null) {
            return;
        }
        try {
            List<String> headers = List.of("Date", "Type", "Account", "Votehead Code", "Reference", "Description", "Debit", "Credit", "Student ID", "Receipt ID", "Voucher ID", "Created By");
            List<List<String>> rows = accountingService.transactions().stream().map(t -> List.of(
                    DateUtil.format(t.getDate()),
                    t.getType() != null ? t.getType().name() : "",
                    t.getAccountType() != null ? t.getAccountType().getDisplayName() : "",
                    safe(t.getVoteheadCode()),
                    safe(t.getReference()),
                    safe(t.getDescription()),
                    CurrencyUtil.formatPlain(t.getDebit()),
                    CurrencyUtil.formatPlain(t.getCredit()),
                    safe(t.getStudentId()),
                    safe(t.getReceiptId()),
                    safe(t.getVoucherId()),
                    safe(t.getCreatedBy())
            )).toList();
            exportService.export(file.toPath(), "Ledger Transactions", headers, rows);
            AlertUtil.info("Export complete", "Ledger transactions exported to:\n" + file.getAbsolutePath());
        } catch (IOException e) {
            AlertUtil.error("Export failed", e.getMessage());
        }
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private File choosePdfFile(String title, String initialFileName) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(title);
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF files", "*.pdf"));
        chooser.setInitialFileName(initialFileName);
        return chooser.showSaveDialog(getScene() != null ? getScene().getWindow() : null);
    }

    private File chooseSaveFile(String title, String initialFileName) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(title);
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("CSV files", "*.csv"),
                new FileChooser.ExtensionFilter("Excel files", "*.xlsx")
        );
        chooser.setInitialFileName(initialFileName);
        return chooser.showSaveDialog(getScene() != null ? getScene().getWindow() : null);
    }

    @Override
    public void refresh() {
        balancesTable.getItems().setAll(reportService.feeBalances(termBox.getValue()));
        defaultersTable.getItems().setAll(reportService.defaulters(termBox.getValue(), null));
        dailyTable.getItems().setAll(reportService.dailyCollection(dailyDate.getValue()));
        voteheadTable.getItems().setAll(reportService.voteheadSummaries());
        trialTable.getItems().setAll(reportService.trialBalance());
        reprintTable.setItems(ReceiptStore.getInstance().getReceipts());
        studentBox.setItems(StudentStore.getInstance().getStudents());
    }
}
