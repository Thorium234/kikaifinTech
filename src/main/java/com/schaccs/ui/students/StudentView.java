package com.schaccs.ui.students;

import com.schaccs.enums.BoardingStatus;
import com.schaccs.enums.StudentStatus;
import com.schaccs.model.student.Student;
import com.schaccs.repository.PersistenceService;
import com.schaccs.service.Services;
import com.schaccs.service.fee.FeeCalculationService;
import com.schaccs.service.export.SpreadsheetExportService;
import com.schaccs.service.importer.StudentImportService;
import com.schaccs.service.student.StudentService;
import com.schaccs.ui.component.SearchBar;
import com.schaccs.ui.layout.MainLayout;
import com.schaccs.util.AlertUtil;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class StudentView extends VBox implements MainLayout.Refreshable {

    private final StudentService studentService = Services.getInstance().student();
    private final FeeCalculationService feeService = Services.getInstance().feeCalculation();
    private final StudentImportService importService = new StudentImportService();
    private final SpreadsheetExportService exportService = new SpreadsheetExportService();

    private final TableView<Student> table = new TableView<>();
    private final FilteredList<Student> filtered;
    private final SearchBar searchBar = new SearchBar("Search by name, admission no, class…");
    private final StackPane contentStack = new StackPane();

    private final TextField admField = new TextField();
    private final TextField nameField = new TextField();
    private final ComboBox<String> classBox = new ComboBox<>();
    private final ComboBox<String> genderBox = new ComboBox<>();
    private final ComboBox<BoardingStatus> boardingBox = new ComboBox<>();
    private final TextField phoneField = new TextField();

    private final VBox listPanel = new VBox(10);
    private final VBox formPanel = new VBox(10);
    private final Label formTitle = new Label("Add Student");
    private final Button toggleFormBtn = new Button("Add Student");
    private final Button toggleListBtn = new Button("Back to List");

    private Student editing;

    public StudentView() {
        setSpacing(14);
        setPadding(new Insets(4));

        filtered = new FilteredList<>(studentService.getAll(), s -> true);
        searchBar.textProperty().addListener((obs, o, q) ->
                filtered.setPredicate(s -> s.matchesSearch(q)));

        Label heading = new Label("Student Registry");
        heading.getStyleClass().add("section-title");

        buildListPanel();
        buildFormPanel();

        contentStack.getChildren().addAll(formPanel, listPanel);
        VBox.setVgrow(contentStack, Priority.ALWAYS);

        getChildren().addAll(heading, contentStack);
        showList();
    }

    private void buildListPanel() {
        Label badge = new Label("Student Registry");
        badge.getStyleClass().add("student-header-badge");
        Label sub = new Label("Browse, search, and select a student to edit. Import students from CSV/XLSX.");
        sub.getStyleClass().add("muted");

        Button importBtn = new Button("Import CSV/XLSX");
        importBtn.getStyleClass().add("secondary-button");
        importBtn.setOnAction(e -> importStudents());
        Button exportBtn = new Button("Export");
        exportBtn.getStyleClass().add("secondary-button");
        exportBtn.setOnAction(e -> exportStudents());
        Button templateBtn = new Button("Template");
        templateBtn.getStyleClass().add("secondary-button");
        templateBtn.setOnAction(e -> downloadTemplate());

        toggleFormBtn.getStyleClass().add("primary-button");
        toggleFormBtn.setOnAction(e -> { clearForm(); showForm(); });

        HBox toolbar = new HBox(10, searchBar, toggleFormBtn, importBtn, exportBtn, templateBtn);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(searchBar, Priority.ALWAYS);

        setupTable();

        VBox card = new VBox(10, badge, sub, toolbar, table);
        card.getStyleClass().addAll("card", "student-table-card");
        VBox.setVgrow(table, Priority.ALWAYS);

        listPanel.getChildren().add(card);
    }

    private void buildFormPanel() {
        formTitle.getStyleClass().add("section-title");
        toggleListBtn.getStyleClass().add("secondary-button");
        toggleListBtn.setOnAction(e -> showList());

        classBox.getItems().addAll(
                "Form 1 A", "Form 1 B", "Form 1 C",
                "Form 2 A", "Form 2 B", "Form 2 C",
                "Form 3 A", "Form 3 B", "Form 3 C",
                "Form 4 A", "Form 4 B", "Form 4 C"
        );
        classBox.setPromptText("Select class/stream");
        genderBox.getItems().addAll("Male", "Female");
        genderBox.setValue("Male");
        boardingBox.getItems().addAll(BoardingStatus.values());
        boardingBox.setValue(BoardingStatus.BOARDING);

        admField.setPromptText("Admission number (e.g. 2026/009)");
        nameField.setPromptText("Full name");
        phoneField.setPromptText("Phone (e.g. 0712345678)");

        Button saveBtn = new Button("Save Student");
        saveBtn.getStyleClass().add("success-button");
        saveBtn.setOnAction(e -> save());

        Button cancelBtn = new Button("Cancel");
        cancelBtn.getStyleClass().add("secondary-button");
        cancelBtn.setOnAction(e -> showList());

        HBox actions = new HBox(10, saveBtn, cancelBtn, toggleListBtn);
        actions.setAlignment(Pos.CENTER_LEFT);

        VBox fields = new VBox(12,
                labeled("Admission No", admField),
                labeled("Full Name", nameField),
                labeled("Class / Stream", classBox),
                labeled("Gender", genderBox),
                labeled("Boarding Status", boardingBox),
                labeled("Phone", phoneField)
        );
        fields.setPadding(new Insets(10, 0, 0, 0));

        VBox card = new VBox(14, formTitle, toggleListBtn, fields, actions);
        card.getStyleClass().add("card");
        card.setMaxWidth(500);

        formPanel.getChildren().add(card);
    }

    private VBox labeled(String label, javafx.scene.Node field) {
        Label lbl = new Label(label);
        lbl.getStyleClass().add("student-form-label");
        if (field instanceof javafx.scene.layout.Region r) r.setMaxWidth(Double.MAX_VALUE);
        VBox box = new VBox(4, lbl, field);
        box.getStyleClass().add("student-field-box");
        return box;
    }

    private void setupTable() {
        TableColumn<Student, String> adm = new TableColumn<>("Adm No");
        adm.setCellValueFactory(c -> c.getValue().admissionNumberProperty());
        adm.setPrefWidth(100);

        TableColumn<Student, String> name = new TableColumn<>("Name");
        name.setCellValueFactory(c -> c.getValue().nameProperty());
        name.setPrefWidth(200);

        TableColumn<Student, String> cls = new TableColumn<>("Class");
        cls.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getClassLabel()));
        cls.setPrefWidth(120);

        TableColumn<Student, String> board = new TableColumn<>("Boarding");
        board.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getBoardingStatus() != null ? c.getValue().getBoardingStatus().getDisplayName() : ""));
        board.setPrefWidth(90);

        TableColumn<Student, String> phone = new TableColumn<>("Phone");
        phone.setCellValueFactory(c -> c.getValue().phoneProperty());
        phone.setPrefWidth(130);

        TableColumn<Student, String> st = new TableColumn<>("Status");
        st.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getStatus() != null ? c.getValue().getStatus().getDisplayName() : ""));
        st.setPrefWidth(90);
        st.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().removeAll("student-status-active", "student-status-inactive");
                if (empty || item == null) { setText(null); return; }
                setText(item);
                Student s = getTableRow() != null ? (Student) getTableRow().getItem() : null;
                if (s != null && s.getStatus() != null) {
                    switch (s.getStatus()) {
                        case ACTIVE -> getStyleClass().add("student-status-active");
                        case INACTIVE -> getStyleClass().add("student-status-inactive");
                    }
                }
            }
        });

        table.getColumns().addAll(adm, name, cls, board, phone, st);
        table.setItems(filtered);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setRowFactory(tv -> new TableRow<>() {
            @Override
            protected void updateItem(Student item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().removeAll("student-row-active", "student-row-inactive");
                if (!empty && item != null && item.getStatus() != null) {
                    switch (item.getStatus()) {
                        case ACTIVE -> getStyleClass().add("student-row-active");
                        case INACTIVE -> getStyleClass().add("student-row-inactive");
                    }
                }
            }
        });
        table.getSelectionModel().selectedItemProperty().addListener((obs, o, s) -> {
            if (s != null) {
                loadForm(s);
                showForm();
            }
        });
    }

    private void clearForm() {
        editing = null;
        admField.clear();
        nameField.clear();
        classBox.setValue(null);
        genderBox.setValue("Male");
        boardingBox.setValue(BoardingStatus.BOARDING);
        phoneField.clear();
        admField.setDisable(false);
        formTitle.setText("Add Student");
        toggleListBtn.setText("Back to List");
    }

    private void loadForm(Student s) {
        editing = s;
        admField.setText(s.getAdmissionNumber());
        admField.setDisable(true);
        nameField.setText(s.getName());
        classBox.setValue(s.getClassLabel());
        genderBox.setValue(s.getGender() != null ? s.getGender() : "Male");
        boardingBox.setValue(s.getBoardingStatus() != null ? s.getBoardingStatus() : BoardingStatus.BOARDING);
        phoneField.setText(s.getPhone());
        formTitle.setText("Edit Student");
        toggleListBtn.setText("Back to List");
    }

    private void save() {
        String adm = admField.getText().trim();
        String name = nameField.getText().trim();
        String cls = classBox.getValue();
        String gender = genderBox.getValue();
        BoardingStatus boarding = boardingBox.getValue();
        String phone = phoneField.getText().trim();

        if (adm.isEmpty() || name.isEmpty() || cls == null || gender == null || boarding == null) {
            AlertUtil.warn("Missing fields", "Admission number, name, class, gender, and boarding status are required.");
            return;
        }

        String formClass = cls.contains(" ") ? cls.substring(0, cls.lastIndexOf(' ')) : cls;
        String stream = cls.contains(" ") ? cls.substring(cls.lastIndexOf(' ') + 1) : "";

        if (editing == null) {
            Student s = new Student(adm, name, formClass, stream, boarding, phone);
            s.setGender(gender);
            s.setStatus(StudentStatus.ACTIVE);
            List<String> errors = studentService.addStudent(s);
            if (!errors.isEmpty()) {
                AlertUtil.warn("Validation", String.join("\n", errors));
                return;
            }
            feeService.chargeAnnualFees(s);
            PersistenceService.getInstance().saveAll();
            AlertUtil.info("Saved", "Student " + adm + " added and fees charged.");
        } else {
            editing.setAdmissionNumber(adm);
            editing.setName(name);
            editing.setFormClass(formClass);
            editing.setStream(stream);
            editing.setGender(gender);
            editing.setBoardingStatus(boarding);
            editing.setPhone(phone);
            List<String> errors = studentService.updateStudent(editing);
            if (!errors.isEmpty()) {
                AlertUtil.warn("Validation", String.join("\n", errors));
                return;
            }
            PersistenceService.getInstance().saveAll();
            AlertUtil.info("Saved", "Student details updated.");
        }
        table.refresh();
        showList();
    }

    private void exportStudents() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export Students");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("CSV files", "*.csv"),
                new FileChooser.ExtensionFilter("Excel files", "*.xlsx"));
        chooser.setInitialFileName("students-export.csv");
        File file = chooser.showSaveDialog(getScene() != null ? getScene().getWindow() : null);
        if (file == null) return;
        try {
            List<String> headers = List.of("Admission Number", "Full Name", "Gender", "Class", "Boarding Status", "Phone", "Status");
            List<List<String>> rows = studentService.getAll().stream().map(s -> List.of(
                    safe(s.getAdmissionNumber()), safe(s.getName()), safe(s.getGender()),
                    s.getClassLabel(),
                    s.getBoardingStatus() != null ? s.getBoardingStatus().getDisplayName() : "",
                    safe(s.getPhone()),
                    s.getStatus() != null ? s.getStatus().getDisplayName() : "")).toList();
            exportService.export(file.toPath(), "Students", headers, rows);
            AlertUtil.info("Export complete", "Students exported to:\n" + file.getAbsolutePath());
        } catch (IOException e) {
            AlertUtil.error("Export failed", e.getMessage());
        }
    }

    private void downloadTemplate() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save Student Import Template");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("CSV files", "*.csv"),
                new FileChooser.ExtensionFilter("Excel files", "*.xlsx"));
        chooser.setInitialFileName("student-import-template.xlsx");
        File file = chooser.showSaveDialog(getScene() != null ? getScene().getWindow() : null);
        if (file == null) return;
        try {
            new com.schaccs.service.export.StudentTemplateService(exportService).generateTemplate(file.toPath());
            AlertUtil.info("Template saved", "Template saved to:\n" + file.getAbsolutePath());
        } catch (IOException e) {
            AlertUtil.error("Template failed", e.getMessage());
        }
    }

    private void importStudents() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Import Students");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Spreadsheet files", "*.csv", "*.xlsx"),
                new FileChooser.ExtensionFilter("CSV files", "*.csv"),
                new FileChooser.ExtensionFilter("Excel files", "*.xlsx"));
        File file = chooser.showOpenDialog(getScene() != null ? getScene().getWindow() : null);
        if (file == null) return;
        StudentImportService.ImportResult preview = importService.previewFile(file.toPath());
        if (!showImportPreviewDialog(preview)) return;
        StudentImportService.ImportResult result = importService.importFile(file.toPath());
        table.refresh();
        if (result.getImported() > 0) AlertUtil.info("Import complete", "Imported " + result.getImported() + " students.");
        else AlertUtil.warn("Import finished", buildImportMessage(result, false));
    }

    private boolean showImportPreviewDialog(StudentImportService.ImportResult result) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Import Preview");
        dialog.setHeaderText("Review student import before commit");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, ButtonType.OK);
        Label summary = new Label(buildImportMessage(result, true));
        summary.setWrapText(true);
        TableView<String> warningTable = new TableView<>();
        TableColumn<String, String> warningColumn = new TableColumn<>("Validation details");
        warningColumn.setCellValueFactory(c -> new SimpleStringProperty(c.getValue()));
        warningTable.getColumns().add(warningColumn);
        warningTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        warningTable.getItems().setAll(result.getWarnings());
        warningTable.setPrefHeight(260);
        VBox content = new VBox(10, summary, warningTable);
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().lookupButton(ButtonType.OK).setDisable(result.getImported() <= 0);
        return dialog.showAndWait().filter(ButtonType.OK::equals).isPresent();
    }

    private String buildImportMessage(StudentImportService.ImportResult result, boolean preview) {
        StringBuilder msg = new StringBuilder();
        msg.append(preview ? "Ready to import: " : "Imported: ").append(result.getImported()).append("\n");
        msg.append("Skipped: ").append(result.getSkipped());
        if (result.hasWarnings()) {
            msg.append("\n\nDetails:\n");
            result.getWarnings().stream().limit(12).forEach(w -> msg.append("- ").append(w).append("\n"));
            if (result.getWarnings().size() > 12) msg.append("...and ").append(result.getWarnings().size() - 12).append(" more");
        }
        return msg.toString().trim();
    }

    private void showList() {
        clearForm();
        table.getSelectionModel().clearSelection();
        listPanel.toFront();
    }

    private void showForm() {
        formPanel.toFront();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    @Override
    public void refresh() {
        table.refresh();
    }
}
