package com.schaccs.ui.school;

import com.schaccs.model.school.SchoolFormClass;
import com.schaccs.model.school.SchoolStream;
import com.schaccs.service.school.SchoolCustomService;
import com.schaccs.ui.layout.MainLayout;
import com.schaccs.util.AlertUtil;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.List;

public class SchoolCustomView extends VBox implements MainLayout.Refreshable {

    private final SchoolCustomService service = new SchoolCustomService();

    private final TableView<SchoolFormClass> formTable = new TableView<>();
    private final TableView<SchoolStream> streamTable = new TableView<>();
    private final TabPane tabPane = new TabPane();

    public SchoolCustomView() {
        setSpacing(14);
        setPadding(new Insets(4));

        Label heading = new Label("School Customization");
        heading.getStyleClass().add("section-title");

        Tab formTab = new Tab("Form Classes");
        Tab streamTab = new Tab("Streams");
        tabPane.getTabs().addAll(formTab, streamTab);
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        formTab.setContent(buildFormClassPanel());
        streamTab.setContent(buildStreamPanel());

        VBox.setVgrow(tabPane, Priority.ALWAYS);
        getChildren().addAll(heading, tabPane);
    }

    private VBox buildFormClassPanel() {
        Label badge = new Label("Form Classes");
        badge.getStyleClass().add("student-header-badge");
        Label sub = new Label("Manage school form/class levels (e.g. Form 1, Form 2, Form 3, Form 4).");
        sub.getStyleClass().add("muted");

        Button addBtn = new Button("Add Form Class");
        addBtn.getStyleClass().add("primary-button");
        addBtn.setOnAction(e -> showFormClassDialog(null));

        Button editBtn = new Button("Edit");
        editBtn.getStyleClass().add("secondary-button");
        editBtn.setOnAction(e -> {
            SchoolFormClass selected = formTable.getSelectionModel().getSelectedItem();
            if (selected == null) { AlertUtil.warn("No selection", "Select a form class to edit."); return; }
            showFormClassDialog(selected);
        });

        Button deleteBtn = new Button("Delete");
        deleteBtn.getStyleClass().add("secondary-button");
        deleteBtn.setOnAction(e -> {
            SchoolFormClass selected = formTable.getSelectionModel().getSelectedItem();
            if (selected == null) { AlertUtil.warn("No selection", "Select a form class to delete."); return; }
            if (AlertUtil.confirm("Delete", "Remove \"" + selected.getName() + "\"?")) {
                service.removeFormClass(selected);
                refresh();
            }
        });

        HBox toolbar = new HBox(10, addBtn, editBtn, deleteBtn);
        toolbar.setAlignment(Pos.CENTER_LEFT);

        setupFormClassTable();

        VBox card = new VBox(10, badge, sub, toolbar, formTable);
        card.getStyleClass().addAll("card");
        VBox.setVgrow(formTable, Priority.ALWAYS);
        return card;
    }

    private VBox buildStreamPanel() {
        Label badge = new Label("Streams");
        badge.getStyleClass().add("student-header-badge");
        Label sub = new Label("Manage school streams/arms (e.g. A, B, C, North, East).");
        sub.getStyleClass().add("muted");

        Button addBtn = new Button("Add Stream");
        addBtn.getStyleClass().add("primary-button");
        addBtn.setOnAction(e -> showStreamDialog(null));

        Button editBtn = new Button("Edit");
        editBtn.getStyleClass().add("secondary-button");
        editBtn.setOnAction(e -> {
            SchoolStream selected = streamTable.getSelectionModel().getSelectedItem();
            if (selected == null) { AlertUtil.warn("No selection", "Select a stream to edit."); return; }
            showStreamDialog(selected);
        });

        Button deleteBtn = new Button("Delete");
        deleteBtn.getStyleClass().add("secondary-button");
        deleteBtn.setOnAction(e -> {
            SchoolStream selected = streamTable.getSelectionModel().getSelectedItem();
            if (selected == null) { AlertUtil.warn("No selection", "Select a stream to delete."); return; }
            if (AlertUtil.confirm("Delete", "Remove stream \"" + selected.getName() + "\"?")) {
                service.removeStream(selected);
                refresh();
            }
        });

        HBox toolbar = new HBox(10, addBtn, editBtn, deleteBtn);
        toolbar.setAlignment(Pos.CENTER_LEFT);

        setupStreamTable();

        VBox card = new VBox(10, badge, sub, toolbar, streamTable);
        card.getStyleClass().addAll("card");
        VBox.setVgrow(streamTable, Priority.ALWAYS);
        return card;
    }

