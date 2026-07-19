package com.schaccs.ui.sync;

import com.schaccs.config.db.DatasourceManager;
import com.schaccs.repository.Database;
import com.schaccs.service.sync.SyncEngine;
import com.schaccs.service.sync.SyncReportService;
import com.schaccs.ui.layout.MainLayout;
import com.schaccs.util.AlertUtil;
import com.schaccs.util.DateUtil;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.concurrent.CompletableFuture;

public class SyncStatusView extends VBox implements MainLayout.Refreshable {

    private final Label statusLabel = new Label("Not connected");
    private final Label lastSyncLabel = new Label("Never");
    private final Label totalRecordsLabel = new Label("0");
    private final Label syncedRecordsLabel = new Label("0");
    private final Label pendingRecordsLabel = new Label("0");
    private final Label failedCountLabel = new Label("0");
    private final Label connectionLabel = new Label("Disconnected");
    private final Label schemaLabel = new Label("Not checked");
    private final ProgressIndicator spinner = new ProgressIndicator();
    private final TextArea errorArea = new TextArea();
    private final TableView<SyncReportService.TableBreakdown> breakdownTable = new TableView<>();
    private final Button syncButton = new Button("Sync Now");
    private final Button validateButton = new Button("Validate Connection");
    private final Button refreshButton = new Button("Refresh Report");
    private final SyncEngine syncEngine = SyncEngine.getInstance();
    private final SyncReportService reportService = SyncReportService.getInstance();

    public SyncStatusView() {
        setSpacing(12);
        setPadding(new Insets(4));

        Label heading = new Label("Synchronization");
        heading.getStyleClass().add("section-title");
        Label sub = new Label("Upload unsynchronised local records to the centralised remote database.");
        sub.getStyleClass().add("muted");

        spinner.setVisible(false);
        spinner.setMaxSize(20, 20);

        VBox statusCard = buildStatusCard();
        VBox breakdownCard = buildBreakdownCard();
        VBox errorsCard = buildErrorsCard();

        VBox allContent = new VBox(14, heading, sub, statusCard, breakdownCard, errorsCard);
        allContent.setPadding(new Insets(0, 0, 60, 0));

        ScrollPane scroll = new ScrollPane(allContent);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("inline-scroll-pane");
        VBox.setVgrow(scroll, Priority.ALWAYS);
        getChildren().add(scroll);

        refresh();
    }

    private VBox buildStatusCard() {
        syncButton.getStyleClass().add("primary-button");
        syncButton.setOnAction(e -> runSync());

        validateButton.getStyleClass().add("secondary-button");
        validateButton.setOnAction(e -> runValidation());

        refreshButton.getStyleClass().add("secondary-button");
        refreshButton.setOnAction(e -> refresh());

        HBox buttons = new HBox(10, syncButton, validateButton, refreshButton, spinner);
        buttons.setPadding(new Insets(8, 0, 0, 0));

        connectionLabel.setStyle("-fx-font-weight: bold;");

        VBox info = new VBox(6,
                new Label("Remote Connection:") {{
                    setStyle("-fx-font-weight: bold; -fx-text-fill: #555;");
                }},
                connectionLabel,
                new Label("Schema Status:") {{
                    setStyle("-fx-font-weight: bold; -fx-text-fill: #555;");
                }},
                schemaLabel,
                new Label("Last Sync:") {{
                    setStyle("-fx-font-weight: bold; -fx-text-fill: #555;");
                }},
                lastSyncLabel,
                new HBox(40,
                        new VBox(2, new Label("Total Records") {{ getStyleClass().add("muted"); }}, totalRecordsLabel),
                        new VBox(2, new Label("Synced") {{ getStyleClass().add("muted"); }}, syncedRecordsLabel),
                        new VBox(2, new Label("Pending") {{ getStyleClass().add("muted"); }}, pendingRecordsLabel),
                        new VBox(2, new Label("Failed") {{ getStyleClass().add("muted"); }}, failedCountLabel)
                ),
                buttons
        );

        VBox card = new VBox(10, info);
        card.getStyleClass().add("card");
        return card;
    }

