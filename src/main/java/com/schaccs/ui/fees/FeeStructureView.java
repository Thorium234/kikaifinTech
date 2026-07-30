package com.schaccs.ui.fees;

import com.schaccs.enums.AcademicTerm;
import com.schaccs.enums.BoardingStatus;
import com.schaccs.model.fee.FeeStructure;
import com.schaccs.model.fee.FeeStructureItem;
import com.schaccs.model.fee.FeeStructureTemplate;
import com.schaccs.model.fee.FeeStructureTemplateItem;
import com.schaccs.model.finance.Votehead;
import com.schaccs.repository.PersistenceService;
import com.schaccs.store.FeeStructureStore;
import com.schaccs.ui.layout.MainLayout;
import com.schaccs.util.AlertUtil;
import com.schaccs.util.CurrencyUtil;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

public class FeeStructureView extends VBox implements MainLayout.Refreshable {

    private final FeeStructureStore store = FeeStructureStore.getInstance();
    private final ComboBox<FeeStructure> structureBox = new ComboBox<>();
    private final ComboBox<AcademicTerm> termBox = new ComboBox<>();
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

        Button saveTemplateBtn = new Button("Save as Template");
        saveTemplateBtn.getStyleClass().add("secondary-button");
        saveTemplateBtn.setOnAction(e -> saveAsTemplate());

        Button delTemplateBtn = new Button("Delete Template");
        delTemplateBtn.getStyleClass().add("secondary-button");
        delTemplateBtn.setOnAction(e -> deleteTemplate());

        HBox bar = new HBox(10, newStruct, delStruct, saveTemplateBtn, delTemplateBtn);
        bar.setAlignment(Pos.CENTER_LEFT);
        VBox box = new VBox(8, new Label("Structures"), bar);
        box.getStyleClass().add("card");
        return box;
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

    private void saveAsTemplate() {
        FeeStructure s = structureBox.getValue();
        if (s == null) {
            AlertUtil.warn("Select structure", "Select a structure to save as template.");
            return;
        }
        String name = s.getName();
        TextField nameInput = new TextField(name);
        javafx.scene.control.Dialog<String> dialog = new javafx.scene.control.Dialog<>();
        dialog.setTitle("Save as Template");
        dialog.getDialogPane().setContent(new VBox(8, new Label("Template name:"), nameInput));
        dialog.getDialogPane().getButtonTypes().addAll(javafx.scene.control.ButtonType.OK, javafx.scene.control.ButtonType.CANCEL);
        dialog.setResultConverter(bt -> bt == javafx.scene.control.ButtonType.OK ? nameInput.getText().trim() : null);
        dialog.initOwner(getScene().getWindow());
        String result = dialog.showAndWait().orElse(null);
        if (result == null || result.isBlank()) return;

        FeeStructureTemplate t = new FeeStructureTemplate(result);
        for (FeeStructureItem item : s.getItems()) {
            t.addItem(new FeeStructureTemplateItem(
                    item.getVoteheadCode(), item.getVoteheadName(), item.getTerm(), item.getAmount()));
        }
        store.addTemplate(t);
        PersistenceService.getInstance().saveAll();
        AlertUtil.info("Template Saved", "Template '" + result + "' saved.");
    }

    private void deleteTemplate() {
        FeeStructureTemplate t = selectTemplate();
        if (t == null) return;
        if (!AlertUtil.confirm("Delete Template", "Delete template '" + t.getName() + "'?")) return;
        store.removeTemplate(t);
        PersistenceService.getInstance().saveAll();
    }

    private FeeStructureTemplate selectTemplate() {
        if (store.getTemplates().isEmpty()) {
            AlertUtil.warn("No Templates", "No templates available.");
            return null;
        }
        ComboBox<FeeStructureTemplate> box = new ComboBox<>(store.getTemplates());
        box.setPrefWidth(300);
        javafx.scene.control.Dialog<FeeStructureTemplate> dialog = new javafx.scene.control.Dialog<>();
        dialog.setTitle("Select Template");
        dialog.getDialogPane().setContent(new VBox(8, new Label("Choose a template to delete:"), box));
        dialog.getDialogPane().getButtonTypes().addAll(javafx.scene.control.ButtonType.OK, javafx.scene.control.ButtonType.CANCEL);
        dialog.setResultConverter(bt -> bt == javafx.scene.control.ButtonType.OK ? box.getValue() : null);
        dialog.initOwner(getScene().getWindow());
        return dialog.showAndWait().orElse(null);
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
        TableColumn<FeeStructureItem, String> term = new TableColumn<>("Term");
        term.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getTerm() != null ? c.getValue().getTerm().getDisplayName() : ""));
        term.setPrefWidth(90);

        TableColumn<FeeStructureItem, String> code = new TableColumn<>("Code");
        code.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getVoteheadCode()));
        code.setPrefWidth(90);

        TableColumn<FeeStructureItem, String> name = new TableColumn<>("Vote Head");
        name.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getVoteheadName()));
        name.setPrefWidth(180);

        TableColumn<FeeStructureItem, String> amount = new TableColumn<>("Amount");
        amount.setCellValueFactory(c -> new SimpleStringProperty(CurrencyUtil.format(c.getValue().getAmount())));
        amount.setPrefWidth(120);

        @SuppressWarnings("unchecked")
        TableColumn<FeeStructureItem, String>[] columns1 = new TableColumn[]{term, code, name, amount};
        itemTable.getColumns().addAll(columns1);
        itemTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
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
