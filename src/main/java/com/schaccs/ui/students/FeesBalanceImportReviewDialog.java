package com.schaccs.ui.students;

import com.schaccs.config.CurrencyConfig;
import com.schaccs.enums.BoardingStatus;
import com.schaccs.service.importer.FeesBalanceImportService;
import com.schaccs.service.importer.FeesBalanceRow;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.control.cell.ComboBoxTableCell;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Fees-balance import with the Clean Data split: every row without a blocking
 * mistake is committed immediately when the dialog opens; rows that carry a
 * mistake (missing name or admission number, class not inferred, duplicate
 * admission number in file) are held in the Clean Data table. A held row is
 * committed automatically the moment its mistakes are cleared — either by
 * editing the details inline, or by unchecking it to skip it.
 */
public class FeesBalanceImportReviewDialog extends Dialog<ButtonType> {

    private static final String ALREADY_IMPORTED =
            "Admission number was already imported in this batch - change it to a unique number or uncheck to skip";

    private static final ButtonType CLOSE_TYPE =
            new ButtonType("Close", ButtonBar.ButtonData.CANCEL_CLOSE);

    private final FeesBalanceImportService importService;
    private final ObservableList<FeesBalanceRow> cleanData = FXCollections.observableArrayList();
    private final TableView<FeesBalanceRow> table = new TableView<>();
    private final Label summaryLabel = new Label();
    private final Set<String> committedAdmissionNumbers = new HashSet<>();

    private int imported;

    public FeesBalanceImportReviewDialog(FeesBalanceImportService importService, List<FeesBalanceRow> rows) {
        this.importService = importService;

        setTitle("Import Fees Balance - Clean Data");
        initModality(Modality.APPLICATION_MODAL);
        getDialogPane().getButtonTypes().addAll(CLOSE_TYPE);

        List<FeesBalanceRow> held = new ArrayList<>();
        List<FeesBalanceRow> autoRows = new ArrayList<>();
        for (FeesBalanceRow row : rows) {
            if (isAutoImportable(row)) {
                autoRows.add(row);
            } else {
                held.add(row);
            }
        }
        importNow(autoRows, held);
        cleanData.setAll(held);
        for (FeesBalanceRow row : cleanData) {
            row.includeProperty().addListener((obs, oldValue, newValue) -> importCleanedRows());
        }

        buildTable();
        updateHeader();
        updateSummary();

        summaryLabel.getStyleClass().add("muted");
        summaryLabel.setWrapText(true);
        summaryLabel.setMaxWidth(Double.MAX_VALUE);
        Label hint = new Label("Clean Data: the rows below carry mistakes and were held back. "
                + "Fix the details inline (or uncheck a row to skip it) - a row imports automatically the moment its "
                + "mistakes are cleared. Informational notes (credit balance, penalty, totals mismatch) do not block import.");
        hint.getStyleClass().add("muted");
        hint.setWrapText(true);

        HBox.setHgrow(summaryLabel, Priority.ALWAYS);
        HBox toolbar = new HBox(10, summaryLabel);
        toolbar.setAlignment(Pos.CENTER_LEFT);

        VBox content = new VBox(10, toolbar, table, hint);
        content.setPadding(new Insets(8));
        getDialogPane().setContent(content);
        getDialogPane().setPrefSize(1560, 640);
    }