    private VBox buildBreakdownCard() {
        Label title = new Label("Per-Table Sync Status");
        title.getStyleClass().add("section-title");

        TableColumn<SyncReportService.TableBreakdown, String> tableCol = new TableColumn<>("Table");
        tableCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getTableName()));

        TableColumn<SyncReportService.TableBreakdown, String> totalCol = new TableColumn<>("Total");
        totalCol.setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue().getTotal())));

        TableColumn<SyncReportService.TableBreakdown, String> syncedCol = new TableColumn<>("Synced");
        syncedCol.setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue().getSynced())));

        TableColumn<SyncReportService.TableBreakdown, String> pendingCol = new TableColumn<>("Pending");
        pendingCol.setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue().getPending())));

        TableColumn<SyncReportService.TableBreakdown, String> pctCol = new TableColumn<>("Sync %");
        pctCol.setCellValueFactory(c -> new SimpleStringProperty(
                String.format("%.1f%%", c.getValue().getSyncPercentage())));

        breakdownTable.getColumns().addAll(tableCol, totalCol, syncedCol, pendingCol, pctCol);
        breakdownTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        breakdownTable.setPrefHeight(300);
        VBox.setVgrow(breakdownTable, Priority.ALWAYS);

        VBox card = new VBox(10, title, breakdownTable);
        card.getStyleClass().add("card");
        return card;
    }

    private VBox buildErrorsCard() {
        Label title = new Label("Recent Sync Errors");
        title.getStyleClass().add("section-title");

        errorArea.setEditable(false);
        errorArea.setPrefRowCount(6);
        errorArea.setWrapText(true);
        errorArea.setStyle("-fx-font-family: monospace; -fx-font-size: 11px;");

        VBox card = new VBox(10, title, errorArea);
        card.getStyleClass().add("card");
        return card;
    }

    private void runValidation() {
        validateButton.setDisable(true);
        spinner.setVisible(true);

        CompletableFuture.runAsync(() -> {
            SyncEngine.SyncResult connResult = syncEngine.validateConnectivity();
            SyncEngine.SyncResult schemaResult = syncEngine.validateRemoteSchema();
            SyncEngine.SyncResult versionResult = syncEngine.validateSchemaVersion();

            Platform.runLater(() -> {
                updateConnectionStatus(connResult);
                if (connResult.isSuccess()) {
                    schemaLabel.setText(schemaResult.getMessage());
                    if (schemaResult.isSuccess()) {
                        schemaLabel.setStyle("-fx-text-fill: #1a472a;");
                    } else {
                        schemaLabel.setStyle("-fx-text-fill: #e65100;");
                    }
                }
                if (versionResult != null && !versionResult.isSuccess()) {
                    AlertUtil.warn("Schema Version Mismatch", versionResult.getMessage());
                }
                validateButton.setDisable(false);
                spinner.setVisible(false);
            });
        });
    }

    private void runSync() {
        if (syncEngine.isRunning()) {
            AlertUtil.warn("Sync in progress", "A sync operation is already running.");
            return;
        }
        if (!DatasourceManager.getInstance().isOnline()) {
            AlertUtil.warn("Offline", "Remote database is not connected. Validate connection first.");
            return;
        }

        syncButton.setDisable(true);
        spinner.setVisible(true);
        statusLabel.setText("Syncing...");

        CompletableFuture.runAsync(() -> {
            SyncEngine.SyncSummary summary = syncEngine.syncAll();
            Platform.runLater(() -> {
                if (summary.getError() != null) {
                    AlertUtil.error("Sync Failed", summary.getError());
                } else {
                    AlertUtil.info("Sync Complete", summary.toDisplayString());
                }
                syncButton.setDisable(false);
                spinner.setVisible(false);
                refresh();
            });
        });
    }

    private void updateConnectionStatus(SyncEngine.SyncResult result) {
        if (result.isSuccess()) {
            connectionLabel.setText("Connected");
            connectionLabel.setStyle("-fx-text-fill: #1a472a; -fx-font-weight: bold;");
        } else {
            connectionLabel.setText("Disconnected");
            connectionLabel.setStyle("-fx-text-fill: #b00020; -fx-font-weight: bold;");
        }
    }

    @Override
    public void refresh() {
        CompletableFuture.runAsync(() -> {
            boolean online = DatasourceManager.getInstance().isOnline();
            if (online) {
                DatasourceManager.getInstance().getRemoteConnection();
            }
            SyncReportService.SyncReport report = reportService.generate();

            Platform.runLater(() -> {
                if (online) {
                    connectionLabel.setText("Connected");
                    connectionLabel.setStyle("-fx-text-fill: #1a472a; -fx-font-weight: bold;");
                } else {
                    DatasourceManager.DbConfig cfg = Database.getInstance().loadDbConfig();
                    if (cfg != null) {
                        connectionLabel.setText("Configured (disconnected)");
                        connectionLabel.setStyle("-fx-text-fill: #e65100; -fx-font-weight: bold;");
                    } else {
                        connectionLabel.setText("Not configured");
                        connectionLabel.setStyle("-fx-text-fill: #999; -fx-font-weight: bold;");
                    }
                }

                if (report.hasError()) {
                    schemaLabel.setText("Error: " + report.getError());
                    schemaLabel.setStyle("-fx-text-fill: #b00020;");
                } else {
                    schemaLabel.setText("Local schema OK");
                    schemaLabel.setStyle("-fx-text-fill: #1a472a;");
                }

                lastSyncLabel.setText(report.getLastSyncTime() != null
                        ? report.getLastSyncTime().toString() : "Never");
                totalRecordsLabel.setText(String.valueOf(report.getTotalLocalRecords()));
                syncedRecordsLabel.setText(String.valueOf(report.getTotalSynced()));
                pendingRecordsLabel.setText(String.valueOf(report.getTotalPending()));
                failedCountLabel.setText(String.valueOf(report.getTotalFailed()));

                breakdownTable.getItems().setAll(report.getPerTableBreakdown());

                if (!report.getRecentErrors().isEmpty()) {
                    errorArea.setText(String.join("\n", report.getRecentErrors()));
                } else {
                    errorArea.setText("No recent errors.");
                }
            });
        });
    }
}
