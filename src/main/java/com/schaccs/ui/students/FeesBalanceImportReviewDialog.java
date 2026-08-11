package com.schaccs.ui.students;

import com.schaccs.config.CurrencyConfig;
import com.schaccs.enums.BoardingStatus;
import com.schaccs.service.importer.FeesBalanceImportService;
import com.schaccs.service.importer.FeesBalanceRow;
import com.schaccs.util.AlertUtil;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
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
import java.util.List;

/**
 * Scrutiny + review dialog for a "FEES BALANCE" workbook. Every student row
 * found on any sheet is staged; the report shows how each row was matched
 * against the student registry and flags anomalies (inconsistent totals,
 * missing admission numbers, credit balances, penalties, duplicates, sheets
 * that were skipped). Details can be corrected inline before applying.
 */
public class FeesBalanceImportReviewDialog extends Dialog<ButtonType> {

    private static final ButtonType IMPORT_TYPE =
            new ButtonType("Import Checked", ButtonBar.ButtonData.OK_DONE);
    private static final ButtonType CANCEL_TYPE =
            new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);

    private final FeesBalanceImportService importService;
    private final ObservableList<FeesBalanceRow> rows;
    private final TableView<FeesBalanceRow> table = new TableView<>();
    private final Label summaryLabel = new Label();

    private int imported;

    public FeesBalanceImportReviewDialog(FeesBalanceImportService importService, List<FeesBalanceRow> rows) {
        this.importService = importService;
        this.rows = FXCollections.observableArrayList(rows);

        setTitle("Import Fees Balance - Review & Scrutiny");
        initModality(Modality.APPLICATION_MODAL);
        updateHeader();
        getDialogPane().getButtonTypes().addAll(CANCEL_TYPE, IMPORT_TYPE);

        buildTable();
        updateSummary();

        summaryLabel.getStyleClass().add("muted");
        summaryLabel.setWrapText(true);
        summaryLabel.setMaxWidth(Double.MAX_VALUE);
        Label hint = new Label("Tip: uncheck rows to skip them, fix names/admission numbers/classes inline, and edit the "
                + "Balance cell before importing. Unmatched students are created as Active; the Balance is written as "
                + "arrears (or advance when it is a credit).");
        hint.getStyleClass().add("muted");
        hint.setWrapText(true);

        HBox.setHgrow(summaryLabel, Priority.ALWAYS);
        HBox toolbar = new HBox(10, summaryLabel);
        toolbar.setAlignment(Pos.CENTER_LEFT);

        VBox content = new VBox(10, toolbar, table, hint);
        content.setPadding(new Insets(8));
        getDialogPane().setContent(content);
        getDialogPane().setPrefSize(1560, 640);

        Button importButton = (Button) getDialogPane().lookupButton(IMPORT_TYPE);
        importButton.getStyleClass().add("primary-button");
        importButton.setOnAction(e -> {
            e.consume();
            commit();
        });
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
            updateSummary();
        });
        adm.setPrefWidth(90);

        TableColumn<FeesBalanceRow, String> name = new TableColumn<>("Name");
        name.setCellValueFactory(c -> c.getValue().nameProperty());
        name.setCellFactory(c -> new TextFieldTableCell<>());
        name.setOnEditCommit(e -> {
            e.getRowValue().setName(e.getNewValue());
            updateSummary();
        });
        name.setPrefWidth(210);

        TableColumn<FeesBalanceRow, String> form = new TableColumn<>("Form");
        form.setCellValueFactory(c -> c.getValue().formClassProperty());
        form.setCellFactory(c -> new TextFieldTableCell<>());
        form.setOnEditCommit(e -> e.getRowValue().setFormClass(e.getNewValue()));
        form.setPrefWidth(75);

        TableColumn<FeesBalanceRow, String> stream = new TableColumn<>("Stream");
        stream.setCellValueFactory(c -> c.getValue().streamProperty());
        stream.setCellFactory(c -> new TextFieldTableCell<>());
        stream.setOnEditCommit(e -> e.getRowValue().setStream(e.getNewValue()));
        stream.setPrefWidth(70);

        TableColumn<FeesBalanceRow, BoardingStatus> boarding = new TableColumn<>("Boarding");
        boarding.setCellValueFactory(c -> c.getValue().boardingStatusProperty());
        boarding.setCellFactory(c -> new ComboBoxTableCell<>(BoardingStatus.values()));
        boarding.setOnEditCommit(e -> e.getRowValue().setBoardingStatus(e.getNewValue()));
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
                updateSummary();
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
        table.setItems(rows);
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

    private void updateHeader() {
        long newCount = rows.stream().filter(r -> r.getMatchStatus().equals("New")).count();
        long existingCount = rows.stream().filter(r -> r.getMatchStatus().equals("Existing")).count();
        long warningCount = rows.stream().filter(r -> !r.getWarnings().isEmpty()).count();
        setHeaderText(rows.size() + " student row(s) staged from the workbook. "
                + newCount + " new · " + existingCount + " existing · "
                + warningCount + " need attention. Review below, then Import Checked.");
    }

    private void updateSummary() {
        long checked = rows.stream().filter(r -> r.isInclude() && !r.getMatchStatus().equals("Skipped")).count();
        summaryLabel.setText("Staged: " + rows.size() + "   |   Will import: " + checked
                + "   |   Imported so far: " + imported
                + (imported > 0 ? "" : "   |   Uncheck a row to skip it"));
        summaryLabel.setAlignment(Pos.CENTER_LEFT);
    }

    private void commit() {
        List<FeesBalanceRow> selected = new ArrayList<>();
        for (FeesBalanceRow row : rows) {
            if (row.isInclude() && !row.getMatchStatus().equals("Skipped")) {
                selected.add(row);
            }
        }
        if (selected.isEmpty()) {
            AlertUtil.warn("Nothing selected", "Tick at least one row to import.");
            return;
        }
        long nameless = selected.stream().filter(r -> r.getName() == null || r.getName().isBlank()).count();
        if (nameless > 0) {
            AlertUtil.warn("Rows without a name",
                    nameless + " selected row(s) have no student name and will be skipped on import.");
        }
        FeesBalanceImportService.ApplyResult result = importService.apply(selected);
        imported += result.getCreated() + result.getExisting();

        StringBuilder message = new StringBuilder();
        message.append("Created: ").append(result.getCreated()).append("\n");
        message.append("Updated (existing): ").append(result.getExisting()).append("\n");
        message.append("Credit balances -> advance: ").append(result.getCredits()).append("\n");
        message.append("Skipped: ").append(result.getSkipped());
        if (!result.getWarnings().isEmpty()) {
            message.append("\n\nWarnings:\n- ").append(String.join("\n- ", result.getWarnings()));
        }
        AlertUtil.info("Import complete", message.toString());
        setResult(IMPORT_TYPE);
        hide();
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
}