    private void buildTable() {
        table.setEditable(true);

        TableColumn<FeesBalanceRow, Boolean> include = new TableColumn<>("Import");
        include.setCellValueFactory(c -> c.getValue().includeProperty());
        include.setCellFactory(CheckBoxTableCell.forTableColumn(include));
        include.setEditable(true);
        include.setPrefWidth(60);

        TableColumn<FeesBalanceRow, String> sheet = new TableColumn<>("Sheet");
        sheet.setCellValueFactory(c -> c.getValue().sheetNameProperty());
        sheet.setPrefWidth(90);

        TableColumn<FeesBalanceRow, String> rowNo = new TableColumn<>("Row");
        rowNo.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getRowNumber() == 0 ? "" : String.valueOf(c.getValue().getRowNumber())));
        rowNo.setPrefWidth(45);

        TableColumn<FeesBalanceRow, String> adm = new TableColumn<>("Adm No");
        adm.setCellValueFactory(c -> c.getValue().admissionNumberProperty());
        adm.setCellFactory(c -> new TextFieldTableCell<>());
        adm.setOnEditCommit(e -> {
            e.getRowValue().setAdmissionNumber(e.getNewValue());
            afterEdit();
        });
        adm.setPrefWidth(90);

        TableColumn<FeesBalanceRow, String> name = new TableColumn<>("Name");
        name.setCellValueFactory(c -> c.getValue().nameProperty());
        name.setCellFactory(c -> new TextFieldTableCell<>());
        name.setOnEditCommit(e -> {
            e.getRowValue().setName(e.getNewValue());
            afterEdit();
        });
        name.setPrefWidth(210);

        TableColumn<FeesBalanceRow, String> form = new TableColumn<>("Form");
        form.setCellValueFactory(c -> c.getValue().formClassProperty());
        form.setCellFactory(c -> new TextFieldTableCell<>());
        form.setOnEditCommit(e -> {
            e.getRowValue().setFormClass(e.getNewValue());
            afterEdit();
        });
        form.setPrefWidth(75);

        TableColumn<FeesBalanceRow, String> stream = new TableColumn<>("Stream");
        stream.setCellValueFactory(c -> c.getValue().streamProperty());
        stream.setCellFactory(c -> new TextFieldTableCell<>());
        stream.setOnEditCommit(e -> {
            e.getRowValue().setStream(e.getNewValue());
            afterEdit();
        });
        stream.setPrefWidth(70);

        TableColumn<FeesBalanceRow, BoardingStatus> boarding = new TableColumn<>("Boarding");
        boarding.setCellValueFactory(c -> c.getValue().boardingStatusProperty());
        boarding.setCellFactory(c -> new ComboBoxTableCell<>(BoardingStatus.values()));
        boarding.setOnEditCommit(e -> {
            e.getRowValue().setBoardingStatus(e.getNewValue());
            afterEdit();
        });
        boarding.setPrefWidth(95);

        TableColumn<FeesBalanceRow, String> cfees = moneyColumn("C/FEES", r -> r.getCurrentFees(), false);
        TableColumn<FeesBalanceRow, String> arrears = moneyColumn("Arrears", r -> r.getArrears(), false);
        TableColumn<FeesBalanceRow, String> penalty = moneyColumn("Penalty", r -> r.getPenalty(), false);
        TableColumn<FeesBalanceRow, String> balance = moneyColumn("Balance to Import", FeesBalanceRow::getBalance, true);
        balance.setOnEditCommit(e -> {
            BigDecimal value = parseAmount(e.getNewValue());
            if (value != null) {
                FeesBalanceRow row = e.getRowValue();
                row.setTotalFees(CurrencyConfig.money(value.subtract(row.getPenalty())));
                afterEdit();
            }
        });

        TableColumn<FeesBalanceRow, String> status = new TableColumn<>("Match");
        status.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getMatchStatus()));
        status.setPrefWidth(75);

        TableColumn<FeesBalanceRow, String> notes = new TableColumn<>("Warnings / Notes");
        notes.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getWarningText()));
        notes.setCellFactory(c -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().removeAll("import-error-text", "import-ok-text");
                if (empty || item == null || item.isBlank()) {
                    setText(empty ? null : "OK");
                    if (!empty) {
                        getStyleClass().add("import-ok-text");
                    }
                } else {
                    setText(item);
                    getStyleClass().add("import-error-text");
                }
            }
        });
        notes.setPrefWidth(360);

        @SuppressWarnings("unchecked")
        var columns = new TableColumn[]{include, sheet, rowNo, adm, name, form, stream, boarding,
                cfees, arrears, penalty, balance, status, notes};
        table.getColumns().addAll(columns);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setItems(cleanData);
    }

    private TableColumn<FeesBalanceRow, String> moneyColumn(String title,
                                                            java.util.function.Function<FeesBalanceRow, BigDecimal> value,
                                                            boolean editable) {
        TableColumn<FeesBalanceRow, String> column = new TableColumn<>(title);
        column.setCellValueFactory(c -> new SimpleStringProperty(
                value.apply(c.getValue()) == null ? "" : CurrencyConfig.formatPlain(value.apply(c.getValue()))));
        column.setCellFactory(c -> new TextFieldTableCell<>());
        column.setEditable(editable);
        column.setPrefWidth(110);
        return column;
    }

    private void afterEdit() {
        revalidateCleanData();
        importCleanedRows();
    }

    /**
     * A row can be imported right away when it is ticked, not a sheet-level
     * skip, has a student name and carries no blocking mistake.
     */
    private boolean isAutoImportable(FeesBalanceRow row) {
        if (!row.isInclude() || "Skipped".equals(row.getMatchStatus())) {
            return false;
        }
        if (row.getName() == null || row.getName().isBlank()) {
            return false;
        }
        return !row.requiresCleaning();
    }

    /**
     * Recompute the scrutiny notes for the held rows and re-flag admission
     * numbers that were already committed in this session so duplicates cannot
     * silently overwrite a balance.
     */
    private void revalidateCleanData() {
        if (cleanData.isEmpty()) {
            return;
        }
        importService.scrutinize(cleanData);
        for (FeesBalanceRow row : cleanData) {
            String adm = normalizeAdm(row.getAdmissionNumber());
            if (!adm.isEmpty() && committedAdmissionNumbers.contains(adm)
                    && !row.getWarnings().contains(ALREADY_IMPORTED)) {
                row.getWarnings().add(ALREADY_IMPORTED);
            }
        }
    }

    /**
     * Commit every held row that became importable (mistakes cleared, still
     * ticked) immediately, and drop it from the Clean Data table. Rows that
     * still fail to apply stay held with the failure reason shown.
     */
    private void importCleanedRows() {
        List<FeesBalanceRow> ready = new ArrayList<>();
        for (FeesBalanceRow row : cleanData) {
            String adm = normalizeAdm(row.getAdmissionNumber());
            if (isAutoImportable(row) && !committedAdmissionNumbers.contains(adm)) {
                ready.add(row);
            }
        }
        if (ready.isEmpty()) {
            updateSummary();
            table.refresh();
            return;
        }
        importNow(ready, cleanData);
        revalidateCleanData();
        updateSummary();
        table.refresh();
    }

    /**
     * Apply the given rows in one pass and record them as committed. Rows that
     * failed to apply (warnings starting with "Skipped") are returned to the
     * held list with the reason shown.
     */
    private void importNow(List<FeesBalanceRow> toImport, List<FeesBalanceRow> held) {
        if (toImport.isEmpty()) {
            return;
        }
        FeesBalanceImportService.ApplyResult result = importService.apply(toImport);
        imported += result.getCreated() + result.getExisting();

        Set<String> skippedLabels = result.getWarnings().stream()
                .filter(w -> w.startsWith("Skipped "))
                .map(w -> w.substring("Skipped ".length()))
                .map(w -> w.indexOf(':') >= 0 ? w.substring(0, w.indexOf(':')).trim() : w.trim())
                .collect(Collectors.toSet());

        for (FeesBalanceRow row : toImport) {
            if (skippedLabels.contains(label(row))) {
                if (!held.contains(row)) {
                    held.add(row);
                }
                for (String warning : result.getWarnings()) {
                    if (warning.startsWith("Skipped " + label(row) + ":")
                            && !row.getWarnings().contains(warning)) {
                        row.getWarnings().add(warning);
                    }
                }
            } else {
                committedAdmissionNumbers.add(normalizeAdm(row.getAdmissionNumber()));
                held.remove(row);
            }
        }
    }

    private void updateHeader() {
        setHeaderText("Fees balance import: " + cleanData.size()
                + " row(s) need cleaning"
                + (imported > 0 ? " (" + imported + " valid row(s) were committed immediately)" : "")
                + ". Fix the details below and each row imports automatically when its mistakes are cleared.");
    }

    private void updateSummary() {
        summaryLabel.setText("Imported: " + imported
                + "   |   Clean Data: " + cleanData.size()
                + (cleanData.isEmpty() ? "   |   All rows imported." : "   |   Fix a row (or uncheck to skip) and it imports automatically."));
        summaryLabel.setAlignment(Pos.CENTER_LEFT);
    }

    private String label(FeesBalanceRow row) {
        return row.getName() + (row.getAdmissionNumber().isBlank() ? "" : " (" + row.getAdmissionNumber() + ")");
    }

    private String normalizeAdm(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private BigDecimal parseAmount(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(value.trim().replaceAll(",", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public int getImportedCount() {
        return imported;
    }

    public int getRemainingCount() {
        return cleanData.size();
    }

    /**
     * The rows still held for cleaning when the dialog closes. Used to persist
     * Clean Data so a later session can continue fixing them.
     */
    public List<FeesBalanceRow> getHeldRows() {
        return new ArrayList<>(cleanData);
    }
}
