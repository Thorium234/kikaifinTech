package com.schaccs.ui.layout;

import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
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
    public static final String SETTINGS = "Settings";

    private final Map<String, Button> buttons = new LinkedHashMap<>();
    private Consumer<String> onNavigate;
    private String active = DASHBOARD;

    public Sidebar() {
        getStyleClass().add("sidebar");
        setSpacing(2);

        Label title = new Label("SCHACCS");
        title.getStyleClass().add("sidebar-title");
        Label sub = new Label("Friends School Kikai Boys");
        sub.getStyleClass().add("sidebar-sub");

        getChildren().addAll(title, sub);

        addNav(DASHBOARD, "Dashboard");
        addNav(STUDENTS, "Students");
        addNav(FEES, "Fee Structure");
        addNav(RECEIPTS, "Receipting");
        addNav(VOUCHERS, "Payment Vouchers");
        addNav(REPORTS, "Reports");
        addNav(SETTINGS, "Settings");

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);
        Label version = new Label("Version 1.1.0");
        version.getStyleClass().add("sidebar-sub");
        version.setPadding(new Insets(12, 16, 16, 16));
        getChildren().addAll(spacer, version);

        setActive(DASHBOARD);
    }

    private void addNav(String key, String label) {
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
        getChildren().add(btn);
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
