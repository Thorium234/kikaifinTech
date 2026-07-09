package com.schaccs.ui.fees;

import com.schaccs.enums.AcademicTerm;
import com.schaccs.model.fee.FeeStructure;
import com.schaccs.model.fee.FeeStructureItem;
import com.schaccs.model.finance.Votehead;
import com.schaccs.store.FeeStructureStore;
import com.schaccs.ui.layout.MainLayout;
import com.schaccs.util.CurrencyUtil;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
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
        filters.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        setupItemTable();
        setupVoteheadTable();

        VBox itemsCard = new VBox(8, new Label("Fee Lines"), itemTable);
        itemsCard.getStyleClass().add("card");
        VBox.setVgrow(itemTable, Priority.ALWAYS);

        VBox vhCard = new VBox(8, new Label("Vote Heads"), voteheadTable);
        vhCard.getStyleClass().add("card");
        vhCard.setPrefWidth(360);
        VBox.setVgrow(voteheadTable, Priority.ALWAYS);

        HBox body = new HBox(16, itemsCard, vhCard);
        HBox.setHgrow(itemsCard, Priority.ALWAYS);
        VBox.setVgrow(body, Priority.ALWAYS);

        Label note = new Label("2026 boarding totals: Term 1 = 21,000 · Term 2 = 12,500 · Term 3 = 7,000 · Year = 40,500");
        note.getStyleClass().add("muted");

        getChildren().addAll(heading, filters, body, note);
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
        loadItems();
        voteheadTable.refresh();
    }
}
