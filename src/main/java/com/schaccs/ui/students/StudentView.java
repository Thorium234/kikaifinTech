package com.schaccs.ui.students;

import com.schaccs.enums.AcademicTerm;
import com.schaccs.enums.BoardingStatus;
import com.schaccs.enums.StudentStatus;
import com.schaccs.model.CleanDataEntry;
import com.schaccs.model.student.Student;
import com.schaccs.model.student.StudentFeeLedger;
import com.schaccs.repository.PersistenceService;
import com.schaccs.service.Services;
import com.schaccs.service.fee.FeeCalculationService;
import com.schaccs.service.export.SpreadsheetExportService;
import com.schaccs.service.importer.FeesBalanceImportService;
import com.schaccs.service.importer.FeesBalanceRow;
import com.schaccs.service.importer.StudentImportService;
import com.schaccs.service.student.StudentService;
import com.schaccs.service.student.StudentTransitionService;
import com.schaccs.store.CleanDataStore;
import com.schaccs.store.SchoolCustomStore;
import com.schaccs.store.StudentStore;
import com.schaccs.ui.component.SearchBar;
import com.schaccs.ui.component.TypeToConfirmDialog;
import com.schaccs.ui.layout.MainLayout;
import com.schaccs.util.AlertUtil;
import com.schaccs.util.FileDialogMemory;
import com.schaccs.util.FileNamingUtil;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.Tab;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;

