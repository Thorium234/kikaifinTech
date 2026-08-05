package com.schaccs.ui.fees;

import com.schaccs.config.AppConfig;
import com.schaccs.enums.BoardingStatus;
import com.schaccs.model.fee.FeeStructure;
import com.schaccs.model.fee.FeeStructureItem;
import com.schaccs.repository.PersistenceService;
import com.schaccs.service.importer.FeeStructureImportService;
import com.schaccs.store.FeeStructureStore;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

public class FeeStructureImportDialog extends Stage {

    private final FeeStructureStore store;
    private final Path file;
    private final FeeStructureImportService importService = new FeeStructureImportService();
    private final TextField nameField = new TextField();
    private final TextField yearField = new TextField();
    private final ComboBox<String> formClassBox = new ComboBox<>();
    private final ComboBox<BoardingStatus> boardingBox = new ComboBox<>();
    private final Label summaryLabel = new Label();
    private final TextArea warningsArea = new TextArea();

    public FeeStructureImportDialog(FeeStructureStore store, Window owner, Path file) {
        this.store = store;
        this.file = file;
        setTitle("Import Fee Structure");
        initOwner(owner);
        initModality(Modality.APPLICATION_MODAL);

        String stem = file.getFileName().toString();
        int dot = stem.lastIndexOf('.');
        nameField.setText(dot > 0 ? stem.substring(0, dot) : stem);
        yearField.setText(String.valueOf(AppConfig.getInstance().getAcademicYear()));
        formClassBox.getItems().addAll("ALL", "Form 1", "Form 2", "Form 3", "Form 4");
        formClassBox.setEditable(true);
        formClassBox.setValue("ALL");
        boardingBox.setItems(FXCollections.observableArrayList(BoardingStatus.values()));
        boardingBox.setValue(BoardingStatus.BOARDING);

        GridPane form = new GridPane();
        form.setHgap(10);
        form.setVgap(8);
        form.add(new Label("Name:"), 0, 0);
        form.add(nameField, 1, 0);
        form.add(new Label("Academic Year:"), 0, 1);
        form.add(yearField, 1, 1);
        form.add(new Label("Form Class:"), 0, 2);
        form.add(formClassBox, 1, 2);
        form.add(new Label("Boarding Status:"), 0, 3);
        form.add(boardingBox, 1, 3);

        warningsArea.setEditable(false);
        warningsArea.setPrefHeight(110);
        warningsArea.setWrapText(true);

        Button importBtn = new Button("Import");
        importBtn.getStyleClass().add("primary-button");
        importBtn.setOnAction(e -> doImport());

        Button cancelBtn = new Button("Cancel");
        cancelBtn.getStyleClass().add("secondary-button");
        cancelBtn.setOnAction(e -> close());

        HBox actions = new HBox(10, importBtn, cancelBtn);
        actions.setAlignment(Pos.CENTER_LEFT);

        VBox root = new VBox(12,
                new Label("Importing: " + file.getFileName()),
                form,
                summaryLabel,
                new Label("Warnings (rows skipped):"),
                warningsArea,
                actions);
        root.setPadding(new Insets(16));
        root.setPrefSize(520, 420);
        setScene(new javafx.scene.Scene(root));
        loadPreview();
    }

    private void loadPreview() {
        try {
            java.util.List<Map<String, String>> rows = importService.parseFile(file);
            FeeStructureImportService.ImportResult result =
                    importService.parseItems(rows, boardingBox.getValue());
            summaryLabel.setText("Parsed " + rows.size() + " row(s); "
                    + result.items().size() + " valid fee item(s) will be imported.");
            warningsArea.setText(String.join("\n", result.warnings()));
        } catch (IOException ex) {
            summaryLabel.setText("Could not read file: " + ex.getMessage());
            warningsArea.setText("");
        }
    }

    private void doImport() {
        String name = nameField.getText().trim();
        if (name.isBlank()) {
            name = "Imported Fee Structure";
        }
        int year;
        try {
            year = Integer.parseInt(yearField.getText().trim());
        } catch (NumberFormatException e) {
            year = AppConfig.getInstance().getAcademicYear();
        }
        String formClass = formClassBox.getValue();
        if (formClass == null || formClass.isBlank()) formClass = "ALL";

        try {
            java.util.List<Map<String, String>> rows = importService.parseFile(file);
            FeeStructureImportService.ImportResult result =
                    importService.parseItems(rows, boardingBox.getValue());
            FeeStructure fs = new FeeStructure(year, formClass, boardingBox.getValue(), name);
            for (FeeStructureItem item : result.items()) {
                fs.addItem(item);
            }
            store.addStructure(fs);
            PersistenceService.getInstance().saveAll();
            close();
        } catch (IOException ex) {
            summaryLabel.setText("Could not read file: " + ex.getMessage());
        }
    }
}
