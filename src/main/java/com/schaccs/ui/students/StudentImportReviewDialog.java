package com.schaccs.ui.students;

import com.schaccs.enums.BoardingStatus;
import com.schaccs.enums.StudentStatus;
import com.schaccs.model.student.Student;
import com.schaccs.repository.PersistenceService;
import com.schaccs.service.importer.StudentImportService;
import com.schaccs.service.school.SchoolCustomService;
import com.schaccs.store.SchoolCustomStore;
import com.schaccs.util.AlertUtil;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
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
import java.time.LocalDate;
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
    private static final String SCOPE_FILL_BLANKS = "Fill blank cells only";
    private static final String SCOPE_OVERWRITE = "Overwrite all rows";

    private final StudentImportService importService;
    private final SchoolCustomService schoolCustomService = new SchoolCustomService();
    private final ObservableList<Student> staged;
    private final List<Map<String, String>> rawRows;
    private final Map<Student, Map<String, List<String>>> errorsByStudent = new HashMap<>();

    private final TableView<Student> table = new TableView<>();
    private final Label summaryLabel = new Label();

    // Batch context: the year is picked first, then the class (Form/Grade).
    private final Spinner<Integer> contextYear =
            new Spinner<>(1990, 2100, LocalDate.now().getYear());
    private final ComboBox<String> contextClassType = new ComboBox<>();
    private final ComboBox<Integer> contextLevel = new ComboBox<>();
    private final ComboBox<String> contextStream = new ComboBox<>();
    private final ComboBox<String> contextScope =
            new ComboBox<>(FXCollections.observableArrayList(SCOPE_FILL_BLANKS, SCOPE_OVERWRITE));
    private final Button applyContextButton = new Button("Apply to rows");
    private final Label contextStatusLabel = new Label();

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

        VBox content = new VBox(10, buildBatchContextPanel(), toolbar, table, hint);
        content.setPadding(new Insets(8));
        getDialogPane().setContent(content);
        getDialogPane().setPrefSize(1480, 660);
    }

    /**
     * The batch panel: specify the academic year first, then the class this
     * intake belongs to — Form 1-6 or Grade 8-12 — plus an optional stream.
     * Applying fills the staged rows (blanks by default) and registers any
     * newly named class/stream so dropdowns and reports pick them up.
     */
    private VBox buildBatchContextPanel() {
        contextYear.setEditable(true);
        contextYear.setPrefWidth(100);

        contextClassType.getItems().addAll("Form", "Grade");
        contextClassType.setPromptText("Form / Grade");
        contextClassType.setPrefWidth(130);
        contextLevel.setPromptText("Level");
        contextLevel.setPrefWidth(90);
        contextLevel.setDisable(true);
        contextClassType.setOnAction(e -> {
            String type = contextClassType.getValue();
            contextLevel.getItems().setAll(classLevels(type));
            contextLevel.getSelectionModel().clearSelection();
            contextLevel.setDisable(type == null);
        });

        contextStream.setEditable(true);
        contextStream.setPromptText("Stream (optional)");
        contextStream.setPrefWidth(130);
        ObservableList<String> streamItems = FXCollections.observableArrayList();
        if (!SchoolCustomStore.getInstance().getStreams().isEmpty()) {
            SchoolCustomStore.getInstance().getStreams().forEach(s -> streamItems.add(s.getName()));
        } else {
            streamItems.addAll("A", "B", "C");
        }
        contextStream.setItems(streamItems);

        contextScope.getSelectionModel().selectFirst();
        contextScope.setPrefWidth(170);
        applyContextButton.getStyleClass().add("primary");
        applyContextButton.setOnAction(e -> applyBatchContext());

        contextStatusLabel.getStyleClass().add("muted");
        contextStatusLabel.setWrapText(true);
        contextStatusLabel.setText("Optional: pick the academic year, then the class this intake belongs to "
                + "(Form 1-6 or Grade 8-12) and an optional stream, then Apply. "
                + "Blank cells are filled (or every row when you choose Overwrite), the class is created in "
                + "the school registry, and rows that become valid import immediately.");

        HBox row = new HBox(8,
                new Label("Academic Year"), contextYear,
                new Label("Class"), contextClassType, contextLevel,
                new Label("Stream"), contextStream,
                contextScope, applyContextButton);
        row.setAlignment(Pos.CENTER_LEFT);

        VBox panel = new VBox(4, row, contextStatusLabel);
        panel.getStyleClass().add("card");
        panel.setPadding(new Insets(10));
        return panel;
    }

    private static List<Integer> classLevels(String type) {
        if ("Form".equals(type)) {
            return List.of(1, 2, 3, 4, 5, 6);
        }
        if ("Grade".equals(type)) {
            return List.of(8, 9, 10, 11, 12);
        }
        return List.of();
    }

    /**
     * Apply the chosen year/class/stream to every staged row, register the
     * class and stream in the school registry, then commit whatever rows just
     * became valid.
     */
    private void applyBatchContext() {
        Integer year = contextYear.getValue();
        String type = contextClassType.getValue();
        Integer level = contextLevel.getValue();
        String stream = contextStream.getValue();

        if (year == null) {
            AlertUtil.warn("No academic year",
                    "Pick the academic year this batch belongs to before applying.");
            return;
        }
        boolean typeChosen = type != null;
        boolean levelChosen = level != null;
        if (typeChosen != levelChosen) {
            AlertUtil.warn("Class incomplete",
                    "Choose both the class type and its level - for example \"Grade 10\" or \"Form 3\".");
            return;
        }

        String classLabel = typeChosen ? type + " " + level : null;
        StudentImportService.ImportContext context = new StudentImportService.ImportContext(
                year, classLabel, stream, SCOPE_OVERWRITE.equals(contextScope.getValue()));

        int changed = importService.applyImportContext(rawRows, staged, context);
        if (classLabel != null) {
            schoolCustomService.ensureFormClass(classLabel);
        }
        if (stream != null && !stream.isBlank()) {
            schoolCustomService.ensureStream(stream);
        }

        int before = imported;
        importValidRowsImmediately();
        updateHeader();
        updateSummary();
        table.refresh();

        contextStatusLabel.setText(context.describe()
                + " applied to " + changed + " row(s)"
                + (imported > before ? " - " + (imported - before) + " row(s) imported." : "."));
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

        TableColumn<Student, String> cls = new TableColumn<>("Class (Form/Grade)");
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

    /**
     * The rows still held for cleaning when the dialog closes. Used to persist
     * Clean Data so a later session can continue fixing them.
     */
    public List<Map<String, String>> getHeldRawRows() {
        return new ArrayList<>(rawRows);
    }
}
