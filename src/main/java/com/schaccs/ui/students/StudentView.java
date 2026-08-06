package com.schaccs.ui.students;

import com.schaccs.enums.AcademicTerm;
import com.schaccs.enums.BoardingStatus;
import com.schaccs.enums.StudentStatus;
import com.schaccs.model.student.Student;
import com.schaccs.model.student.StudentFeeLedger;
import com.schaccs.repository.PersistenceService;
import com.schaccs.service.Services;
import com.schaccs.service.fee.FeeCalculationService;
import com.schaccs.service.export.SpreadsheetExportService;
import com.schaccs.service.importer.StudentImportService;
import com.schaccs.service.student.StudentService;
import com.schaccs.service.student.StudentTransitionService;
import com.schaccs.store.SchoolCustomStore;
import com.schaccs.store.StudentStore;
import com.schaccs.ui.component.SearchBar;
import com.schaccs.ui.layout.MainLayout;
import com.schaccs.util.AlertUtil;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class StudentView extends VBox implements MainLayout.Refreshable {

    private final StudentService studentService = Services.getInstance().student();
    private final FeeCalculationService feeService = Services.getInstance().feeCalculation();
    private final StudentImportService importService = new StudentImportService();
    private final SpreadsheetExportService exportService = new SpreadsheetExportService();

    private final TableView<Student> table = new TableView<>();
    private final FilteredList<Student> filtered;
    private final SearchBar searchBar = new SearchBar("Search by name, admission no, class…");
    private final TabPane tabPane = new TabPane();
    private final Tab listTab = new Tab("Students List");
    private final Tab formTab = new Tab("Add Student");

    private final TextField admField = new TextField();
    private final TextField nameField = new TextField();
    private final ComboBox<String> classBox = new ComboBox<>();
    private final ComboBox<String> streamBox = new ComboBox<>();
    private final ComboBox<String> genderBox = new ComboBox<>();
    private final ComboBox<BoardingStatus> boardingBox = new ComboBox<>();
    private final TextField phoneField = new TextField();
    private final TextField parentNameField = new TextField();
    private final Label feeStructureLabel = new Label();

    private Student editing;

    public StudentView() {
        setSpacing(14);
        setPadding(new Insets(4));

        filtered = new FilteredList<>(studentService.getAll(), s -> true);
        searchBar.textProperty().addListener((obs, o, q) ->
                filtered.setPredicate(s -> s.matchesSearch(q)));

        Label heading = new Label("Student Registry");
        heading.getStyleClass().add("section-title");

        buildListTab();
        buildFormTab();
        tabPane.getTabs().addAll(listTab, formTab);
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        VBox.setVgrow(tabPane, Priority.ALWAYS);

        getChildren().addAll(heading, tabPane);
    }

    private void buildListTab() {
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

        Button addBtn = new Button("Add Student");
        addBtn.getStyleClass().add("primary-button");
        addBtn.setOnAction(e -> { clearForm(); switchToForm(); });

        HBox toolbar = new HBox(10, searchBar, addBtn, importBtn, exportBtn, templateBtn);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(searchBar, Priority.ALWAYS);

        setupTable();

        VBox card = new VBox(10, badge, sub, toolbar, table);
        card.getStyleClass().addAll("card", "student-table-card");
        VBox.setVgrow(table, Priority.ALWAYS);

        listTab.setContent(card);
    }

    private void buildFormTab() {
        populateDropdowns();
        classBox.setPromptText("Select class");
        streamBox.setPromptText("Select stream (optional)");
        genderBox.getItems().addAll("Male", "Female");
        genderBox.setValue("Male");
        boardingBox.getItems().addAll(BoardingStatus.values());
        boardingBox.setValue(BoardingStatus.BOARDING);

        admField.setPromptText("Admission number (e.g. 2026/009)");
        nameField.setPromptText("Full name");
        phoneField.setPromptText("Phone (e.g. 0712345678)");
        parentNameField.setPromptText("Parent / Guardian name");

        Button saveBtn = new Button("Save Student");
        saveBtn.getStyleClass().add("success-button");
        saveBtn.setOnAction(e -> save());

        Button clearBtn = new Button("Clear");
        clearBtn.getStyleClass().add("secondary-button");
        clearBtn.setOnAction(e -> clearForm());

        HBox actions = new HBox(10, saveBtn, clearBtn);
        actions.setAlignment(Pos.CENTER_LEFT);
        actions.setPadding(new Insets(12, 0, 0, 0));

        GridPane grid = new GridPane();
        grid.setHgap(20);
        grid.setVgap(4);
        ColumnConstraints cc = new ColumnConstraints();
        cc.setFillWidth(true);
        cc.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(cc, cc);

        // Column 1 — Student details
        grid.add(labeled("Admission No", admField), 0, 0);
        grid.add(labeled("Full Name", nameField), 0, 1);
        grid.add(labeled("Class", classBox), 0, 2);
        grid.add(labeled("Stream", streamBox), 0, 3);
        grid.add(labeled("Gender", genderBox), 0, 4);
        grid.add(labeled("Boarding Status", boardingBox), 0, 5);

        // Column 2 — Contact & Parent/Guardian
        grid.add(labeled("Phone", phoneField), 1, 0);
        grid.add(labeled("Parent / Guardian Name", parentNameField), 1, 1);

        feeStructureLabel.getStyleClass().add("muted");
        boardingBox.setOnAction(e -> updateFeeStructureLabel());
        updateFeeStructureLabel();

        VBox card = new VBox(14, grid, feeStructureLabel, actions);
        card.getStyleClass().add("card");
        card.setMaxWidth(700);

        ScrollPane formScroll = new ScrollPane(card);
        formScroll.setFitToWidth(true);
        formScroll.setFitToHeight(true);
        formScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        formScroll.getStyleClass().add("content-scroll");

        formTab.setContent(formScroll);
    }

    private void populateDropdowns() {
        SchoolCustomStore store = SchoolCustomStore.getInstance();

        classBox.getItems().clear();
        if (!store.getFormClasses().isEmpty()) {
            store.getFormClasses().forEach(fc -> classBox.getItems().add(fc.getName()));
        } else {
            classBox.getItems().addAll("Form 1", "Form 2", "Form 3", "Form 4");
        }

        streamBox.getItems().clear();
        if (!store.getStreams().isEmpty()) {
            store.getStreams().forEach(s -> streamBox.getItems().add(s.getName()));
        } else {
            streamBox.getItems().addAll("A", "B", "C");
        }
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

        TableColumn<Student, String> parentCol = new TableColumn<>("Parent/Guardian");
        parentCol.setCellValueFactory(c -> c.getValue().parentNameProperty());
        parentCol.setPrefWidth(150);

        TableColumn<Student, String> st = new TableColumn<>("Status");
        st.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getStatus() != null ? c.getValue().getStatus().getDisplayName() : ""));
        st.setPrefWidth(90);

        @SuppressWarnings("unchecked")
        var columns1 = new TableColumn[]{adm, name, cls, board, phone, parentCol, st};
        table.getColumns().addAll(columns1);

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
                switchToForm();
            }
        });
    }

    private void updateFeeStructureLabel() {
        BoardingStatus bs = boardingBox.getValue();
        if (bs == null) return;
        int year = com.schaccs.config.AppConfig.getInstance().getAcademicYear();
        com.schaccs.store.FeeStructureStore fsStore = com.schaccs.store.FeeStructureStore.getInstance();
        fsStore.findStructure(year, bs).ifPresentOrElse(
                fs -> {
                    java.math.BigDecimal termTotal = fs.totalForTerm(AcademicTerm.TERM_1);
                    feeStructureLabel.setText("Fee structure: " + fs.getName()
                            + " | Term 1 total: KES " + com.schaccs.util.CurrencyUtil.formatPlain(termTotal));
                },
                () -> feeStructureLabel.setText("No fee structure found for " + bs.getDisplayName() + " in " + year)
        );
    }

    private void clearForm() {
        editing = null;
        admField.clear();
        nameField.clear();
        classBox.setValue(null);
        streamBox.setValue(null);
        genderBox.setValue("Male");
        boardingBox.setValue(BoardingStatus.BOARDING);
        phoneField.clear();
        parentNameField.clear();
        admField.setDisable(false);
        formTab.setText("Add Student");
        updateFeeStructureLabel();
    }

    private void loadForm(Student s) {
        editing = s;
        admField.setText(s.getAdmissionNumber());
        admField.setDisable(true);
        nameField.setText(s.getName());
        classBox.setValue(s.getFormClass());
        streamBox.setValue(s.getStream());
        genderBox.setValue(s.getGender() != null ? s.getGender() : "Male");
        boardingBox.setValue(s.getBoardingStatus() != null ? s.getBoardingStatus() : BoardingStatus.BOARDING);
        phoneField.setText(s.getPhone());
        parentNameField.setText(s.getParentName());
        formTab.setText("Edit Student");
        updateFeeStructureLabel();
    }

    private void save() {
        String adm = admField.getText().trim();
        String name = nameField.getText().trim();
        String formClass = classBox.getValue();
        String stream = streamBox.getValue();
        String gender = genderBox.getValue();
        BoardingStatus boarding = boardingBox.getValue();
        String phone = phoneField.getText().trim();
        String parentName = parentNameField.getText().trim();

        if (adm.isEmpty() || name.isEmpty() || formClass == null || gender == null || boarding == null) {
            AlertUtil.warn("Missing fields", "Admission number, name, class, gender, and boarding status are required.");
            return;
        }

        if (editing == null) {
            Student s = new Student(adm, name, formClass, stream, boarding, phone);
            s.setGender(gender);
            s.setStatus(StudentStatus.ACTIVE);
            s.setAcademicYear(com.schaccs.config.AppConfig.getInstance().getAcademicYear());
            s.setParentName(parentName);
            List<String> errors = studentService.addStudent(s);
            if (!errors.isEmpty()) {
                AlertUtil.warn("Validation", String.join("\n", errors));
                return;
            }
            feeService.chargeTermFees(s, AcademicTerm.TERM_1);
            PersistenceService.getInstance().saveAll();
            AlertUtil.info("Saved", "Student " + adm + " added with Term 1 fees charged.");
        } else {
            BoardingStatus previousStatus = editing.getBoardingStatus();
            editing.setAdmissionNumber(adm);
            editing.setName(name);
            editing.setFormClass(formClass);
            editing.setStream(stream);
            editing.setGender(gender);
            editing.setPhone(phone);
            editing.setParentName(parentName);
            if (previousStatus != boarding) {
                StudentFeeLedger ledger = StudentStore.getInstance().getLedger(editing.getId());
                new StudentTransitionService().apply(editing, boarding, ledger.getCurrentTerm());
            } else {
                editing.setBoardingStatus(boarding);
            }
            List<String> errors = studentService.updateStudent(editing);
            if (!errors.isEmpty()) {
                AlertUtil.warn("Validation", String.join("\n", errors));
                return;
            }
            PersistenceService.getInstance().saveAll();
            AlertUtil.info("Saved", "Student details updated.");
        }
        table.refresh();
        clearForm();
        switchToList();
    }

    private void exportStudents() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export Students");
        FileChooser.ExtensionFilter csv = new FileChooser.ExtensionFilter("CSV files", "*.csv");
        FileChooser.ExtensionFilter excel = new FileChooser.ExtensionFilter("Excel files", "*.xlsx");
        chooser.getExtensionFilters().addAll(csv, excel);
        chooser.setSelectedExtensionFilter(csv);
        chooser.setInitialFileName("students-export.csv");
        File file = chooser.showSaveDialog(getScene() != null ? getScene().getWindow() : null);
        if (file == null) return;
        File target = withChosenExtension(file, chooser);
        try {
            List<String> headers = List.of("Admission Number", "Full Name", "Gender", "Class", "Stream", "Boarding Status", "Phone", "Parent/Guardian", "Status");
            List<List<String>> rows = studentService.getAll().stream().map(s -> List.of(
                    safe(s.getAdmissionNumber()), safe(s.getName()), safe(s.getGender()),
                    safe(s.getFormClass()), safe(s.getStream()),
                    s.getBoardingStatus() != null ? s.getBoardingStatus().getDisplayName() : "",
                    safe(s.getPhone()),
                    safe(s.getParentName()),
                    s.getStatus() != null ? s.getStatus().getDisplayName() : "")).toList();
            exportService.export(target.toPath(), "Students", headers, rows);
            AlertUtil.info("Export complete", "Students exported to:\n" + target.getAbsolutePath());
        } catch (Exception e) {
            AlertUtil.error("Export failed", "The students could not be exported.\n\n" + e.getMessage());
        }
    }

    private void downloadTemplate() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save Student Import Template");
        FileChooser.ExtensionFilter excel = new FileChooser.ExtensionFilter("Excel files", "*.xlsx");
        FileChooser.ExtensionFilter csv = new FileChooser.ExtensionFilter("CSV files", "*.csv");
        chooser.getExtensionFilters().addAll(excel, csv);
        chooser.setSelectedExtensionFilter(excel);
        chooser.setInitialFileName("student-import-template.xlsx");
        File file = chooser.showSaveDialog(getScene() != null ? getScene().getWindow() : null);
        if (file == null) return;
        File target = withChosenExtension(file, chooser);
        try {
            new com.schaccs.service.export.StudentTemplateService(exportService).generateTemplate(target.toPath());
            AlertUtil.info("Template saved", "Template saved to:\n" + target.getAbsolutePath());
        } catch (Exception e) {
            AlertUtil.error("Template failed", "The student template could not be saved.\n\n" + e.getMessage());
        }
    }

    private File withChosenExtension(File file, FileChooser chooser) {
        String name = file.getName().toLowerCase();
        if (name.endsWith(".csv") || name.endsWith(".xlsx")) {
            return file;
        }
        FileChooser.ExtensionFilter chosen = chooser.getSelectedExtensionFilter();
        String ext = chosen == null || chosen.getExtensions().isEmpty()
                ? ".xlsx" : chosen.getExtensions().get(0).replace("*", "");
        return new File(file.getParentFile(), file.getName() + ext);
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
        try {
            List<Map<String, String>> rows = importService.parseFile(file.toPath());
            if (rows.isEmpty()) {
                AlertUtil.warn("Nothing to import", "The selected file has no data rows.");
                return;
            }
            List<Student> students = rows.stream()
                    .map(importService::toStudent)
                    .collect(Collectors.toList());
            StudentImportReviewDialog dialog = new StudentImportReviewDialog(rows, students, importService);
            dialog.showAndWait();
            if (dialog.getImportedCount() > 0) {
                table.refresh();
                int remaining = dialog.getRemainingCount();
                if (remaining > 0) {
                    AlertUtil.warn("Import partially complete",
                            "Imported " + dialog.getImportedCount() + " student(s). " + remaining
                                    + " row(s) were not saved because they still have errors.");
                } else {
                    AlertUtil.info("Import complete", "Imported " + dialog.getImportedCount() + " student(s).");
                }
            }
        } catch (java.io.IOException e) {
            AlertUtil.error("Import failed", e.getMessage());
        } catch (IllegalArgumentException e) {
            AlertUtil.error("Import failed", e.getMessage());
        }
    }

    private void switchToList() {
        tabPane.getSelectionModel().select(listTab);
    }

    private void switchToForm() {
        tabPane.getSelectionModel().select(formTab);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    @Override
    public void refresh() {
        populateDropdowns();
        table.refresh();
    }
}
