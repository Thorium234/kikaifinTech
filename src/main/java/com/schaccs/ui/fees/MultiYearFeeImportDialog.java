package com.schaccs.ui.fees;

import com.schaccs.model.fee.FeeStructure;
import com.schaccs.repository.PersistenceService;
import com.schaccs.service.importer.MultiYearFeeImportService;
import com.schaccs.store.FeeStructureStore;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public class MultiYearFeeImportDialog extends Stage {

    private final FeeStructureStore store;
    private final Path file;
    private final MultiYearFeeImportService importService;
    private final Label summaryLabel = new Label();
    private final TextArea warningsArea = new TextArea();

    public MultiYearFeeImportDialog(FeeStructureStore store, Window owner, Path file) {
        this(store, owner, file, new MultiYearFeeImportService());
    }

    public MultiYearFeeImportDialog(FeeStructureStore store, Window owner, Path file,
                                     MultiYearFeeImportService importService) {
        this.store = store;
        this.file = file;
        this.importService = importService;
        setTitle("Multi-Year Fee Structure Import");
        initOwner(owner);
        initModality(Modality.APPLICATION_MODAL);

        Label fileLabel = new Label("Importing: " + file.getFileName());
        fileLabel.getStyleClass().add("section-title");

        Label info = new Label("Expected columns: Academic Year, Student Category (Boarding/Day), "
                + "Votehead Code/Name, Term 1 Fee, Term 2 Fee, Term 3 Fee");
        info.getStyleClass().add("muted");
        info.setWrapText(true);

        warningsArea.setEditable(false);
        warningsArea.setPrefHeight(140);
        warningsArea.setWrapText(true);

        Button importBtn = new Button("Import All Structures");
        importBtn.getStyleClass().add("primary-button");
        importBtn.setOnAction(e -> doImport());

        Button cancelBtn = new Button("Cancel");
        cancelBtn.getStyleClass().add("secondary-button");
        cancelBtn.setOnAction(e -> close());

        HBox actions = new HBox(10, importBtn, cancelBtn);
        actions.setAlignment(Pos.CENTER_LEFT);

        VBox root = new VBox(12, fileLabel, info, summaryLabel,
                new Label("Warnings:"), warningsArea, actions);
        root.setPadding(new Insets(16));
        root.setPrefSize(560, 440);
        setScene(new javafx.scene.Scene(root));
        loadPreview();
    }

    private void loadPreview() {
        try {
            List<Map<String, String>> rows = importService.parseFile(file);
            MultiYearFeeImportService.MultiImportResult preview =
                    importService.parseAndBuild(rows);
            summaryLabel.setText("Parsed " + rows.size() + " row(s); "
                    + preview.structures().size() + " fee structure(s) created for "
                    + countYearCategories(preview.structures()) + " year/category combinations.");
            warningsArea.setText(String.join("\n", preview.warnings()));
        } catch (IOException ex) {
            summaryLabel.setText("Could not read file: " + ex.getMessage());
        }
    }

    private void doImport() {
        try {
            List<Map<String, String>> rows = importService.parseFile(file);
            MultiYearFeeImportService.MultiImportResult result =
                    importService.parseAndBuild(rows);
            PersistenceService.getInstance().saveAll();
            summaryLabel.setText("Imported " + result.structures().size()
                    + " structure(s). " + result.warnings().size() + " warning(s).");
            warningsArea.setText(String.join("\n", result.warnings()));
        } catch (IOException ex) {
            summaryLabel.setText("Import failed: " + ex.getMessage());
        }
    }

    private int countYearCategories(List<FeeStructure> structures) {
        return (int) structures.stream()
                .map(s -> s.getAcademicYear() + "|" + s.getBoardingStatus())
                .distinct()
                .count();
    }
}
