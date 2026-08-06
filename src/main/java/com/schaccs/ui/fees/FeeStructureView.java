package com.schaccs.ui.fees;

import com.schaccs.config.CurrencyConfig;
import com.schaccs.enums.AcademicTerm;
import com.schaccs.enums.BoardingStatus;
import com.schaccs.model.fee.FeeStructure;
import com.schaccs.model.fee.FeeStructureItem;
import com.schaccs.model.finance.Votehead;
import com.schaccs.repository.PersistenceService;
import com.schaccs.service.export.FeeStructureExportService;
import com.schaccs.service.export.PdfExportService;
import com.schaccs.store.FeeStructureStore;
import com.schaccs.ui.layout.MainLayout;
import com.schaccs.util.AlertUtil;
import com.schaccs.util.CurrencyUtil;
import com.schaccs.util.FileDialogMemory;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.ComboBoxTableCell;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.util.StringConverter;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;

public class FeeStructureView extends VBox implements MainLayout.Refreshable {

    private final FeeStructureStore store = FeeStructureStore.getInstance();
    private final FeeStructureExportService exportService = new FeeStructureExportService();
    private final PdfExportService pdfService = new PdfExportService();
    private final ComboBox<FeeStructure> structureBox = new ComboBox<>();
    private final ComboBox<AcademicTerm> termBox = new ComboBox<>();
    private final ComboBox<AcademicTerm> pdfTermBox = new ComboBox<>();
    private final TableView<FeeStructureItem> itemTable = new TableView<>();
    private final TableView<Votehead> voteheadTable = new TableView<>();
    private final Label totalLabel = new Label();

    public FeeStructureView() {
        setSpacing(14);
        setPadding(new Insets(4));

        Label heading = new Label("Fee Structure Management");
        heading.getStyleClass().add("section-title");

        structureBox.setItems(store.getStructures());
        structureBox.setPrefWidth(320);
        if (!store.getStructures().isEmpty()) {
            structureBox.getSelectionModel().selectFirst();
        }
        structureBox.setOnAction(e -> loadItems());

        termBox.getItems().add(AcademicTerm.TERM_1);
        termBox.getItems().add(AcademicTerm.TERM_2);
        termBox.getItems().add(AcademicTerm.TERM_3);
        termBox.setPromptText("All terms");
        termBox.setOnAction(e -> loadItems());

        HBox filters = new HBox(12,
                new Label("Structure:"), structureBox,
                new Label("Term:"), termBox,
                totalLabel);
        filters.setAlignment(Pos.CENTER_LEFT);

        setupItemTable();
        setupVoteheadTable();

        VBox itemsCard = new VBox(8, new Label("Fee Lines"), itemTable, buildItemToolbar());
        itemsCard.getStyleClass().add("card");

        VBox vhCard = new VBox(8, new Label("Vote Heads"), buildVoteheadToolbar(), voteheadTable);
        vhCard.getStyleClass().add("card");
        vhCard.setPrefWidth(360);
        vhCard.setMinWidth(300);

        HBox body = new HBox(16, itemsCard, vhCard);
        body.setFillHeight(true);
        HBox.setHgrow(itemsCard, Priority.ALWAYS);

        VBox structureToolbar = buildStructureToolbar();

        Label note = new Label("Use the dialog to create Day, Boarding, or both structures side by side with per-term votehead amounts.");
        note.getStyleClass().add("muted");

        itemTable.setPrefHeight(300);
        voteheadTable.setPrefHeight(300);
        getChildren().addAll(heading, structureToolbar, filters, body, note);
        loadItems();
    }

