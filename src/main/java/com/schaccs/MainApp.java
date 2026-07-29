package com.schaccs;

import com.schaccs.config.AppConfig;
import com.schaccs.repository.AppBootstrap;
import com.schaccs.update.UpdateScheduler;
import com.schaccs.update.UpdateService;
import com.schaccs.ui.dashboard.DashboardView;
import com.schaccs.ui.dashboard.FeeReminderView;
import com.schaccs.ui.fees.FeeStructureView;
import com.schaccs.ui.layout.MainLayout;
import com.schaccs.ui.layout.Sidebar;
import com.schaccs.ui.layout.TitleBar;
import com.schaccs.ui.audit.AuditLogView;
import com.schaccs.ui.banking.BankReconciliationView;
import com.schaccs.ui.receipts.ReceiptView;
import com.schaccs.ui.reports.ReportsView;
import com.schaccs.ui.school.SchoolCustomView;
import com.schaccs.ui.assets.FixedAssetView;
import com.schaccs.ui.settings.SettingsView;
import com.schaccs.ui.sync.SyncStatusView;
import com.schaccs.ui.students.StudentView;
import com.schaccs.ui.vouchers.VoucherView;
import com.schaccs.ui.payroll.EmployeeView;
import com.schaccs.ui.payroll.PayrollView;
import com.schaccs.ui.procurement.SupplierView;
import com.schaccs.ui.procurement.ProcurementRequestView;
import com.schaccs.ui.procurement.TenderView;
import com.schaccs.ui.procurement.ContractView;
import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

import java.io.InputStream;
import java.util.Objects;
import java.util.Properties;

public class MainApp extends Application {

    private UpdateScheduler updateScheduler;

    @Override
    public void start(Stage stage) {
        Stage splashStage = new Stage();
        splashStage.initStyle(StageStyle.UNDECORATED);
        splashStage.getIcons().add(new Image(Objects.requireNonNull(
                getClass().getResourceAsStream("/icon.png"))));
        Image splashImage = new Image(Objects.requireNonNull(
                getClass().getResourceAsStream("/Splashscreen.png")));
        ImageView splashView = new ImageView(splashImage);
        splashView.setFitWidth(600);
        splashView.setPreserveRatio(true);
        StackPane splashRoot = new StackPane(splashView);
        splashRoot.setStyle("-fx-background-color: #0D1B2A;");
        Scene splashScene = new Scene(splashRoot, 600, 327);
        splashStage.setScene(splashScene);
        splashStage.centerOnScreen();
        splashStage.show();

        AppBootstrap.initialize();

        String appVersion = loadVersion();
        UpdateService updateService = new UpdateService(appVersion);
        updateScheduler = new UpdateScheduler(updateService);
        updateScheduler.start();

        PauseTransition delay = new PauseTransition(Duration.seconds(1.5));
        delay.setOnFinished(e -> {
            splashStage.close();

            stage.initStyle(StageStyle.UNDECORATED);
            stage.getIcons().add(new Image(Objects.requireNonNull(
                    getClass().getResourceAsStream("/icon.png"))));

            TitleBar titleBar = new TitleBar(stage);

            MainLayout layout = new MainLayout();
            layout.register(Sidebar.DASHBOARD, Sidebar.DASHBOARD, DashboardView::new);
            layout.register(Sidebar.STUDENTS, Sidebar.STUDENTS, StudentView::new);
            layout.register(Sidebar.FEES, Sidebar.FEES, FeeStructureView::new);
            layout.register(Sidebar.RECEIPTS, Sidebar.RECEIPTS, ReceiptView::new);
            layout.register(Sidebar.VOUCHERS, Sidebar.VOUCHERS, VoucherView::new);
            layout.register(Sidebar.REPORTS, Sidebar.REPORTS, ReportsView::new);
            layout.register(Sidebar.FEE_REMINDER, Sidebar.FEE_REMINDER, FeeReminderView::new);
            layout.register(Sidebar.AUDIT_LOG, Sidebar.AUDIT_LOG, AuditLogView::new);
            layout.register(Sidebar.BANK_RECONCILIATION, Sidebar.BANK_RECONCILIATION, BankReconciliationView::new);
            layout.register(Sidebar.SYNC, Sidebar.SYNC, SyncStatusView::new);
            layout.register(Sidebar.FIXED_ASSETS, Sidebar.FIXED_ASSETS, FixedAssetView::new);
            layout.register(Sidebar.EMPLOYEES, Sidebar.EMPLOYEES, EmployeeView::new);
            layout.register(Sidebar.PAYROLL, Sidebar.PAYROLL, PayrollView::new);
            layout.register(Sidebar.SCHOOL_CUSTOM, Sidebar.SCHOOL_CUSTOM, SchoolCustomView::new);
            layout.register(Sidebar.PROCUREMENT, Sidebar.PROCUREMENT, ProcurementRequestView::new);
            layout.register(Sidebar.TENDERS, Sidebar.TENDERS, TenderView::new);
            layout.register(Sidebar.SUPPLIERS, Sidebar.SUPPLIERS, SupplierView::new);
            layout.register(Sidebar.CONTRACTS, Sidebar.CONTRACTS, ContractView::new);
            UpdateService finalUs = updateService;
            layout.register(Sidebar.SETTINGS, Sidebar.SETTINGS, () -> new SettingsView(finalUs));

            layout.show(Sidebar.DASHBOARD);

            VBox root = new VBox();
            VBox.setVgrow(layout, Priority.ALWAYS);
            root.getChildren().addAll(titleBar, layout);

            Scene scene = new Scene(root, 1280, 800);
            scene.getStylesheets().add(
                    Objects.requireNonNull(getClass().getResource("/styles/app.css")).toExternalForm());
            titleBar.attachResizeListeners(scene);

            stage.setTitle("ThorCash — " + AppConfig.getInstance().getSchoolProfile().getSchoolName());
            stage.setScene(scene);
            stage.setMinWidth(1100);
            stage.setMinHeight(700);
            stage.setOnCloseRequest(ev -> AppBootstrap.shutdown());
            stage.show();
        });
        delay.play();
    }

    @Override
    public void stop() {
        if (updateScheduler != null) updateScheduler.stop();
        AppBootstrap.shutdown();
    }

    private static String loadVersion() {
        try (InputStream in = MainApp.class.getResourceAsStream("/version.properties")) {
            if (in == null) return "0.0.0";
            Properties props = new Properties();
            props.load(in);
            return props.getProperty("app.version", "0.0.0");
        } catch (Exception e) {
            return "0.0.0";
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
