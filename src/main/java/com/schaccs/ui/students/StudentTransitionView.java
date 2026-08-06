package com.schaccs.ui.students;

import com.schaccs.enums.AcademicTerm;
import com.schaccs.enums.BoardingStatus;
import com.schaccs.model.student.Student;
import com.schaccs.service.student.StudentTransitionService;
import com.schaccs.store.StudentStore;
import com.schaccs.ui.component.SearchBar;
import com.schaccs.ui.layout.MainLayout;
import com.schaccs.util.AlertUtil;
import com.schaccs.util.CurrencyUtil;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.util.List;

/**
 * Sidebar view for moving a student between Day and Boarding. The transition
 * bills only the selected term's fee difference; past terms billed under the
 * previous status are never touched.
 */
public class StudentTransitionView extends VBox implements MainLayout.Refreshable {

    private final StudentTransitionService service;
    private final SearchBar searchBar = new SearchBar("Search by name, admission no, class…");
    private final ComboBox<AcademicTerm> termBox = new ComboBox<>();
    private final TableView<Student> table = new TableView<>();
    private final FilteredList<Student> filtered;

    public StudentTransitionView() {
        this(new StudentTransitionService(), StudentStore.getInstance().getStudents());
    }

    public StudentTransitionView(StudentTransitionService service, javafx.collections.ObservableList<Student> source) {
        this.service = service;
        this.filtered = new FilteredList<>(source, s -> s != null
                && s.getStatus() == com.schaccs.enums.StudentStatus.ACTIVE);
        setSpacing(14);
        setPadding(new Insets(4));

        Label heading = new Label("Student Transitions");
        heading.getStyleClass().add("section-title");

        Label sub = new Label("Move a student between Day and Boarding. Only the fee for the "
                + "selected term is adjusted — previous terms keep their original billing.");
        sub.getStyleClass().add("muted");
        sub.setWrapText(true);

        termBox.getItems().addAll(AcademicTerm.TERM_1, AcademicTerm.TERM_2, AcademicTerm.TERM_3);
        termBox.setValue(AcademicTerm.TERM_1);
        termBox.setPrefWidth(120);

        searchBar.textProperty().addListener((obs, o, q) ->
                filtered.setPredicate(s -> s != null
                        && s.getStatus() == com.schaccs.enums.StudentStatus.ACTIVE
                        && s.matchesSearch(q)));

        Button toBoardingBtn = new Button("Transition to Boarding");
        toBoardingBtn.getStyleClass().add("primary-button");
        toBoardingBtn.setOnAction(e -> transition(BoardingStatus.BOARDING));

        Button toDayBtn = new Button("Transition to Day");
        toDayBtn.getStyleClass().add("secondary-button");
        toDayBtn.setOnAction(e -> transition(BoardingStatus.DAY));

        HBox toolbar = new HBox(10, searchBar, new Label("Term:"), termBox, toBoardingBtn, toDayBtn);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(searchBar, Priority.ALWAYS);

        setupTable();

        VBox card = new VBox(10, sub, toolbar, table);
        card.getStyleClass().add("card");
        VBox.setVgrow(table, Priority.ALWAYS);

        getChildren().addAll(heading, card);
    }

    private void setupTable() {
        TableColumn<Student, String> adm = new TableColumn<>("Adm No");
        adm.setCellValueFactory(c -> c.getValue().admissionNumberProperty());
        adm.setPrefWidth(100);

        TableColumn<Student, String> name = new TableColumn<>("Name");
        name.setCellValueFactory(c -> c.getValue().nameProperty());
        name.setPrefWidth(220);

        TableColumn<Student, String> cls = new TableColumn<>("Class");
        cls.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getClassLabel()));
        cls.setPrefWidth(120);

        TableColumn<Student, String> status = new TableColumn<>("Boarding Status");
        status.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getBoardingStatus() != null
                        ? c.getValue().getBoardingStatus().getDisplayName() : ""));
        status.setPrefWidth(150);
        status.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().removeAll("student-status-active", "student-status-inactive");
                if (empty || item == null) {
                    setText(null);
                    return;
                }
                setText(item);
                Student s = getTableRow() != null ? (Student) getTableRow().getItem() : null;
                if (s != null && s.getBoardingStatus() == BoardingStatus.BOARDING) {
                    getStyleClass().add("student-status-active");
                } else {
                    getStyleClass().add("student-status-inactive");
                }
            }
        });

        table.getColumns().addAll(adm, name, cls, status);
        table.setItems(filtered);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(new Label("No active students found."));
        table.setPrefHeight(420);
    }

    private void transition(BoardingStatus target) {
        Student student = table.getSelectionModel().getSelectedItem();
        if (student == null) {
            AlertUtil.warn("Select student", "Select a student to transition.");
            return;
        }
        if (student.getBoardingStatus() == target) {
            AlertUtil.warn("Already " + target.getDisplayName(),
                    student.getName() + " is already a " + target.getDisplayName() + " scholar.");
            return;
        }
        AcademicTerm term = termBox.getValue();
        if (term == null) {
            AlertUtil.warn("Select term", "Select the term for this transition.");
            return;
        }

        List<StudentTransitionService.TransitionDelta> deltas = service.preview(student, target, term);
        BigDecimal net = deltas.stream()
                .map(StudentTransitionService.TransitionDelta::delta)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        StringBuilder message = new StringBuilder();
        message.append(student.getName()).append(" (").append(student.getAdmissionNumber()).append(")\n");
        message.append("Move from ").append(student.getBoardingStatus().getDisplayName())
                .append(" to ").append(target.getDisplayName())
                .append(" for ").append(term.getDisplayName()).append(".\n\n");
        if (deltas.isEmpty()) {
            message.append("No fee change for this term (structures are equal).");
        } else {
            for (StudentTransitionService.TransitionDelta d : deltas) {
                if (d.delta().compareTo(BigDecimal.ZERO) > 0) {
                    message.append("+ ").append(CurrencyUtil.formatPlain(d.delta()))
                            .append(" — ").append(d.name()).append("\n");
                } else {
                    message.append("- ").append(CurrencyUtil.formatPlain(d.delta().negate()))
                            .append(" — ").append(d.name()).append(" (reduced)\n");
                }
            }
            message.append("\nNet change: ").append(CurrencyUtil.formatPlain(net)).append(" for ")
                    .append(term.getDisplayName()).append(".");
        }

        if (!AlertUtil.confirm("Confirm Transition", message.toString())) {
            return;
        }

        StudentTransitionService.TransitionResult result = service.apply(student, target, term);
        if (!result.success()) {
            AlertUtil.warn("Transition failed", String.join("\n", result.errors()));
            return;
        }
        AlertUtil.info("Transition complete",
                student.getName() + " is now " + target.getDisplayName() + ".\n"
                        + "Fee for " + term.getDisplayName() + " updated"
                        + (deltas.isEmpty() ? "" : " (" + CurrencyUtil.formatPlain(net) + " net)."));
        refresh();
    }

    @Override
    public void refresh() {
        filtered.setPredicate(s -> s != null
                && s.getStatus() == com.schaccs.enums.StudentStatus.ACTIVE);
        table.refresh();
    }
}
