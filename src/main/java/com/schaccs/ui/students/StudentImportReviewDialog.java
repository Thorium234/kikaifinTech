package com.schaccs.ui.students;

import com.schaccs.enums.BoardingStatus;
import com.schaccs.enums.StudentStatus;
import com.schaccs.model.student.Student;
import com.schaccs.repository.PersistenceService;
import com.schaccs.service.importer.StudentImportService;
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
import javafx.scene.control.Tooltip;
import javafx.scene.control.cell.ComboBoxTableCell;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Student import with the Clean Data split: every row that validates cleanly is
 * committed immediately when the dialog opens; rows that carry mistakes
 * (duplicate admission number, missing details, bad formats, ...) are held in
 * the Clean Data table. A held row is committed automatically the moment its
 * mistakes are cleared by editing the red cells, and it then leaves the table.
 */
public class StudentImportReviewDialog extends Dialog<ButtonType> {

    private static final ButtonType CLOSE_TYPE =
            new ButtonType("Close", ButtonBar.ButtonData.CANCEL_CLOSE);

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

        setTitle("Import Students - Clean Data");
        initModality(Modality.APPLICATION_MODAL);
        getDialogPane().getButtonTypes().addAll(CLOSE_TYPE);

        importValidRowsImmediately();
        buildTable();
        updateHeader();
        updateSummary();

        summaryLabel.getStyleClass().add("muted");
        summaryLabel.setWrapText(true);
        summaryLabel.setMaxWidth(Double.MAX_VALUE);
        Label hint = new Label("Clean Data: the rows below carry mistakes and were held back. "
                + "Double-click a red cell to edit it; a row is imported automatically the moment its mistakes "
                + "are cleared and it leaves this list. Already-correct rows were committed immediately.");
        hint.getStyleClass().add("muted");
        hint.setWrapText(true);

        HBox.setHgrow(summaryLabel, Priority.ALWAYS);
        HBox toolbar = new HBox(10, summaryLabel);
        toolbar.setAlignment(Pos.CENTER_LEFT);

        VBox content = new VBox(10, toolbar, table, hint);
        content.setPadding(new Insets(8));
        getDialogPane().setContent(content);
        getDialogPane().setPrefSize(1480, 620);
    }

    /**
     * Commit every staged row that has no validation errors right away, leaving
     * only the rows that need cleaning in the table.
     */
    private void importValidRowsImmediately() {
        revalidate();
        List<Student> valid = staged.stream()
                .filter(s -> errorsByStudent.getOrDefault(s, Map.of()).isEmpty())
                .collect(Collectors.toList());
        for (Student student : valid) {
            List<String> errors = importService.commitStudent(student);
            if (errors.isEmpty()) {
                imported++;
                removeRow(student);
            } else {
                errorsByStudent.get(student).computeIfAbsent("row", k -> new ArrayList<>()).addAll(errors);
            }
        }
        if (!valid.isEmpty()) {
            PersistenceService.getInstance().saveAll();
        }
        revalidate();
        updateSummary();
        table.refresh();
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
            afterEdit(e.getRowValue());
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

    private void afterEdit(Student student) {
        revalidate();
        tryImportStudent(student);
    }

    /**
     * Import the edited row the moment it no longer has any errors, then leave
     * the list. Otherwise just refresh the highlights and summary.
     */
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
            removeRow(student);
            PersistenceService.getInstance().saveAll();
            revalidate();
        }
        updateSummary();
        table.refresh();
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

    private void updateHeader() {
        setHeaderText("Clean Data: " + staged.size() + " row(s) still have mistakes"
                + (imported > 0 ? " (" + imported + " valid row(s) were imported automatically)" : "")
                + ". Edit the red cells — a row imports the moment its mistakes are cleared.");
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
        summaryLabel.setText("Imported automatically: " + imported
                + "   |   Clean Data: " + staged.size()
                + (staged.isEmpty() ? "   |   Nothing left to clean." : "   |   Fix a row and it imports immediately."));
        summaryLabel.setAlignment(Pos.CENTER_LEFT);
    }

    private void removeRow(Student student) {
        int index = staged.indexOf(student);
        if (index >= 0) {
            staged.remove(index);
            if (index < rawRows.size()) {
                rawRows.remove(index);
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
