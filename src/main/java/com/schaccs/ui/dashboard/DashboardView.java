package com.schaccs.ui.dashboard;

import com.schaccs.config.ThemeConfig;
import com.schaccs.enums.AccountType;
import com.schaccs.model.receipt.Receipt;
import com.schaccs.model.student.StudentBalance;
import com.schaccs.service.Services;
import com.schaccs.service.finance.AccountingService;
import com.schaccs.service.report.ReportService;
import com.schaccs.service.student.StudentService;
import com.schaccs.store.ReceiptStore;
import com.schaccs.ui.component.DashboardCard;
import com.schaccs.ui.layout.MainLayout;
import com.schaccs.util.CurrencyUtil;
import com.schaccs.util.DateUtil;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.util.List;

public class DashboardView extends VBox implements MainLayout.Refreshable {

    private final ReportService reportService = Services.getInstance().report();
    private final StudentService studentService = Services.getInstance().student();
    private final AccountingService accountingService = Services.getInstance().accounting();

    private final DashboardCard studentsCard;
    private final DashboardCard collectionCard;
    private final DashboardCard todayCard;
    private final DashboardCard outstandingCard;
    private final DashboardCard schoolFundCard;
    private final TableView<Receipt> recentReceipts = new TableView<>();
    private final TableView<StudentBalance> topDefaulters = new TableView<>();
    private final Label integrityBanner = new Label();
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

        FlowPane cards = new FlowPane(12, 12);
        cards.getChildren().addAll(studentsCard, collectionCard, todayCard, outstandingCard, schoolFundCard);

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
        defBox.setPrefWidth(420);
        VBox.setVgrow(tables, Priority.ALWAYS);

        getChildren().addAll(heading, integrityBanner, cards, tables);
        refresh();
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

        recentReceipts.getColumns().addAll(num, student, amount, date);
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

        topDefaulters.getColumns().addAll(adm, name, bal);
        topDefaulters.setPrefHeight(260);
        topDefaulters.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
    }

    @Override
    public void refresh() {
        studentsCard.setValue(String.valueOf(studentService.activeCount()));
        collectionCard.setValue(CurrencyUtil.format(reportService.totalCollectionAll()));
        todayCard.setValue(CurrencyUtil.format(reportService.totalCollectionOn(LocalDate.now())));
        outstandingCard.setValue(CurrencyUtil.format(reportService.totalOutstanding()));
        schoolFundCard.setValue(CurrencyUtil.format(accountingService.balance(AccountType.SCHOOL_FUND)));

        if (reportService.isLedgerBalanced()) {
            integrityBanner.setText("");
            integrityBanner.getStyleClass().removeAll("policy-banner", "danger-banner");
        } else {
            integrityBanner.setText("⚠ Ledger is out of balance — debits do not equal credits. "
                    + "Run the Trial Balance report and review recent postings.");
            integrityBanner.getStyleClass().setAll("policy-banner", "danger-banner");
        }
        List<Receipt> receipts = ReceiptStore.getInstance().getReceipts();
        recentReceipts.getItems().setAll(receipts.stream().limit(10).toList());

        List<StudentBalance> def = reportService.defaulters(null).stream().limit(8).toList();
        topDefaulters.getItems().setAll(def);
    }
}
