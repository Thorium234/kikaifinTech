package com.schaccs.ui.dashboard;

import com.schaccs.config.AppConfig;
import com.schaccs.config.SchoolProfile;
import com.schaccs.config.ThemeConfig;
import com.schaccs.enums.AccountType;
import com.schaccs.enums.PaymentMode;
import com.schaccs.model.receipt.Receipt;
import com.schaccs.model.student.Student;
import com.schaccs.model.student.StudentBalance;
import com.schaccs.service.Services;
import com.schaccs.service.export.PdfExportService;
import com.schaccs.service.finance.AccountingService;
import com.schaccs.service.report.ReportService;
import com.schaccs.service.student.StudentService;
import com.schaccs.store.AccountStore;
import com.schaccs.store.ReceiptStore;
import com.schaccs.store.StudentStore;
import com.schaccs.ui.component.DashboardCard;
import com.schaccs.util.AlertUtil;
import com.schaccs.ui.layout.MainLayout;
import com.schaccs.ui.layout.Sidebar;
import com.schaccs.util.CurrencyUtil;
import com.schaccs.util.DateUtil;
import com.schaccs.util.MailMergeEngine;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class DashboardView extends VBox implements MainLayout.Refreshable {

    private final ReportService reportService = Services.getInstance().report();
    private final StudentService studentService = Services.getInstance().student();
    private final AccountingService accountingService = Services.getInstance().accounting();
    private final PdfExportService pdfExportService = new PdfExportService();

    private final DashboardCard studentsCard;
    private final DashboardCard collectionCard;
    private final DashboardCard todayCard;
    private final DashboardCard outstandingCard;
    private final DashboardCard schoolFundCard;
    private final LineChart<String, Number> trendChart = createTrendChart();
    private final TableView<Receipt> recentReceipts = new TableView<>();
    private final TableView<StudentBalance> topDefaulters = new TableView<>();
    private final TableView<StudentBalance> reminderTable = new TableView<>();
    private final Label integrityBanner = new Label();
    private final PieChart paymentModeChart = new PieChart();
    private final ProgressBar budgetProgressBar = new ProgressBar(0);
    private final Label budgetPctLabel = new Label("0.0%");

    public DashboardView() {
        setSpacing(16);
        setPadding(new Insets(4));

        Label heading = new Label("Overview Dashboard");
        heading.getStyleClass().add("section-title");

        studentsCard = new DashboardCard("Active Students", "0", ThemeConfig.PRIMARY);
        collectionCard = new DashboardCard("Total Collection", "KSh 0", ThemeConfig.SUCCESS);
        todayCard = new DashboardCard("Today's Collection", "KSh 0", ThemeConfig.ACCENT);
        outstandingCard = new DashboardCard("Outstanding Fees", "KSh 0", ThemeConfig.DANGER);
        schoolFundCard = new DashboardCard("School Fund Balance", "KSh 0", ThemeConfig.PRIMARY_DARK);

        configureNavigation();

        FlowPane cards = new FlowPane(12, 12);
        cards.getChildren().addAll(studentsCard, collectionCard, todayCard, outstandingCard, schoolFundCard);

        trendChart.setPrefHeight(240);
        trendChart.setMaxWidth(800);
        VBox chartBox = new VBox(4, new Label("Daily Collection (Last 30 Days)"), trendChart);
        chartBox.getStyleClass().add("card");
        chartBox.setPadding(new Insets(12));

        paymentModeChart.setTitle("Payment Mode (This Month)");
        paymentModeChart.setAnimated(false);
        paymentModeChart.setLabelsVisible(true);
        paymentModeChart.setPrefHeight(180);

        VBox pieBox = new VBox(4, new Label("Payment Mode Breakdown"), paymentModeChart);
        pieBox.getStyleClass().add("card");
        pieBox.setPadding(new Insets(12));
        pieBox.setPrefWidth(400);

        budgetProgressBar.setPrefWidth(Double.MAX_VALUE);
        budgetProgressBar.setPrefHeight(22);

        Label budgetTitle = new Label("Budget Utilization");
        budgetTitle.getStyleClass().add("section-title");

        VBox budgetBox = new VBox(8, budgetTitle, budgetProgressBar, budgetPctLabel);
        budgetBox.getStyleClass().add("card");
        budgetBox.setPadding(new Insets(12));
        budgetBox.setPrefWidth(300);

        FlowPane chartsGaugeBox = new FlowPane(16, 16);
        chartsGaugeBox.getChildren().addAll(pieBox, budgetBox);

        Label recentTitle = new Label("Recent Receipts");
        recentTitle.getStyleClass().add("section-title");
        setupRecentReceipts();

        Label defTitle = new Label("Top Defaulters");
        defTitle.getStyleClass().add("section-title");
        setupDefaulters();

        VBox recentBox = new VBox(8, recentTitle, recentReceipts);
        recentBox.getStyleClass().add("card");
        VBox.setVgrow(recentReceipts, Priority.ALWAYS);

        VBox defBox = new VBox(8, defTitle, topDefaulters);
        defBox.getStyleClass().add("card");
        VBox.setVgrow(topDefaulters, Priority.ALWAYS);

        FlowPane tables = new FlowPane(16, 16);
        tables.getChildren().addAll(recentBox, defBox);
        recentBox.setPrefWidth(520);
        recentBox.setMinWidth(360);
        defBox.setPrefWidth(420);
        defBox.setMinWidth(320);

        VBox.setVgrow(tables, Priority.ALWAYS);
        ScrollPane tableScroll = new ScrollPane(tables);
        tableScroll.setFitToWidth(true);
        tableScroll.setFitToHeight(true);
        tableScroll.setPannable(true);
        tableScroll.getStyleClass().add("inline-scroll-pane");
        VBox.setVgrow(tableScroll, Priority.ALWAYS);

        VBox feeReminderSection = buildFeeReminderSection();

        VBox allContent = new VBox(16, heading, integrityBanner, cards, chartBox, chartsGaugeBox,
                tableScroll, feeReminderSection);
        allContent.setPadding(new Insets(4));

        ScrollPane mainScroll = new ScrollPane(allContent);
        mainScroll.setFitToWidth(true);
        mainScroll.setFitToHeight(true);
        mainScroll.setPannable(true);
        mainScroll.getStyleClass().add("content-scroll");
        VBox.setVgrow(mainScroll, Priority.ALWAYS);

        getChildren().add(mainScroll);
        refresh();
    }

    private LineChart<String, Number> createTrendChart() {
        CategoryAxis xAxis = new CategoryAxis();
        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("Amount (KSh)");
        LineChart<String, Number> chart = new LineChart<>(xAxis, yAxis);
        chart.setAnimated(false);
        chart.setLegendVisible(false);
        chart.setCreateSymbols(true);
        return chart;
    }

    private void refreshTrendChart() {
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        var trend = reportService.dailyCollectionTrend(30);
        for (var entry : trend) {
            series.getData().add(new XYChart.Data<>(
                    entry.getKey().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM")),
                    entry.getValue()));
        }
        trendChart.getData().clear();
        trendChart.getData().add(series);
    }

    private void refreshPieChart() {
        List<Receipt> receipts = ReceiptStore.getInstance().getReceipts();
        YearMonth currentMonth = YearMonth.now();
        Map<PaymentMode, BigDecimal> modeTotals = new HashMap<>();
        for (Receipt r : receipts) {
            if (r.getDate() == null) continue;
            if (!YearMonth.from(r.getDate()).equals(currentMonth)) continue;
            if (r.isReversed()) continue;
            modeTotals.merge(r.getPaymentMode(), r.getAmount(), BigDecimal::add);
        }
        paymentModeChart.getData().clear();
        for (Map.Entry<PaymentMode, BigDecimal> e : modeTotals.entrySet()) {
            paymentModeChart.getData().add(new PieChart.Data(
                    e.getKey().getDisplayName(),
                    e.getValue().doubleValue()
            ));
        }
    }

    private void refreshBudgetGauge() {
        AccountStore.getInstance().findOpenFiscalYear().ifPresent(fy -> {
            double pct = Services.getInstance().budget().getOverallUtilization(fy.getId());
            budgetProgressBar.setProgress(pct / 100.0);
            budgetPctLabel.setText(String.format("%.1f%% utilized", pct));
        });
    }

    private VBox buildFeeReminderSection() {
        Label reminderTitle = new Label("Fee Reminder \u2014 Mail Merge");
        reminderTitle.getStyleClass().add("section-title");
        Label reminderSub = new Label("Review defaulters and export personalised fee reminder letters or copy message templates.");
        reminderSub.getStyleClass().add("muted");
        reminderSub.setWrapText(true);

        TableColumn<StudentBalance, String> adm = new TableColumn<>("Adm No");
        adm.setCellValueFactory(c -> c.getValue().admissionNumberProperty());
        TableColumn<StudentBalance, String> name = new TableColumn<>("Name");
        name.setCellValueFactory(c -> c.getValue().studentNameProperty());
        TableColumn<StudentBalance, String> phone = new TableColumn<>("Phone");
        phone.setCellValueFactory(c -> {
            String admNo = c.getValue().getAdmissionNumber();
            Student s = StudentStore.getInstance().findByAdmissionNumber(admNo).orElse(null);
            return new SimpleStringProperty(s != null && s.getPhone() != null ? s.getPhone() : "");
        });
        TableColumn<StudentBalance, String> cls = new TableColumn<>("Class");
        cls.setCellValueFactory(c -> c.getValue().classLabelProperty());
        TableColumn<StudentBalance, String> charged = new TableColumn<>("Term Fee");
        charged.setCellValueFactory(c -> new SimpleStringProperty(CurrencyUtil.format(c.getValue().getTotalCharged())));
        TableColumn<StudentBalance, String> paid = new TableColumn<>("Paid");
        paid.setCellValueFactory(c -> new SimpleStringProperty(CurrencyUtil.format(c.getValue().getTotalPaid())));
        TableColumn<StudentBalance, String> bal = new TableColumn<>("Balance");
        bal.setCellValueFactory(c -> new SimpleStringProperty(CurrencyUtil.format(c.getValue().getBalance())));
        @SuppressWarnings("unchecked")
        TableColumn<StudentBalance, String>[] columns1 = new TableColumn[]{adm, name, phone, cls, charged, paid, bal};
        reminderTable.getColumns().addAll(columns1);
        reminderTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        reminderTable.setPrefHeight(280);

        Button refreshBtn = new Button("Refresh");
        refreshBtn.getStyleClass().add("secondary-button");
        refreshBtn.setOnAction(e -> {
            List<StudentBalance> def = reportService.defaulters(null);
            reminderTable.getItems().setAll(def);
        });

        Button exportPdf = new Button("Export All Reminders to PDF");
        exportPdf.getStyleClass().add("primary-button");
        exportPdf.setOnAction(e -> exportFeeRemindersPdf());

        Button copySms = new Button("Copy SMS Template");
        copySms.getStyleClass().add("secondary-button");
        copySms.setOnAction(e -> copySmsTemplate());

        Button copyEmail = new Button("Copy Email Template");
        copyEmail.getStyleClass().add("secondary-button");
        copyEmail.setOnAction(e -> copyEmailTemplate());

        HBox bar = new HBox(10, refreshBtn, exportPdf, copySms, copyEmail);

        VBox section = new VBox(10, reminderTitle, reminderSub, bar, reminderTable);
        section.getStyleClass().add("card");
        section.setPadding(new Insets(12));
        return section;
    }

    private void exportFeeRemindersPdf() {
        List<StudentBalance> defaulters = reminderTable.getItems();
        if (defaulters.isEmpty()) {
            AlertUtil.warn("No defaulters", "Refresh the defaulter list first.");
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export Fee Reminders PDF");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF files", "*.pdf"));
        chooser.setInitialFileName("fee-reminders.pdf");
        File file = chooser.showSaveDialog(getScene() != null ? getScene().getWindow() : null);
        if (file == null) return;
        File finalFile = file;
        List<StudentBalance> defs = List.copyOf(defaulters);
        CompletableFuture.runAsync(() -> {
            try {
                pdfExportService.exportFeeRemindersPdf(finalFile.toPath(), defs, StudentStore.getInstance(), reportService);
                Platform.runLater(() -> AlertUtil.info("Export complete", "Fee reminders PDF exported to:\n" + finalFile.getAbsolutePath()));
            } catch (IOException e) {
                Platform.runLater(() -> AlertUtil.error("Export failed", e.getMessage()));
            }
        });
    }

    private void copySmsTemplate() {
        List<StudentBalance> defaulters = reminderTable.getItems();
        if (defaulters.isEmpty()) {
            AlertUtil.warn("No defaulters", "Refresh the defaulter list first.");
            return;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("FEE REMINDER BULK SMS\n");
        sb.append("=====================\n\n");
        for (StudentBalance b : defaulters) {
            var fields = MailMergeEngine.resolveFields(b);
            String phone = fields.getOrDefault("Student_Phone", "NO-PHONE");
            sb.append("To: ").append(phone).append("\n");
            String sms = MailMergeEngine.merge(
                    "Dear {Guardian_Name}, this is a reminder that KSh {Total_Due} in school fees for {Student_Name} ({Adm_No} - {Class}) remains unpaid. Kindly clear the balance to avoid disruption. Thank you.",
                    fields);
            sb.append(sms);
            sb.append("\n\n");
        }
        ClipboardContent content = new ClipboardContent();
        content.putString(sb.toString());
        Clipboard.getSystemClipboard().setContent(content);
        AlertUtil.info("Copied", "SMS template copied to clipboard (" + defaulters.size() + " messages).");
    }

    private void copyEmailTemplate() {
        List<StudentBalance> defaulters = reminderTable.getItems();
        if (defaulters.isEmpty()) {
            AlertUtil.warn("No defaulters", "Refresh the defaulter list first.");
            return;
        }
        SchoolProfile school = AppConfig.getInstance().getSchoolProfile();
        StringBuilder sb = new StringBuilder();
        sb.append("<html><body>");
        sb.append("<h2>Fee Reminder - Bulk Email Template</h2>");
        sb.append("<hr>");
        for (StudentBalance b : defaulters) {
            var fields = MailMergeEngine.resolveFields(b);
            sb.append("<div style='border:1px solid #ccc; padding:12px; margin:12px 0; font-family:Arial,sans-serif;'>");
            String emailBody = MailMergeEngine.merge(
                    "<p><strong>Dear {Guardian_Name},</strong></p>"
                    + "<p>This is a reminder regarding outstanding school fees for <strong>{Student_Name}</strong> (Adm: {Adm_No}, Class: {Class}).</p>"
                    + "<table border='1' cellpadding='6' style='border-collapse:collapse;'>"
                    + "<tr><td><strong>Description</strong></td><td><strong>Amount (KSh)</strong></td></tr>"
                    + "<tr><td>Term Fee Charged</td><td>{Billed_Fee}</td></tr>"
                    + "<tr><td>Amount Paid</td><td>{Paid_Amount}</td></tr>"
                    + "<tr><td>Arrears B/F</td><td>{Arrears}</td></tr>"
                    + "<tr style='background:#ffe0e0;'><td><strong>BALANCE DUE</strong></td><td><strong>{Total_Due}</strong></td></tr>"
                    + "</table>"
                    + "<p>Payment can be made via:<br>"
                    + "Bank: " + safe(school.getBankName()) + " | A/C: " + safe(school.getBankAccount()) + "<br>"
                    + "M-Pesa PayBill: " + safe(school.getPayBill()) + " | Account: " + safe(school.getPayBillAccount()) + "</p>"
                    + "<p>Thank you for your prompt attention.</p>"
                    + "<p>Yours faithfully,<br><strong>" + safe(school.getPrincipal()) + "</strong><br>Principal, " + safe(school.getSchoolName()) + "</p>",
                    fields);
            sb.append(emailBody);
            sb.append("</div>");
            sb.append("<hr>");
        }
        sb.append("</body></html>");
        ClipboardContent content = new ClipboardContent();
        content.putString(sb.toString());
        Clipboard.getSystemClipboard().setContent(content);
        AlertUtil.info("Copied", "Email HTML template copied to clipboard (" + defaulters.size() + " messages).");
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private void configureNavigation() {
        studentsCard.setHint("Click to open Students");
        studentsCard.setOnNavigate(() -> navigateTo(Sidebar.STUDENTS));
        collectionCard.setHint("Click to open Reports");
        collectionCard.setOnNavigate(() -> navigateTo(Sidebar.REPORTS));
        todayCard.setHint("Click to open Receipting");
        todayCard.setOnNavigate(() -> navigateTo(Sidebar.RECEIPTS));
        outstandingCard.setHint("Click to open Reports");
        outstandingCard.setOnNavigate(() -> navigateTo(Sidebar.REPORTS));
        schoolFundCard.setHint("Click to open Payment Vouchers");
        schoolFundCard.setOnNavigate(() -> navigateTo(Sidebar.VOUCHERS));

        recentReceipts.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2 && recentReceipts.getSelectionModel().getSelectedItem() != null) {
                navigateTo(Sidebar.RECEIPTS);
            }
        });
        topDefaulters.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2 && topDefaulters.getSelectionModel().getSelectedItem() != null) {
                navigateTo(Sidebar.STUDENTS);
            }
        });
    }

    private void navigateTo(String key) {
        if (getScene() == null || getScene().getRoot() == null) {
            return;
        }
        if (getScene().getRoot() instanceof MainLayout layout) {
            layout.show(key);
        }
    }

    private void setupRecentReceipts() {
        TableColumn<Receipt, String> num = new TableColumn<>("Receipt #");
        num.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getReceiptNumberDisplay()));
        num.setPrefWidth(90);

        TableColumn<Receipt, String> student = new TableColumn<>("Student");
        student.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getStudentName()));
        student.setPrefWidth(160);

        TableColumn<Receipt, String> amount = new TableColumn<>("Amount");
        amount.setCellValueFactory(c -> new SimpleStringProperty(CurrencyUtil.format(c.getValue().getAmount())));
        amount.setPrefWidth(110);

        TableColumn<Receipt, String> date = new TableColumn<>("Date");
        date.setCellValueFactory(c -> new SimpleStringProperty(DateUtil.format(c.getValue().getDate())));
        date.setPrefWidth(100);

        @SuppressWarnings("unchecked")
        TableColumn<Receipt, String>[] columns2 = new TableColumn[]{num, student, amount, date};
        recentReceipts.getColumns().addAll(columns2);
        recentReceipts.setPrefHeight(260);
        recentReceipts.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
    }

    private void setupDefaulters() {
        TableColumn<StudentBalance, String> adm = new TableColumn<>("Adm No");
        adm.setCellValueFactory(c -> c.getValue().admissionNumberProperty());
        adm.setPrefWidth(90);

        TableColumn<StudentBalance, String> name = new TableColumn<>("Name");
        name.setCellValueFactory(c -> c.getValue().studentNameProperty());
        name.setPrefWidth(150);

        TableColumn<StudentBalance, String> bal = new TableColumn<>("Balance");
        bal.setCellValueFactory(c -> new SimpleStringProperty(CurrencyUtil.format(c.getValue().getBalance())));
        bal.setPrefWidth(120);

        @SuppressWarnings("unchecked")
        TableColumn<StudentBalance, String>[] columns3 = new TableColumn[]{adm, name, bal};
        topDefaulters.getColumns().addAll(columns3);
        topDefaulters.setPrefHeight(260);
        topDefaulters.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
    }

    @Override
    public void refresh() {
        studentsCard.setValue(String.valueOf(studentService.activeCount()));
        collectionCard.setValue(CurrencyUtil.format(reportService.totalCollectionAll()));
        todayCard.setValue(CurrencyUtil.format(reportService.totalCollectionOn(LocalDate.now())));
        outstandingCard.setValue(CurrencyUtil.format(reportService.totalOutstanding()));
        schoolFundCard.setValue(CurrencyUtil.format(accountingService.balance(AccountType.CASH_AT_BANK)));

        if (reportService.isLedgerBalanced()) {
            integrityBanner.setText("");
            integrityBanner.getStyleClass().removeAll("policy-banner", "danger-banner");
        } else {
            integrityBanner.setText("\u26A0 Ledger is out of balance \u2014 debits do not equal credits. "
                    + "Run the Trial Balance report and review recent postings.");
            integrityBanner.getStyleClass().setAll("policy-banner", "danger-banner");
        }
        List<Receipt> receipts = ReceiptStore.getInstance().getReceipts();
        recentReceipts.getItems().setAll(receipts.stream().limit(10).toList());

        List<StudentBalance> def = reportService.defaulters(null).stream().limit(8).toList();
        topDefaulters.getItems().setAll(def);

        reminderTable.getItems().setAll(reportService.defaulters(null));
        refreshTrendChart();
        refreshPieChart();
        refreshBudgetGauge();
    }
}
