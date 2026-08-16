package com.schaccs.ui.dashboard;

import com.schaccs.config.AppConfig;
import com.schaccs.config.SchoolProfile;
import com.schaccs.model.student.Student;
import com.schaccs.model.student.StudentBalance;
import com.schaccs.service.Services;
import com.schaccs.service.export.PdfExportService;
import com.schaccs.service.report.ReportService;
import com.schaccs.store.SchoolCustomStore;
import com.schaccs.store.StudentStore;
import com.schaccs.ui.component.SearchBar;
import com.schaccs.ui.layout.MainLayout;
import com.schaccs.util.AlertUtil;
import com.schaccs.util.CurrencyUtil;
import com.schaccs.util.FileDialogMemory;
import com.schaccs.util.FileNamingUtil;
import com.schaccs.util.MailMergeEngine;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
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
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class FeeReminderView extends VBox implements MainLayout.Refreshable {

    private final ReportService reportService = Services.getInstance().report();
    private final PdfExportService pdfExportService = new PdfExportService();
    private final TableView<StudentBalance> reminderTable = new TableView<>();

    private final ComboBox<String> targetFormBox = new ComboBox<>();
    private final ComboBox<String> targetStreamBox = new ComboBox<>();
    private final SearchBar individualSearch = new SearchBar("Search individual by name or adm no...");
    private final ComboBox<String> scopeBox = new ComboBox<>();

    public FeeReminderView() {
        setSpacing(16);
        setPadding(new Insets(8));

        Label heading = new Label("Fee Reminder \u2014 Mail Merge");
        heading.getStyleClass().add("section-title");
        Label subtitle = new Label("Select a target group, then export personalised fee reminder letters as PDF, or copy SMS / Email templates for bulk communication.");
        subtitle.getStyleClass().add("muted");
        subtitle.setWrapText(true);

        setupFilterControls();
        setupReminderTable();

        Button refreshBtn = new Button("Refresh");
        refreshBtn.getStyleClass().add("secondary-button");
        refreshBtn.setOnAction(e -> loadDefaulters());

        Button exportPdf = new Button("Export All Reminders to PDF");
        exportPdf.getStyleClass().add("primary-button");
        exportPdf.setOnAction(e -> exportFeeRemindersPdf());

        Button copySms = new Button("Copy SMS Template");
        copySms.getStyleClass().add("secondary-button");
        copySms.setOnAction(e -> copySmsTemplate());

        Button copyEmail = new Button("Copy Email Template");
        copyEmail.getStyleClass().add("secondary-button");
        copyEmail.setOnAction(e -> copyEmailTemplate());

        FlowPane bar = new FlowPane(10, 10, refreshBtn, exportPdf, copySms, copyEmail);
        bar.setPadding(new Insets(0, 0, 8, 0));

        VBox.setVgrow(reminderTable, Priority.ALWAYS);

        VBox filterCard = new VBox(8,
                new Label("Target Selection:"),
                scopeBox, individualSearch, new HBox(10, targetFormBox, targetStreamBox));
        filterCard.getStyleClass().add("card");
        filterCard.setPadding(new Insets(10));

        getChildren().addAll(heading, subtitle, filterCard, bar, reminderTable);
        loadDefaulters();
    }

    private void setupFilterControls() {
        scopeBox.getItems().addAll("Entire School", "By Form/Grade", "By Stream", "Individual Student");
        scopeBox.setValue("Entire School");
        scopeBox.setOnAction(e -> updateFilterVisibility());

        SchoolCustomStore scs = SchoolCustomStore.getInstance();
        targetFormBox.setPromptText("Select Form/Grade");
        targetFormBox.getItems().clear();
        if (!scs.getFormClasses().isEmpty()) {
            scs.getFormClasses().forEach(fc -> targetFormBox.getItems().add(fc.getName()));
        } else {
            targetFormBox.getItems().addAll("Form 1", "Form 2", "Form 3", "Form 4", "G10", "G11", "G12", "G13");
        }

        targetStreamBox.setPromptText("Select Stream");
        targetStreamBox.getItems().clear();
        if (!scs.getStreams().isEmpty()) {
            scs.getStreams().forEach(s -> targetStreamBox.getItems().add(s.getName()));
        } else {
            targetStreamBox.getItems().addAll("A", "W", "E", "S", "N");
        }

        targetFormBox.setVisible(false);
        targetStreamBox.setVisible(false);
        individualSearch.setVisible(false);

        targetFormBox.setOnAction(e -> loadDefaulters());
        targetStreamBox.setOnAction(e -> loadDefaulters());
        individualSearch.textProperty().addListener((obs, o, n) -> loadDefaulters());
    }

    private void updateFilterVisibility() {
        String scope = scopeBox.getValue();
        boolean byForm = "By Form/Grade".equals(scope);
        boolean byStream = "By Stream".equals(scope);
        boolean individual = "Individual Student".equals(scope);

        targetFormBox.setVisible(byForm || byStream);
        targetStreamBox.setVisible(byStream);
        individualSearch.setVisible(individual);
        loadDefaulters();
    }

    private void setupReminderTable() {
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
    }

    private void loadDefaulters() {
        List<StudentBalance> all = reportService.defaulters(null);
        String scope = scopeBox.getValue();

        if ("Individual Student".equals(scope)) {
            String q = individualSearch.getText();
            if (q != null && !q.isBlank()) {
                String query = q.trim().toLowerCase();
                all = all.stream()
                        .filter(b -> b.getAdmissionNumber().toLowerCase().contains(query)
                                || b.getStudentName().toLowerCase().contains(query))
                        .collect(Collectors.toList());
            }
        } else if ("By Form/Grade".equals(scope)) {
            String form = targetFormBox.getValue();
            if (form != null && !form.isBlank()) {
                all = all.stream()
                        .filter(b -> b.getClassLabel().startsWith(form))
                        .collect(Collectors.toList());
            }
        } else if ("By Stream".equals(scope)) {
            String stream = targetStreamBox.getValue();
            if (stream != null && !stream.isBlank()) {
                all = all.stream()
                        .filter(b -> b.getClassLabel().endsWith(stream))
                        .collect(Collectors.toList());
            }
        }

        reminderTable.getItems().setAll(all);
    }

    private List<StudentBalance> getFilteredDefaulters() {
        return reminderTable.getItems();
    }

    private void exportFeeRemindersPdf() {
        List<StudentBalance> defaulters = getFilteredDefaulters();
        if (defaulters.isEmpty()) {
            AlertUtil.warn("No defaulters", "No defaulters match the current filter selection.");
            return;
        }
        FileChooser chooser = new FileChooser();
        FileDialogMemory.applyTo(chooser);
        chooser.setTitle("Export Fee Reminders PDF");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF files", "*.pdf"));
        chooser.setInitialFileName(FileNamingUtil.suggest("fee-reminders.pdf"));
        File file = chooser.showSaveDialog(getScene() != null ? getScene().getWindow() : null);
        if (file == null) return;
        FileDialogMemory.remember(file);
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
        List<StudentBalance> defaulters = getFilteredDefaulters();
        if (defaulters.isEmpty()) {
            AlertUtil.warn("No defaulters", "No defaulters match the current filter selection.");
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
        List<StudentBalance> defaulters = getFilteredDefaulters();
        if (defaulters.isEmpty()) {
            AlertUtil.warn("No defaulters", "No defaulters match the current filter selection.");
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

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    @Override
    public void refresh() {
        loadDefaulters();
    }
}
