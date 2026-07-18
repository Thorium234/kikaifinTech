package com.schaccs;

import com.schaccs.config.AppConfig;
import com.schaccs.repository.AppBootstrap;
import com.schaccs.ui.dashboard.DashboardView;
import com.schaccs.ui.fees.FeeStructureView;
import com.schaccs.ui.layout.MainLayout;
import com.schaccs.ui.layout.Sidebar;
import com.schaccs.ui.audit.AuditLogView;
import com.schaccs.ui.banking.BankReconciliationView;
import com.schaccs.ui.receipts.ReceiptView;
import com.schaccs.ui.reports.ReportsView;
import com.schaccs.ui.settings.SettingsView;
import com.schaccs.ui.students.StudentView;
import com.schaccs.ui.vouchers.VoucherView;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.util.Objects;

public class MainApp extends Application {

    @Override
    public void start(Stage stage) {
        AppBootstrap.initialize();

        MainLayout layout = new MainLayout();
        layout.register(Sidebar.DASHBOARD, Sidebar.DASHBOARD, DashboardView::new);
        layout.register(Sidebar.STUDENTS, Sidebar.STUDENTS, StudentView::new);
        layout.register(Sidebar.FEES, Sidebar.FEES, FeeStructureView::new);
        layout.register(Sidebar.RECEIPTS, Sidebar.RECEIPTS, ReceiptView::new);
        layout.register(Sidebar.VOUCHERS, Sidebar.VOUCHERS, VoucherView::new);
        layout.register(Sidebar.REPORTS, Sidebar.REPORTS, ReportsView::new);
        layout.register(Sidebar.AUDIT_LOG, Sidebar.AUDIT_LOG, AuditLogView::new);
        layout.register(Sidebar.BANK_RECONCILIATION, Sidebar.BANK_RECONCILIATION, BankReconciliationView::new);
        layout.register(Sidebar.SETTINGS, Sidebar.SETTINGS, SettingsView::new);

        layout.show(Sidebar.DASHBOARD);

        Scene scene = new Scene(layout, 1280, 800);
        scene.getStylesheets().add(
                Objects.requireNonNull(getClass().getResource("/styles/app.css")).toExternalForm());

        stage.setTitle("SCHACCS — " + AppConfig.getInstance().getSchoolProfile().getSchoolName());
        stage.setScene(scene);
        stage.setMinWidth(1100);
        stage.setMinHeight(700);
        stage.setOnCloseRequest(e -> AppBootstrap.shutdown());
        stage.show();
    }

    @Override
    public void stop() {
        AppBootstrap.shutdown();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
