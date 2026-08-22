package com.schaccs.ui.component;

import com.schaccs.model.student.Student;
import com.schaccs.store.SchoolCustomStore;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Pos;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.List;

/**
 * Searchable student picker for dialogs with large registries: type to filter
 * by name/admission/class, narrow further by form and stream, then pick one row.
 */
public class StudentPicker extends VBox {

    private static final String ALL_FORMS = "All Forms";
    private static final String ALL_STREAMS = "All Streams";

    private final FilteredList<Student> filtered;
    private final TableView<Student> table = new TableView<>();
    private final ComboBox<String> formBox = new ComboBox<>();
    private final ComboBox<String> streamBox = new ComboBox<>();
    private final Label selectedLabel = new Label("No student selected");

    public StudentPicker(List<Student> candidates) {
        setSpacing(8);

        ObservableList<Student> source = FXCollections.observableArrayList(candidates);
        filtered = new FilteredList<>(source, s -> !s.isDeleted());

        SearchBar searchBar = new SearchBar("Search by name, admission no, class…");
        searchBar.textProperty().addListener((o, ov, q) -> applyFilters(q));

        populateFilters();
        formBox.valueProperty().addListener((o, ov, nv) -> applyFilters(searchBar.getText()));
        streamBox.valueProperty().addListener((o, ov, nv) -> applyFilters(searchBar.getText()));

        HBox filters = new HBox(10, formBox, streamBox);
        filters.setAlignment(Pos.CENTER_LEFT);

        setupTable();

        selectedLabel.getStyleClass().add("pay-value");
        selectedLabel.setWrapText(true);

        VBox.setVgrow(table, Priority.ALWAYS);
        getChildren().addAll(searchBar, filters, table, selectedLabel);
    }

    private void populateFilters() {
        formBox.setPrefWidth(180);
        streamBox.setPrefWidth(180);
        formBox.getItems().add(ALL_FORMS);
        streamBox.getItems().add(ALL_STREAMS);
        SchoolCustomStore store = SchoolCustomStore.getInstance();
        store.getFormClasses().forEach(fc -> formBox.getItems().add(fc.getName()));
        store.getStreams().forEach(s -> streamBox.getItems().add(s.getName()));
        formBox.setValue(ALL_FORMS);
        streamBox.setValue(ALL_STREAMS);
    }

    private void setupTable() {
        TableColumn<Student, String> admCol = new TableColumn<>("Student ID");
        admCol.setCellValueFactory(c -> c.getValue().admissionNumberProperty());
        admCol.setPrefWidth(140);

        TableColumn<Student, String> nameCol = new TableColumn<>("Name");
        nameCol.setCellValueFactory(c -> c.getValue().nameProperty());
        nameCol.setPrefWidth(240);

        TableColumn<Student, String> classCol = new TableColumn<>("Class");
        classCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getClassLabel()));
        classCol.setPrefWidth(120);

        table.getColumns().addAll(admCol, nameCol, classCol);
        table.setItems(filtered);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPrefHeight(300);
        table.setPlaceholder(new Label("No students match the current search/filters."));

        table.getSelectionModel().selectedItemProperty().addListener((o, ov, stu) -> {
            if (stu == null) {
                selectedLabel.setText("No student selected");
            } else {
                selectedLabel.setText("Selected: " + stu.getAdmissionNumber() + " — " + stu.getName()
                        + " (" + stu.getClassLabel() + ")");
            }
        });
    }

    private void applyFilters(String query) {
        String form = formBox.getValue();
        String stream = streamBox.getValue();
        filtered.setPredicate(s -> !s.isDeleted() && s.matchesSearch(query)
                && (form == null || ALL_FORMS.equals(form) || form.equals(s.getFormClass()))
                && (stream == null || ALL_STREAMS.equals(stream) || stream.equals(s.getStream())));
    }

    public Student getSelectedStudent() {
        return table.getSelectionModel().getSelectedItem();
    }

    public void selectFirst() {
        if (!table.getItems().isEmpty()) {
            table.getSelectionModel().select(0);
        }
    }
}
