package com.schaccs.ui.students;

import com.schaccs.enums.BoardingStatus;
import com.schaccs.enums.StudentStatus;
import com.schaccs.model.student.Student;
import com.schaccs.repository.PersistenceService;
import com.schaccs.service.importer.StudentImportService;
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
import javafx.scene.control.Tooltip;
import javafx.scene.control.cell.ComboBoxTableCell;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Import review dialog: stages every row from an imported file (including rows
 * with mistakes), highlights the cells that need correction (duplicate admission
 * numbers, non-numeric years, bad phone format, missing required fields, ...) and
 * lets the user fix them inline in the table before saving. Saving commits only
 * the rows that are now valid; rows still in error stay highlighted.
 */
public class StudentImportReviewDialog extends Dialog<ButtonType> {

    private static final ButtonType SAVE_TYPE =
            new ButtonType("Save Valid Rows", ButtonBar.ButtonData.OK_DONE);
    private static final ButtonType CANCEL_TYPE =
            new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);

    private final StudentImportService importService;
    private final ObservableList<Student> staged;
    private final List<Map<String, String>> rawRows;
    private final Map<Student, Map<String, List<String>>> errorsByStudent = new HashMap<>();

    private final TableView<Student> table = new TableView<>();
    private final Label summaryLabel = new Label();

    private int imported;

    public StudentImportReviewDialog(List<Map<String, String>> rawRows,
                                     List<Student> students,
                                     StudentImportService importService) {
        this.importService = importService;
        this.rawRows = new ArrayList<>(rawRows);
        this.staged = FXCollections.observableArrayList(students);

        setTitle("Review Import");
        initModality(Modality.APPLICATION_MODAL);
        setHeaderText("All " + staged.size() + " row(s) from the file are staged below. "
                + "Red cells contain mistakes (e.g. duplicate admission number, number format, "
                + "Academic Year / Year of Admission). "
                + "Edit them directly in the table, then click Save Valid Rows.");
        getDialogPane().getButtonTypes().addAll(CANCEL_TYPE, SAVE_TYPE);

        buildTable();

        summaryLabel.getStyleClass().add("muted");
        summaryLabel.setWrapText(true);
        Label hint = new Label("Tip: double-click a red cell to edit it. Rows that are still in error will not be saved.");
        hint.getStyleClass().add("muted");
        hint.setWrapText(true);

        VBox content = new VBox(10, summaryLabel, table, hint);
        content.setPadding(new Insets(8));
        getDialogPane().setContent(content);
        getDialogPane().setPrefSize(1480, 620);

        Button saveButton = (Button) getDialogPane().lookupButton(SAVE_TYPE);
        saveButton.getStyleClass().add("primary-button");
        saveButton.setOnAction(e -> {
            e.consume();
            commitValid();
        });

        revalidate();
    }

    private void buildTable() {
        table.setEditable(true);

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
            revalidate();
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
            revalidate();
        });
        name.setPrefWidth(170);

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
            revalidate();
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
            revalidate();
        });
        cls.setPrefWidth(100);

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
            revalidate();
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
            revalidate();
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
            revalidate();
        });
        phone.setPrefWidth(120);

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
            revalidate();
        });
        parent.setPrefWidth(130);

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
            revalidate();
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
            revalidate();
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
            revalidate();
        });
        yearOfAdmission.setPrefWidth(110);

        TableColumn<Student, String> errorsCol = new TableColumn<>("Errors to Fix");
        errorsCol.setCellValueFactory(c -> new SimpleStringProperty(allErrors(c.getValue())));
        errorsCol.setCellFactory(c -> new TableCell<>() {
            @Override
            public void updateItem(String item, boolean empty) {
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
        errorsCol.setPrefWidth(220);

        @SuppressWarnings("unchecked")
        var columns = new TableColumn[]{adm, name, gender, cls, stream, boarding, phone, parent, status,
                academicYear, yearOfAdmission, errorsCol};
        table.getColumns().addAll(columns);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setItems(staged);
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
            cell.setTooltip(new Tooltip(String.join("\n", messages)));
        }
    }

    /**
     * Keep the original imported row in sync with inline edits so revalidation
     * always inspects the values currently shown in the table.
     */
    private void syncRaw(Student student, String key, String value) {
        int index = staged.indexOf(student);
        if (index >= 0 && index < rawRows.size()) {
            rawRows.get(index).put(key, value == null ? "" : value);
        }
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

    private void revalidate() {
        errorsByStudent.clear();
        for (int i = 0; i < staged.size(); i++) {
            Student student = staged.get(i);
            List<Student> others = new ArrayList<>(staged);
            others.remove(student);
            List<String> messages = importService.validateRow(rawRows.get(i), student, others);
            Map<String, List<String>> byField = new LinkedHashMap<>();
            for (String message : messages) {
                byField.computeIfAbsent(fieldForMessage(message), k -> new ArrayList<>()).add(message);
            }
            errorsByStudent.put(student, byField);
        }
        updateSummary();
        table.refresh();
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

    private String allErrors(Student student) {
        Map<String, List<String>> byField = errorsByStudent.get(student);
        if (byField == null) {
            return "";
        }
        return byField.values().stream()
                .flatMap(List::stream)
                .distinct()
                .collect(Collectors.joining("; "));
    }

    private void updateSummary() {
        long valid = errorsByStudent.values().stream().filter(Map::isEmpty).count();
        long needingFix = staged.size() - valid;
        summaryLabel.setText("Rows staged: " + staged.size()
                + "   |   Valid: " + valid
                + "   |   Need fixing: " + needingFix
                + (imported > 0 ? "   |   Saved so far: " + imported : ""));
        summaryLabel.setAlignment(Pos.CENTER_LEFT);
    }

    private void commitValid() {
        revalidate();
        List<Student> valid = new ArrayList<>();
        for (Student student : staged) {
            if (errorsByStudent.getOrDefault(student, Map.of()).isEmpty()) {
                valid.add(student);
            }
        }
        int savedNow = 0;
        for (Student student : valid) {
            if (importService.commitStudent(student).isEmpty()) {
                imported++;
                savedNow++;
            }
        }
        if (savedNow > 0) {
            PersistenceService.getInstance().saveAll();
        }
        if (!valid.isEmpty()) {
            removeStaged(valid);
        }
        if (staged.isEmpty()) {
            setResult(SAVE_TYPE);
            hide();
            return;
        }
        revalidate();
        if (savedNow > 0) {
            AlertUtil.info("Import progress",
                    "Saved " + savedNow + " valid row(s) (total saved: " + imported + "). "
                            + staged.size() + " row(s) still have errors — correct them in the table, then click Save Valid Rows again.");
        } else {
            AlertUtil.warn("Nothing saved",
                    staged.size() + " row(s) still have errors. Correct the highlighted cells in the table, then click Save Valid Rows.");
        }
    }

    private void removeStaged(List<Student> valid) {
        Set<String> ids = valid.stream().map(Student::getId).collect(Collectors.toSet());
        for (int i = staged.size() - 1; i >= 0; i--) {
            if (ids.contains(staged.get(i).getId())) {
                staged.remove(i);
                rawRows.remove(i);
            }
        }
    }

    public int getImportedCount() {
        return imported;
    }

    public int getRemainingCount() {
        return staged.size();
    }
}
