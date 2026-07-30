package com.schaccs.ui.procurement;

import com.schaccs.config.AppConfig;
import com.schaccs.config.CurrencyConfig;
import com.schaccs.enums.BidStatus;
import com.schaccs.enums.ProcurementCategory;
import com.schaccs.enums.TenderStatus;
import com.schaccs.enums.TenderType;
import com.schaccs.model.procurement.Supplier;
import com.schaccs.model.procurement.Tender;
import com.schaccs.model.procurement.TenderBid;
import com.schaccs.model.procurement.TenderEvaluation;
import com.schaccs.service.Services;
import com.schaccs.service.procurement.TenderService;
import com.schaccs.store.ProcurementStore;
import com.schaccs.ui.layout.MainLayout;
import com.schaccs.util.AlertUtil;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public class TenderView extends VBox implements MainLayout.Refreshable {

    private final TenderService tenderService = Services.getInstance().tender();
    private final ProcurementStore store = ProcurementStore.getInstance();

    private final TabPane tabPane = new TabPane();

    // --- Tenders Tab fields ---
    private final TextField titleField = new TextField();
    private final TextArea descriptionArea = new TextArea();
    private final DatePicker openingDatePick = new DatePicker();
    private final DatePicker closingDatePick = new DatePicker();
    private final ComboBox<TenderType> tenderTypeBox = new ComboBox<>();
    private final ComboBox<ProcurementCategory> categoryBox = new ComboBox<>();
    private final TextField budgetField = new TextField();
    private final TextArea criteriaArea = new TextArea();
    private final TableView<Tender> tenderTable = new TableView<>();
    private final FilteredList<Tender> filteredTenders = new FilteredList<>(store.getTenders(), p -> true);
    private Tender selectedTender;

    // --- Bids Tab fields ---
    private final ComboBox<Tender> bidTenderBox = new ComboBox<>();
    private final ComboBox<Supplier> bidSupplierBox = new ComboBox<>();
    private final TextField bidAmountField = new TextField();
    private final TextField bidDocsField = new TextField();
    private final TextArea bidRemarksArea = new TextArea();
    private final TableView<TenderBid> bidTable = new TableView<>();

    // --- Evaluation Tab fields ---
    private final ComboBox<Tender> evalTenderBox = new ComboBox<>();
    private final ComboBox<TenderBid> evalBidBox = new ComboBox<>();
    private final TextField evaluatorField = new TextField();
    private final ComboBox<String> evalTypeBox = new ComboBox<>();
    private final TextField scoreField = new TextField();
    private final TextField maxScoreField = new TextField();
    private final TextArea evalCommentsArea = new TextArea();
    private final DatePicker evalDatePick = new DatePicker();
    private final TableView<TenderEvaluation> evalTable = new TableView<>();

    public TenderView() {
        setSpacing(12);
        setPadding(new Insets(4));

        Label heading = new Label("Tender Management");
        heading.getStyleClass().add("section-title");

        tabPane.getTabs().addAll(buildTendersTab(), buildBidsTab(), buildEvaluationTab());
        VBox.setVgrow(tabPane, Priority.ALWAYS);

        VBox content = new VBox(12, heading, tabPane);
        content.setPadding(new Insets(4));
        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        scrollPane.setPannable(true);
        scrollPane.getStyleClass().add("content-scroll");
        VBox.setVgrow(scrollPane, Priority.ALWAYS);
        getChildren().add(scrollPane);
    }

    // ========== TENDERS TAB ==========

    private Tab buildTendersTab() {
        tenderTypeBox.getItems().setAll(TenderType.values());
        tenderTypeBox.setPrefWidth(200);
        categoryBox.getItems().setAll(ProcurementCategory.values());
        categoryBox.setPrefWidth(200);
        descriptionArea.setPrefRowCount(2);
        descriptionArea.setWrapText(true);
        criteriaArea.setPrefRowCount(2);
        criteriaArea.setWrapText(true);

        setupTenderTable();

        Button createBtn = new Button("Create Tender");
        createBtn.getStyleClass().add("btn-primary");
        createBtn.setOnAction(e -> createTender());

        Button publishBtn = new Button("Publish");
        publishBtn.getStyleClass().add("btn-secondary");
        publishBtn.setOnAction(e -> publishTender());

        Button closeBtn = new Button("Close");
        closeBtn.getStyleClass().add("btn-secondary");
        closeBtn.setOnAction(e -> closeTender());

        Button evalStartBtn = new Button("Start Evaluation");
        evalStartBtn.getStyleClass().add("btn-secondary");
        evalStartBtn.setOnAction(e -> startEvaluation());

        Button cancelBtn = new Button("Cancel");
        cancelBtn.getStyleClass().add("btn-secondary");
        cancelBtn.setOnAction(e -> cancelTender());

        HBox buttons = new HBox(10, createBtn, publishBtn, closeBtn, evalStartBtn, cancelBtn);
        buttons.setAlignment(Pos.CENTER_LEFT);

        GridPane form = new GridPane();
        form.setHgap(10);
        form.setVgap(8);
        form.add(voucherLabel("Title"), 0, 0);
        form.add(voucherFieldBox(titleField, "Tender title"), 1, 0);
        form.add(voucherLabel("Description"), 0, 1);
        form.add(voucherFieldBox(descriptionArea, "Detailed description"), 1, 1);
        form.add(voucherLabel("Opening Date"), 0, 2);
        form.add(voucherFieldBox(openingDatePick, "Bidding opens"), 1, 2);
        form.add(voucherLabel("Closing Date"), 0, 3);
        form.add(voucherFieldBox(closingDatePick, "Bidding closes"), 1, 3);
        form.add(voucherLabel("Tender Type"), 0, 4);
        form.add(voucherFieldBox(tenderTypeBox, "Procurement method"), 1, 4);
        form.add(voucherLabel("Category"), 0, 5);
        form.add(voucherFieldBox(categoryBox, "Procurement category"), 1, 5);
        form.add(voucherLabel("Est. Budget"), 0, 6);
        form.add(voucherFieldBox(budgetField, "Estimated budget"), 1, 6);
        form.add(voucherLabel("Evaluation Criteria"), 0, 7);
        form.add(voucherFieldBox(criteriaArea, "Evaluation criteria description"), 1, 7);
        titleField.setPrefWidth(280);
        budgetField.setPrefWidth(200);

        Label formTitle = new Label("Tender Details");
        formTitle.getStyleClass().add("card-title");
        VBox formCard = new VBox(10, formTitle, form, buttons);
        formCard.getStyleClass().add("card");
        formCard.setPadding(new Insets(12));

        Label tableTitle = new Label("Tenders");
        tableTitle.getStyleClass().add("card-title");
        VBox tableCard = new VBox(10, tableTitle, tenderTable);
        tableCard.getStyleClass().add("card");
        tableCard.setPadding(new Insets(12));
        VBox.setVgrow(tenderTable, Priority.ALWAYS);
        tenderTable.setPrefHeight(300);

        VBox tabContent = new VBox(12, formCard, tableCard);
        tabContent.setPadding(new Insets(8));
        ScrollPane sp = new ScrollPane(tabContent);
        sp.setFitToWidth(true);
        sp.setPannable(true);

        Tab tab = new Tab("Tenders", sp);
        tab.setClosable(false);
        return tab;
    }

    private void setupTenderTable() {
        TableColumn<Tender, String> numCol = new TableColumn<>("Tender No");
        numCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getTenderNumber()));
        numCol.setPrefWidth(120);

        TableColumn<Tender, String> titleCol = new TableColumn<>("Title");
        titleCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getTitle()));
        titleCol.setPrefWidth(180);

        TableColumn<Tender, String> typeCol = new TableColumn<>("Type");
        typeCol.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getTenderType() != null ? c.getValue().getTenderType().getDisplayName() : ""));
        typeCol.setPrefWidth(120);

        TableColumn<Tender, String> catCol = new TableColumn<>("Category");
        catCol.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getCategory() != null ? c.getValue().getCategory().getDisplayName() : ""));
        catCol.setPrefWidth(100);

        TableColumn<Tender, String> budgetCol = new TableColumn<>("Budget");
        budgetCol.setCellValueFactory(c -> new SimpleStringProperty(CurrencyConfig.format(c.getValue().getEstimatedBudget())));
        budgetCol.setPrefWidth(120);

        TableColumn<Tender, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getStatus() != null ? c.getValue().getStatus().getDisplayName() : ""));
        statusCol.setPrefWidth(110);

        TableColumn<Tender, String> closeCol = new TableColumn<>("Closing Date");
        closeCol.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getClosingDate() != null ? c.getValue().getClosingDate().toString() : ""));
        closeCol.setPrefWidth(100);

        @SuppressWarnings("unchecked")
        var columns = new TableColumn[]{numCol, titleCol, typeCol, catCol, budgetCol, statusCol, closeCol};
        tenderTable.getColumns().addAll(columns);
        tenderTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        tenderTable.setItems(filteredTenders);
        tenderTable.getSelectionModel().selectedItemProperty().addListener((obs, o, s) -> selectTender(s));
    }

    private void selectTender(Tender t) {
        selectedTender = t;
        if (t == null) {
            clearTenderForm();
            return;
        }
        titleField.setText(t.getTitle());
        descriptionArea.setText(t.getDescription());
        openingDatePick.setValue(t.getOpeningDate());
        closingDatePick.setValue(t.getClosingDate());
        tenderTypeBox.setValue(t.getTenderType());
        categoryBox.setValue(t.getCategory());
        budgetField.setText(t.getEstimatedBudget() != null ? t.getEstimatedBudget().toPlainString() : "");
        criteriaArea.setText(t.getEvaluationCriteria());
    }

    private void createTender() {
        if (!AlertUtil.confirm("Create Tender", "Create new tender?")) return;
        Tender t = buildTenderFromForm();
        List<String> errors = tenderService.createTender(t);
        if (!errors.isEmpty()) {
            AlertUtil.error("Validation Error", String.join("\n", errors));
            return;
        }
        AlertUtil.info("Success", "Tender " + t.getTenderNumber() + " created.");
        clearTenderForm();
        tenderTable.refresh();
    }

    private void publishTender() {
        if (selectedTender == null) {
            AlertUtil.warn("No Selection", "Select a tender to publish.");
            return;
        }
        if (!AlertUtil.confirm("Publish", "Publish tender " + selectedTender.getTenderNumber() + "?")) return;
        List<String> errors = tenderService.publishTender(selectedTender);
        if (!errors.isEmpty()) {
            AlertUtil.error("Error", String.join("\n", errors));
            return;
        }
        AlertUtil.info("Published", "Tender published.");
        tenderTable.refresh();
    }

    private void closeTender() {
        if (selectedTender == null) {
            AlertUtil.warn("No Selection", "Select a tender to close.");
            return;
        }
        if (!AlertUtil.confirm("Close", "Close tender " + selectedTender.getTenderNumber() + "?")) return;
        List<String> errors = tenderService.closeTender(selectedTender);
        if (!errors.isEmpty()) {
            AlertUtil.error("Error", String.join("\n", errors));
            return;
        }
        AlertUtil.info("Closed", "Tender closed.");
        tenderTable.refresh();
    }

    private void startEvaluation() {
        if (selectedTender == null) {
            AlertUtil.warn("No Selection", "Select a tender to start evaluation.");
            return;
        }
        if (!AlertUtil.confirm("Start Evaluation", "Start evaluation for " + selectedTender.getTenderNumber() + "?")) return;
        List<String> errors = tenderService.startEvaluation(selectedTender);
        if (!errors.isEmpty()) {
            AlertUtil.error("Error", String.join("\n", errors));
            return;
        }
        AlertUtil.info("Evaluation Started", "Tender evaluation initiated.");
        tenderTable.refresh();
    }

    private void cancelTender() {
        if (selectedTender == null) {
            AlertUtil.warn("No Selection", "Select a tender to cancel.");
            return;
        }
        if (!AlertUtil.confirm("Cancel", "Cancel tender " + selectedTender.getTenderNumber() + "?")) return;
        List<String> errors = tenderService.cancelTender(selectedTender);
        if (!errors.isEmpty()) {
            AlertUtil.error("Error", String.join("\n", errors));
            return;
        }
        AlertUtil.info("Cancelled", "Tender cancelled.");
        tenderTable.refresh();
    }

    private Tender buildTenderFromForm() {
        Tender t = new Tender();
        t.setTitle(titleField.getText());
        t.setDescription(descriptionArea.getText());
        t.setOpeningDate(openingDatePick.getValue());
        t.setClosingDate(closingDatePick.getValue());
        t.setTenderType(tenderTypeBox.getValue());
        t.setCategory(categoryBox.getValue());
        try {
            t.setEstimatedBudget(CurrencyConfig.money(budgetField.getText().trim()));
        } catch (NumberFormatException e) {
            t.setEstimatedBudget(CurrencyConfig.zero());
        }
        t.setEvaluationCriteria(criteriaArea.getText());
        return t;
    }

    private void clearTenderForm() {
        titleField.clear();
        descriptionArea.clear();
        openingDatePick.setValue(null);
        closingDatePick.setValue(null);
        tenderTypeBox.setValue(null);
        categoryBox.setValue(null);
        budgetField.clear();
        criteriaArea.clear();
    }

    // ========== BIDS TAB ==========

    private Tab buildBidsTab() {
        bidTenderBox.getItems().addAll(store.getTenders());
        bidTenderBox.setPrefWidth(280);
        bidTenderBox.valueProperty().addListener((obs, o, t) -> refreshBidTable());

        bidRemarksArea.setPrefRowCount(2);
        bidRemarksArea.setWrapText(true);

        setupBidTable();

        Button submitBidBtn = new Button("Submit Bid");
        submitBidBtn.getStyleClass().add("btn-primary");
        submitBidBtn.setOnAction(e -> submitBid());

        Button rankBtn = new Button("Rank Bids");
        rankBtn.getStyleClass().add("btn-secondary");
        rankBtn.setOnAction(e -> rankBids());

        HBox buttons = new HBox(10, submitBidBtn, rankBtn);
        buttons.setAlignment(Pos.CENTER_LEFT);

        GridPane form = new GridPane();
        form.setHgap(10);
        form.setVgap(8);
        form.add(voucherLabel("Select Tender"), 0, 0);
        form.add(voucherFieldBox(bidTenderBox, "Choose a published tender"), 1, 0);
        form.add(voucherLabel("Supplier"), 0, 1);
        form.add(voucherFieldBox(bidSupplierBox, "Bidder"), 1, 1);
        form.add(voucherLabel("Bid Amount"), 0, 2);
        form.add(voucherFieldBox(bidAmountField, "Total bid amount"), 1, 2);
        form.add(voucherLabel("Documents"), 0, 3);
        form.add(voucherFieldBox(bidDocsField, "Document references"), 1, 3);
        form.add(voucherLabel("Remarks"), 0, 4);
        form.add(voucherFieldBox(bidRemarksArea, "Additional remarks"), 1, 4);
        bidAmountField.setPrefWidth(200);

        Label formTitle = new Label("Bid Entry");
        formTitle.getStyleClass().add("card-title");
        VBox formCard = new VBox(10, formTitle, form, buttons);
        formCard.getStyleClass().add("card");
        formCard.setPadding(new Insets(12));

        Label tableTitle = new Label("Bids");
        tableTitle.getStyleClass().add("card-title");
        VBox tableCard = new VBox(10, tableTitle, bidTable);
        tableCard.getStyleClass().add("card");
        tableCard.setPadding(new Insets(12));
        VBox.setVgrow(bidTable, Priority.ALWAYS);
        bidTable.setPrefHeight(250);

        VBox tabContent = new VBox(12, formCard, tableCard);
        tabContent.setPadding(new Insets(8));
        ScrollPane sp = new ScrollPane(tabContent);
        sp.setFitToWidth(true);
        sp.setPannable(true);

        Tab tab = new Tab("Bids", sp);
        tab.setClosable(false);
        return tab;
    }

    private void refreshBidTable() {
        Tender t = bidTenderBox.getValue();
        if (t == null) {
            bidTable.getItems().clear();
            bidSupplierBox.getItems().clear();
            return;
        }
        bidTable.setItems(store.bidsForTender(t.getId()));
        bidSupplierBox.getItems().setAll(store.getSuppliers());
    }

    private void setupBidTable() {
        TableColumn<TenderBid, String> supplierCol = new TableColumn<>("Supplier");
        supplierCol.setCellValueFactory(c -> {
            String sid = c.getValue().getSupplierId();
            return store.findSupplierById(sid)
                    .map(s -> new SimpleStringProperty(s.getBusinessName()))
                    .orElse(new SimpleStringProperty(sid));
        });
        supplierCol.setPrefWidth(140);

        TableColumn<TenderBid, String> amountCol = new TableColumn<>("Amount");
        amountCol.setCellValueFactory(c -> new SimpleStringProperty(CurrencyConfig.format(c.getValue().getBidAmount())));
        amountCol.setPrefWidth(120);

        TableColumn<TenderBid, String> techCol = new TableColumn<>("Tech Score");
        techCol.setCellValueFactory(c -> new SimpleStringProperty(CurrencyConfig.format(c.getValue().getTechnicalScore())));
        techCol.setPrefWidth(90);

        TableColumn<TenderBid, String> finCol = new TableColumn<>("Fin Score");
        finCol.setCellValueFactory(c -> new SimpleStringProperty(CurrencyConfig.format(c.getValue().getFinancialScore())));
        finCol.setPrefWidth(90);

        TableColumn<TenderBid, String> weightedCol = new TableColumn<>("Weighted Score");
        weightedCol.setCellValueFactory(c -> new SimpleStringProperty(CurrencyConfig.format(c.getValue().getWeightedScore())));
        weightedCol.setPrefWidth(100);

        TableColumn<TenderBid, String> rankCol = new TableColumn<>("Rank");
        rankCol.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getRank() > 0 ? String.valueOf(c.getValue().getRank()) : ""));
        rankCol.setPrefWidth(50);

        TableColumn<TenderBid, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getStatus() != null ? c.getValue().getStatus().getDisplayName() : ""));
        statusCol.setPrefWidth(90);

        @SuppressWarnings("unchecked")
        var columns = new TableColumn[]{supplierCol, amountCol, techCol, finCol, weightedCol, rankCol, statusCol};
        bidTable.getColumns().addAll(columns);
        bidTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
    }

    private void submitBid() {
        Tender t = bidTenderBox.getValue();
        Supplier s = bidSupplierBox.getValue();
        if (t == null || s == null) {
            AlertUtil.warn("Incomplete", "Select both a tender and a supplier.");
            return;
        }
        if (!AlertUtil.confirm("Submit Bid", "Submit bid for tender " + t.getTenderNumber() + "?")) return;
        TenderBid bid = new TenderBid();
        bid.setTenderId(t.getId());
        bid.setSupplierId(s.getId());
        bid.setSubmissionDate(LocalDate.now());
        try {
            bid.setBidAmount(CurrencyConfig.money(bidAmountField.getText().trim()));
        } catch (NumberFormatException e) {
            AlertUtil.error("Error", "Invalid bid amount.");
            return;
        }
        bid.setDocuments(bidDocsField.getText());
        bid.setRemarks(bidRemarksArea.getText());

        List<String> errors = tenderService.submitBid(bid);
        if (!errors.isEmpty()) {
            AlertUtil.error("Error", String.join("\n", errors));
            return;
        }
        AlertUtil.info("Success", "Bid submitted.");
        bidAmountField.clear();
        bidDocsField.clear();
        bidRemarksArea.clear();
        refreshBidTable();
    }

    private void rankBids() {
        Tender t = bidTenderBox.getValue();
        if (t == null) {
            AlertUtil.warn("No Selection", "Select a tender to rank bids.");
            return;
        }
        TextInputDialog techDialog = new TextInputDialog("0.6");
        techDialog.setTitle("Technical Weight");
        techDialog.setHeaderText("Enter technical evaluation weight (0-1):");
        techDialog.setContentText("Weight:");
        techDialog.showAndWait().ifPresent(techStr -> {
            TextInputDialog finDialog = new TextInputDialog("0.4");
            finDialog.setTitle("Financial Weight");
            finDialog.setHeaderText("Enter financial evaluation weight (0-1):");
            finDialog.setContentText("Weight:");
            finDialog.showAndWait().ifPresent(finStr -> {
                try {
                    BigDecimal techW = new BigDecimal(techStr.trim());
                    BigDecimal finW = new BigDecimal(finStr.trim());
                    List<String> errors = tenderService.rankBids(t.getId(), techW, finW);
                    if (!errors.isEmpty()) {
                        AlertUtil.error("Error", String.join("\n", errors));
                        return;
                    }
                    AlertUtil.info("Ranked", "Bids ranked successfully.");
                    refreshBidTable();
                } catch (NumberFormatException ex) {
                    AlertUtil.error("Invalid Input", "Enter valid decimal weights.");
                }
            });
        });
    }

    // ========== EVALUATION TAB ==========

    private Tab buildEvaluationTab() {
        evalTenderBox.getItems().addAll(store.getTenders());
        evalTenderBox.setPrefWidth(280);
        evalTenderBox.valueProperty().addListener((obs, o, t) -> refreshEvalBidBox());

        evalBidBox.valueProperty().addListener((obs, o, b) -> refreshEvalTable());

        evalTypeBox.getItems().setAll("TECHNICAL", "FINANCIAL");
        evalTypeBox.setPrefWidth(200);
        evalCommentsArea.setPrefRowCount(2);
        evalCommentsArea.setWrapText(true);
        evalDatePick.setValue(LocalDate.now());

        setupEvalTable();

        Button recordBtn = new Button("Record Evaluation");
        recordBtn.getStyleClass().add("btn-primary");
        recordBtn.setOnAction(e -> recordEvaluation());

        GridPane form = new GridPane();
        form.setHgap(10);
        form.setVgap(8);
        form.add(voucherLabel("Select Tender"), 0, 0);
        form.add(voucherFieldBox(evalTenderBox, "Choose a tender"), 1, 0);
        form.add(voucherLabel("Select Bid"), 0, 1);
        form.add(voucherFieldBox(evalBidBox, "Choose a bid to evaluate"), 1, 1);
        form.add(voucherLabel("Evaluator Name"), 0, 2);
        form.add(voucherFieldBox(evaluatorField, "Name of evaluator"), 1, 2);
        form.add(voucherLabel("Evaluation Type"), 0, 3);
        form.add(voucherFieldBox(evalTypeBox, "TECHNICAL or FINANCIAL"), 1, 3);
        form.add(voucherLabel("Score"), 0, 4);
        form.add(voucherFieldBox(scoreField, "Score awarded"), 1, 4);
        form.add(voucherLabel("Max Score"), 0, 5);
        form.add(voucherFieldBox(maxScoreField, "Maximum possible score"), 1, 5);
        form.add(voucherLabel("Comments"), 0, 6);
        form.add(voucherFieldBox(evalCommentsArea, "Evaluator comments"), 1, 6);
        form.add(voucherLabel("Date"), 0, 7);
        form.add(voucherFieldBox(evalDatePick, "Evaluation date"), 1, 7);
        scoreField.setPrefWidth(200);
        maxScoreField.setPrefWidth(200);

        Label formTitle = new Label("Evaluation Entry");
        formTitle.getStyleClass().add("card-title");
        VBox formCard = new VBox(10, formTitle, form, recordBtn);
        formCard.getStyleClass().add("card");
        formCard.setPadding(new Insets(12));

        Label tableTitle = new Label("Evaluations");
        tableTitle.getStyleClass().add("card-title");
        VBox tableCard = new VBox(10, tableTitle, evalTable);
        tableCard.getStyleClass().add("card");
        tableCard.setPadding(new Insets(12));
        VBox.setVgrow(evalTable, Priority.ALWAYS);
        evalTable.setPrefHeight(250);

        VBox tabContent = new VBox(12, formCard, tableCard);
        tabContent.setPadding(new Insets(8));
        ScrollPane sp = new ScrollPane(tabContent);
        sp.setFitToWidth(true);
        sp.setPannable(true);

        Tab tab = new Tab("Evaluation", sp);
        tab.setClosable(false);
        return tab;
    }

    private void refreshEvalBidBox() {
        Tender t = evalTenderBox.getValue();
        evalBidBox.getItems().clear();
        evalTable.getItems().clear();
        if (t == null) return;
        evalBidBox.getItems().addAll(store.bidsForTender(t.getId()));
    }

    private void refreshEvalTable() {
        Tender t = evalTenderBox.getValue();
        if (t == null) {
            evalTable.getItems().clear();
            return;
        }
        evalTable.setItems(store.evaluationsForTender(t.getId()));
    }

    private void setupEvalTable() {
        TableColumn<TenderEvaluation, String> evaluatorCol = new TableColumn<>("Evaluator");
        evaluatorCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getEvaluatorName()));
        evaluatorCol.setPrefWidth(120);

        TableColumn<TenderEvaluation, String> typeCol = new TableColumn<>("Type");
        typeCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getEvaluationType()));
        typeCol.setPrefWidth(100);

        TableColumn<TenderEvaluation, String> scoreCol = new TableColumn<>("Score");
        scoreCol.setCellValueFactory(c -> new SimpleStringProperty(CurrencyConfig.format(c.getValue().getScore())));
        scoreCol.setPrefWidth(80);

        TableColumn<TenderEvaluation, String> maxCol = new TableColumn<>("Max Score");
        maxCol.setCellValueFactory(c -> new SimpleStringProperty(CurrencyConfig.format(c.getValue().getMaxScore())));
        maxCol.setPrefWidth(80);

        TableColumn<TenderEvaluation, String> pctCol = new TableColumn<>("%");
        pctCol.setCellValueFactory(c -> new SimpleStringProperty(String.format("%.1f%%", c.getValue().getScorePercentage())));
        pctCol.setPrefWidth(60);

        TableColumn<TenderEvaluation, String> commentsCol = new TableColumn<>("Comments");
        commentsCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getComments()));
        commentsCol.setPrefWidth(200);

        TableColumn<TenderEvaluation, String> dateCol = new TableColumn<>("Date");
        dateCol.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getEvaluatedDate() != null ? c.getValue().getEvaluatedDate().toString() : ""));
        dateCol.setPrefWidth(90);

        @SuppressWarnings("unchecked")
        var columns = new TableColumn[]{evaluatorCol, typeCol, scoreCol, maxCol, pctCol, commentsCol, dateCol};
        evalTable.getColumns().addAll(columns);
        evalTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
    }

    private void recordEvaluation() {
        Tender t = evalTenderBox.getValue();
        TenderBid b = evalBidBox.getValue();
        if (t == null || b == null) {
            AlertUtil.warn("Incomplete", "Select both a tender and a bid.");
            return;
        }
        if (!AlertUtil.confirm("Record Evaluation", "Record evaluation for selected bid?")) return;
        TenderEvaluation ev = new TenderEvaluation();
        ev.setTenderId(t.getId());
        ev.setBidId(b.getId());
        ev.setEvaluatorName(evaluatorField.getText());
        ev.setEvaluationType(evalTypeBox.getValue());
        try {
            ev.setScore(CurrencyConfig.money(scoreField.getText().trim()));
        } catch (NumberFormatException e) {
            AlertUtil.error("Error", "Invalid score.");
            return;
        }
        try {
            ev.setMaxScore(CurrencyConfig.money(maxScoreField.getText().trim()));
        } catch (NumberFormatException e) {
            AlertUtil.error("Error", "Invalid max score.");
            return;
        }
        ev.setComments(evalCommentsArea.getText());
        ev.setEvaluatedDate(evalDatePick.getValue());

        List<String> errors = tenderService.recordEvaluation(ev);
        if (!errors.isEmpty()) {
            AlertUtil.error("Error", String.join("\n", errors));
            return;
        }
        AlertUtil.info("Recorded", "Evaluation recorded.");
        evaluatorField.clear();
        scoreField.clear();
        maxScoreField.clear();
        evalCommentsArea.clear();
        refreshEvalTable();
    }

    // ========== COMMON ==========

    private static Label voucherLabel(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("field-label");
        return l;
    }

    private static VBox voucherFieldBox(javafx.scene.control.Control field, String hint) {
        VBox box = new VBox(2);
        box.getChildren().addAll(field, new Label(hint));
        return box;
    }

    @Override
    public void refresh() {
        tenderTable.refresh();
        bidTenderBox.getItems().setAll(store.getTenders());
        evalTenderBox.getItems().setAll(store.getTenders());
        refreshBidTable();
        refreshEvalTable();
    }
}