    private VBox buildStructureToolbar() {
        Button newStruct = new Button("New Structure");
        newStruct.getStyleClass().add("primary-button");
        newStruct.setOnAction(e -> {
            new FeeStructureDialog(store, getScene().getWindow()).showAndWait();
            refresh();
        });

        Button delStruct = new Button("Delete Structure");
        delStruct.getStyleClass().add("secondary-button");
        delStruct.setOnAction(e -> deleteStructure());

        Button importBtn = new Button("Import from File");
        importBtn.getStyleClass().add("secondary-button");
        importBtn.setOnAction(e -> importFromFile());

        Button templatesBtn = new Button("Templates");
        templatesBtn.getStyleClass().add("secondary-button");
        templatesBtn.setOnAction(e -> {
            new FeeStructureTemplateDialog(store, getScene().getWindow(), structureBox.getValue()).showAndWait();
            refresh();
        });

        Button excelBtn = new Button("Export Excel");
        excelBtn.getStyleClass().add("secondary-button");
        excelBtn.setOnAction(e -> exportExcel());

        pdfTermBox.getItems().addAll(AcademicTerm.TERM_1, AcademicTerm.TERM_2, AcademicTerm.TERM_3);
        pdfTermBox.setPromptText("PDF (All terms)");

        Button pdfBtn = new Button("Export PDF");
        pdfBtn.getStyleClass().add("secondary-button");
        pdfBtn.setOnAction(e -> exportPdf());

        HBox bar = new HBox(10, newStruct, delStruct, importBtn, templatesBtn, excelBtn, pdfTermBox, pdfBtn);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setSpacing(10);
        VBox box = new VBox(8, new Label("Structures"), bar);
        box.getStyleClass().add("card");
        return box;
    }

    private void importFromFile() {
        FileChooser chooser = new FileChooser();
        FileDialogMemory.applyTo(chooser);
        chooser.setTitle("Import Fee Structure");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Spreadsheets", "*.csv", "*.xlsx"));
        java.io.File file = chooser.showOpenDialog(getScene().getWindow());
        if (file == null) {
            return;
        }
        FileDialogMemory.remember(file);
        new FeeStructureImportDialog(store, getScene().getWindow(), file.toPath()).showAndWait();
        refresh();
    }

