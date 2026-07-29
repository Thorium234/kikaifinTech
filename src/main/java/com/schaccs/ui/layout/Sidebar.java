package com.schaccs.ui.layout;

import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

public class Sidebar extends VBox {

    public static final String DASHBOARD = "Dashboard";
    public static final String STUDENTS = "Students";
    public static final String FEES = "Fee Structure";
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

        addNav(navContent, DASHBOARD, "Dashboard");
        addNav(navContent, STUDENTS, "Students");
        addNav(navContent, FEES, "Fee Structure");
        addNav(navContent, RECEIPTS, "Receipting");
        addNav(navContent, VOUCHERS, "Payment Vouchers");
        addNav(navContent, REPORTS, "Reports");
        addNav(navContent, FEE_REMINDER, "Fee Reminder");
        addNav(navContent, AUDIT_LOG, "Audit Log");
        addNav(navContent, BANK_RECONCILIATION, "Bank Reconciliation");
        addNav(navContent, SYNC, "Sync");
        addNav(navContent, FIXED_ASSETS, "Fixed Assets");
        addNav(navContent, EMPLOYEES, "Employees");
        addNav(navContent, PAYROLL, "Payroll");
        addNav(navContent, PROCUREMENT, "Procurement");
        addNav(navContent, TENDERS, "Tenders");
        addNav(navContent, SUPPLIERS, "Suppliers");
        addNav(navContent, CONTRACTS, "Contracts");
        addNav(navContent, SCHOOL_CUSTOM, "School Custom");
        addNav(navContent, SETTINGS, "Settings");

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
        Button btn = new Button(label);
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
