package com.schaccs.ui.fees;

import com.schaccs.enums.AcademicTerm;
import com.schaccs.enums.BoardingStatus;
import com.schaccs.model.fee.FeeStructure;
import com.schaccs.model.fee.FeeStructureItem;
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
        VBox.setVgrow(itemTable, Priority.ALWAYS);

        VBox vhCard = new VBox(8, new Label("Vote Heads"), voteheadTable);
        vhCard.getStyleClass().add("card");
        vhCard.setPrefWidth(360);
        VBox.setVgrow(voteheadTable, Priority.ALWAYS);

        HBox body = new HBox(16, itemsCard, vhCard);
        HBox.setHgrow(itemsCard, Priority.ALWAYS);
        VBox.setVgrow(body, Priority.ALWAYS);

        VBox structureToolbar = buildStructureToolbar();

        Label note = new Label("2026 boarding totals: Term 1 = 21,000 · Term 2 = 12,500 · Term 3 = 7,000 · Year = 40,500");
        note.getStyleClass().add("muted");

        getChildren().addAll(heading, structureToolbar, filters, body, note);
        loadItems();
    }

    private VBox buildStructureToolbar() {
        Button newStruct = new Button("New Structure");
        newStruct.getStyleClass().add("primary-button");
        newStruct.setOnAction(e -> createStructure());

        Button delStruct = new Button("Delete Structure");
        delStruct.getStyleClass().add("secondary-button");
        delStruct.setOnAction(e -> deleteStructure());

        HBox bar = new HBox(10, newStruct, delStruct);
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

    private void createStructure() {
        FeeStructure s = new FeeStructure(2026, "ALL", BoardingStatus.BOARDING, "New Fee Structure");
        store.addStructure(s);
        structureBox.getSelectionModel().select(s);
        PersistenceService.getInstance().saveAll();
        AlertUtil.info("Created", "New fee structure added. Add fee lines, then it saves automatically.");
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

        itemTable.getColumns().addAll(term, code, name, amount);
        itemTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        itemTable.setPrefHeight(400);
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

        voteheadTable.getColumns().addAll(code, name, acct);
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
