package com.schaccs.ui.reports;

import com.schaccs.enums.AcademicTerm;
import com.schaccs.model.receipt.Receipt;
import com.schaccs.model.report.AgeingBucket;
import com.schaccs.model.report.BalanceSheetRow;
import com.schaccs.model.report.CashbookRow;
import com.schaccs.model.report.CollectionSummary;
import com.schaccs.model.report.IncomeExpenditureRow;
import com.schaccs.model.report.TrialBalanceRow;
import com.schaccs.model.report.VoteheadSummary;
import com.schaccs.model.school.SchoolFormClass;
import com.schaccs.model.school.SchoolStream;
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
import com.schaccs.store.SchoolCustomStore;
import com.schaccs.store.StudentStore;
import com.schaccs.ui.layout.MainLayout;
import com.schaccs.util.AlertUtil;
import com.schaccs.util.CurrencyUtil;
import com.schaccs.util.DateUtil;
import com.schaccs.util.PrintUtil;
import com.schaccs.util.ReceiptPrinter;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CompletableFuture;

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
    private final TableView<CashbookRow> cashbookTable = new TableView<>();
    private final TableView<IncomeExpenditureRow> ieTable = new TableView<>();
    private final TableView<BalanceSheetRow> balanceSheetTable = new TableView<>();
    private final TableView<ReportService.CashFlowRow> cashFlowTable = new TableView<>();
    private final TableView<Receipt> reprintTable = new TableView<>();
    private final TextArea statementArea = new TextArea();
    private final TextArea reprintPreview = new TextArea();
    private final ComboBox<Student> studentBox = new ComboBox<>();
    private final ComboBox<SchoolFormClass> formBox = new ComboBox<>();
    private final ComboBox<SchoolStream> streamBox = new ComboBox<>();
    private final TextField admField = new TextField();
    private final DatePicker dailyDate = new DatePicker(LocalDate.now());
    private final DatePicker cashbookFrom = new DatePicker(LocalDate.now().withDayOfMonth(1));
    private final DatePicker cashbookTo = new DatePicker(LocalDate.now());
    private final ComboBox<AcademicTerm> termBox = new ComboBox<>();
    private final DatePicker trialFromDate = new DatePicker(LocalDate.now().withDayOfMonth(1));
    private final DatePicker trialToDate = new DatePicker(LocalDate.now());
    private final DatePicker cashFlowFrom = new DatePicker(LocalDate.now().withDayOfMonth(1));
    private final DatePicker cashFlowTo = new DatePicker(LocalDate.now());
    private final Label reportsModeBadge = new Label();

    public ReportsView() {
        setSpacing(12);
        setPadding(new Insets(4));

        Label heading = new Label("Reports");
        heading.getStyleClass().add("section-title");
        Label badge = new Label("Finance Reporting Workspace");
        badge.getStyleClass().add("reports-header-badge");
        Label subtitle = new Label("Analyse balances, collections, receipts, ageing, and trial balance outputs from one reporting hub.");
        subtitle.getStyleClass().addAll("muted", "reports-subtitle");

        javafx.scene.control.TabPane tabs = new javafx.scene.control.TabPane();
        javafx.scene.control.Tab balTab = tab("Fee Balances", buildBalances());
        javafx.scene.control.Tab defTab = tab("Defaulters", buildDefaulters());
        javafx.scene.control.Tab dailyTab2 = tab("Daily Collection", buildDaily());
        javafx.scene.control.Tab vhTab = tab("Votehead Summary", buildVotehead());
        javafx.scene.control.Tab stmtTab = tab("Student Statement", buildStatement());
        javafx.scene.control.Tab repTab = tab("Receipt Reprint", buildReprint());
        javafx.scene.control.Tab ageTab = tab("Ageing", buildAgeing());
        javafx.scene.control.Tab tbTab = tab("Trial Balance", buildTrial());
        javafx.scene.control.Tab cbTab = tab("Cashbook", buildCashbook());
        javafx.scene.control.Tab ieTab = tab("Income & Expenditure", buildIncomeExpenditure());
        javafx.scene.control.Tab bsTab = tab("Balance Sheet", buildBalanceSheet());
        javafx.scene.control.Tab cfTab = tab("Cash Flow", buildCashFlow());
        tabs.getTabs().addAll(balTab, defTab, dailyTab2, vhTab, stmtTab, repTab, ageTab, tbTab, cbTab, ieTab, bsTab, cfTab);
        tabs.getSelectionModel().selectedItemProperty().addListener((obs, o, t) -> {
            if (t == balTab) refreshBalances();
            else if (t == defTab) refreshDefaulters();
            else if (t == dailyTab2) refreshDaily();
            else if (t == vhTab) refreshVotehead();
            else if (t == repTab) reprintTable.setItems(ReceiptStore.getInstance().getReceipts());
            else if (t == tbTab) refreshTrial();
            else if (t == ieTab) refreshIncomeExpenditure();
            else if (t == bsTab) refreshBalanceSheet();
            else if (t == cfTab) refreshCashFlow();
        });
        VBox.setVgrow(tabs, Priority.ALWAYS);

        Button reportPackBtn = new Button("Export Full Report Pack");
        reportPackBtn.getStyleClass().add("secondary-button");
        reportPackBtn.setOnAction(e -> exportReportPack());
        HBox headerActions = new HBox(10, reportPackBtn);
        headerActions.getStyleClass().add("reports-toolbar");
        reportsModeBadge.getStyleClass().addAll("reports-mode-badge", "reports-mode-ready");
        reportsModeBadge.setText("Ready to Generate and Export Reports");
        VBox headerCard = new VBox(8, badge, heading, subtitle, headerActions);
        headerCard.getStyleClass().addAll("card", "reports-header-card");
        getChildren().addAll(headerCard, reportsModeBadge, tabs);
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
        VBox box = new VBox(10, reportSectionTitle("Fee Balances", "Review student balances by term and export as spreadsheet or PDF."), bar, balancesTable);
        box.getStyleClass().add("reports-section-card");
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
        VBox box = new VBox(10, reportSectionTitle("Defaulters", "Track unpaid balances and optionally roll arrears forward."), bar, defaultersTable);
        box.getStyleClass().add("reports-section-card");
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
        @SuppressWarnings("unchecked")
        var columns1 = new TableColumn[]{adm, name, cls, charged, paid, arrears, bal};
        table.getColumns().addAll(columns1);
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
        @SuppressWarnings("unchecked")
        var columns2 = new TableColumn[]{date, mode, count, total};
        dailyTable.getColumns().addAll(columns2);
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
        VBox box = new VBox(10, reportSectionTitle("Daily Collection", "Summarise daily fee collections by payment mode."), bar, dailyTable);
        box.getStyleClass().add("reports-section-card");
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
        @SuppressWarnings("unchecked")
        var columns3 = new TableColumn[]{code, name, charged, coll, out};
        voteheadTable.getColumns().addAll(columns3);
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
        VBox box = new VBox(10, reportSectionTitle("Votehead Summary", "Review revenue and outstanding balances by votehead."), new HBox(10, refresh, export, pdf), voteheadTable);
        box.getStyleClass().add("reports-section-card");
        box.setPadding(new Insets(10));
        VBox.setVgrow(voteheadTable, Priority.ALWAYS);
        return box;
    }

    private VBox buildStatement() {
        formBox.setItems(SchoolCustomStore.getInstance().getFormClasses());
        formBox.setPromptText("All Forms");
        formBox.setPrefWidth(130);
        streamBox.setItems(SchoolCustomStore.getInstance().getStreams());
        streamBox.setPromptText("All Streams");
        streamBox.setPrefWidth(130);
        admField.setPromptText("Admission No");
        admField.setPrefWidth(150);
        studentBox.setPrefWidth(320);
        studentBox.setPromptText("Select student...");
        Button searchBtn = new Button("Search");
        searchBtn.getStyleClass().add("primary-button");
        searchBtn.setOnAction(e -> {
            SchoolFormClass fc = formBox.getValue();
            SchoolStream st = streamBox.getValue();
            String adm = admField.getText().trim();
            List<Student> all = StudentStore.getInstance().getStudents();
            List<Student> filtered = all.stream().filter(s -> {
                if (fc != null && !fc.getName().equalsIgnoreCase(s.getFormClass())) return false;
                if (st != null && !st.getName().equalsIgnoreCase(s.getStream())) return false;
                if (!adm.isEmpty() && !s.getAdmissionNumber().toLowerCase().contains(adm.toLowerCase())) return false;
                return true;
            }).toList();
            studentBox.getItems().setAll(filtered);
            if (filtered.size() == 1) {
                studentBox.setValue(filtered.get(0));
            } else if (filtered.isEmpty()) {
                AlertUtil.info("No results", "No students match the selected filters.");
            }
        });

        Button load = new Button("Generate Statement");
        load.getStyleClass().add("primary-button");
        load.setOnAction(e -> {
            Student s = studentBox.getValue();
            if (s == null) {
                AlertUtil.warn("Select student", "Search and select a student first.");
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
                        r.getPaymentMode() != null ? r.getPaymentMode().getDisplayName() : ""));
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
        HBox filterBar = new HBox(8, new Label("Form:"), formBox, new Label("Stream:"), streamBox, new Label("Adm:"), admField, searchBtn);
        HBox actionBar = new HBox(10, new Label("Student:"), studentBox, load, print, export, pdf);
        VBox box = new VBox(10, reportSectionTitle("Student Statement", "Filter by Form, Stream, and Admission Number, then search. Select a student and generate a statement."), filterBar, actionBar, statementArea);
        box.getStyleClass().add("reports-section-card");
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
        @SuppressWarnings("unchecked")
        var columns4 = new TableColumn[]{num, student, amount, date};
        reprintTable.getColumns().addAll(columns4);
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
        Button verifyBtn = new Button("Verify Selected");
        verifyBtn.getStyleClass().add("secondary-button");
        verifyBtn.setOnAction(e -> verifyReceipt());
        VBox previewActions = new VBox(8, exportBtn, pdfBtn, printBtn, verifyBtn, reverseBtn, reprintPreview);
        SplitPane body = new SplitPane(reprintTable, previewActions);
        body.setDividerPositions(0.58);
        VBox.setVgrow(reprintPreview, Priority.ALWAYS);
        VBox box = new VBox(10, reportSectionTitle("Receipt Reprint", "Re-open posted receipts for export, printing, or reversal."), body);
        box.getStyleClass().add("reports-section-card");
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

    private void verifyReceipt() {
        Receipt r = reprintTable.getSelectionModel().getSelectedItem();
        if (r == null) {
            AlertUtil.warn("Select receipt", "Select a receipt to verify.");
            return;
        }
        if (Services.getInstance().receipt().verifyReceipt(r)) {
            AlertUtil.info("Verified", "Receipt " + r.getReceiptNumberDisplay()
                    + " integrity hash matches its recorded fields.");
        } else {
            AlertUtil.warn("Not verified",
                    "Receipt " + r.getReceiptNumberDisplay()
                            + " has no integrity hash or its recorded fields have changed since posting.");
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
        @SuppressWarnings("unchecked")
        var columns5 = new TableColumn[]{acct, debit, credit};
        trialTable.getColumns().addAll(columns5);
        trialTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        Button refresh = new Button("Refresh");
        refresh.getStyleClass().add("secondary-button");
        refresh.setOnAction(e -> refreshTrial());
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
        HBox dateBar = new HBox(6, new Label("From:"), trialFromDate, new Label("To:"), trialToDate);
        dateBar.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        VBox box = new VBox(10, reportSectionTitle("Trial Balance", "Validate ledger equality and export trial balance or ledger transaction data."), dateBar, new HBox(10, refresh, export, pdf, exportLedger, pdfLedger), trialTable);
        box.getStyleClass().add("reports-section-card");
        box.setPadding(new Insets(10));
        VBox.setVgrow(trialTable, Priority.ALWAYS);
        return box;
    }

    private VBox buildCashbook() {
        TableColumn<CashbookRow, String> dateCol = new TableColumn<>("Date");
        dateCol.setCellValueFactory(c -> new SimpleStringProperty(DateUtil.format(c.getValue().getDate())));
        TableColumn<CashbookRow, String> refCol = new TableColumn<>("Reference");
        refCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getReference()));
        TableColumn<CashbookRow, String> descCol = new TableColumn<>("Description");
        descCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getDescription()));
        TableColumn<CashbookRow, String> recCol = new TableColumn<>("Receipts");
        recCol.setCellValueFactory(c -> new SimpleStringProperty(CurrencyUtil.format(c.getValue().getReceipts())));
        TableColumn<CashbookRow, String> payCol = new TableColumn<>("Payments");
        payCol.setCellValueFactory(c -> new SimpleStringProperty(CurrencyUtil.format(c.getValue().getPayments())));
        TableColumn<CashbookRow, String> balCol = new TableColumn<>("Balance");
        balCol.setCellValueFactory(c -> new SimpleStringProperty(CurrencyUtil.format(c.getValue().getBalance())));
        @SuppressWarnings("unchecked")
        var columns6 = new TableColumn[]{dateCol, refCol, descCol, recCol, payCol, balCol};
        cashbookTable.getColumns().addAll(columns6);
        cashbookTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        Button load = new Button("Load");
        load.getStyleClass().add("primary-button");
        load.setOnAction(e -> cashbookTable.getItems().setAll(
                reportService.cashbook(cashbookFrom.getValue(), cashbookTo.getValue())));
        Button export = new Button("Export");
        export.getStyleClass().add("secondary-button");
        export.setOnAction(e -> exportCashbook());
        Button pdf = new Button("PDF");
        pdf.getStyleClass().add("secondary-button");
        pdf.setOnAction(e -> exportCashbookPdf());
        HBox bar = new HBox(10, new Label("From:"), cashbookFrom, new Label("To:"), cashbookTo, load, export, pdf);
        VBox box = new VBox(10, reportSectionTitle("Cashbook", "View all receipts and payments with running balance for a date range."), bar, cashbookTable);
        box.getStyleClass().add("reports-section-card");
        box.setPadding(new Insets(10));
        VBox.setVgrow(cashbookTable, Priority.ALWAYS);
        return box;
    }

    private VBox buildIncomeExpenditure() {
        TableColumn<IncomeExpenditureRow, String> catCol = new TableColumn<>("Category");
        catCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getCategory()));
        TableColumn<IncomeExpenditureRow, String> itemCol = new TableColumn<>("Item");
        itemCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getItem()));
        TableColumn<IncomeExpenditureRow, String> amtCol = new TableColumn<>("Amount");
        amtCol.setCellValueFactory(c -> new SimpleStringProperty(CurrencyUtil.format(c.getValue().getAmount())));
        @SuppressWarnings("unchecked")
        var columns7 = new TableColumn[]{catCol, itemCol, amtCol};
        ieTable.getColumns().addAll(columns7);
        ieTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        Button refresh = new Button("Refresh");
        refresh.getStyleClass().add("secondary-button");
        refresh.setOnAction(e -> ieTable.getItems().setAll(reportService.incomeExpenditure()));
        Button export = new Button("Export");
        export.getStyleClass().add("secondary-button");
        export.setOnAction(e -> exportIncomeExpenditure());
        Button pdf = new Button("PDF");
        pdf.getStyleClass().add("secondary-button");
        pdf.setOnAction(e -> exportIncomeExpenditurePdf());
        VBox box = new VBox(10, reportSectionTitle("Income & Expenditure", "Summarise income collected and expenditure by category."), new HBox(10, refresh, export, pdf), ieTable);
        box.getStyleClass().add("reports-section-card");
        box.setPadding(new Insets(10));
        VBox.setVgrow(ieTable, Priority.ALWAYS);
        return box;
    }

    private VBox buildBalanceSheet() {
        TableColumn<BalanceSheetRow, String> secCol = new TableColumn<>("Section");
        secCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getSection()));
        TableColumn<BalanceSheetRow, String> itemCol = new TableColumn<>("Item");
        itemCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getItem()));
        TableColumn<BalanceSheetRow, String> amtCol = new TableColumn<>("Amount");
        amtCol.setCellValueFactory(c -> new SimpleStringProperty(CurrencyUtil.format(c.getValue().getAmount())));
        @SuppressWarnings("unchecked")
        var columns8 = new TableColumn[]{secCol, itemCol, amtCol};
        balanceSheetTable.getColumns().addAll(columns8);
        balanceSheetTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        Button refresh = new Button("Refresh");
        refresh.getStyleClass().add("secondary-button");
        refresh.setOnAction(e -> balanceSheetTable.getItems().setAll(reportService.balanceSheet()));
        Button export = new Button("Export");
        export.getStyleClass().add("secondary-button");
        export.setOnAction(e -> exportBalanceSheet());
        Button pdf = new Button("PDF");
        pdf.getStyleClass().add("secondary-button");
        pdf.setOnAction(e -> exportBalanceSheetPdf());
        VBox box = new VBox(10, reportSectionTitle("Balance Sheet", "View assets, fund balances, and accumulated surplus/deficit."), new HBox(10, refresh, export, pdf), balanceSheetTable);
        box.getStyleClass().add("reports-section-card");
        box.setPadding(new Insets(10));
        VBox.setVgrow(balanceSheetTable, Priority.ALWAYS);
        return box;
    }

    private VBox buildCashFlow() {
        TableColumn<ReportService.CashFlowRow, String> catCol = new TableColumn<>("Category");
        catCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getCategory()));
        TableColumn<ReportService.CashFlowRow, String> itemCol = new TableColumn<>("Item");
        itemCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getItem()));
        TableColumn<ReportService.CashFlowRow, String> amtCol = new TableColumn<>("Amount");
        amtCol.setCellValueFactory(c -> new SimpleStringProperty(CurrencyUtil.format(c.getValue().getAmount())));
        @SuppressWarnings("unchecked")
        var columns9 = new TableColumn[]{catCol, itemCol, amtCol};
        cashFlowTable.getColumns().addAll(columns9);
        cashFlowTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        Button load = new Button("Load");
        load.getStyleClass().add("primary-button");
        load.setOnAction(e -> cashFlowTable.getItems().setAll(
                reportService.cashFlowStatement(cashFlowFrom.getValue(), cashFlowTo.getValue())));
        Button export = new Button("Export");
        export.getStyleClass().add("secondary-button");
        export.setOnAction(e -> exportCashFlow());
        Button pdf = new Button("PDF");
        pdf.getStyleClass().add("secondary-button");
        pdf.setOnAction(e -> exportCashFlowPdf());
        HBox bar = new HBox(10, new Label("From:"), cashFlowFrom, new Label("To:"), cashFlowTo, load, export, pdf);
        VBox box = new VBox(10, reportSectionTitle("Cash Flow Statement", "Analyse cash inflows and outflows across operating, investing, and financing activities."), bar, cashFlowTable);
        box.getStyleClass().add("reports-section-card");
        box.setPadding(new Insets(10));
        VBox.setVgrow(cashFlowTable, Priority.ALWAYS);
        return box;
    }

    private void exportCashbook() {
        File file = chooseSaveFile("Export Cashbook", "cashbook.csv");
        if (file == null) return;
        try {
            List<String> headers = List.of("Date", "Reference", "Description", "Receipts", "Payments", "Balance");
            List<List<String>> rows = cashbookTable.getItems().stream().map(r -> List.of(
                    DateUtil.format(r.getDate()), r.getReference(), r.getDescription(),
                    CurrencyUtil.formatPlain(r.getReceipts()), CurrencyUtil.formatPlain(r.getPayments()),
                    CurrencyUtil.formatPlain(r.getBalance()))).toList();
            exportService.export(file.toPath(), "Cashbook", headers, rows);
            AlertUtil.info("Export complete", "Cashbook exported to:\n" + file.getAbsolutePath());
        } catch (IOException e) {
            AlertUtil.error("Export failed", e.getMessage());
        }
    }

    private void exportIncomeExpenditure() {
        File file = chooseSaveFile("Export Income & Expenditure", "income-expenditure.csv");
        if (file == null) return;
        try {
            List<String> headers = List.of("Category", "Item", "Amount");
            List<List<String>> rows = ieTable.getItems().stream().map(r -> List.of(
                    r.getCategory(), r.getItem(), CurrencyUtil.formatPlain(r.getAmount()))).toList();
            exportService.export(file.toPath(), "Income & Expenditure", headers, rows);
            AlertUtil.info("Export complete", "Income & Expenditure exported to:\n" + file.getAbsolutePath());
        } catch (IOException e) {
            AlertUtil.error("Export failed", e.getMessage());
        }
    }

    private void exportBalanceSheet() {
        File file = chooseSaveFile("Export Balance Sheet", "balance-sheet.csv");
        if (file == null) return;
        try {
            List<String> headers = List.of("Section", "Item", "Amount");
            List<List<String>> rows = balanceSheetTable.getItems().stream().map(r -> List.of(
                    r.getSection(), r.getItem(), CurrencyUtil.formatPlain(r.getAmount()))).toList();
            exportService.export(file.toPath(), "Balance Sheet", headers, rows);
            AlertUtil.info("Export complete", "Balance sheet exported to:\n" + file.getAbsolutePath());
        } catch (IOException e) {
            AlertUtil.error("Export failed", e.getMessage());
        }
    }

    private void exportCashbookPdf() {
        File file = choosePdfFile("Export Cashbook PDF", "cashbook.pdf");
        if (file == null) return;
        File finalFile = file;
        List<String> headers = List.of("Date", "Reference", "Description", "Receipts", "Payments", "Balance");
        List<List<String>> rows = cashbookTable.getItems().stream().map(r -> List.of(
                DateUtil.format(r.getDate()), r.getReference(), r.getDescription(),
                CurrencyUtil.formatPlain(r.getReceipts()), CurrencyUtil.formatPlain(r.getPayments()),
                CurrencyUtil.formatPlain(r.getBalance()))).toList();
        CompletableFuture.runAsync(() -> {
            try {
                pdfExportService.exportTable(finalFile.toPath(), "Cashbook", headers, rows);
                Platform.runLater(() -> AlertUtil.info("Export complete", "PDF exported to:\n" + finalFile.getAbsolutePath()));
            } catch (IOException e) {
                Platform.runLater(() -> AlertUtil.error("Export failed", e.getMessage()));
            }
        });
    }

    private void exportIncomeExpenditurePdf() {
        File file = choosePdfFile("Export Income & Expenditure PDF", "income-expenditure.pdf");
        if (file == null) return;
        File finalFile = file;
        List<String> headers = List.of("Category", "Item", "Amount");
        List<List<String>> rows = ieTable.getItems().stream().map(r -> List.of(
                r.getCategory(), r.getItem(), CurrencyUtil.formatPlain(r.getAmount()))).toList();
        CompletableFuture.runAsync(() -> {
            try {
                pdfExportService.exportTable(finalFile.toPath(), "Income & Expenditure", headers, rows);
                Platform.runLater(() -> AlertUtil.info("Export complete", "PDF exported to:\n" + finalFile.getAbsolutePath()));
            } catch (IOException e) {
                Platform.runLater(() -> AlertUtil.error("Export failed", e.getMessage()));
            }
        });
    }

    private void exportBalanceSheetPdf() {
        File file = choosePdfFile("Export Balance Sheet PDF", "balance-sheet.pdf");
        if (file == null) return;
        File finalFile = file;
        List<String> headers = List.of("Section", "Item", "Amount");
        List<List<String>> rows = balanceSheetTable.getItems().stream().map(r -> List.of(
                r.getSection(), r.getItem(), CurrencyUtil.formatPlain(r.getAmount()))).toList();
        CompletableFuture.runAsync(() -> {
            try {
                pdfExportService.exportTable(finalFile.toPath(), "Balance Sheet", headers, rows);
                Platform.runLater(() -> AlertUtil.info("Export complete", "PDF exported to:\n" + finalFile.getAbsolutePath()));
            } catch (IOException e) {
                Platform.runLater(() -> AlertUtil.error("Export failed", e.getMessage()));
            }
        });
    }

    private void exportCashFlow() {
        File file = chooseSaveFile("Export Cash Flow Statement", "cash-flow.csv");
        if (file == null) return;
        try {
            List<String> headers = List.of("Category", "Item", "Amount");
            List<List<String>> rows = cashFlowTable.getItems().stream().map(r -> List.of(
                    r.getCategory(), r.getItem(), CurrencyUtil.formatPlain(r.getAmount()))).toList();
            exportService.export(file.toPath(), "Cash Flow Statement", headers, rows);
            AlertUtil.info("Export complete", "Cash flow statement exported to:\n" + file.getAbsolutePath());
        } catch (IOException e) {
            AlertUtil.error("Export failed", e.getMessage());
        }
    }

    private void exportCashFlowPdf() {
        File file = choosePdfFile("Export Cash Flow Statement PDF", "cash-flow.pdf");
        if (file == null) return;
        File finalFile = file;
        List<String> headers = List.of("Category", "Item", "Amount");
        List<List<String>> rows = cashFlowTable.getItems().stream().map(r -> List.of(
                r.getCategory(), r.getItem(), CurrencyUtil.formatPlain(r.getAmount()))).toList();
        CompletableFuture.runAsync(() -> {
            try {
                pdfExportService.exportTable(finalFile.toPath(), "Cash Flow Statement", headers, rows);
                Platform.runLater(() -> AlertUtil.info("Export complete", "PDF exported to:\n" + finalFile.getAbsolutePath()));
            } catch (IOException e) {
                Platform.runLater(() -> AlertUtil.error("Export failed", e.getMessage()));
            }
        });
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
        @SuppressWarnings("unchecked")
        var columns9 = new TableColumn[]{bucket, amt, cnt};
        table.getColumns().addAll(columns9);
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
        VBox box = new VBox(10, reportSectionTitle("Ageing Analysis", "Break down unpaid balances by ageing buckets for follow-up."), new HBox(10, refresh, export, pdf), table);
        box.getStyleClass().add("reports-section-card");
        box.setPadding(new Insets(10));
        VBox.setVgrow(table, Priority.ALWAYS);
        return box;
    }

    private VBox reportSectionTitle(String title, String hint) {
        Label heading = new Label(title);
        heading.getStyleClass().add("section-title");
        Label sub = new Label(hint);
        sub.getStyleClass().add("muted");
        sub.setWrapText(true);
        return new VBox(4, heading, sub);
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
        File finalFile = file;
        Receipt receipt = r;
        CompletableFuture.runAsync(() -> {
            try {
                pdfExportService.exportReceipt(finalFile.toPath(), receipt);
                Platform.runLater(() -> AlertUtil.info("Export complete", "Receipt PDF exported to:\n" + finalFile.getAbsolutePath()));
            } catch (IOException e) {
                Platform.runLater(() -> AlertUtil.error("Export failed", e.getMessage()));
            }
        });
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
        File finalFile = file;
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
        CompletableFuture.runAsync(() -> {
            try {
                pdfExportService.exportTable(finalFile.toPath(), title, headers, rows);
                Platform.runLater(() -> AlertUtil.info("Export complete", "PDF exported to:\n" + finalFile.getAbsolutePath()));
            } catch (IOException e) {
                Platform.runLater(() -> AlertUtil.error("Export failed", e.getMessage()));
            }
        });
    }

    private void exportDailyCollectionPdf() {
        File file = choosePdfFile("Export Daily Collection PDF", "daily-collection.pdf");
        if (file == null) return;
        File finalFile = file;
        List<String> headers = List.of("Date", "Payment Mode", "Receipts", "Total Amount");
        List<List<String>> rows = dailyTable.getItems().stream().map(d -> List.of(
                DateUtil.format(d.getDate()),
                d.getPaymentMode().getDisplayName(),
                String.valueOf(d.getReceiptCount()),
                CurrencyUtil.formatPlain(d.getTotalAmount())
        )).toList();
        CompletableFuture.runAsync(() -> {
            try {
                pdfExportService.exportTable(finalFile.toPath(), "Daily Collection", headers, rows);
                Platform.runLater(() -> AlertUtil.info("Export complete", "PDF exported to:\n" + finalFile.getAbsolutePath()));
            } catch (IOException e) {
                Platform.runLater(() -> AlertUtil.error("Export failed", e.getMessage()));
            }
        });
    }

    private void exportVoteheadSummaryPdf() {
        File file = choosePdfFile("Export Votehead Summary PDF", "votehead-summary.pdf");
        if (file == null) return;
        File finalFile = file;
        List<String> headers = List.of("Code", "Vote Head", "Charged", "Collected", "Outstanding");
        List<List<String>> rows = voteheadTable.getItems().stream().map(v -> List.of(
                v.getVoteheadCode(),
                v.getVoteheadName(),
                CurrencyUtil.formatPlain(v.getCharged()),
                CurrencyUtil.formatPlain(v.getCollected()),
                CurrencyUtil.formatPlain(v.getOutstanding())
        )).toList();
        CompletableFuture.runAsync(() -> {
            try {
                pdfExportService.exportTable(finalFile.toPath(), "Votehead Summary", headers, rows);
                Platform.runLater(() -> AlertUtil.info("Export complete", "PDF exported to:\n" + finalFile.getAbsolutePath()));
            } catch (IOException e) {
                Platform.runLater(() -> AlertUtil.error("Export failed", e.getMessage()));
            }
        });
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
        File finalFile = file;
        Student student = s;
        CompletableFuture.runAsync(() -> {
            try {
                StudentBalance bal = reportService.studentStatement(student);
                List<List<String>> rows = new java.util.ArrayList<>();
                rows.add(List.of("Student", student.getName()));
                rows.add(List.of("Admission Number", student.getAdmissionNumber()));
                rows.add(List.of("Class", student.getClassLabel()));
                rows.add(List.of("Charged", CurrencyUtil.formatPlain(bal.getTotalCharged())));
                rows.add(List.of("Paid", CurrencyUtil.formatPlain(bal.getTotalPaid())));
                rows.add(List.of("Arrears", CurrencyUtil.formatPlain(bal.getArrears())));
                rows.add(List.of("Balance", CurrencyUtil.formatPlain(bal.getBalance())));
                for (Receipt r : reportService.studentReceipts(student)) {
                    rows.add(List.of("Receipt #" + r.getReceiptNumberDisplay(), DateUtil.format(r.getDate()) + " | "
                            + CurrencyUtil.formatPlain(r.getAmount()) + " | " + (r.getPaymentMode() != null ? r.getPaymentMode().getDisplayName() : "")));
                }
                pdfExportService.exportTable(finalFile.toPath(), "Student Statement", List.of("Field", "Value"), rows);
                Platform.runLater(() -> AlertUtil.info("Export complete", "PDF exported to:\n" + finalFile.getAbsolutePath()));
            } catch (IOException e) {
                Platform.runLater(() -> AlertUtil.error("Export failed", e.getMessage()));
            }
        });
    }

    private void exportTrialBalancePdf() {
        File file = choosePdfFile("Export Trial Balance PDF", "trial-balance.pdf");
        if (file == null) return;
        File finalFile = file;
        List<String> headers = List.of("Account", "Debit", "Credit");
        List<List<String>> rows = trialTable.getItems().stream().map(t -> List.of(
                t.getAccountName(),
                CurrencyUtil.formatPlain(t.getDebit()),
                CurrencyUtil.formatPlain(t.getCredit())
        )).toList();
        CompletableFuture.runAsync(() -> {
            try {
                pdfExportService.exportTable(finalFile.toPath(), "Trial Balance", headers, rows);
                Platform.runLater(() -> AlertUtil.info("Export complete", "PDF exported to:\n" + finalFile.getAbsolutePath()));
            } catch (IOException e) {
                Platform.runLater(() -> AlertUtil.error("Export failed", e.getMessage()));
            }
        });
    }

    private void exportAgeingPdf(TableView<AgeingBucket> table) {
        File file = choosePdfFile("Export Ageing PDF", "ageing.pdf");
        if (file == null) return;
        File finalFile = file;
        List<String> headers = List.of("Ageing Bucket", "Outstanding", "Students");
        List<List<String>> rows = table.getItems().stream().map(a -> List.of(
                a.getLabel(),
                CurrencyUtil.formatPlain(a.getAmount()),
                String.valueOf(a.getStudents())
        )).toList();
        CompletableFuture.runAsync(() -> {
            try {
                pdfExportService.exportTable(finalFile.toPath(), "Ageing", headers, rows);
                Platform.runLater(() -> AlertUtil.info("Export complete", "PDF exported to:\n" + finalFile.getAbsolutePath()));
            } catch (IOException e) {
                Platform.runLater(() -> AlertUtil.error("Export failed", e.getMessage()));
            }
        });
    }

    private void exportLedgerTransactionsPdf() {
        File file = choosePdfFile("Export Ledger Transactions PDF", "ledger-transactions.pdf");
        if (file == null) return;
        File finalFile = file;
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
        CompletableFuture.runAsync(() -> {
            try {
                pdfExportService.exportTable(finalFile.toPath(), "Ledger Transactions", headers, rows);
                Platform.runLater(() -> AlertUtil.info("Export complete", "PDF exported to:\n" + finalFile.getAbsolutePath()));
            } catch (IOException e) {
                Platform.runLater(() -> AlertUtil.error("Export failed", e.getMessage()));
            }
        });
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
        File finalFile = file;
        CompletableFuture.runAsync(() -> {
            try {
                reportPackExportService.exportFullReportPack(finalFile.toPath(), dailyDate.getValue());
                Platform.runLater(() -> AlertUtil.info("Export complete", "Report pack exported to:\n" + finalFile.getAbsolutePath()));
            } catch (IOException e) {
                Platform.runLater(() -> AlertUtil.error("Export failed", e.getMessage()));
            }
        });
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
        refreshBalances();
        refreshDefaulters();
        refreshDaily();
        refreshVotehead();
        refreshTrial();
        // Ageing, Cashbook, I&E, Balance Sheet, Statement, Reprint load on tab selection
    }

    private void refreshBalances() {
        balancesTable.getItems().setAll(reportService.feeBalances(termBox.getValue()));
    }

    private void refreshDefaulters() {
        defaultersTable.getItems().setAll(reportService.defaulters(termBox.getValue(), null));
    }

    private void refreshDaily() {
        dailyTable.getItems().setAll(reportService.dailyCollection(dailyDate.getValue()));
    }

    private void refreshVotehead() {
        voteheadTable.getItems().setAll(reportService.voteheadSummaries());
    }

    private void refreshTrial() {
        LocalDate f = trialFromDate.getValue();
        LocalDate t = trialToDate.getValue();
        trialTable.getItems().setAll(reportService.trialBalance(f, t));
    }

    private void refreshIncomeExpenditure() {
        ieTable.getItems().setAll(reportService.incomeExpenditure());
    }

    private void refreshBalanceSheet() {
        balanceSheetTable.getItems().setAll(reportService.balanceSheet());
    }

    private void refreshCashFlow() {
        cashFlowTable.getItems().setAll(
                reportService.cashFlowStatement(cashFlowFrom.getValue(), cashFlowTo.getValue()));
    }

}
