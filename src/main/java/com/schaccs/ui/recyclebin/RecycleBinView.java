package com.schaccs.ui.recyclebin;

import com.schaccs.model.student.DeletedStudent;
import com.schaccs.service.Services;
import com.schaccs.service.student.StudentService;
import com.schaccs.store.RecycleBinStore;
import com.schaccs.ui.component.TypeToConfirmDialog;
import com.schaccs.ui.layout.MainLayout;
import com.schaccs.util.AlertUtil;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class RecycleBinView extends VBox implements MainLayout.Refreshable {

    private final StudentService studentService = Services.getInstance().student();
    private final TableView<DeletedStudent> table = new TableView<>();
    private final Label summary = new Label();

    public RecycleBinView() {
        setSpacing(14);
        setPadding(new Insets(4));

        Label heading = new Label("Recycle Bin");
        heading.getStyleClass().add("section-title");

        Label badge = new Label("Recycle Bin");
        badge.getStyleClass().add("student-header-badge");
        Label sub = new Label("Students deleted from the registry land here. Restore them to re-enter the "
                + "school records, or delete them permanently. Deleting permanently cannot be undone.");
        sub.getStyleClass().add("muted");

        Button restoreBtn = new Button("Restore");
        restoreBtn.getStyleClass().add("success-button");
        restoreBtn.setGraphic(new FontIcon(FontAwesomeSolid.TRASH_RESTORE));
        restoreBtn.setOnAction(e -> restoreSelected());

        Button purgeBtn = new Button("Delete Permanently");
        purgeBtn.getStyleClass().add("danger-button");
        purgeBtn.setGraphic(new FontIcon(FontAwesomeSolid.TRASH));
        purgeBtn.setOnAction(e -> purgeSelected());

        HBox toolbar = new HBox(10, restoreBtn, purgeBtn);
        toolbar.setAlignment(Pos.CENTER_LEFT);

        summary.getStyleClass().add("muted");
        summary.setWrapText(true);

        setupTable();

        VBox card = new VBox(10, badge, sub, toolbar, summary, table);
        card.getStyleClass().addAll("card", "student-table-card");
        VBox.setVgrow(table, Priority.ALWAYS);

        getChildren().addAll(heading, card);
        refresh();
    }

    private void setupTable() {
        table.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

        TableColumn<DeletedStudent, String> adm = new TableColumn<>("Adm No");
        adm.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getAdmissionNumber()));
        adm.setPrefWidth(110);

        TableColumn<DeletedStudent, String> name = new TableColumn<>("Name");
        name.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getName()));
        name.setPrefWidth(220);

        TableColumn<DeletedStudent, String> cls = new TableColumn<>("Class");
        cls.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getClassLabel()));
        cls.setPrefWidth(140);

        TableColumn<DeletedStudent, String> deleted = new TableColumn<>("Deleted On");
        deleted.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getDeletedAt() == null ? "" : c.getValue().getDeletedAt().toLocalDate().toString()));
        deleted.setPrefWidth(130);

        table.getColumns().add(adm);
        table.getColumns().add(name);
        table.getColumns().add(cls);
        table.getColumns().add(deleted);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
    }

    @Override
    public void refresh() {
        ObservableList<DeletedStudent> items = RecycleBinStore.getInstance().getItems();
        table.setItems(items);
        summary.setText(items.isEmpty()
                ? "The recycle bin is empty."
                : items.size() + " deleted student(s) in the recycle bin.");
        table.refresh();
    }

    private List<DeletedStudent> selected() {
        return new ArrayList<>(table.getSelectionModel().getSelectedItems());
    }

    private void restoreSelected() {
        List<DeletedStudent> items = selected();
        if (items.isEmpty()) {
            AlertUtil.warn("No selection", "Select one or more deleted students to restore.");
            return;
        }
        List<String> errors = studentService.restore(items);
        refresh();
        if (errors.isEmpty()) {
            AlertUtil.info("Restored", items.size() + " student(s) restored to the registry.");
        } else {
            AlertUtil.warn("Not fully restored", String.join("\n", errors));
        }
    }

    private void purgeSelected() {
        List<DeletedStudent> items = selected();
        if (items.isEmpty()) {
            AlertUtil.warn("No selection", "Select one or more deleted students to delete permanently.");
            return;
        }
        String detail = items.stream()
                .map(d -> "• " + d.getAdmissionNumber() + " — " + d.getName())
                .collect(Collectors.joining("\n"));
        TypeToConfirmDialog dialog = new TypeToConfirmDialog(
                "Delete Permanently",
                "This permanently deletes " + items.size() + " record(s). This cannot be undone.\n\n" + detail,
                "DELETE", "Delete Permanently");
        Optional<Boolean> result = dialog.showAndWait();
        if (!result.isPresent() || !result.get()) {
            return;
        }
        studentService.purge(items);
        refresh();
        AlertUtil.info("Deleted permanently", items.size() + " record(s) permanently removed from the recycle bin.");
    }
}