    private void setupFormClassTable() {
        TableColumn<SchoolFormClass, String> nameCol = new TableColumn<>("Form Class");
        nameCol.setCellValueFactory(c -> c.getValue().nameProperty());
        nameCol.setPrefWidth(300);

        formTable.getColumns().add(nameCol);
        formTable.setItems(service.getFormClasses());
        formTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        formTable.setRowFactory(tv -> {
            TableRow<SchoolFormClass> row = new TableRow<>();
            row.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !row.isEmpty()) {
                    showFormClassDialog(row.getItem());
                }
            });
            return row;
        });
    }

    private void setupStreamTable() {
        TableColumn<SchoolStream, String> nameCol = new TableColumn<>("Stream");
        nameCol.setCellValueFactory(c -> c.getValue().nameProperty());
        nameCol.setPrefWidth(300);

        streamTable.getColumns().add(nameCol);
        streamTable.setItems(service.getStreams());
        streamTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        streamTable.setRowFactory(tv -> {
            TableRow<SchoolStream> row = new TableRow<>();
            row.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !row.isEmpty()) {
                    showStreamDialog(row.getItem());
                }
            });
            return row;
        });
    }

    private void showFormClassDialog(SchoolFormClass existing) {
        boolean isEdit = existing != null;
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(isEdit ? "Edit Form Class" : "Add Form Class");
        dialog.setHeaderText(isEdit ? "Rename form class" : "Enter a new form class name");

        TextField nameField = new TextField();
        nameField.setPromptText("e.g. Form 1");
        if (isEdit) nameField.setText(existing.getName());

        VBox content = new VBox(10, new Label("Name:"), nameField);
        content.setPadding(new Insets(10));
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, ButtonType.OK);

        dialog.showAndWait().filter(ButtonType.OK::equals).ifPresent(btn -> {
            String name = nameField.getText().trim();
            if (name.isEmpty()) {
                AlertUtil.warn("Validation", "Name is required.");
                return;
            }
            List<String> errors;
            if (isEdit) {
                errors = service.updateFormClass(existing, name);
            } else {
                errors = service.addFormClass(name);
            }
            if (!errors.isEmpty()) {
                AlertUtil.warn("Validation", String.join("\n", errors));
                return;
            }
            refresh();
        });
    }

    private void showStreamDialog(SchoolStream existing) {
        boolean isEdit = existing != null;
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(isEdit ? "Edit Stream" : "Add Stream");
        dialog.setHeaderText(isEdit ? "Rename stream" : "Enter a new stream name");

        TextField nameField = new TextField();
        nameField.setPromptText("e.g. A");
        if (isEdit) nameField.setText(existing.getName());

        VBox content = new VBox(10, new Label("Name:"), nameField);
        content.setPadding(new Insets(10));
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, ButtonType.OK);

        dialog.showAndWait().filter(ButtonType.OK::equals).ifPresent(btn -> {
            String name = nameField.getText().trim();
            if (name.isEmpty()) {
                AlertUtil.warn("Validation", "Name is required.");
                return;
            }
            List<String> errors;
            if (isEdit) {
                errors = service.updateStream(existing, name);
            } else {
                errors = service.addStream(name);
            }
            if (!errors.isEmpty()) {
                AlertUtil.warn("Validation", String.join("\n", errors));
                return;
            }
            refresh();
        });
    }

    @Override
    public void refresh() {
        formTable.refresh();
        streamTable.refresh();
    }
}
