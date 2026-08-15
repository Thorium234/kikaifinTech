package com.schaccs.ui.students;

import com.schaccs.model.CleanDataEntry;
import com.schaccs.repository.PersistenceService;
import com.schaccs.service.importer.FeesBalanceImportService;
import com.schaccs.service.importer.FeesBalanceRow;
import com.schaccs.service.importer.StudentImportService;
import com.schaccs.store.CleanDataStore;
import com.schaccs.model.student.Student;
import com.schaccs.util.AlertUtil;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Persistent Clean Data: import rows that carried mistakes and were held back
 * instead of being committed. Each row can be opened in the matching review
 * dialog — a row imports automatically the moment its mistakes are cleared and
 * leaves this list. Rows can also be discarded.
 */
public class CleanDataDialog extends Dialog<ButtonType> {

    private static final ButtonType CLOSE_TYPE =
            new ButtonType("Close", ButtonBar.ButtonData.CANCEL_CLOSE);

    private final Runnable onChange;
    private final TableView<CleanDataEntry> table = new TableView<>();
    private final Label summary = new Label();

    public CleanDataDialog(Runnable onChange) {
        this.onChange = onChange;

        setTitle("Clean Data");
        initModality(Modality.APPLICATION_MODAL);
        getDialogPane().getButtonTypes().addAll(CLOSE_TYPE);

        Button fixStudents = new Button("Fix Student Rows");
        fixStudents.getStyleClass().add("primary-button");
        fixStudents.setGraphic(new FontIcon(FontAwesomeSolid.USER));
        fixStudents.setOnAction(e -> fixStudentRows());

        Button fixFees = new Button("Fix Fees Balance Rows");
        fixFees.getStyleClass().add("primary-button");
        fixFees.setGraphic(new FontIcon(FontAwesomeSolid.COINS));
        fixFees.setOnAction(e -> fixFeesBalanceRows());

        Button discardBtn = new Button("Discard Selected");
        discardBtn.getStyleClass().add("danger-button");
        discardBtn.setGraphic(new FontIcon(FontAwesomeSolid.TRASH));
        discardBtn.setOnAction(e -> discardSelected());

        HBox toolbar = new HBox(10, fixStudents, fixFees, discardBtn);
        toolbar.setAlignment(Pos.CENTER_LEFT);

        summary.getStyleClass().add("muted");
        summary.setWrapText(true);
        summary.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(summary, Priority.ALWAYS);

        Label hint = new Label("Clean Data holds rows that failed validation during an import. "
                + "Open the rows you want to fix — a row imports automatically the moment its mistakes are cleared "
                + "and leaves this list. Discard a row to drop it permanently without importing.");
        hint.getStyleClass().add("muted");
        hint.setWrapText(true);

        buildTable();

        VBox content = new VBox(10, toolbar, summary, table, hint);
        content.setPadding(new Insets(8));
        getDialogPane().setContent(content);
        getDialogPane().setPrefSize(980, 520);

        refresh();
    }

    private void buildTable() {
        table.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

        TableColumn<CleanDataEntry, String> type = new TableColumn<>("Type");
        type.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getType() == CleanDataEntry.Type.STUDENT ? "Student" : "Fees Balance"));
        type.setPrefWidth(120);

        TableColumn<CleanDataEntry, String> name = new TableColumn<>("Name");
        name.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getName()));
        name.setPrefWidth(220);

        TableColumn<CleanDataEntry, String> details = new TableColumn<>("Details");
        details.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getDetail()));
        details.setPrefWidth(360);

        TableColumn<CleanDataEntry, String> held = new TableColumn<>("Held Since");
        held.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getCreatedAt().toLocalDate().toString()));
        held.setPrefWidth(110);

        table.getColumns().add(type);
        table.getColumns().add(name);
        table.getColumns().add(details);
        table.getColumns().add(held);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
    }

    private void refresh() {
        table.setItems(CleanDataStore.getInstance().getItems());
        long studentCount = CleanDataStore.getInstance()
                .forType(CleanDataEntry.Type.STUDENT).size();
        long feesCount = CleanDataStore.getInstance()
                .forType(CleanDataEntry.Type.FEES_BALANCE).size();
        summary.setText(table.getItems().isEmpty()
                ? "Nothing to clean — imports that need fixing will land here."
                : studentCount + " student row(s) and " + feesCount + " fees-balance row(s) waiting to be fixed.");
        table.refresh();
        if (onChange != null) {
            onChange.run();
        }
    }

    private void fixStudentRows() {
        CleanDataStore store = CleanDataStore.getInstance();
        List<Map<String, String>> rows = store.rowsFor(CleanDataEntry.Type.STUDENT);
        if (rows.isEmpty()) {
            AlertUtil.warn("Nothing to fix", "There are no student rows in Clean Data.");
            return;
        }
        StudentImportService importService = new StudentImportService();
        List<Student> students = rows.stream()
                .map(importService::toStudent)
                .collect(Collectors.toList());
        StudentImportReviewDialog dialog = new StudentImportReviewDialog(rows, students, importService);
        dialog.showAndWait();
        store.replaceRows(CleanDataEntry.Type.STUDENT, dialog.getHeldRawRows());
        PersistenceService.getInstance().saveAll();
        refresh();
    }

    private void fixFeesBalanceRows() {
        CleanDataStore store = CleanDataStore.getInstance();
        List<Map<String, String>> rows = store.rowsFor(CleanDataEntry.Type.FEES_BALANCE);
        if (rows.isEmpty()) {
            AlertUtil.warn("Nothing to fix", "There are no fees-balance rows in Clean Data.");
            return;
        }
        FeesBalanceImportService importService = new FeesBalanceImportService();
        List<FeesBalanceRow> feesRows = rows.stream()
                .map(FeesBalanceRow::fromFields)
                .collect(Collectors.toList());
        importService.scrutinize(feesRows);
        FeesBalanceImportReviewDialog dialog = new FeesBalanceImportReviewDialog(importService, feesRows);
        dialog.showAndWait();
        List<Map<String, String>> held = dialog.getHeldRows().stream()
                .map(FeesBalanceRow::toFields)
                .collect(Collectors.toList());
        store.replaceRows(CleanDataEntry.Type.FEES_BALANCE, held);
        PersistenceService.getInstance().saveAll();
        refresh();
    }

    private void discardSelected() {
        List<CleanDataEntry> selected = List.copyOf(table.getSelectionModel().getSelectedItems());
        if (selected.isEmpty()) {
            AlertUtil.warn("No selection", "Select one or more rows to discard.");
            return;
        }
        for (CleanDataEntry entry : selected) {
            CleanDataStore.getInstance().remove(entry);
        }
        PersistenceService.getInstance().saveAll();
        refresh();
    }
}