import java.io.File;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
    private final TextField courseCodeField = new TextField();
    private final TextField durationValueField = new TextField();
    private final ComboBox<com.schaccs.enums.DurationUnit> durationUnitBox = new ComboBox<>();
    private final javafx.scene.control.DatePicker enrollmentDatePicker = new javafx.scene.control.DatePicker();
    private final Label completionLabel = new Label();

    private Button cleanDataBtn;
    private Student editing;

    public StudentView() {
        setSpacing(14);
        setPadding(new Insets(4));

        filtered = new FilteredList<>(studentService.getAll(), s -> !s.isDeleted());
        searchBar.textProperty().addListener((obs, o, q) ->
                filtered.setPredicate(s -> !s.isDeleted() && s.matchesSearch(q)));

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
        Label sub = new Label("Double-click a student to edit. Use Ctrl/Shift to select several, then Delete moves them to the Recycle Bin.");
        sub.getStyleClass().add("muted");

        Button importBtn = new Button("Import CSV/XLSX");
        importBtn.getStyleClass().add("secondary-button");
        importBtn.setOnAction(e -> importStudents());
        Button importBalanceBtn = new Button("Import Fees Balance");
        importBalanceBtn.getStyleClass().add("secondary-button");
        importBalanceBtn.setGraphic(new FontIcon(FontAwesomeSolid.COINS));
        importBalanceBtn.setOnAction(e -> importFeesBalance());
        Button balanceTemplateBtn = new Button("Balances Template");
        balanceTemplateBtn.getStyleClass().add("secondary-button");
        balanceTemplateBtn.setGraphic(new FontIcon(FontAwesomeSolid.FILE_EXCEL));
        balanceTemplateBtn.setOnAction(e -> downloadFeesBalanceTemplate());
        Button cleanDataBtn = new Button("Clean Data");
        cleanDataBtn.getStyleClass().add("secondary-button");
        cleanDataBtn.setGraphic(new FontIcon(FontAwesomeSolid.BROOM));
        cleanDataBtn.setOnAction(e -> openCleanData());
        Button exportBtn = new Button("Export");
        exportBtn.getStyleClass().add("secondary-button");
        exportBtn.setOnAction(e -> exportStudents());
        Button templateBtn = new Button("Template");
        templateBtn.getStyleClass().add("secondary-button");
        templateBtn.setOnAction(e -> downloadTemplate());

        Button addBtn = new Button("Add Student");
        addBtn.getStyleClass().add("primary-button");
        addBtn.setOnAction(e -> { clearForm(); switchToForm(); });

        Button deleteBtn = new Button("Delete");
        deleteBtn.getStyleClass().add("danger-button");
        deleteBtn.setGraphic(new FontIcon(FontAwesomeSolid.TRASH));
        deleteBtn.setOnAction(e -> deleteSelected());

        FlowPane toolbar = new FlowPane(10, 10, searchBar, addBtn, deleteBtn, importBtn, importBalanceBtn,
                balanceTemplateBtn, cleanDataBtn, exportBtn, templateBtn);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        this.cleanDataBtn = cleanDataBtn;

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
        courseCodeField.setPromptText("e.g. KCSE, Certificate, Diploma");
        durationValueField.setPromptText("e.g. 4");
        durationUnitBox.getItems().addAll(com.schaccs.enums.DurationUnit.values());
        durationUnitBox.setValue(com.schaccs.enums.DurationUnit.YEARS);
        durationValueField.textProperty().addListener((obs, o, n) -> updateCompletionLabel());
        durationUnitBox.valueProperty().addListener((obs, o, n) -> updateCompletionLabel());
        enrollmentDatePicker.valueProperty().addListener((obs, o, n) -> updateCompletionLabel());

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

        HBox durationRow = new HBox(10,
                labeled("Course Duration (amount)", durationValueField),
                labeled("Unit", durationUnitBox));
        durationRow.setAlignment(Pos.BOTTOM_LEFT);

        // Column 1 (cont.) — Course tracking
        grid.add(labeled("Course Code (optional)", courseCodeField), 0, 6);
        grid.add(durationRow, 0, 7);
        grid.add(labeled("Enrollment Date", enrollmentDatePicker), 0, 8);

        completionLabel.getStyleClass().add("muted");
        completionLabel.setWrapText(true);
        completionLabel.setMaxWidth(Double.MAX_VALUE);
        grid.add(completionLabel, 0, 9, 2, 1);

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
            classBox.getItems().addAll("Form 1", "Form 2", "Form 3", "Form 4", "Form 5", "Form 6",
                    "Grade 8", "Grade 9", "Grade 10", "Grade 11", "Grade 12");
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
        table.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        table.setRowFactory(tv -> {
            TableRow<Student> row = new TableRow<>() {
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
            };
            row.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !row.isEmpty() && row.getItem() != null) {
                    loadForm(row.getItem());
                    switchToForm();
                }
            });
            return row;
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
        courseCodeField.clear();
        durationValueField.clear();
        durationUnitBox.setValue(com.schaccs.enums.DurationUnit.YEARS);
        enrollmentDatePicker.setValue(null);
        completionLabel.setText("");
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
        courseCodeField.setText(s.getCourseCode());
        durationValueField.setText(s.getDurationValue() != null ? String.valueOf(s.getDurationValue()) : "");
        durationUnitBox.setValue(s.getDurationUnit() != null ? s.getDurationUnit() : com.schaccs.enums.DurationUnit.YEARS);
        enrollmentDatePicker.setValue(s.getEnrollmentDate());
        updateCompletionLabel();
        formTab.setText("Edit Student");
        updateFeeStructureLabel();
    }

    private void updateCompletionLabel() {
        try {
            Integer value = durationValueField.getText().isBlank()
                    ? null : Integer.valueOf(durationValueField.getText().trim());
            java.time.LocalDate enrolled = enrollmentDatePicker.getValue();
            if (value == null || value <= 0 || enrolled == null) {
                completionLabel.setText("");
                return;
            }
            com.schaccs.enums.DurationUnit unit = durationUnitBox.getValue();
            java.time.LocalDate expected = com.schaccs.enums.DurationUnit.YEARS == unit
                    ? enrolled.plusYears(value)
                    : enrolled.plusMonths(value * 4L);
            completionLabel.setText("Expected course completion: " + expected
                    + (expected.isBefore(java.time.LocalDate.now())
                    ? " (already passed - this student will be marked Completed and fees frozen)" : ""));
        } catch (NumberFormatException e) {
            completionLabel.setText("");
        }
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
            applyCourseFields(s);
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
            applyCourseFields(editing);
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

    private void applyCourseFields(Student s) {
        s.setCourseCode(courseCodeField.getText().trim().isEmpty() ? null : courseCodeField.getText().trim());
        Integer value = null;
        try {
            value = durationValueField.getText().isBlank()
                    ? null : Integer.valueOf(durationValueField.getText().trim());
        } catch (NumberFormatException ignored) {
            value = null;
        }
        s.setDurationValue(value);
        s.setDurationUnit(durationUnitBox.getValue());
        s.setEnrollmentDate(enrollmentDatePicker.getValue());
        s.setExpectedCompletionDate(Services.getInstance().academicCalendar().expectedCompletionDate(s));
    }

    private void deleteSelected() {
        List<Student> selected = new ArrayList<>(table.getSelectionModel().getSelectedItems());
        if (selected.isEmpty()) {
            AlertUtil.warn("No selection", "Select one or more students, then click Delete.");
            return;
        }
        String detail = selected.stream()
                .map(s -> "• " + safe(s.getAdmissionNumber()) + " — " + safe(s.getName()))
                .collect(Collectors.joining("\n"));
        TypeToConfirmDialog dialog = new TypeToConfirmDialog(
                "Delete Students",
                "This will move " + selected.size() + " student(s) to the Recycle Bin and remove them "
                        + "from the school financial records.\n\n" + detail,
                "DELETE", "Delete");
        Optional<Boolean> result = dialog.showAndWait();
        if (!result.isPresent() || !result.get()) {
            return;
        }
        studentService.deleteToRecycleBin(selected);
        if (editing != null && selected.contains(editing)) {
            clearForm();
        }
        switchToList();
        table.refresh();
        AlertUtil.info("Deleted",
                selected.size() + " student(s) moved to the Recycle Bin. "
                        + "Restore or purge them from the Recycle Bin anytime.");
    }

    private void exportStudents() {
        FileChooser chooser = new FileChooser();
        FileDialogMemory.applyTo(chooser);
        chooser.setTitle("Export Students");
        FileChooser.ExtensionFilter csv = new FileChooser.ExtensionFilter("CSV files", "*.csv");
        FileChooser.ExtensionFilter excel = new FileChooser.ExtensionFilter("Excel files", "*.xlsx");
        chooser.getExtensionFilters().addAll(csv, excel);
        chooser.setSelectedExtensionFilter(csv);
        chooser.setInitialFileName(FileNamingUtil.suggest("students-export.csv"));
        File file = chooser.showSaveDialog(getScene() != null ? getScene().getWindow() : null);
        if (file == null) return;
        FileDialogMemory.remember(file);
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
        FileDialogMemory.applyTo(chooser);
        chooser.setTitle("Save Student Import Template");
        FileChooser.ExtensionFilter excel = new FileChooser.ExtensionFilter("Excel files", "*.xlsx");
        FileChooser.ExtensionFilter csv = new FileChooser.ExtensionFilter("CSV files", "*.csv");
        chooser.getExtensionFilters().addAll(excel, csv);
        chooser.setSelectedExtensionFilter(excel);
        chooser.setInitialFileName(FileNamingUtil.suggest("student-import-template.xlsx"));
        File file = chooser.showSaveDialog(getScene() != null ? getScene().getWindow() : null);
        if (file == null) return;
        FileDialogMemory.remember(file);
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
        FileDialogMemory.applyTo(chooser);
        chooser.setTitle("Import Students");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Spreadsheet files", "*.csv", "*.xlsx"),
                new FileChooser.ExtensionFilter("CSV files", "*.csv"),
                new FileChooser.ExtensionFilter("Excel files", "*.xlsx"));
        File file = chooser.showOpenDialog(getScene() != null ? getScene().getWindow() : null);
        if (file == null) return;
        FileDialogMemory.remember(file);
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
            CleanDataStore.getInstance().addRows(CleanDataEntry.Type.STUDENT, dialog.getHeldRawRows());
            PersistenceService.getInstance().saveAll();
            if (dialog.getImportedCount() > 0) {
                table.refresh();
                int remaining = dialog.getRemainingCount();
                if (remaining > 0) {
                    AlertUtil.warn("Import partially complete",
                            "Imported " + dialog.getImportedCount() + " student(s) automatically. "
                                    + remaining + " row(s) still have errors and were moved to Clean Data for fixing.");
                } else {
                    AlertUtil.info("Import complete", "Imported " + dialog.getImportedCount() + " student(s).");
                }
            } else if (!dialog.getHeldRawRows().isEmpty()) {
                AlertUtil.warn("Nothing imported",
                        "All " + dialog.getHeldRawRows().size()
                                + " row(s) had errors and were moved to Clean Data. Fix them from the Clean Data list anytime.");
            }
            updateCleanDataButton();
        } catch (java.io.IOException e) {
            AlertUtil.error("Import failed", e.getMessage());
        } catch (IllegalArgumentException e) {
            AlertUtil.error("Import failed", e.getMessage());
        }
    }

    /** Saves the official fees-balance template: Adm No / Name / Class / Stream / Boarding / Balance. */
    private void downloadFeesBalanceTemplate() {
        FileChooser chooser = new FileChooser();
        FileDialogMemory.applyTo(chooser);
        chooser.setTitle("Save Fees Balance Template");
        FileChooser.ExtensionFilter excel = new FileChooser.ExtensionFilter("Excel files", "*.xlsx");
        FileChooser.ExtensionFilter csv = new FileChooser.ExtensionFilter("CSV files", "*.csv");
        chooser.getExtensionFilters().addAll(excel, csv);
        chooser.setSelectedExtensionFilter(excel);
        chooser.setInitialFileName(FileNamingUtil.suggest("fees-balance-template.xlsx"));
        File file = chooser.showSaveDialog(getScene() != null ? getScene().getWindow() : null);
        if (file == null) return;
        FileDialogMemory.remember(file);
        File target = withChosenExtension(file, chooser);
        try {
            new com.schaccs.service.export.FeesBalanceTemplateService(exportService).generateTemplate(target.toPath());
            AlertUtil.info("Template saved", "Fees balance template saved to:\n" + target.getAbsolutePath()
                    + "\n\nFill in BALANCE as exactly what each student still owes today, then use "
                    + "Import Fees Balance. The system reads this format verbatim - no guessing.");
        } catch (Exception e) {
            AlertUtil.error("Template failed", "The fees balance template could not be saved.\n\n" + e.getMessage());
        }
    }

    private void importFeesBalance() {        FileChooser chooser = new FileChooser();
        FileDialogMemory.applyTo(chooser);
        chooser.setTitle("Import Fees Balance");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Excel workbook", "*.xlsx"));
        File file = chooser.showOpenDialog(getScene() != null ? getScene().getWindow() : null);
        if (file == null) return;
        FileDialogMemory.remember(file);
        try {
            FeesBalancePrompt prompt = promptFeesBalanceImport();
            if (prompt == null) {
                return;
            }
            Integer year = prompt.year();
            if (Services.getInstance().academicCalendar().ensureYearCalendar(year)) {
                AlertUtil.info("Calendar scaffolded",
                        "The calendar had no periods for " + year + ", so Term 1, Term 2 and Term 3 were "
                                + "generated as ended periods. Edit their dates in the Academic Calendar if needed.");
            }
            FeesBalanceImportService service = new FeesBalanceImportService();
            List<FeesBalanceRow> rows = service.parseWorkbook(file.toPath());
            if (rows.isEmpty()) {
                AlertUtil.warn("Nothing to import", "The workbook contains no recognisable student fee-balance tables.");
                return;
            }
            // Fill the batch class into every row whose Form column could not
            // be inferred, so it no longer blocks those rows.
            if (prompt.classLabel() != null || prompt.stream() != null) {
                int filled = service.applyWorkbookDefaults(rows, prompt.classLabel(),
                        prompt.stream(), false);
                com.schaccs.service.school.SchoolCustomService customService =
                        new com.schaccs.service.school.SchoolCustomService();
                if (prompt.classLabel() != null) {
                    customService.ensureFormClass(prompt.classLabel());
                }
                if (prompt.stream() != null) {
                    customService.ensureStream(prompt.stream());
                }
                if (filled > 0) {
                    AlertUtil.info("Class applied",
                            prompt.classLabel() + (prompt.stream() != null ? " (Stream " + prompt.stream() + ")" : "")
                                    + " was filled into " + filled + " row(s) whose class could not be read "
                                    + "from the workbook.");
                }
            }
            service.scrutinize(rows);
            FeesBalanceImportService.ImportContext context =
                    FeesBalanceImportService.ImportContext.of(year,
                            com.schaccs.config.AppConfig.getInstance().getCurrentUser(),
                            prompt.classLabel(), prompt.stream());
            FeesBalanceImportReviewDialog dialog = new FeesBalanceImportReviewDialog(service, rows, context);
            dialog.showAndWait();
            List<Map<String, String>> heldFields = dialog.getHeldRows().stream()
                    .map(FeesBalanceRow::toFields)
                    .collect(Collectors.toList());
            CleanDataStore.getInstance().addRows(CleanDataEntry.Type.FEES_BALANCE, heldFields);
            PersistenceService.getInstance().saveAll();
            if (dialog.getImportedCount() > 0) {
                table.refresh();
                int remaining = dialog.getRemainingCount();
                if (remaining > 0) {
                    AlertUtil.warn("Import partially complete",
                            "Imported " + dialog.getImportedCount() + " balance(s) for " + year + " automatically. "
                                    + remaining + " row(s) still need cleaning and were moved to Clean Data for fixing.");
                } else {
                    AlertUtil.info("Import complete",
                            "Imported " + dialog.getImportedCount() + " balance(s) for " + year + ".");
                }
            } else if (!heldFields.isEmpty()) {
                AlertUtil.warn("Nothing imported",
                        "All " + heldFields.size()
                                + " row(s) had errors and were moved to Clean Data. Fix them from the Clean Data list anytime.");
            }
            updateCleanDataButton();
        } catch (java.io.UncheckedIOException e) {
            AlertUtil.error("Import failed", e.getMessage());
        }
    }

    /** Batch choices for a fees-balance import: the year first, then the class. */
    private record FeesBalancePrompt(Integer year, String classLabel, String stream) {
    }

    /**
     * Ask which academic year the fees-balance workbook belongs to and, right
     * after the year, the class this workbook covers — Form 1-6 or Grade 8-12,
     * plus an optional stream. The class is optional: workbooks that carry one
     * sheet per class do not need it.
     */
    private FeesBalancePrompt promptFeesBalanceImport() {
        javafx.scene.control.Spinner<Integer> year =
                new javafx.scene.control.Spinner<>(1990, 2100, LocalDate.now().getYear());
        year.setEditable(true);
        year.setPrefWidth(110);

        javafx.scene.control.ComboBox<String> type = new javafx.scene.control.ComboBox<>();
        type.getItems().addAll("Form", "Grade");
        type.setPromptText("Form / Grade (optional)");
        type.setPrefWidth(180);

        javafx.scene.control.ComboBox<Integer> level = new javafx.scene.control.ComboBox<>();
        level.setPromptText("Level");
        level.setPrefWidth(90);
        level.setDisable(true);
        type.setOnAction(e -> {
            String selected = type.getValue();
            if ("Form".equals(selected)) {
                level.getItems().setAll(1, 2, 3, 4, 5, 6);
            } else if ("Grade".equals(selected)) {
                level.getItems().setAll(8, 9, 10, 11, 12);
            } else {
                level.getItems().clear();
            }
            level.getSelectionModel().clearSelection();
            level.setDisable(selected == null);
        });

        javafx.scene.control.ComboBox<String> stream = new javafx.scene.control.ComboBox<>();
        stream.setEditable(true);
        stream.setPromptText("Stream (optional)");
        stream.setPrefWidth(130);
        stream.getItems().addAll("A", "B", "C");

        VBox content = new VBox(10,
                new Label("Which academic year does this fees-balance workbook belong to?"),
                year,
                new Label("Which class does it cover? Pick Form or Grade and its level - e.g. \"Grade 10\" - "
                        + "and every row whose class cannot be read from the sheets is filled with it."),
                new HBox(8, type, level, stream),
                new Label("Imported balances are booked against the year; students new to the registry also get "
                        + "the current term's fees charged from the fee structure automatically."));
        content.setPadding(new Insets(10));

        javafx.scene.control.Dialog<ButtonType> dialog = new javafx.scene.control.Dialog<>();
        dialog.setTitle("Import Fees Balance");
        dialog.setHeaderText("Select the academic year, then the class for this import");
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, ButtonType.OK);
        Optional<ButtonType> choice = dialog.showAndWait();
        if (choice.isEmpty() || choice.get() != ButtonType.OK) {
            return null;
        }
        Integer selectedYear = year.getValue();
        if (selectedYear == null) {
            AlertUtil.warn("No year", "Select an academic year to continue the import.");
            return null;
        }
        String typeValue = type.getValue();
        Integer levelValue = level.getValue();
        if ((typeValue != null) != (levelValue != null)) {
            AlertUtil.warn("Class incomplete",
                    "Choose both the class type and its level - for example \"Grade 10\" - or leave both empty.");
            return null;
        }
        String classLabel = typeValue == null ? null : typeValue + " " + levelValue;
        return new FeesBalancePrompt(selectedYear, classLabel,
                stream.getValue() == null || stream.getValue().isBlank() ? null : stream.getValue().trim());
    }

    private void openCleanData() {
        CleanDataDialog dialog = new CleanDataDialog(() -> { });
        dialog.showAndWait();
        table.refresh();
        updateCleanDataButton();
    }

    private void updateCleanDataButton() {
        if (cleanDataBtn == null) {
            return;
        }
        int count = CleanDataStore.getInstance().getItems().size();
        cleanDataBtn.setText(count == 0 ? "Clean Data" : "Clean Data (" + count + ")");
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
        updateCleanDataButton();
    }
}
