package com.schaccs.ui.dashboard;

import com.schaccs.config.AppConfig;
import com.schaccs.config.SchoolProfile;
import com.schaccs.model.student.Student;
import com.schaccs.model.student.StudentBalance;
import com.schaccs.service.Services;
import com.schaccs.service.export.PdfExportService;
import com.schaccs.service.report.ReportService;
import com.schaccs.store.StudentStore;
import com.schaccs.ui.layout.MainLayout;
import com.schaccs.util.AlertUtil;
import com.schaccs.util.CurrencyUtil;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class FeeReminderView extends VBox implements MainLayout.Refreshable {

    private final ReportService reportService = Services.getInstance().report();
    private final PdfExportService pdfExportService = new PdfExportService();
    private final TableView<StudentBalance> reminderTable = new TableView<>();

    public FeeReminderView() {
        setSpacing(16);
        setPadding(new Insets(8));

        Label heading = new Label("Fee Reminder \u2014 Mail Merge");
        heading.getStyleClass().add("section-title");
        Label subtitle = new Label("View all defaulters, export personalised fee reminder letters as PDF, or copy SMS / Email templates for bulk communication.");
        subtitle.getStyleClass().add("muted");
        subtitle.setWrapText(true);

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

        HBox bar = new HBox(10, refreshBtn, exportPdf, copySms, copyEmail);
        bar.setPadding(new Insets(0, 0, 8, 0));

        VBox.setVgrow(reminderTable, Priority.ALWAYS);
        getChildren().addAll(heading, subtitle, bar, reminderTable);
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
        reminderTable.getColumns().addAll(adm, name, phone, cls, charged, paid, bal);
        reminderTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
    }

    private void loadDefaulters() {
        List<StudentBalance> def = reportService.defaulters(null);
        reminderTable.getItems().setAll(def);
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
            Student s = StudentStore.getInstance().findByAdmissionNumber(b.getAdmissionNumber()).orElse(null);
            String phone = s != null && s.getPhone() != null ? s.getPhone() : "NO-PHONE";
            String parent = s != null && s.getParentName() != null ? s.getParentName() : "Parent/Guardian";
            sb.append("To: ").append(phone).append("\n");
            sb.append("Dear ").append(parent).append(",\n");
            sb.append("This is a reminder that KSh ").append(CurrencyUtil.formatPlain(b.getBalance()))
                    .append(" in school fees for ").append(b.getStudentName())
                    .append(" (").append(b.getAdmissionNumber()).append(" - ").append(b.getClassLabel())
                    .append(") remains unpaid. Kindly clear the balance to avoid disruption. Thank you.");
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
            Student s = StudentStore.getInstance().findByAdmissionNumber(b.getAdmissionNumber()).orElse(null);
            String parent = s != null && s.getParentName() != null ? s.getParentName() : "Parent/Guardian";
            sb.append("<div style='border:1px solid #ccc; padding:12px; margin:12px 0; font-family:Arial,sans-serif;'>");
            sb.append("<p><strong>Dear ").append(parent).append(",</strong></p>");
            sb.append("<p>This is a reminder regarding outstanding school fees for <strong>")
                    .append(b.getStudentName()).append("</strong> (Adm: ").append(b.getAdmissionNumber())
                    .append(", Class: ").append(b.getClassLabel()).append(").</p>");
            sb.append("<table border='1' cellpadding='6' style='border-collapse:collapse;'>");
            sb.append("<tr><td><strong>Description</strong></td><td><strong>Amount (KSh)</strong></td></tr>");
            sb.append("<tr><td>Term Fee Charged</td><td>").append(CurrencyUtil.formatPlain(b.getTotalCharged())).append("</td></tr>");
            sb.append("<tr><td>Amount Paid</td><td>").append(CurrencyUtil.formatPlain(b.getTotalPaid())).append("</td></tr>");
            sb.append("<tr><td>Arrears B/F</td><td>").append(CurrencyUtil.formatPlain(b.getArrears())).append("</td></tr>");
            sb.append("<tr style='background:#ffe0e0;'><td><strong>BALANCE DUE</strong></td><td><strong>")
                    .append(CurrencyUtil.formatPlain(b.getBalance())).append("</strong></td></tr>");
            sb.append("</table>");
            sb.append("<p>Payment can be made via:<br>")
                    .append("Bank: ").append(safe(school.getBankName())).append(" | A/C: ").append(safe(school.getBankAccount())).append("<br>")
                    .append("M-Pesa PayBill: ").append(safe(school.getPayBill())).append(" | Account: ").append(safe(school.getPayBillAccount())).append("</p>");
            sb.append("<p>Thank you for your prompt attention.</p>");
            sb.append("<p>Yours faithfully,<br><strong>").append(safe(school.getPrincipal())).append("</strong><br>Principal</p>");
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
