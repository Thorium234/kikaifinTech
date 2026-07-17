package com.schaccs.ui.students;

import com.schaccs.enums.BoardingStatus;
import com.schaccs.enums.StudentStatus;
import com.schaccs.model.student.Student;
import com.schaccs.repository.PersistenceService;
import com.schaccs.service.Services;
import com.schaccs.service.fee.FeeCalculationService;
import com.schaccs.service.export.SpreadsheetExportService;
import com.schaccs.service.export.StudentTemplateService;
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
import javafx.scene.control.Separator;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.stage.FileChooser;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class StudentView extends VBox implements MainLayout.Refreshable {

    private final StudentService studentService = Services.getInstance().student();
    private final FeeCalculationService feeService = Services.getInstance().feeCalculation();
    private final StudentImportService importService = new StudentImportService();
    private final SpreadsheetExportService exportService = new SpreadsheetExportService();
    private final StudentTemplateService templateService = new StudentTemplateService(exportService);

    private final TableView<Student> table = new TableView<>();
    private final FilteredList<Student> filtered;
    private final SearchBar searchBar = new SearchBar("Search by name, admission no, class…");

    private final TextField admField = new TextField();
    private final TextField nameField = new TextField();
    private final TextField upiField = new TextField();
    private final TextField formField = new TextField();
    private final TextField streamField = new TextField();
    private final TextField phoneField = new TextField();
    private final TextField parentField = new TextField();
    private final TextField guardianField = new TextField();
    private final ComboBox<BoardingStatus> boardingBox = new ComboBox<>();
    private final ComboBox<StudentStatus> statusBox = new ComboBox<>();
    private final ComboBox<String> genderBox = new ComboBox<>();
    private final TextField avatarPathField = new TextField();
    private final ImageView avatarPreview = new ImageView();
    private final StackPane avatarPane = new StackPane();
    private final Label validationBanner = new Label();
    private final Label modeBadge = new Label();

    private Student editing;

    public StudentView() {
        setSpacing(14);
        setPadding(new Insets(4));

        filtered = new FilteredList<>(studentService.getAll(), s -> true);
        searchBar.textProperty().addListener((obs, o, q) ->
                filtered.setPredicate(s -> s.matchesSearch(q)));

        Label heading = new Label("Student Registry");
        heading.getStyleClass().add("section-title");

        Button addBtn = new Button("New Student");
        addBtn.getStyleClass().add("primary-button");
        addBtn.setOnAction(e -> clearForm());

        Button saveBtn = new Button("Save");
        saveBtn.getStyleClass().add("success-button");
        saveBtn.setOnAction(e -> save());

        Button importBtn = new Button("Import CSV/XLSX");
        importBtn.getStyleClass().add("secondary-button");
        importBtn.setOnAction(e -> importStudents());

        Button exportBtn = new Button("Export Students");
        exportBtn.getStyleClass().add("secondary-button");
        exportBtn.setOnAction(e -> exportStudents());

        Button templateBtn = new Button("Download Template");
        templateBtn.getStyleClass().add("secondary-button");
        templateBtn.setOnAction(e -> downloadTemplate());

        Button inactiveBtn = new Button("Mark Inactive");
        inactiveBtn.getStyleClass().add("secondary-button");
        inactiveBtn.setOnAction(e -> markInactive());

        Label tableBadge = new Label("Student Registry Workspace");
        tableBadge.getStyleClass().add("student-header-badge");
        VBox toolbarHeader = new VBox(4, tableBadge, heading);

        HBox toolbar = new HBox(10, searchBar, addBtn, saveBtn, importBtn, exportBtn, templateBtn, inactiveBtn);
        toolbar.getStyleClass().add("student-toolbar");
        toolbar.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(searchBar, Priority.ALWAYS);

        VBox toolbarCard = new VBox(10, toolbarHeader, toolbar);
        toolbarCard.getStyleClass().addAll("card", "student-toolbar-card");

        setupTable();
        Label registryTitle = new Label("Registered Students");
        registryTitle.getStyleClass().add("section-title");
        Label registrySub = new Label("Browse, search, and select a student to edit details.");
        registrySub.getStyleClass().add("muted");
        VBox tableCard = new VBox(10, registryTitle, registrySub, table);
        tableCard.getStyleClass().addAll("card", "student-table-card");
        VBox.setVgrow(table, Priority.ALWAYS);
        VBox.setVgrow(tableCard, Priority.ALWAYS);

        VBox formCard = buildForm();
        formCard.setPrefWidth(360);
        formCard.setMinWidth(320);
        ScrollPane formScroll = new ScrollPane(formCard);
        formScroll.setFitToWidth(true);
        formScroll.setPannable(true);
        formScroll.getStyleClass().add("inline-scroll-pane");
        formScroll.setPrefWidth(390);
        formScroll.setMinWidth(340);

        HBox body = new HBox(16, tableCard, formScroll);
        HBox.setHgrow(tableCard, Priority.ALWAYS);
        HBox.setHgrow(formScroll, Priority.NEVER);
        VBox.setVgrow(body, Priority.ALWAYS);

        getChildren().addAll(toolbarCard, body);
        configureLiveValidation();
        updateModeBadge();
    }

    private void setupTable() {
        TableColumn<Student, String> avatar = new TableColumn<>("Avatar");
        avatar.setCellValueFactory(c -> c.getValue().avatarPathProperty());
        avatar.setCellFactory(col -> new javafx.scene.control.TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null);
                    return;
                }
                setGraphic(buildStudentAvatarNode((Student) getTableRow().getItem(), 28));
            }
        });
        avatar.setPrefWidth(70);

        TableColumn<Student, String> adm = new TableColumn<>("Adm No");
        adm.setCellValueFactory(c -> c.getValue().admissionNumberProperty());
        adm.setPrefWidth(100);

        TableColumn<Student, String> name = new TableColumn<>("Name");
        name.setCellValueFactory(c -> c.getValue().nameProperty());
        name.setPrefWidth(180);

        TableColumn<Student, String> cls = new TableColumn<>("Class");
        cls.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getClassLabel()));
        cls.setPrefWidth(80);

        TableColumn<Student, String> board = new TableColumn<>("Boarding");
        board.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getBoardingStatus() != null ? c.getValue().getBoardingStatus().getDisplayName() : ""));
        board.setPrefWidth(90);

        TableColumn<Student, String> phone = new TableColumn<>("Phone");
        phone.setCellValueFactory(c -> c.getValue().phoneProperty());
        phone.setPrefWidth(110);

        TableColumn<Student, String> st = new TableColumn<>("Status");
        st.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getStatus() != null ? c.getValue().getStatus().getDisplayName() : ""));
        st.setPrefWidth(100);
        st.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().removeAll("student-status-active", "student-status-inactive");
                if (empty || item == null || item.isBlank()) {
                    setText(null);
                    return;
                }
                setText(item);
                Student student = getTableRow() != null ? (Student) getTableRow().getItem() : null;
                if (student != null && student.getStatus() != null) {
                    switch (student.getStatus()) {
                        case ACTIVE -> getStyleClass().add("student-status-active");
                        case INACTIVE -> getStyleClass().add("student-status-inactive");
                        default -> {
                        }
                    }
                }
            }
        });

        table.getColumns().addAll(avatar, adm, name, cls, board, phone, st);
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
                        default -> {
                        }
                    }
                }
            }
        });
        table.getSelectionModel().selectedItemProperty().addListener((obs, o, s) -> {
            if (s != null) {
                loadForm(s);
            }
        });
    }

    private VBox buildForm() {
        Label title = new Label("New Student / Edit Student");
        title.getStyleClass().add("section-title");
        Label subtitle = new Label("Capture identity, class placement, guardian details, and student profile image.");
        subtitle.getStyleClass().addAll("muted", "student-form-subtitle");
        modeBadge.getStyleClass().add("student-mode-badge");
        validationBanner.getStyleClass().add("student-validation-banner");
        validationBanner.setManaged(false);
        validationBanner.setVisible(false);

        boardingBox.getItems().setAll(BoardingStatus.values());
        boardingBox.setValue(BoardingStatus.BOARDING);
        statusBox.getItems().setAll(StudentStatus.values());
        statusBox.setValue(StudentStatus.ACTIVE);
        genderBox.getItems().setAll("Male", "Female");
        genderBox.setValue("Male");

        avatarPathField.setPromptText("Optional student picture path");
        configureAvatarPreview();
        nameField.textProperty().addListener((obs, oldValue, newValue) -> refreshAvatarPreview(editing));
        avatarPathField.textProperty().addListener((obs, oldValue, newValue) -> refreshAvatarPreview(editing));
        GridPane grid = new GridPane();
        grid.getStyleClass().add("student-form-grid");
        grid.setHgap(10);
        grid.setVgap(10);
        int r = 0;
        Label identityLabel = new Label("Student Identity");
        identityLabel.getStyleClass().add("student-form-section");
        grid.add(identityLabel, 0, r++, 2, 1);
        grid.add(formLabel("Admission No"), 0, r);
        grid.add(formFieldBox(admField, "Required. Use the official admission number format."), 1, r++);
        grid.add(formLabel("Full Name"), 0, r);
        grid.add(formFieldBox(nameField, "Enter full student name as per admission records."), 1, r++);
        grid.add(formLabel("UPI"), 0, r);
        grid.add(formFieldBox(upiField, "Kenyan learner UPI identifier, if assigned."), 1, r++);
        grid.add(formLabel("Gender"), 0, r);
        grid.add(formFieldBox(genderBox, "Select the student gender."), 1, r++);
        grid.add(formLabel("Form / Class"), 0, r);
        grid.add(formFieldBox(formField, "Example: Form 1, Form 2, Form 3, Form 4."), 1, r++);
        grid.add(formLabel("Stream"), 0, r);
        grid.add(formFieldBox(streamField, "Example: A, B, North, East."), 1, r++);
        grid.add(formLabel("Boarding"), 0, r);
        grid.add(formFieldBox(boardingBox, "Select boarding or day status."), 1, r++);
        grid.add(new Separator(), 0, r++, 2, 1);
        Label guardianLabel = new Label("Guardian & Contact");
        guardianLabel.getStyleClass().add("student-form-section");
        grid.add(guardianLabel, 0, r++, 2, 1);
        grid.add(formLabel("Parent / Guardian"), 0, r);
        grid.add(formFieldBox(parentField, "Primary parent or guardian full name."), 1, r++);
        grid.add(formLabel("Guardian Key"), 0, r);
        grid.add(formFieldBox(guardianField, "Shared family key used for sibling discount linking."), 1, r++);
        grid.add(formLabel("Phone"), 0, r);
        grid.add(formFieldBox(phoneField, "Allowed: +254..., 07..., or 01..."), 1, r++);
        grid.add(new Separator(), 0, r++, 2, 1);
        Label profileLabel = new Label("Profile & Status");
        profileLabel.getStyleClass().add("student-form-section");
        grid.add(profileLabel, 0, r++, 2, 1);
        grid.add(formLabel("Student Picture"), 0, r);
        Button browseAvatar = new Button("Browse...");
        browseAvatar.getStyleClass().add("secondary-button");
        browseAvatar.setOnAction(e -> chooseAvatar());
        Button clearAvatar = new Button("Clear");
        clearAvatar.getStyleClass().add("secondary-button");
        clearAvatar.setOnAction(e -> clearAvatar());
        HBox avatarActions = new HBox(8, avatarPathField, browseAvatar, clearAvatar);
        avatarActions.getStyleClass().add("student-avatar-actions");
        VBox avatarBox = new VBox(8, avatarPane, avatarActions);
        avatarBox.getStyleClass().add("student-avatar-box");
        grid.add(avatarBox, 1, r++);
        grid.add(formLabel("Status"), 0, r);
        grid.add(formFieldBox(statusBox, "Inactive students remain in history but are not active in operations."), 1, r);

        admField.setPromptText("2026/009");
        nameField.setPromptText("Student full name");
        upiField.setPromptText("e.g. UPI001234");
        formField.setPromptText("Form 1");
        streamField.setPromptText("A");
        guardianField.setPromptText("e.g. KIT-001 (links siblings)");

        HBox actionBar = new HBox(10);
        Button clearBtn = new Button("Clear Form");
        clearBtn.getStyleClass().add("secondary-button");
        clearBtn.setOnAction(e -> clearForm());
        Label actionHint = new Label("Save posts the student and charges annual fees for new records.");
        actionHint.getStyleClass().addAll("muted", "student-action-hint");
        actionBar.getChildren().addAll(clearBtn, actionHint);
        actionBar.getStyleClass().add("student-form-actions");

        VBox box = new VBox(14, modeBadge, title, subtitle, validationBanner, grid, actionBar);
        box.getStyleClass().addAll("card", "student-form-card");
        return box;
    }

    private void clearForm() {
        editing = null;
        table.getSelectionModel().clearSelection();
        admField.clear();
        nameField.clear();
        upiField.clear();
        formField.clear();
        streamField.clear();
        phoneField.clear();
        parentField.clear();
        guardianField.clear();
        avatarPathField.clear();
        refreshAvatarPreview(null);
        boardingBox.setValue(BoardingStatus.BOARDING);
        statusBox.setValue(StudentStatus.ACTIVE);
        genderBox.setValue("Male");
        admField.setDisable(false);
        setValidationBanner(List.of());
        updateModeBadge();
        refreshValidationState();
    }

    private void loadForm(Student s) {
        editing = s;
        admField.setText(s.getAdmissionNumber());
        admField.setDisable(true);
        nameField.setText(s.getName());
        upiField.setText(s.getUpi());
        formField.setText(s.getFormClass());
        streamField.setText(s.getStream());
        phoneField.setText(s.getPhone());
        parentField.setText(s.getParentName());
        guardianField.setText(s.getGuardianKey());
        avatarPathField.setText(s.getAvatarPath());
        refreshAvatarPreview(s);
        boardingBox.setValue(s.getBoardingStatus());
        statusBox.setValue(s.getStatus());
        genderBox.setValue(s.getGender() != null ? s.getGender() : "Male");
        setValidationBanner(List.of());
        updateModeBadge();
        refreshValidationState();
    }

    private void save() {
        if (editing == null) {
            Student s = new Student();
            applyForm(s);
            List<String> errors = studentService.addStudent(s);
            if (!errors.isEmpty()) {
                setValidationBanner(errors);
                AlertUtil.warn("Validation", String.join("\n", errors));
                return;
            }
            feeService.chargeAnnualFees(s);
            PersistenceService.getInstance().saveAll();
            setValidationBanner(List.of());
            AlertUtil.info("Saved", "Student " + s.getAdmissionNumber() + " added and fees charged.");
            clearForm();
        } else {
            applyForm(editing);
            List<String> errors = studentService.updateStudent(editing);
            if (!errors.isEmpty()) {
                setValidationBanner(errors);
                AlertUtil.warn("Validation", String.join("\n", errors));
            } else {
                setValidationBanner(List.of());
                AlertUtil.info("Saved", "Student details updated.");
            }
        }
        table.refresh();
        refreshValidationState();
    }

    private void applyForm(Student s) {
        s.setAdmissionNumber(admField.getText().trim());
        s.setName(nameField.getText().trim());
        s.setUpi(upiField.getText().trim());
        s.setFormClass(formField.getText().trim());
        s.setStream(streamField.getText().trim());
        s.setPhone(phoneField.getText().trim());
        s.setParentName(parentField.getText().trim());
        s.setGuardianKey(guardianField.getText().trim());
        s.setAvatarPath(avatarPathField.getText().trim().isEmpty() ? null : avatarPathField.getText().trim());
        s.setBoardingStatus(boardingBox.getValue());
        s.setStatus(statusBox.getValue());
        s.setGender(genderBox.getValue());
        if (s.getAcademicYear() == null) {
            s.setAcademicYear(2026);
        }
        if (s.getYearOfAdmission() == null) {
            s.setYearOfAdmission(2026);
        }
    }

    private void markInactive() {
        Student s = table.getSelectionModel().getSelectedItem();
        if (s == null) {
            AlertUtil.warn("Select student", "Select a student first.");
            return;
        }
        if (AlertUtil.confirm("Confirm", "Mark " + s.getName() + " as inactive?")) {
            studentService.markInactive(s);
            table.refresh();
        }
    }

    private void exportStudents() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export Students");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("CSV files", "*.csv"),
                new FileChooser.ExtensionFilter("Excel files", "*.xlsx")
        );
        chooser.setInitialFileName("students-export.csv");
        File file = chooser.showSaveDialog(getScene() != null ? getScene().getWindow() : null);
        if (file == null) {
            return;
        }
        try {
            List<String> headers = List.of("Admission Number", "Full Name", "Gender", "Form Class", "Stream",
                    "Boarding Status", "Parent Name", "Guardian Key", "Phone", "UPI", "Academic Year",
                    "Year Of Admission", "Student Status");
            List<List<String>> rows = studentService.getAll().stream().map(s -> List.of(
                    safe(s.getAdmissionNumber()),
                    safe(s.getName()),
                    safe(s.getGender()),
                    safe(s.getFormClass()),
                    safe(s.getStream()),
                    s.getBoardingStatus() != null ? s.getBoardingStatus().getDisplayName() : "",
                    safe(s.getParentName()),
                    safe(s.getGuardianKey()),
                    safe(s.getPhone()),
                    safe(s.getUpi()),
                    s.getAcademicYear() != null ? String.valueOf(s.getAcademicYear()) : "",
                    s.getYearOfAdmission() != null ? String.valueOf(s.getYearOfAdmission()) : "",
                    s.getStatus() != null ? s.getStatus().getDisplayName() : ""
            )).toList();
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
                new FileChooser.ExtensionFilter("Excel files", "*.xlsx")
        );
        chooser.setInitialFileName("student-import-template.xlsx");
        File file = chooser.showSaveDialog(getScene() != null ? getScene().getWindow() : null);
        if (file == null) {
            return;
        }
        try {
            templateService.generateTemplate(file.toPath());
            AlertUtil.info("Template saved", "Template saved to:\n" + file.getAbsolutePath());
        } catch (IOException e) {
            AlertUtil.error("Template save failed", e.getMessage());
        }
    }

    private void importStudents() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Import Students");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Spreadsheet files", "*.csv", "*.xlsx"),
                new FileChooser.ExtensionFilter("CSV files", "*.csv"),
                new FileChooser.ExtensionFilter("Excel files", "*.xlsx")
        );
        File file = chooser.showOpenDialog(getScene() != null ? getScene().getWindow() : null);
        if (file == null) {
            return;
        }
        StudentImportService.ImportResult preview = importService.previewFile(file.toPath());
        if (!showImportPreviewDialog(preview)) {
            return;
        }
        StudentImportService.ImportResult result = importService.importFile(file.toPath());
        table.refresh();
        if (result.getImported() > 0) {
            AlertUtil.info("Import complete", buildImportMessage(result, false));
        } else {
            AlertUtil.warn("Import finished", buildImportMessage(result, false));
        }
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
            if (result.getWarnings().size() > 12) {
                msg.append("...and ").append(result.getWarnings().size() - 12).append(" more");
            }
        }
        return msg.toString().trim();
    }

    private void configureAvatarPreview() {
        avatarPreview.setFitHeight(110);
        avatarPreview.setFitWidth(110);
        avatarPreview.setPreserveRatio(true);
        avatarPane.setPrefSize(120, 120);
        avatarPane.setMinSize(120, 120);
        avatarPane.setStyle("-fx-border-color: -fx-box-border; -fx-border-radius: 60; -fx-background-radius: 60;");
    }

    private void chooseAvatar() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Choose Student Picture");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Image files", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp", "*.webp"),
                new FileChooser.ExtensionFilter("All files", "*.*")
        );
        File file = chooser.showOpenDialog(getScene() != null ? getScene().getWindow() : null);
        if (file != null) {
            avatarPathField.setText(file.getAbsolutePath());
            refreshAvatarPreview(editing);
        }
    }

    private void clearAvatar() {
        avatarPathField.clear();
        refreshAvatarPreview(editing);
    }

    private void refreshAvatarPreview(Student student) {
        Student source = student != null ? student : new Student();
        if (student == null) {
            source.setName(nameField.getText());
            source.setAvatarPath(avatarPathField.getText());
        }
        avatarPane.getChildren().setAll(buildStudentAvatarNode(source, 110));
    }

    private StackPane buildStudentAvatarNode(Student student, double size) {
        StackPane pane = new StackPane();
        Circle bg = new Circle(size / 2, Color.web("#dbe8dd"));
        String avatarPath = student != null ? student.getAvatarPath() : null;
        if (avatarPath != null && !avatarPath.isBlank()) {
            Path path = Path.of(avatarPath);
            if (Files.exists(path)) {
                ImageView view = new ImageView(new Image(path.toUri().toString(), true));
                view.setFitWidth(size);
                view.setFitHeight(size);
                view.setPreserveRatio(true);
                pane.getChildren().addAll(bg, view);
                return pane;
            }
        }
        String initials = initialsFor(student != null ? student.getName() : "");
        Text text = new Text(initials);
        text.setFill(Color.web("#1a472a"));
        text.setFont(Font.font("System", FontWeight.BOLD, Math.max(12, size / 3.2)));
        pane.getChildren().addAll(bg, text);
        return pane;
    }

    private String initialsFor(String name) {
        if (name == null || name.isBlank()) {
            return "A";
        }
        String[] parts = name.trim().split("\\s+");
        String first = parts[0].substring(0, 1).toUpperCase();
        String second = parts.length > 1 ? parts[1].substring(0, 1).toUpperCase() : "";
        return (first + second).trim();
    }

    private Label formLabel(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("student-form-label");
        return label;
    }

    private VBox formFieldBox(javafx.scene.Node field, String hint) {
        Label hintLabel = new Label(hint);
        hintLabel.getStyleClass().addAll("muted", "student-field-hint");
        VBox box = new VBox(4, field, hintLabel);
        box.getStyleClass().add("student-field-box");
        if (field instanceof javafx.scene.layout.Region region) {
            region.setMaxWidth(Double.MAX_VALUE);
        }
        return box;
    }

    private void setValidationBanner(List<String> errors) {
        boolean hasErrors = errors != null && !errors.isEmpty();
        validationBanner.setText(hasErrors ? String.join("\n", errors) : "");
        validationBanner.setManaged(hasErrors);
        validationBanner.setVisible(hasErrors);
    }

    private void configureLiveValidation() {
        admField.textProperty().addListener((obs, oldValue, newValue) -> refreshValidationState());
        nameField.textProperty().addListener((obs, oldValue, newValue) -> refreshValidationState());
        upiField.textProperty().addListener((obs, oldValue, newValue) -> refreshValidationState());
        formField.textProperty().addListener((obs, oldValue, newValue) -> refreshValidationState());
        streamField.textProperty().addListener((obs, oldValue, newValue) -> refreshValidationState());
        phoneField.textProperty().addListener((obs, oldValue, newValue) -> refreshValidationState());
        parentField.textProperty().addListener((obs, oldValue, newValue) -> refreshValidationState());
        guardianField.textProperty().addListener((obs, oldValue, newValue) -> refreshValidationState());
        avatarPathField.textProperty().addListener((obs, oldValue, newValue) -> refreshValidationState());
        genderBox.valueProperty().addListener((obs, oldValue, newValue) -> refreshValidationState());
        boardingBox.valueProperty().addListener((obs, oldValue, newValue) -> refreshValidationState());
        statusBox.valueProperty().addListener((obs, oldValue, newValue) -> refreshValidationState());
    }

    private void refreshValidationState() {
        setFieldState(admField, requiredFilled(admField));
        setFieldState(nameField, requiredFilled(nameField));
        setFieldState(formField, requiredFilled(formField));
        setFieldState(streamField, optionalFilled(streamField));
        setFieldState(parentField, optionalFilled(parentField));
        setFieldState(guardianField, optionalFilled(guardianField));
        setFieldState(avatarPathField, optionalFilled(avatarPathField));
        setFieldState(upiField, optionalValidUpi());
        setFieldState(phoneField, optionalValidPhone());
        setComboState(genderBox, genderBox.getValue() != null);
        setComboState(boardingBox, boardingBox.getValue() != null);
        setComboState(statusBox, statusBox.getValue() != null);
    }

    private boolean requiredFilled(TextField field) {
        return field.getText() != null && !field.getText().trim().isEmpty();
    }

    private boolean optionalFilled(TextField field) {
        return field.getText() == null || field.getText().trim().isEmpty() || field.getText().trim().length() >= 2;
    }

    private boolean optionalValidPhone() {
        String value = phoneField.getText();
        if (value == null || value.trim().isEmpty()) {
            return true;
        }
        String normalized = value.replaceAll("\\s+", "");
        return normalized.matches("^(\\+254|254|0)(7\\d{8}|1\\d{8})$");
    }

    private boolean optionalValidUpi() {
        String value = upiField.getText();
        if (value == null || value.trim().isEmpty()) {
            return true;
        }
        return value.trim().matches("(?i)^[A-Z0-9]{8,20}$");
    }

    private void setFieldState(TextField field, boolean valid) {
        field.getStyleClass().removeAll("student-field-valid", "student-field-invalid");
        String value = field.getText();
        if (value == null || value.isBlank()) {
            return;
        }
        field.getStyleClass().add(valid ? "student-field-valid" : "student-field-invalid");
    }

    private void setComboState(ComboBox<?> comboBox, boolean valid) {
        comboBox.getStyleClass().removeAll("student-field-valid", "student-field-invalid");
        if (comboBox.getValue() == null) {
            return;
        }
        comboBox.getStyleClass().add(valid ? "student-field-valid" : "student-field-invalid");
    }

    private void updateModeBadge() {
        boolean editingMode = editing != null;
        modeBadge.setText(editingMode ? "Editing Existing Student" : "Create New Student");
        modeBadge.getStyleClass().removeAll("student-mode-create", "student-mode-edit");
        modeBadge.getStyleClass().add(editingMode ? "student-mode-edit" : "student-mode-create");
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    @Override
    public void refresh() {
        table.refresh();
    }
}
