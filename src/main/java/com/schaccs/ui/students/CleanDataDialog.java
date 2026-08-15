package com.schaccs.ui.students;

import com.schaccs.enums.BoardingStatus;
import com.schaccs.enums.StudentStatus;
import com.schaccs.model.CleanDataEntry;
import com.schaccs.model.student.Student;
import com.schaccs.repository.PersistenceService;
import com.schaccs.service.importer.FeesBalanceImportService;
import com.schaccs.service.importer.FeesBalanceRow;
import com.schaccs.service.importer.StudentImportService;
import com.schaccs.store.CleanDataStore;
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
import javafx.scene.control.SelectionMode;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;
import javafx.scene.control.cell.ComboBoxTableCell;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Persistent Clean Data: import rows that carried mistakes were held back
 * instead of being committed. Students are shown in an editable table that
 * explains exactly what is wrong and how to correct it — the wrong cells are
 * highlighted red and the "Errors & How to Fix" column describes the fix. The
 * moment a student's mistakes are cleared it is added to the student list and
 * leaves this table. Fees-balance rows keep their review dialog, and any row
 * can be discarded.
 */
public class CleanDataDialog extends Dialog<ButtonType> {

    private static final ButtonType CLOSE_TYPE =
            new ButtonType("Close", ButtonBar.ButtonData.CANCEL_CLOSE);

    private final Runnable onChange;
    private final StudentImportService importService = new StudentImportService();

    // Students: editable working model kept in sync with CleanDataStore.
    private final ObservableList<Student> stagedStudents = FXCollections.observableArrayList();
    private final List<Map<String, String>> rawStudentRows = new ArrayList<>();
    private final Map<Student, Map<String, List<String>>> errorsByStudent = new HashMap<>();
    private final TableView<Student> studentTable = new TableView<>();
    private final Label studentSummary = new Label();
    private int imported;

    // Fees balance: existing review flow.
    private final TableView<CleanDataEntry> feesTable = new TableView<>();
    private final Label feesSummary = new Label();

    public CleanDataDialog(Runnable onChange) {
        this.onChange = onChange;

        setTitle("Clean Data");
        initModality(Modality.APPLICATION_MODAL);
        getDialogPane().getButtonTypes().addAll(CLOSE_TYPE);

        TabPane tabPane = new TabPane();
        Tab studentsTab = new Tab("Students", buildStudentPane());
        Tab feesTab = new Tab("Fees Balance", buildFeesPane());
        tabPane.getTabs().addAll(studentsTab, feesTab);

        getDialogPane().setContent(tabPane);
        getDialogPane().setPrefSize(1580, 680);

        loadStudentRows();
        loadFeesRows();
        setOnCloseRequest(e -> syncStudentStore());
    }

    // =========================================================================
    // Students tab
    // =========================================================================

    private VBox buildStudentPane() {
        buildStudentTable();

        studentSummary.getStyleClass().add("muted");
        studentSummary.setWrapText(true);
        studentSummary.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(studentSummary, Priority.ALWAYS);

        Button discardBtn = new Button("Discard Selected");
        discardBtn.getStyleClass().add("danger-button");
        discardBtn.setGraphic(new FontIcon(FontAwesomeSolid.TRASH));
        discardBtn.setOnAction(e -> discardSelectedStudents());

        HBox toolbar = new HBox(10, studentSummary, discardBtn);
        toolbar.setAlignment(Pos.CENTER_LEFT);

        Label hint = new Label("The red cells hold the wrong information from the import. "
                + "Double-click a red cell and type/choose the correct value — the \"Errors & How to Fix\" column "
                + "explains what is wrong and what to correct. The moment all mistakes are cleared, the student is "
                + "added to the student list and leaves this table.");
        hint.getStyleClass().add("muted");
        hint.setWrapText(true);

        VBox content = new VBox(10, toolbar, studentTable, hint);
        content.setPadding(new Insets(8));
        return content;
    }

    private void buildStudentTable() {
        studentTable.setEditable(true);
        studentTable.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

        TableColumn<Student, String> adm = new TableColumn<>("Adm No");
        adm.setCellValueFactory(c -> c.getValue().admissionNumberProperty());
        adm.setCellFactory(c -> new TextFieldTableCell<>() {
            @Override
            public void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                applyHighlight(this, "adm", empty);
            }
        });
        adm.setOnEditCommit(e -> {
            e.getRowValue().setAdmissionNumber(e.getNewValue());
            syncRaw(e.getRowValue(), "admissionnumber", e.getNewValue());
            afterEdit(e.getRowValue());
        });
        adm.setPrefWidth(110);

