package com.schaccs.ui.enrollments;

import com.schaccs.enums.StudentStatus;
import com.schaccs.model.student.MidTermStudent;
import com.schaccs.model.student.Student;
import com.schaccs.service.Services;
import com.schaccs.service.student.MidTermEnrollmentService;
import com.schaccs.store.StudentStore;
import com.schaccs.ui.component.CurrencyField;
import com.schaccs.ui.layout.MainLayout;
import com.schaccs.util.AlertUtil;
import com.schaccs.util.CurrencyUtil;
import com.schaccs.util.DateUtil;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Mid-Term Enrollments workspace: register a student admitted part-way through
 * the current term, optionally bill the remaining part of the current term with
 * a custom mid-term fee, and let the end-of-term transition charge the full
 * standard tuition automatically from the next term onward.
 */
public class MidTermEnrollmentView extends VBox implements MainLayout.Refreshable {

    private final MidTermEnrollmentService service = Services.getInstance().midTermEnrollment();

    private final TableView<MidTermStudent> table = new TableView<>();

    private final Label hintDetail = new Label();

    public MidTermEnrollmentView() {
        setSpacing(14);
        setPadding(new Insets(4));

        Label heading = new Label("Mid-Term Enrollments");
        heading.getStyleClass().add("section-title");

        Label sub = new Label("Students admitted part-way through the current term. Tick "
                + "\"Charge for Current Mid-Term?\" to bill only the remaining part with a custom fee; "
                + "the full standard tuition is charged automatically from the next term onward.");
        sub.getStyleClass().add("muted");
        sub.setWrapText(true);

        getChildren().addAll(heading, sub, buildHintCard(), buildTableCard());
    }

    private VBox buildHintCard() {
        hintDetail.getStyleClass().add("muted");
        hintDetail.setWrapText(true);

        VBox card = new VBox(8, new Label("Billing"),
                hintDetail);
        card.getStyleClass().add("card");
        return card;
    }

    private VBox buildTableCard() {
        Button enrollBtn = new Button("Enroll Student");
        enrollBtn.getStyleClass().add("primary-button");
        enrollBtn.setOnAction(e -> showEnrollDialog());

        Button editBtn = new Button("Edit");
        editBtn.getStyleClass().add("secondary-button");
        editBtn.setOnAction(e -> {
            MidTermStudent selected = table.getSelectionModel().getSelectedItem();
            if (selected == null) {
                AlertUtil.warn("No selection", "Select an enrollment to edit.");
                return;
            }
            showEditDialog(selected);
        });

        Button deleteBtn = new Button("Delete");
        deleteBtn.getStyleClass().add("secondary-button");
        deleteBtn.setOnAction(e -> {
            MidTermStudent selected = table.getSelectionModel().getSelectedItem();
            if (selected == null) {
                AlertUtil.warn("No selection", "Select an enrollment to delete.");
                return;
            }
            if (AlertUtil.confirm("Delete Enrollment",
                    "Remove the mid-term enrollment for " + selected.getName() + "?")) {
                service.deleteEnrollment(selected);
                refresh();
            }
        });

        Button refreshBtn = new Button("Refresh");
        refreshBtn.getStyleClass().add("secondary-button");
        refreshBtn.setOnAction(e -> refresh());

        HBox toolbar = new HBox(10, enrollBtn, editBtn, deleteBtn, refreshBtn);
        toolbar.setAlignment(Pos.CENTER_LEFT);

        setupTable();

        VBox card = new VBox(10, toolbar, table);
        card.getStyleClass().add("card");
        VBox.setVgrow(table, Priority.ALWAYS);
        return card;
    }