    private void exportExcel() {
        FeeStructure s = structureBox.getValue();
        if (s == null) {
            AlertUtil.warn("Select structure", "Select a structure to export.");
            return;
        }
        FileChooser chooser = new FileChooser();
        FileDialogMemory.applyTo(chooser);
        chooser.setTitle("Export Fee Structure");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Excel Workbook", "*.xlsx"),
                new FileChooser.ExtensionFilter("CSV", "*.csv"));
        chooser.setInitialFileName(safeName(s.getName()) + ".xlsx");
        java.io.File file = chooser.showSaveDialog(getScene().getWindow());
        if (file == null) {
            return;
        }
        FileDialogMemory.remember(file);
        try {
            exportService.exportStructures(file.toPath(), List.of(s));
            AlertUtil.info("Export complete", "Exported to " + file.getAbsolutePath());
        } catch (Exception ex) {
            AlertUtil.warn("Export failed", ex.getMessage());
        }
    }

    private void exportPdf() {
        FeeStructure s = structureBox.getValue();
        if (s == null) {
            AlertUtil.warn("Select structure", "Select a structure to export.");
            return;
        }
        FileChooser chooser = new FileChooser();
        FileDialogMemory.applyTo(chooser);
        chooser.setTitle("Export Fee Structure as PDF");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF", "*.pdf"));
        chooser.setInitialFileName(safeName(s.getName()) + ".pdf");
        java.io.File file = chooser.showSaveDialog(getScene().getWindow());
        if (file == null) {
            return;
        }
        FileDialogMemory.remember(file);
        try {
            pdfService.exportFeeStructurePdf(file.toPath(), s, pdfTermBox.getValue());
            AlertUtil.info("Export complete", "Exported to " + file.getAbsolutePath());
        } catch (Exception ex) {
            AlertUtil.warn("Export failed", ex.getMessage());
        }
    }

    private String safeName(String name) {
        if (name == null) return "fee-structure";
        return name.replaceAll("[^a-zA-Z0-9-_ ]", "").trim().replace(' ', '_');
    }

    private HBox buildItemToolbar() {
        ComboBox<Votehead> vhBox = new ComboBox<>(store.getVoteheads());
        vhBox.setPromptText("Vote head");
        vhBox.setPrefWidth(160);
        ComboBox<AcademicTerm> term = new ComboBox<>();
        term.getItems().setAll(AcademicTerm.TERM_1, AcademicTerm.TERM_2, AcademicTerm.TERM_3);
        term.setPromptText("Term");
        TextField amount = new TextField();
        amount.setPromptText("Amount");
        amount.setPrefWidth(100);
        Button add = new Button("Add Line");
        add.getStyleClass().add("success-button");
        add.setOnAction(e -> addItem(vhBox, term, amount));
        Button remove = new Button("Remove Selected");
        remove.getStyleClass().add("secondary-button");
        remove.setOnAction(e -> removeItem());

        HBox bar = new HBox(8, vhBox, term, amount, add, remove);
        bar.setAlignment(Pos.CENTER_LEFT);
        return bar;
    }

    private void deleteStructure() {
        FeeStructure s = structureBox.getValue();
        if (s == null) {
            AlertUtil.warn("Select structure", "Select a structure to delete.");
            return;
        }
        if (!AlertUtil.confirm("Confirm", "Delete structure '" + s.getName() + "'?")) {
            return;
        }
        store.getStructures().remove(s);
        PersistenceService.getInstance().saveAll();
        if (!store.getStructures().isEmpty()) {
            structureBox.getSelectionModel().selectFirst();
        }
        loadItems();
    }

    private void addItem(ComboBox<Votehead> vhBox, ComboBox<AcademicTerm> term, TextField amount) {
        FeeStructure s = structureBox.getValue();
        if (s == null) {
            AlertUtil.warn("Select structure", "Select a structure first.");
            return;
        }
        Votehead vh = vhBox.getValue();
        AcademicTerm t = term.getValue();
        if (vh == null || t == null) {
            AlertUtil.warn("Missing", "Select a vote head and term.");
            return;
        }
        try {
            java.math.BigDecimal amt = com.schaccs.config.CurrencyConfig.money(amount.getText());
            if (amt.compareTo(java.math.BigDecimal.ZERO) <= 0) {
                AlertUtil.warn("Invalid", "Amount must be greater than zero.");
                return;
            }
            s.addItem(new FeeStructureItem(vh.getCode(), vh.getName(), t,
                    s.getBoardingStatus(), amt));
            amount.clear();
            PersistenceService.getInstance().saveAll();
            loadItems();
        } catch (NumberFormatException ex) {
            AlertUtil.warn("Invalid", "Enter a valid amount.");
        }
    }

    private void removeItem() {
        FeeStructure s = structureBox.getValue();
        FeeStructureItem item = itemTable.getSelectionModel().getSelectedItem();
        if (s == null || item == null) {
            AlertUtil.warn("Select line", "Select a fee line to remove.");
            return;
        }
        s.getItems().remove(item);
        PersistenceService.getInstance().saveAll();
        loadItems();
    }

    private HBox buildVoteheadToolbar() {
        Button addBtn = new Button("New Vote Head");
        addBtn.getStyleClass().add("success-button");
        addBtn.setOnAction(e -> {
            new VoteheadDialog(store, getScene().getWindow()).showAndWait();
            voteheadTable.refresh();
        });

        Button delBtn = new Button("Delete");
        delBtn.getStyleClass().add("secondary-button");
        delBtn.setOnAction(e -> deleteVotehead());

        HBox bar = new HBox(8, addBtn, delBtn);
        bar.setAlignment(Pos.CENTER_LEFT);
        return bar;
    }

    private void deleteVotehead() {
        Votehead vh = voteheadTable.getSelectionModel().getSelectedItem();
        if (vh == null) {
            AlertUtil.warn("Select vote head", "Select a vote head to delete.");
            return;
        }
        if (!AlertUtil.confirm("Confirm", "Delete vote head '" + vh.getName() + "'?")) {
            return;
        }
        store.removeVotehead(vh);
        PersistenceService.getInstance().saveAll();
        voteheadTable.refresh();
    }

    private void setupItemTable() {
        TableColumn<FeeStructureItem, AcademicTerm> term = new TableColumn<>("Term");
        term.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().getTerm()));
        StringConverter<AcademicTerm> termConverter = new StringConverter<>() {
            @Override
            public String toString(AcademicTerm t) {
                return t != null ? t.getDisplayName() : "";
            }

            @Override
            public AcademicTerm fromString(String s) {
                return null;
            }
        };
        term.setCellFactory(ComboBoxTableCell.forTableColumn(termConverter, AcademicTerm.values()));
        term.setOnEditCommit(e -> {
            e.getRowValue().setTerm(e.getNewValue());
            persist();
        });
        term.setPrefWidth(90);

        TableColumn<FeeStructureItem, String> code = new TableColumn<>("Code");
        code.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getVoteheadCode()));
        code.setCellFactory(TextFieldTableCell.forTableColumn());
        code.setOnEditCommit(e -> {
            e.getRowValue().setVoteheadCode(e.getNewValue());
            persist();
        });
        code.setPrefWidth(90);

        TableColumn<FeeStructureItem, String> name = new TableColumn<>("Vote Head");
        name.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getVoteheadName()));
        name.setCellFactory(TextFieldTableCell.forTableColumn());
        name.setOnEditCommit(e -> {
            e.getRowValue().setVoteheadName(e.getNewValue());
            persist();
        });
        name.setPrefWidth(180);

        StringConverter<BigDecimal> amountConverter = new StringConverter<>() {
            @Override
            public String toString(BigDecimal value) {
                return value != null ? CurrencyUtil.formatPlain(value) : "";
            }

            @Override
            public BigDecimal fromString(String s) {
                if (s == null || s.trim().isBlank()) {
                    return null;
                }
                return CurrencyConfig.money(s.trim());
            }
        };
        TableColumn<FeeStructureItem, BigDecimal> amount = new TableColumn<>("Amount");
        amount.setCellValueFactory(c -> new SimpleObjectProperty<>(c.getValue().getAmount()));
        amount.setCellFactory(TextFieldTableCell.forTableColumn(amountConverter));
        amount.setOnEditCommit(e -> {
            if (e.getNewValue() != null && e.getNewValue().compareTo(BigDecimal.ZERO) > 0) {
                e.getRowValue().setAmount(e.getNewValue());
                persist();
            } else {
                loadItems();
            }
        });
        amount.setPrefWidth(120);

        itemTable.getColumns().addAll(term, code, name, amount);
        itemTable.setEditable(true);
        itemTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
    }

    private void persist() {
        PersistenceService.getInstance().saveAll();
        loadItems();
    }

    private void setupVoteheadTable() {
        TableColumn<Votehead, String> code = new TableColumn<>("Code");
        code.setCellValueFactory(c -> c.getValue().codeProperty());
        code.setPrefWidth(80);

        TableColumn<Votehead, String> name = new TableColumn<>("Name");
        name.setCellValueFactory(c -> c.getValue().nameProperty());
        name.setPrefWidth(140);

        TableColumn<Votehead, String> acct = new TableColumn<>("Account");
        acct.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getAccountType() != null ? c.getValue().getAccountType().getDisplayName() : ""));
        acct.setPrefWidth(120);

        @SuppressWarnings("unchecked")
        var columns2 = new TableColumn[]{code, name, acct};
        voteheadTable.getColumns().addAll(columns2);
        voteheadTable.setItems(store.getVoteheads());
        voteheadTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
    }

    private void loadItems() {
        FeeStructure structure = structureBox.getValue();
        if (structure == null) {
            itemTable.getItems().clear();
            totalLabel.setText("");
            return;
        }
        AcademicTerm term = termBox.getValue();
        if (term == null) {
            itemTable.getItems().setAll(structure.getItems());
            totalLabel.setText("Year total: " + CurrencyUtil.format(structure.grandTotal()));
        } else {
            itemTable.getItems().setAll(structure.itemsForTerm(term));
            totalLabel.setText(term.getDisplayName() + " total: "
                    + CurrencyUtil.format(structure.totalForTerm(term)));
        }
    }

    @Override
    public void refresh() {
        structureBox.setItems(store.getStructures());
        loadItems();
        voteheadTable.refresh();
    }
}
