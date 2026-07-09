package com.schaccs.ui.students;

import com.schaccs.enums.BoardingStatus;
import com.schaccs.enums.StudentStatus;
import com.schaccs.model.student.Student;
import com.schaccs.service.fee.FeeCalculationService;
import com.schaccs.service.student.StudentService;
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
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.List;

public class StudentView extends VBox implements MainLayout.Refreshable {

    private final StudentService studentService = new StudentService();
    private final FeeCalculationService feeService = new FeeCalculationService();

    private final TableView<Student> table = new TableView<>();
    private final FilteredList<Student> filtered;
    private final SearchBar searchBar = new SearchBar("Search by name, admission no, class…");

    private final TextField admField = new TextField();
    private final TextField nameField = new TextField();
    private final TextField formField = new TextField();
    private final TextField streamField = new TextField();
    private final TextField phoneField = new TextField();
    private final TextField parentField = new TextField();
    private final ComboBox<BoardingStatus> boardingBox = new ComboBox<>();
    private final ComboBox<StudentStatus> statusBox = new ComboBox<>();
    private final ComboBox<String> genderBox = new ComboBox<>();

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

        Button inactiveBtn = new Button("Mark Inactive");
        inactiveBtn.getStyleClass().add("secondary-button");
        inactiveBtn.setOnAction(e -> markInactive());

        HBox toolbar = new HBox(10, searchBar, addBtn, saveBtn, inactiveBtn);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(searchBar, Priority.ALWAYS);

        setupTable();
        VBox tableCard = new VBox(8, table);
        tableCard.getStyleClass().add("card");
        VBox.setVgrow(table, Priority.ALWAYS);
        VBox.setVgrow(tableCard, Priority.ALWAYS);

        VBox formCard = buildForm();
        formCard.setPrefWidth(360);

        HBox body = new HBox(16, tableCard, formCard);
        HBox.setHgrow(tableCard, Priority.ALWAYS);
        VBox.setVgrow(body, Priority.ALWAYS);

        getChildren().addAll(heading, toolbar, body);
    }

    private void setupTable() {
        TableColumn<Student, String> adm = new TableColumn<>("Adm No");
        adm.setCellValueFactory(c -> c.getValue().admissionNumberProperty());
        adm.setPrefWidth(100);

        TableColumn<Student, String> name = new TableColumn<>("Name");
        name.setCellValueFactory(c -> c.getValue().nameProperty());
        name.setPrefWidth(180);

        TableColumn<Student, String> cls = new TableColumn<>("Class");
        cls.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getClassLabel()));
        cls.setPrefWidth(80);

        TableColumn<Student, String> board = new TableColumn<>("Status");
        board.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getBoardingStatus() != null ? c.getValue().getBoardingStatus().getDisplayName() : ""));
        board.setPrefWidth(90);

        TableColumn<Student, String> phone = new TableColumn<>("Phone");
        phone.setCellValueFactory(c -> c.getValue().phoneProperty());
        phone.setPrefWidth(110);

        TableColumn<Student, String> st = new TableColumn<>("Active");
        st.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getStatus() != null ? c.getValue().getStatus().getDisplayName() : ""));
        st.setPrefWidth(90);

        table.getColumns().addAll(adm, name, cls, board, phone, st);
        table.setItems(filtered);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.getSelectionModel().selectedItemProperty().addListener((obs, o, s) -> {
            if (s != null) {
                loadForm(s);
            }
        });
    }

    private VBox buildForm() {
        Label title = new Label("Student Details");
        title.getStyleClass().add("section-title");

        boardingBox.getItems().setAll(BoardingStatus.values());
        boardingBox.setValue(BoardingStatus.BOARDING);
        statusBox.getItems().setAll(StudentStatus.values());
        statusBox.setValue(StudentStatus.ACTIVE);
        genderBox.getItems().setAll("Male", "Female");
        genderBox.setValue("Male");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        int r = 0;
        grid.add(new Label("Admission No"), 0, r);
        grid.add(admField, 1, r++);
        grid.add(new Label("Full Name"), 0, r);
        grid.add(nameField, 1, r++);
        grid.add(new Label("Gender"), 0, r);
        grid.add(genderBox, 1, r++);
        grid.add(new Label("Form / Class"), 0, r);
        grid.add(formField, 1, r++);
        grid.add(new Label("Stream"), 0, r);
        grid.add(streamField, 1, r++);
        grid.add(new Label("Boarding"), 0, r);
        grid.add(boardingBox, 1, r++);
        grid.add(new Label("Parent"), 0, r);
        grid.add(parentField, 1, r++);
        grid.add(new Label("Phone"), 0, r);
        grid.add(phoneField, 1, r++);
        grid.add(new Label("Status"), 0, r);
        grid.add(statusBox, 1, r);

        admField.setPromptText("2026/009");
        nameField.setPromptText("Student full name");
        formField.setPromptText("Form 1");
        streamField.setPromptText("A");

        VBox box = new VBox(12, title, grid);
        box.getStyleClass().add("card");
        return box;
    }

    private void clearForm() {
        editing = null;
        table.getSelectionModel().clearSelection();
        admField.clear();
        nameField.clear();
        formField.clear();
        streamField.clear();
        phoneField.clear();
        parentField.clear();
        boardingBox.setValue(BoardingStatus.BOARDING);
        statusBox.setValue(StudentStatus.ACTIVE);
        genderBox.setValue("Male");
        admField.setDisable(false);
    }

    private void loadForm(Student s) {
        editing = s;
        admField.setText(s.getAdmissionNumber());
        admField.setDisable(true);
        nameField.setText(s.getName());
        formField.setText(s.getFormClass());
        streamField.setText(s.getStream());
        phoneField.setText(s.getPhone());
        parentField.setText(s.getParentName());
        boardingBox.setValue(s.getBoardingStatus());
        statusBox.setValue(s.getStatus());
        genderBox.setValue(s.getGender() != null ? s.getGender() : "Male");
    }

    private void save() {
        if (editing == null) {
            Student s = new Student();
            applyForm(s);
            List<String> errors = studentService.addStudent(s);
            if (!errors.isEmpty()) {
                AlertUtil.warn("Validation", String.join("\n", errors));
                return;
            }
            feeService.chargeAnnualFees(s);
            AlertUtil.info("Saved", "Student " + s.getAdmissionNumber() + " added and fees charged.");
            clearForm();
        } else {
            applyForm(editing);
            List<String> errors = studentService.updateStudent(editing);
            if (!errors.isEmpty()) {
                AlertUtil.warn("Validation", String.join("\n", errors));
            } else {
                AlertUtil.info("Saved", "Student details updated.");
            }
        }
        table.refresh();
    }

    private void applyForm(Student s) {
        s.setAdmissionNumber(admField.getText().trim());
        s.setName(nameField.getText().trim());
        s.setFormClass(formField.getText().trim());
        s.setStream(streamField.getText().trim());
        s.setPhone(phoneField.getText().trim());
        s.setParentName(parentField.getText().trim());
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

    @Override
    public void refresh() {
        table.refresh();
    }
}