    private void setupTable() {
        TableColumn<MidTermStudent, String> admCol = new TableColumn<>("Student ID");
        admCol.setCellValueFactory(c -> c.getValue().admissionNumberProperty());
        admCol.setPrefWidth(140);

        TableColumn<MidTermStudent, String> nameCol = new TableColumn<>("Name");
        nameCol.setCellValueFactory(c -> c.getValue().nameProperty());
        nameCol.setPrefWidth(220);

        TableColumn<MidTermStudent, String> joinedCol = new TableColumn<>("Date Joined");
        joinedCol.setCellValueFactory(c -> new SimpleStringProperty(DateUtil.format(c.getValue().getDateJoined())));
        joinedCol.setPrefWidth(130);

        TableColumn<MidTermStudent, String> feeCol = new TableColumn<>("Mid-Term Fee Charged");
        feeCol.setCellValueFactory(c -> new SimpleStringProperty(CurrencyUtil.format(c.getValue().getMidTermFee())));
        feeCol.setPrefWidth(160);

        TableColumn<MidTermStudent, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(c -> c.getValue().statusProperty());
        statusCol.setPrefWidth(120);

        table.getColumns().addAll(admCol, nameCol, joinedCol, feeCol, statusCol);
        table.setItems(service.getEnrollments());
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(new Label("No mid-term enrollments yet. Enroll a student to bill the current term."));
        table.setRowFactory(tv -> {
            TableRow<MidTermStudent> row = new TableRow<>();
            row.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !row.isEmpty()) {
                    showEditDialog(row.getItem());
                }
            });
            return row;
        });
    }

    private void showEnrollDialog() {
        List<Student> candidates = new ArrayList<>();
        for (Student s : StudentStore.getInstance().getStudents()) {
            if (s.getStatus() == StudentStatus.ACTIVE && service.findByStudentId(s.getId()).isEmpty()) {
                candidates.add(s);
            }
        }
        if (candidates.isEmpty()) {
            AlertUtil.info("No students available",
                    "There are no active students left to enroll mid-term.");
            return;
        }

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Enroll Mid-Term Student");
        dialog.setHeaderText("Register a student admitted mid-term");

        ComboBox<Student> studentBox = studentBox(candidates);

        DatePicker datePicker = new DatePicker(LocalDate.now());
        datePicker.setPrefWidth(260);

        CheckBox chargeBox = new CheckBox("Charge for Current Mid-Term?");
        CurrencyField feeField = new CurrencyField();
        feeField.setAmount(BigDecimal.ZERO);
        feeField.setPrefWidth(260);
        feeField.setDisable(true);
        chargeBox.selectedProperty().addListener((obs, old, val) -> {
            feeField.setDisable(!val);
            if (val) {
                feeField.requestFocus();
            } else {
                feeField.setAmount(BigDecimal.ZERO);
            }
        });

        Label autoHint = new Label("Full standard tuition is charged automatically from the next term onward.");
        autoHint.getStyleClass().add("muted");
        autoHint.setWrapText(true);

        VBox content = new VBox(10,
                new Label("Student:"), studentBox,
                new Label("Date Joined:"), datePicker,
                chargeBox, feeField, autoHint);
        content.setPadding(new Insets(10));
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, ButtonType.OK);

        dialog.showAndWait().filter(ButtonType.OK::equals).ifPresent(btn -> {
            Student student = studentBox.getValue();
            if (student == null) {
                AlertUtil.warn("Validation", "Select a student.");
                return;
            }
            BigDecimal fee = feeField.getAmount();
            List<String> errors = service.enrollStudent(student, datePicker.getValue(),
                    chargeBox.isSelected(), fee);
            if (!errors.isEmpty()) {
                AlertUtil.warn("Validation", String.join("\n", errors));
                return;
            }
            refresh();
        });
    }

    private void showEditDialog(MidTermStudent enrollment) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Edit Mid-Term Enrollment");
        dialog.setHeaderText(enrollment.getAdmissionNumber() + " — " + enrollment.getName());

        Label studentLabel = new Label(enrollment.getAdmissionNumber() + " — " + enrollment.getName());
        studentLabel.getStyleClass().add("pay-value");

        DatePicker datePicker = new DatePicker(enrollment.getDateJoined());
        datePicker.setPrefWidth(260);

        CheckBox chargeBox = new CheckBox("Charge for Current Mid-Term?");
        chargeBox.setSelected(enrollment.isChargeCurrentTerm());
        CurrencyField feeField = new CurrencyField();
        feeField.setAmount(enrollment.getMidTermFee());
        feeField.setPrefWidth(260);
        feeField.setDisable(!enrollment.isChargeCurrentTerm());
        chargeBox.selectedProperty().addListener((obs, old, val) -> {
            feeField.setDisable(!val);
            if (!val) {
                feeField.setAmount(BigDecimal.ZERO);
            }
        });

        Label autoHint = new Label("Full standard tuition is charged automatically from the next term onward.");
        autoHint.getStyleClass().add("muted");
        autoHint.setWrapText(true);

        VBox content = new VBox(10,
                new Label("Student:"), studentLabel,
                new Label("Date Joined:"), datePicker,
                chargeBox, feeField, autoHint);
        content.setPadding(new Insets(10));
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, ButtonType.OK);

        dialog.showAndWait().filter(ButtonType.OK::equals).ifPresent(btn -> {
            List<String> errors = service.updateEnrollment(enrollment, datePicker.getValue(),
                    chargeBox.isSelected(), feeField.getAmount());
            if (!errors.isEmpty()) {
                AlertUtil.warn("Validation", String.join("\n", errors));
                return;
            }
            refresh();
        });
    }

    private ComboBox<Student> studentBox(List<Student> candidates) {
        ComboBox<Student> box = new ComboBox<>();
        box.getItems().addAll(candidates);
        box.setPrefWidth(320);
        box.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(Student item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "" : item.getAdmissionNumber() + " — " + item.getName()
                        + " (" + item.getClassLabel() + ")");
            }
        });
        box.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(Student item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "" : item.getAdmissionNumber() + " — " + item.getName());
            }
        });
        return box;
    }

    @Override
    public void refresh() {
        int enrolled = service.getEnrollments().size();
        long charged = service.getEnrollments().stream()
                .filter(MidTermStudent::isChargeCurrentTerm)
                .count();
        BigDecimal total = service.getEnrollments().stream()
                .map(MidTermStudent::getMidTermFee)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        hintDetail.setText(enrolled + " student(s) enrolled mid-term; " + charged
                + " charged for the current term (" + CurrencyUtil.format(total)
                + " billed). Full standard tuition applies automatically from the next term onward.");
        table.refresh();
    }
}
