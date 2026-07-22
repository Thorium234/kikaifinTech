package com.schaccs.ui.audit;

import com.schaccs.model.audit.AuditLog;
import com.schaccs.service.export.PdfExportService;
import com.schaccs.service.export.SpreadsheetExportService;
import com.schaccs.store.AuditStore;
import com.schaccs.ui.layout.MainLayout;
import com.schaccs.util.AlertUtil;
import com.schaccs.util.DateUtil;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

public class AuditLogView extends VBox implements MainLayout.Refreshable {

    private final TableView<AuditLog> table = new TableView<>();
    private final TextField filterField = new TextField();
    private final AuditStore store = AuditStore.getInstance();
    private final SpreadsheetExportService exportService = new SpreadsheetExportService();
    private final PdfExportService pdfExportService = new PdfExportService();

    public AuditLogView() {
        setSpacing(12);
        setPadding(new Insets(4));

        Label heading = new Label("Audit Log");
        heading.getStyleClass().add("section-title");
        Label sub = new Label("Track all financial actions — creations, modifications, reversals, and deletions.");
        sub.getStyleClass().add("muted");

        TableColumn<AuditLog, String> tsCol = new TableColumn<>("Timestamp");
        tsCol.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getTimestamp() != null
                        ? java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss").format(c.getValue().getTimestamp())
                        : ""));
        TableColumn<AuditLog, String> actionCol = new TableColumn<>("Action");
        actionCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getActionType()));
        TableColumn<AuditLog, String> entityCol = new TableColumn<>("Entity");
        entityCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getEntityType()));
        TableColumn<AuditLog, String> entityIdCol = new TableColumn<>("Entity ID");
        entityIdCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getEntityId()));
        TableColumn<AuditLog, String> byCol = new TableColumn<>("Performed By");
        byCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getPerformedBy()));
        table.getColumns().addAll(tsCol, actionCol, entityCol, entityIdCol, byCol);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        VBox.setVgrow(table, Priority.ALWAYS);

        filterField.setPromptText("Filter by action, entity, or user...");
        filterField.textProperty().addListener((obs, o, n) -> applyFilter());

        Button refresh = new Button("Refresh");
        refresh.getStyleClass().add("secondary-button");
        refresh.setOnAction(e -> refresh());
        Button export = new Button("Export");
        export.getStyleClass().add("secondary-button");
        export.setOnAction(e -> exportAuditLog());
        Button pdf = new Button("PDF");
        pdf.getStyleClass().add("secondary-button");
        pdf.setOnAction(e -> exportAuditLogPdf());
        HBox bar = new HBox(10, filterField, refresh, export, pdf);
        HBox.setHgrow(filterField, Priority.ALWAYS);

        VBox card = new VBox(10, heading, sub, bar, table);
        card.getStyleClass().add("card");
        ScrollPane scroll = new ScrollPane(card);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("inline-scroll-pane");
        VBox.setVgrow(scroll, Priority.ALWAYS);
        getChildren().add(scroll);
        refresh();
    }

    private void applyFilter() {
        String q = filterField.getText().toLowerCase().trim();
        if (q.isEmpty()) {
            table.setItems(store.getEntries());
        } else {
            table.setItems(store.getEntries().filtered(e ->
                    e.getActionType().toLowerCase().contains(q)
                            || e.getEntityType().toLowerCase().contains(q)
                            || (e.getEntityId() != null && e.getEntityId().toLowerCase().contains(q))
                            || (e.getPerformedBy() != null && e.getPerformedBy().toLowerCase().contains(q))));
        }
    }

    @SuppressWarnings("unchecked")
    private void exportAuditLog() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export Audit Log");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("CSV files", "*.csv"),
                new FileChooser.ExtensionFilter("Excel files", "*.xlsx"));
        chooser.setInitialFileName("audit-log.csv");
        File file = chooser.showSaveDialog(getScene() != null ? getScene().getWindow() : null);
        if (file == null) return;
        try {
            List<String> headers = List.of("Timestamp", "Action", "Entity", "Entity ID", "Performed By");
            List<List<String>> rows = table.getItems().stream().map(e -> List.of(
                    e.getTimestamp() != null ? e.getTimestamp().toString() : "",
                    e.getActionType(), e.getEntityType(), e.getEntityId() != null ? e.getEntityId() : "",
                    e.getPerformedBy() != null ? e.getPerformedBy() : "")).toList();
            exportService.export(file.toPath(), "Audit Log", headers, rows);
            AlertUtil.info("Export complete", "Audit log exported to:\n" + file.getAbsolutePath());
        } catch (IOException e) {
            AlertUtil.error("Export failed", e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private void exportAuditLogPdf() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export Audit Log PDF");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF files", "*.pdf"));
        chooser.setInitialFileName("audit-log.pdf");
        File file = chooser.showSaveDialog(getScene() != null ? getScene().getWindow() : null);
        if (file == null) return;
        try {
            List<String> headers = List.of("Timestamp", "Action", "Entity", "Entity ID", "Performed By");
            List<List<String>> rows = table.getItems().stream().map(e -> List.of(
                    e.getTimestamp() != null ? e.getTimestamp().toString() : "",
                    e.getActionType(), e.getEntityType(), e.getEntityId() != null ? e.getEntityId() : "",
                    e.getPerformedBy() != null ? e.getPerformedBy() : "")).toList();
            pdfExportService.exportTable(file.toPath(), "Audit Log", headers, rows);
            AlertUtil.info("Export complete", "PDF exported to:\n" + file.getAbsolutePath());
        } catch (IOException e) {
            AlertUtil.error("Export failed", e.getMessage());
        }
    }

    @Override
    public void refresh() {
        table.setItems(store.getEntries());
        applyFilter();
    }
}