        TableColumn<Student, String> name = new TableColumn<>("Full Name");
        name.setCellValueFactory(c -> c.getValue().nameProperty());
        name.setCellFactory(c -> new TextFieldTableCell<>() {
            @Override
            public void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                applyHighlight(this, "name", empty);
            }
        });
        name.setOnEditCommit(e -> {
            e.getRowValue().setName(e.getNewValue());
            syncRaw(e.getRowValue(), "fullname", e.getNewValue());
            afterEdit(e.getRowValue());
        });
        name.setPrefWidth(180);

        TableColumn<Student, String> gender = new TableColumn<>("Gender");
        gender.setCellValueFactory(c -> c.getValue().genderProperty());
        gender.setCellFactory(c -> new ComboBoxTableCell<>("Male", "Female") {
            @Override
            public void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                applyHighlight(this, "gender", empty);
            }
        });
        gender.setOnEditCommit(e -> {
            e.getRowValue().setGender(e.getNewValue());
            syncRaw(e.getRowValue(), "gender", e.getNewValue());
            afterEdit(e.getRowValue());
        });
        gender.setPrefWidth(80);

        TableColumn<Student, String> cls = new TableColumn<>("Form Class");
        cls.setCellValueFactory(c -> c.getValue().formClassProperty());
        cls.setCellFactory(c -> new TextFieldTableCell<>() {
            @Override
            public void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                applyHighlight(this, "class", empty);
            }
        });
        cls.setOnEditCommit(e -> {
            e.getRowValue().setFormClass(e.getNewValue());
            syncRaw(e.getRowValue(), "formclass", e.getNewValue());
            afterEdit(e.getRowValue());
        });
        cls.setPrefWidth(110);

        TableColumn<Student, String> stream = new TableColumn<>("Stream");
        stream.setCellValueFactory(c -> c.getValue().streamProperty());
        stream.setCellFactory(c -> new TextFieldTableCell<>() {
            @Override
            public void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                applyHighlight(this, "stream", empty);
            }
        });
        stream.setOnEditCommit(e -> {
            e.getRowValue().setStream(e.getNewValue());
            syncRaw(e.getRowValue(), "stream", e.getNewValue());
            afterEdit(e.getRowValue());
        });
        stream.setPrefWidth(70);

        TableColumn<Student, BoardingStatus> boarding = new TableColumn<>("Boarding");
        boarding.setCellValueFactory(c -> c.getValue().boardingStatusProperty());
        boarding.setCellFactory(c -> new ComboBoxTableCell<>(BoardingStatus.values()) {
            @Override
            public void updateItem(BoardingStatus item, boolean empty) {
                super.updateItem(item, empty);
                applyHighlight(this, "boarding", empty);
            }
        });
        boarding.setOnEditCommit(e -> {
            e.getRowValue().setBoardingStatus(e.getNewValue());
            syncRaw(e.getRowValue(), "boardingstatus",
                    e.getNewValue() == null ? "" : e.getNewValue().getDisplayName());
            afterEdit(e.getRowValue());
        });
        boarding.setPrefWidth(95);

        TableColumn<Student, String> phone = new TableColumn<>("Phone");
        phone.setCellValueFactory(c -> c.getValue().phoneProperty());
        phone.setCellFactory(c -> new TextFieldTableCell<>() {
            @Override
            public void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                applyHighlight(this, "phone", empty);
            }
        });
        phone.setOnEditCommit(e -> {
            e.getRowValue().setPhone(e.getNewValue());
            syncRaw(e.getRowValue(), "phone", e.getNewValue());
            afterEdit(e.getRowValue());
        });
        phone.setPrefWidth(130);

        TableColumn<Student, String> parent = new TableColumn<>("Parent/Guardian");
        parent.setCellValueFactory(c -> c.getValue().parentNameProperty());
        parent.setCellFactory(c -> new TextFieldTableCell<>() {
            @Override
            public void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                applyHighlight(this, "parent", empty);
            }
        });
        parent.setOnEditCommit(e -> {
            e.getRowValue().setParentName(e.getNewValue());
            syncRaw(e.getRowValue(), "parentname", e.getNewValue());
            afterEdit(e.getRowValue());
        });
        parent.setPrefWidth(140);

        TableColumn<Student, StudentStatus> status = new TableColumn<>("Status");
        status.setCellValueFactory(c -> c.getValue().statusProperty());
        status.setCellFactory(c -> new ComboBoxTableCell<>(StudentStatus.values()) {
            @Override
            public void updateItem(StudentStatus item, boolean empty) {
                super.updateItem(item, empty);
                applyHighlight(this, "status", empty);
            }
        });
        status.setOnEditCommit(e -> {
            e.getRowValue().setStatus(e.getNewValue());
            syncRaw(e.getRowValue(), "studentstatus",
                    e.getNewValue() == null ? "" : e.getNewValue().getDisplayName());
            afterEdit(e.getRowValue());
        });
        status.setPrefWidth(90);

        TableColumn<Student, String> academicYear = new TableColumn<>("Academic Year");
        academicYear.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getAcademicYear() == null ? "" : String.valueOf(c.getValue().getAcademicYear())));
        academicYear.setCellFactory(c -> new TextFieldTableCell<>() {
            @Override
            public void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                applyHighlight(this, "academicYear", empty);
            }
        });
        academicYear.setOnEditCommit(e -> {
            String value = e.getNewValue() == null ? "" : e.getNewValue().trim();
            e.getRowValue().setAcademicYear(parseYear(value));
            syncRaw(e.getRowValue(), "academicyear", value);
            afterEdit(e.getRowValue());
        });
        academicYear.setPrefWidth(110);

        TableColumn<Student, String> yearOfAdmission = new TableColumn<>("Year of Admission");
        yearOfAdmission.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getYearOfAdmission() == null ? "" : String.valueOf(c.getValue().getYearOfAdmission())));
        yearOfAdmission.setCellFactory(c -> new TextFieldTableCell<>() {
            @Override
            public void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                applyHighlight(this, "yearOfAdmission", empty);
            }
        });
        yearOfAdmission.setOnEditCommit(e -> {
            String value = e.getNewValue() == null ? "" : e.getNewValue().trim();
            e.getRowValue().setYearOfAdmission(parseYear(value));
            syncRaw(e.getRowValue(), "yearofadmission", value);
            afterEdit(e.getRowValue());
        });
        yearOfAdmission.setPrefWidth(110);

        TableColumn<Student, String> errorsCol = new TableColumn<>("Errors & How to Fix");
        errorsCol.setCellValueFactory(c -> new SimpleStringProperty(allErrors(c.getValue())));
        errorsCol.setCellFactory(c -> new TableCell<>() {
            @Override
            public void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().removeAll("import-error-text", "import-ok-text");
                setTooltip(null);
                if (empty || item == null || item.isBlank()) {
                    setText(empty ? null : "OK — ready to import");
                    if (!empty) {
                        getStyleClass().add("import-ok-text");
                    }
                } else {
                    setText(item);
                    getStyleClass().add("import-error-text");
                    setTooltip(new Tooltip(tooltipErrors(getTableRow() != null
                            ? (Student) getTableRow().getItem() : null)));
                }
            }
        });
        errorsCol.setPrefWidth(430);

        @SuppressWarnings("unchecked")
        var columns = new TableColumn[]{adm, name, gender, cls, stream, boarding, phone, parent, status,
                academicYear, yearOfAdmission, errorsCol};
        studentTable.getColumns().addAll(columns);
        studentTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        studentTable.setItems(stagedStudents);
    }

    private void loadStudentRows() {
        for (CleanDataEntry entry : CleanDataStore.getInstance().forType(CleanDataEntry.Type.STUDENT)) {
            Map<String, String> fields = new LinkedHashMap<>(entry.getFields());
            rawStudentRows.add(fields);
            stagedStudents.add(importService.toStudent(fields));
        }
        revalidate();
    }

    /**
     * Commit every currently clean student row right away (kept for safety after
     * load), then re-validate the rest.
     */
    private void afterEdit(Student student) {
        revalidate();
        tryImportStudent(student);
    }

    private void tryImportStudent(Student student) {
        boolean clean = errorsByStudent.getOrDefault(student, Map.of())
                .values().stream().allMatch(List::isEmpty);
        if (clean) {
            List<String> errors = importService.commitStudent(student);
            if (!errors.isEmpty()) {
                errorsByStudent.get(student).computeIfAbsent("row", k -> new ArrayList<>()).addAll(errors);
                clean = false;
            }
        }
        if (clean) {
            imported++;
            int index = stagedStudents.indexOf(student);
            if (index >= 0) {
                stagedStudents.remove(index);
                if (index < rawStudentRows.size()) {
                    rawStudentRows.remove(index);
                }
            }
            PersistenceService.getInstance().saveAll();
            syncStudentStore();
            revalidate();
        }
        updateStudentSummary();
        studentTable.refresh();
    }

    private void discardSelectedStudents() {
        List<Student> selected = List.copyOf(studentTable.getSelectionModel().getSelectedItems());
        if (selected.isEmpty()) {
            AlertUtil.warn("No selection", "Select one or more student rows to discard.");
            return;
        }
        for (Student student : selected) {
            int index = stagedStudents.indexOf(student);
            if (index >= 0) {
                stagedStudents.remove(index);
                if (index < rawStudentRows.size()) {
                    rawStudentRows.remove(index);
                }
            }
        }
        syncStudentStore();
        revalidate();
    }

    /**
     * Keep the original imported row in sync with inline edits so revalidation
     * always inspects the values currently shown in the table.
     */
    private void syncRaw(Student student, String key, String value) {
        int index = stagedStudents.indexOf(student);
        if (index >= 0 && index < rawStudentRows.size()) {
            rawStudentRows.get(index).put(key, value == null ? "" : value);
        }
    }

    private void revalidate() {
        errorsByStudent.clear();
        for (int i = 0; i < stagedStudents.size(); i++) {
            Student student = stagedStudents.get(i);
            List<Student> others = new ArrayList<>(stagedStudents);
            others.remove(student);
            List<String> messages = importService.validateRow(rawStudentRows.get(i), student, others);
            Map<String, List<String>> byField = new LinkedHashMap<>();
            for (String message : messages) {
                byField.computeIfAbsent(fieldForMessage(message), k -> new ArrayList<>()).add(message);
            }
            errorsByStudent.put(student, byField);
        }
        updateStudentSummary();
        studentTable.refresh();
    }

    private void applyHighlight(TableCell<Student, ?> cell, String field, boolean empty) {
        cell.getStyleClass().remove("import-error-cell");
        cell.setTooltip(null);
        if (empty) {
            return;
        }
        Student student = cell.getTableRow() != null ? cell.getTableRow().getItem() : null;
        if (student == null) {
            return;
        }
        List<String> messages = errorsByStudent.getOrDefault(student, Map.of()).get(field);
        if (messages != null && !messages.isEmpty()) {
            cell.getStyleClass().add("import-error-cell");
            cell.setTooltip(new Tooltip(messages.stream()
                    .map(m -> m + "\n→ " + fixHint(field, m))
                    .collect(Collectors.joining("\n"))));
        }
    }

    private String fieldForMessage(String message) {
        if (message.contains("Admission number")) {
            return "adm";
        }
        if (message.contains("Student name")) {
            return "name";
        }
        if (message.contains("Class / Form")) {
            return "class";
        }
        if (message.contains("Boarding status")) {
            return "boarding";
        }
        if (message.contains("Phone number")) {
            return "phone";
        }
        if (message.startsWith("Academic Year")) {
            return "academicYear";
        }
        if (message.startsWith("Year of Admission")) {
            return "yearOfAdmission";
        }
        return "row";
    }

    private String fixHint(String field, String message) {
        return switch (field) {
            case "adm" -> "Enter a unique admission number (e.g. 2026/123).";
            case "name" -> "Enter the student's full name.";
            case "class" -> "Enter the class/form (e.g. Form 2 or Grade 8).";
            case "boarding" -> "Choose Boarding or Day.";
            case "gender" -> "Choose Male or Female.";
            case "phone" -> "Use a Kenyan number: 07..., 01..., +254..., or 254...";
            case "academicYear" -> "Enter a year as a number (e.g. 2026).";
            case "yearOfAdmission" -> "Enter a year as a number (e.g. 2025).";
            case "status" -> "Choose Active or Inactive.";
            default -> "Correct the highlighted value and the row will be added to the student list automatically.";
        };
    }

    private String allErrors(Student student) {
        Map<String, List<String>> byField = errorsByStudent.get(student);
        if (byField == null) {
            return "";
        }
        return byField.entrySet().stream()
                .flatMap(e -> e.getValue().stream()
                        .map(m -> m + " → " + fixHint(e.getKey(), m)))
                .distinct()
                .collect(Collectors.joining(" • "));
    }

    private String tooltipErrors(Student student) {
        Map<String, List<String>> byField = errorsByStudent.get(student);
        if (byField == null || byField.isEmpty()) {
            return "This row is valid — it will be added to the student list.";
        }
        return byField.entrySet().stream()
                .flatMap(e -> e.getValue().stream()
                        .map(m -> "• " + m + "\n  " + fixHint(e.getKey(), m)))
                .collect(Collectors.joining("\n"));
    }

    private Integer parseYear(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(value.trim()).intValueExact();
        } catch (Exception ex) {
            return null;
        }
    }

    private void updateStudentSummary() {
        if (stagedStudents.isEmpty()) {
            studentSummary.setText("No student rows to clean — students fixed here are added to the student list.");
        } else {
            studentSummary.setText(stagedStudents.size() + " student row(s) need fixing"
                    + (imported > 0 ? " (" + imported + " fixed and added to the student list)" : "")
                    + ". Edit the red cells — each row is added to the student list the moment it validates.");
        }
        studentSummary.setAlignment(Pos.CENTER_LEFT);
    }

    // =========================================================================
    // Fees balance tab
    // =========================================================================

    private VBox buildFeesPane() {
        buildFeesTable();

        feesSummary.getStyleClass().add("muted");
        feesSummary.setWrapText(true);
        feesSummary.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(feesSummary, Priority.ALWAYS);

        Button fixFees = new Button("Fix Fees Balance Rows");
        fixFees.getStyleClass().add("primary-button");
        fixFees.setGraphic(new FontIcon(FontAwesomeSolid.COINS));
        fixFees.setOnAction(e -> fixFeesBalanceRows());

        Button discardBtn = new Button("Discard Selected");
        discardBtn.getStyleClass().add("danger-button");
        discardBtn.setGraphic(new FontIcon(FontAwesomeSolid.TRASH));
        discardBtn.setOnAction(e -> discardSelectedFees());

        HBox toolbar = new HBox(10, feesSummary, fixFees, discardBtn);
        toolbar.setAlignment(Pos.CENTER_LEFT);

        Label hint = new Label("Fees-balance rows that failed validation during a balances import are held here. "
                + "Open them to correct the values; a row imports automatically the moment its mistakes are cleared.");
        hint.getStyleClass().add("muted");
        hint.setWrapText(true);

        VBox content = new VBox(10, toolbar, feesTable, hint);
        content.setPadding(new Insets(8));
        return content;
    }

    private void buildFeesTable() {
        feesTable.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

        TableColumn<CleanDataEntry, String> name = new TableColumn<>("Name");
        name.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getName()));
        name.setPrefWidth(240);

        TableColumn<CleanDataEntry, String> details = new TableColumn<>("Details");
        details.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getDetail()));
        details.setPrefWidth(420);

        TableColumn<CleanDataEntry, String> held = new TableColumn<>("Held Since");
        held.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getCreatedAt().toLocalDate().toString()));
        held.setPrefWidth(120);

        feesTable.getColumns().add(name);
        feesTable.getColumns().add(details);
        feesTable.getColumns().add(held);
        feesTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
    }

    private void loadFeesRows() {
        feesTable.setItems(FXCollections.observableArrayList(
                CleanDataStore.getInstance().forType(CleanDataEntry.Type.FEES_BALANCE)));
        updateFeesSummary();
    }

    private void updateFeesSummary() {
        int count = feesTable.getItems().size();
        feesSummary.setText(count == 0
                ? "No fees-balance rows to clean."
                : count + " fees-balance row(s) waiting to be fixed.");
        feesSummary.setAlignment(Pos.CENTER_LEFT);
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
        loadFeesRows();
        notifyChanged();
    }

    private void discardSelectedFees() {
        List<CleanDataEntry> selected = List.copyOf(feesTable.getSelectionModel().getSelectedItems());
        if (selected.isEmpty()) {
            AlertUtil.warn("No selection", "Select one or more fees-balance rows to discard.");
            return;
        }
        for (CleanDataEntry entry : selected) {
            CleanDataStore.getInstance().remove(entry);
        }
        PersistenceService.getInstance().saveAll();
        loadFeesRows();
        notifyChanged();
    }

    /**
     * Persist the current student working set back into CleanDataStore so the
     * rows still being fixed (including in-progress edits) survive a restart,
     * and the rows already fixed/discarded never come back.
     */
    private void syncStudentStore() {
        CleanDataStore.getInstance().replaceRows(CleanDataEntry.Type.STUDENT, rawStudentRows);
        PersistenceService.getInstance().saveAll();
        notifyChanged();
    }

    private void notifyChanged() {
        if (onChange != null) {
            onChange.run();
        }
    }
}
