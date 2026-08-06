package com.schaccs.ui.layout;

import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

public class Sidebar extends VBox {

    public static final String DASHBOARD = "Dashboard";
    public static final String STUDENTS = "Students";
    public static final String RECYCLE_BIN = "Recycle Bin";
    public static final String MID_TERM_ENROLLMENTS = "Mid-Term Enrollments";
    public static final String TRANSITIONS = "Student Transitions";
    public static final String CALENDAR = "Calendar";
    public static final String FEES = "Fee Structure";
    public static final String PAY = "Pay";
    public static final String RECEIPTS = "Receipting";
    public static final String VOUCHERS = "Payment Vouchers";
    public static final String REPORTS = "Reports";
    public static final String AUDIT_LOG = "Audit Log";
    public static final String BANK_RECONCILIATION = "Bank Reconciliation";
    public static final String SYNC = "Sync";
    public static final String FEE_REMINDER = "Fee Reminder";
    public static final String FIXED_ASSETS = "Fixed Assets";
    public static final String EMPLOYEES = "Employees";
    public static final String PAYROLL = "Payroll";
    public static final String SCHOOL_CUSTOM = "School Custom";
    public static final String PROCUREMENT = "Procurement";
    public static final String TENDERS = "Tenders";
    public static final String SUPPLIERS = "Suppliers";
    public static final String CONTRACTS = "Contracts";
    public static final String SETTINGS = "Settings";

    private final Map<String, Button> buttons = new LinkedHashMap<>();
    private Consumer<String> onNavigate;
    private String active = DASHBOARD;

    public Sidebar() {
        getStyleClass().add("sidebar");
        setSpacing(0);

        VBox navContent = new VBox();
        navContent.setSpacing(2);

        Label title = new Label("ThorCash");
        title.getStyleClass().add("sidebar-title");
        Label sub = new Label("Friends School Kikai Boys");
        sub.getStyleClass().add("sidebar-sub");

        navContent.getChildren().addAll(title, sub);

        addNav(navContent, DASHBOARD, "Dashboard", FontAwesomeSolid.TACHOMETER_ALT);
        addNav(navContent, STUDENTS, "Students", FontAwesomeSolid.USER_GRADUATE);
        addNav(navContent, RECYCLE_BIN, "Recycle Bin", FontAwesomeSolid.TRASH);
        addNav(navContent, MID_TERM_ENROLLMENTS, "Mid-Term Enrollments",
                FontAwesomeSolid.USER_PLUS);
        addNav(navContent, TRANSITIONS, "Student Transitions", FontAwesomeSolid.EXCHANGE_ALT);
        addNav(navContent, CALENDAR, "Calendar", FontAwesomeSolid.CALENDAR_ALT);
        addNav(navContent, FEES, "Fee Structure", FontAwesomeSolid.MONEY_BILL_ALT);
        addNav(navContent, PAY, "Pay", FontAwesomeSolid.CREDIT_CARD);
        addNav(navContent, RECEIPTS, "Receipting", FontAwesomeSolid.RECEIPT);
        addNav(navContent, VOUCHERS, "Payment Vouchers", FontAwesomeSolid.FILE_INVOICE_DOLLAR);
        addNav(navContent, REPORTS, "Reports", FontAwesomeSolid.CHART_BAR);
        addNav(navContent, FEE_REMINDER, "Fee Reminder", FontAwesomeSolid.BELL);
        addNav(navContent, AUDIT_LOG, "Audit Log", FontAwesomeSolid.CLIPBOARD_LIST);
        addNav(navContent, BANK_RECONCILIATION, "Bank Reconciliation",
                FontAwesomeSolid.LANDMARK);
        addNav(navContent, SYNC, "Sync", FontAwesomeSolid.SYNC_ALT);
        addNav(navContent, FIXED_ASSETS, "Fixed Assets", FontAwesomeSolid.BOX);
        addNav(navContent, EMPLOYEES, "Employees", FontAwesomeSolid.USER_TIE);
        addNav(navContent, PAYROLL, "Payroll", FontAwesomeSolid.WALLET);
        addNav(navContent, PROCUREMENT, "Procurement", FontAwesomeSolid.SHOPPING_CART);
        addNav(navContent, TENDERS, "Tenders", FontAwesomeSolid.GAVEL);
        addNav(navContent, SUPPLIERS, "Suppliers", FontAwesomeSolid.TRUCK);
        addNav(navContent, CONTRACTS, "Contracts", FontAwesomeSolid.FILE_CONTRACT);
        addNav(navContent, SCHOOL_CUSTOM, "School Custom", FontAwesomeSolid.SLIDERS_H);
        addNav(navContent, SETTINGS, "Settings", FontAwesomeSolid.COGS);

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);
        Label version = new Label("Version 1.1.0");
        version.getStyleClass().add("sidebar-sub");
        version.setPadding(new Insets(12, 16, 16, 16));
        navContent.getChildren().addAll(spacer, version);

        ScrollPane scrollPane = new ScrollPane(navContent);
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.setPannable(true);
        scrollPane.getStyleClass().add("sidebar-scroll");
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        getChildren().add(scrollPane);

        setActive(DASHBOARD);
    }

    private void addNav(VBox parent, String key, String label) {
        addNav(parent, key, label, null);
    }

    private void addNav(VBox parent, String key, String label, FontAwesomeSolid code) {
        Button btn = new Button(label);
        if (code != null) {
            btn.setGraphic(new FontIcon(code));
        }
        btn.getStyleClass().add("nav-button");
        btn.setMaxWidth(Double.MAX_VALUE);
        btn.setOnAction(e -> {
            setActive(key);
            if (onNavigate != null) {
                onNavigate.accept(key);
            }
        });
        buttons.put(key, btn);
        parent.getChildren().add(btn);
    }

    public void setOnNavigate(Consumer<String> onNavigate) {
        this.onNavigate = onNavigate;
    }

    public void setActive(String key) {
        active = key;
        buttons.forEach((k, b) -> {
            b.getStyleClass().remove("nav-button-active");
            if (k.equals(key)) {
                b.getStyleClass().add("nav-button-active");
            }
        });
    }

    public String getActive() {
        return active;
    }
}
