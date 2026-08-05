package com.schaccs.ui.fees;

import com.schaccs.config.AppConfig;
import com.schaccs.enums.BoardingStatus;
import com.schaccs.model.fee.FeeStructure;
import com.schaccs.model.fee.FeeStructureTemplate;
import com.schaccs.service.fee.FeeStructureTemplateService;
import com.schaccs.store.FeeStructureStore;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

public class FeeStructureTemplateDialog extends Stage {

    private final FeeStructureTemplateService templateService;
    private final ListView<FeeStructureTemplate> templateList = new ListView<>();
    private final TextField nameField = new TextField();
    private final TextField yearField = new TextField();
    private final ComboBox<String> formClassBox = new ComboBox<>();
    private final ComboBox<BoardingStatus> boardingBox = new ComboBox<>();

    public FeeStructureTemplateDialog(FeeStructureStore store, Window owner, FeeStructure currentStructure) {
        this.templateService = new FeeStructureTemplateService(store);
        setTitle("Fee Structure Templates");
        initOwner(owner);
        initModality(Modality.APPLICATION_MODAL);

        templateList.setItems(templateService.getTemplates());
        templateList.setPrefSize(420, 220);

        if (currentStructure != null) {
            nameField.setText(currentStructure.getName());
        }
        nameField.setPromptText("Template name (default: current structure)");
        yearField.setText(String.valueOf(AppConfig.getInstance().getAcademicYear()));
        formClassBox.getItems().addAll("ALL", "Form 1", "Form 2", "Form 3", "Form 4");
        formClassBox.setEditable(true);
        formClassBox.setValue("ALL");
        boardingBox.setItems(FXCollections.observableArrayList(BoardingStatus.values()));
        boardingBox.setValue(BoardingStatus.BOARDING);

        GridPane form = new GridPane();
        form.setHgap(10);
        form.setVgap(8);
        form.add(new Label("Template Name:"), 0, 0);
        form.add(nameField, 1, 0);
        form.add(new Label("Academic Year:"), 0, 1);
        form.add(yearField, 1, 1);
        form.add(new Label("Form Class:"), 0, 2);
        form.add(formClassBox, 1, 2);
        form.add(new Label("Boarding Status:"), 0, 3);
        form.add(boardingBox, 1, 3);

        Button saveBtn = new Button("Save Current Structure as Template");
        saveBtn.getStyleClass().add("primary-button");
        saveBtn.setDisable(currentStructure == null);
        saveBtn.setOnAction(e -> saveTemplate(currentStructure));

        Button createBtn = new Button("Create Structure from Selected Template");
        createBtn.getStyleClass().add("success-button");
        createBtn.setOnAction(e -> createFromTemplate());

        Button deleteBtn = new Button("Delete Template");
        deleteBtn.getStyleClass().add("danger-button");
        deleteBtn.setOnAction(e -> deleteTemplate());

        Button closeBtn = new Button("Close");
        closeBtn.getStyleClass().add("secondary-button");
        closeBtn.setOnAction(e -> close());

        HBox actions = new HBox(10, saveBtn, createBtn, deleteBtn, closeBtn);
        actions.setAlignment(Pos.CENTER_LEFT);

        VBox root = new VBox(12,
                new Label("Saved templates (reusable across years/classes):"),
                templateList,
                form,
                actions);
        root.setPadding(new Insets(16));
        root.setPrefSize(620, 460);
        VBox.setVgrow(templateList, Priority.ALWAYS);
        setScene(new javafx.scene.Scene(root));
    }

    private void saveTemplate(FeeStructure structure) {
        if (structure == null) return;
        String name = nameField.getText().trim();
        if (!name.isBlank()) {
            structure.setName(name);
        }
        templateService.saveAsTemplate(structure);
        templateList.refresh();
    }

    private void createFromTemplate() {
        FeeStructureTemplate template = templateList.getSelectionModel().getSelectedItem();
        if (template == null) return;
        int year;
        try {
            year = Integer.parseInt(yearField.getText().trim());
        } catch (NumberFormatException e) {
            year = AppConfig.getInstance().getAcademicYear();
        }
        String formClass = formClassBox.getValue();
        if (formClass == null || formClass.isBlank()) formClass = "ALL";
        BoardingStatus status = boardingBox.getValue();

        FeeStructure fs = templateService.buildStructure(template, year, formClass, status,
                template.getName() + " " + year);
        FeeStructureStore.getInstance().addStructure(fs);
        com.schaccs.repository.PersistenceService.getInstance().saveAll();
        close();
    }

    private void deleteTemplate() {
        FeeStructureTemplate template = templateList.getSelectionModel().getSelectedItem();
        if (template == null) return;
        templateService.deleteTemplate(template);
    }
}
