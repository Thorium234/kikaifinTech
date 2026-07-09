package com.schaccs.ui.reports;

import com.schaccs.model.receipt.Receipt;
import com.schaccs.model.report.CollectionSummary;
import com.schaccs.model.report.TrialBalanceRow;
import com.schaccs.model.report.VoteheadSummary;
import com.schaccs.model.student.Student;
import com.schaccs.model.student.StudentBalance;
import com.schaccs.service.report.ReportService;
import com.schaccs.store.ReceiptStore;
import com.schaccs.store.StudentStore;
import com.schaccs.ui.layout.MainLayout;
import com.schaccs.util.CurrencyUtil;
import com.schaccs.util.DateUtil;
import com.schaccs.util.ReceiptPrinter;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.time.LocalDate;

public class ReportsView extends VBox implements MainLayout.Refreshable {

    private final ReportService reportService = new ReportService();

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
                tab("Trial Balance", buildTrial())
        );
        VBox.setVgrow(tabs, Priority.ALWAYS);

        getChildren().addAll(heading, tabs);
        refresh();
    }

    private Tab tab(String title, VBox content) {
        Tab t = new Tab(title, content);
        t.setClosable(false);
        return t;
    }

    private VBox buildBalances() {
        setupBalanceColumns(balancesTable);
        Button refresh = new Button("Refresh");
        refresh.getStyleClass().add("secondary-button");
        refresh.setOnAction(e -> balancesTable.getItems().setAll(reportService.feeBalances()));
        VBox box = new VBox(8, refresh, balancesTable);
        box.setPadding(new Insets(10));
        VBox.setVgrow(balancesTable, Priority.ALWAYS);
        return box;
    }

    private VBox buildDefaulters() {
        setupBalanceColumns(defaultersTable);
        Button refresh = new Button("Refresh");
        refresh.getStyleClass().add("secondary-button");
        refresh.setOnAction(e -> defaultersTable.getItems().setAll(reportService.defaulters(null)));
        VBox box = new VBox(8, refresh, defaultersTable);
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
        HBox bar = new HBox(10, new Label("Date:"), dailyDate, load);
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
        VBox box = new VBox(8, refresh, voteheadTable);
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
        HBox bar = new HBox(10, new Label("Student:"), studentBox, load);
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
        HBox body = new HBox(12, reprintTable, reprintPreview);
        HBox.setHgrow(reprintTable, Priority.ALWAYS);
        HBox.setHgrow(reprintPreview, Priority.ALWAYS);
        VBox box = new VBox(8, body);
        box.setPadding(new Insets(10));
        VBox.setVgrow(body, Priority.ALWAYS);
        return box;
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
        VBox box = new VBox(8, refresh, trialTable);
        box.setPadding(new Insets(10));
        VBox.setVgrow(trialTable, Priority.ALWAYS);
        return box;
    }

    @Override
    public void refresh() {
        balancesTable.getItems().setAll(reportService.feeBalances());
        defaultersTable.getItems().setAll(reportService.defaulters(null));
        dailyTable.getItems().setAll(reportService.dailyCollection(dailyDate.getValue()));
        voteheadTable.getItems().setAll(reportService.voteheadSummaries());
        trialTable.getItems().setAll(reportService.trialBalance());
        reprintTable.setItems(ReceiptStore.getInstance().getReceipts());
        studentBox.setItems(StudentStore.getInstance().getStudents());
    }
}
