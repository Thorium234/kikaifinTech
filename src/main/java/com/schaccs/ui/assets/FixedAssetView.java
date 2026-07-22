package com.schaccs.ui.assets;

import com.schaccs.config.CurrencyConfig;
import com.schaccs.enums.AccountType;
import com.schaccs.enums.TransactionType;
import com.schaccs.model.finance.Asset;
import com.schaccs.model.finance.AssetCategory;
import com.schaccs.model.finance.DepreciationSchedule;
import com.schaccs.model.finance.FinancialTransaction;
import com.schaccs.model.finance.LedgerEntry;
import com.schaccs.store.AccountStore;
import com.schaccs.store.LedgerStore;
import com.schaccs.ui.layout.MainLayout;
import com.schaccs.util.AlertUtil;
import com.schaccs.util.CurrencyUtil;
import com.schaccs.util.DateUtil;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public class FixedAssetView extends VBox implements MainLayout.Refreshable {

    private final AccountStore store = AccountStore.getInstance();
    private final LedgerStore ledgerStore = LedgerStore.getInstance();

    private final TableView<Asset> assetTable = new TableView<>();
    private final TextField codeField = new TextField();
    private final TextField nameField = new TextField();
    private final ComboBox<AssetCategory> categoryCombo = new ComboBox<>();
    private final TextField costField = new TextField();
    private final DatePicker acquisitionDate = new DatePicker(LocalDate.now());
    private final TextField locationField = new TextField();
    private final ComboBox<Asset.AssetStatus> statusCombo = new ComboBox<>();
    private final TextArea notesArea = new TextArea();

    private final TableView<AssetCategory> categoryTable = new TableView<>();
    private final TextField catNameField = new TextField();
    private final ComboBox<AssetCategory.DepreciationMethod> depMethodCombo = new ComboBox<>();
    private final TextField usefulLifeField = new TextField();
    private final TextField salvageRateField = new TextField();

    private final TableView<DepreciationSchedule> scheduleTable = new TableView<>();
    private Asset selectedAsset;

    public FixedAssetView() {
        setSpacing(12);
        setPadding(new Insets(4));

        categoryCombo.setItems(store.getAssetCategories());
        categoryCombo.setPrefWidth(200);
        statusCombo.setItems(FXCollections.observableArrayList(Asset.AssetStatus.values()));
        statusCombo.setValue(Asset.AssetStatus.IN_USE);

        depMethodCombo.setItems(FXCollections.observableArrayList(AssetCategory.DepreciationMethod.values()));
        depMethodCombo.setValue(AssetCategory.DepreciationMethod.STRAIGHT_LINE);

        buildAssetsSection();
        buildCategoriesSection();
        buildScheduleSection();

        refresh();
    }

    private void buildAssetsSection() {
        Label heading = new Label("Fixed Assets");
        heading.getStyleClass().add("section-title");
        Label sub = new Label("Manage fixed assets, track depreciation, and record disposals.");
        sub.getStyleClass().add("muted");

        TableColumn<Asset, String> codeCol = new TableColumn<>("Code");
        codeCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getAssetCode()));
        TableColumn<Asset, String> nameCol = new TableColumn<>("Name");
        nameCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getName()));
        TableColumn<Asset, String> catCol = new TableColumn<>("Category");
        catCol.setCellValueFactory(c -> {
            String catId = c.getValue().getCategoryId();
            if (catId == null) return new SimpleStringProperty("");
            return store.getAssetCategories().stream()
                    .filter(cat -> cat.getId().equals(catId))
                    .findFirst()
                    .map(AssetCategory::getName)
                    .map(SimpleStringProperty::new)
                    .orElse(new SimpleStringProperty(""));
        });
        TableColumn<Asset, String> costCol = new TableColumn<>("Cost");
        costCol.setCellValueFactory(c -> new SimpleStringProperty(CurrencyUtil.format(c.getValue().getPurchaseCost())));
        TableColumn<Asset, String> valueCol = new TableColumn<>("Current Value");
        valueCol.setCellValueFactory(c -> new SimpleStringProperty(CurrencyUtil.format(c.getValue().getCurrentValue())));
        TableColumn<Asset, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getStatus().getDisplayName()));
        TableColumn<Asset, String> locCol = new TableColumn<>("Location");
        locCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getLocation()));

        @SuppressWarnings("unchecked")
        var assetColumns = new TableColumn[]{codeCol, nameCol, catCol, costCol, valueCol, statusCol, locCol};
        assetTable.getColumns().addAll(assetColumns);
        assetTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        VBox.setVgrow(assetTable, Priority.SOMETIMES);

        assetTable.getSelectionModel().selectedItemProperty().addListener((obs, o, asset) -> {
            if (asset != null) {
                selectedAsset = asset;
                codeField.setText(asset.getAssetCode());
                nameField.setText(asset.getName());
                categoryCombo.getItems().stream()
                        .filter(c -> c.getId().equals(asset.getCategoryId()))
                        .findFirst()
                        .ifPresent(categoryCombo::setValue);
                costField.setText(CurrencyUtil.formatPlain(asset.getPurchaseCost()));
                acquisitionDate.setValue(asset.getPurchaseDate());
                locationField.setText(asset.getLocation());
                statusCombo.setValue(asset.getStatus());
                notesArea.setText(asset.getDescription());
                scheduleTable.getItems().setAll(store.findSchedulesByAssetId(asset.getId()));
            }
        });

        Button addBtn = new Button("Add Asset");
        addBtn.getStyleClass().add("primary-button");
        addBtn.setOnAction(e -> addAsset());

        Button disposeBtn = new Button("Dispose Selected");
        disposeBtn.getStyleClass().add("danger-button");
        disposeBtn.setOnAction(e -> disposeAsset());

        Button scheduleBtn = new Button("View Depreciation Schedule");
        scheduleBtn.getStyleClass().add("secondary-button");
        scheduleBtn.setOnAction(e -> viewSchedule());

        Button runDepBtn = new Button("Run Depreciation");
        runDepBtn.getStyleClass().add("success-button");
        runDepBtn.setOnAction(e -> runDepreciation());

        GridPane form = new GridPane();
        form.setHgap(10);
        form.setVgap(8);
        int row = 0;
        form.add(new Label("Asset Code"), 0, row);
        form.add(codeField, 1, row++);
        form.add(new Label("Name"), 0, row);
        form.add(nameField, 1, row++);
        form.add(new Label("Category"), 0, row);
        form.add(categoryCombo, 1, row++);
        form.add(new Label("Cost"), 0, row);
        form.add(costField, 1, row++);
        form.add(new Label("Acquisition Date"), 0, row);
        form.add(acquisitionDate, 1, row++);
        form.add(new Label("Location"), 0, row);
        form.add(locationField, 1, row++);
        form.add(new Label("Status"), 0, row);
        form.add(statusCombo, 1, row++);
        form.add(new Label("Notes"), 0, row);
        notesArea.setPrefRowCount(3);
        form.add(notesArea, 1, row++);

        VBox card = new VBox(10, heading, sub,
                new HBox(10, addBtn, disposeBtn, scheduleBtn, runDepBtn),
                assetTable, form);
        card.getStyleClass().add("card");
        getChildren().add(card);
    }

    private void buildCategoriesSection() {
        Label catHeading = new Label("Asset Categories");
        catHeading.getStyleClass().add("section-title");

        TableColumn<AssetCategory, String> nameCol = new TableColumn<>("Name");
        nameCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getName()));
        TableColumn<AssetCategory, String> methodCol = new TableColumn<>("Depreciation Method");
        methodCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getDepreciationMethod().getDisplayName()));
        TableColumn<AssetCategory, String> lifeCol = new TableColumn<>("Useful Life (yrs)");
        lifeCol.setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue().getUsefulLifeYears())));
        TableColumn<AssetCategory, String> salvageCol = new TableColumn<>("Salvage Rate (%)");
        salvageCol.setCellValueFactory(c -> new SimpleStringProperty(String.format("%.1f", c.getValue().getSalvageValuePercent())));

        @SuppressWarnings("unchecked")
        var catColumns = new TableColumn[]{nameCol, methodCol, lifeCol, salvageCol};
        categoryTable.getColumns().addAll(catColumns);
        categoryTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        VBox.setVgrow(categoryTable, Priority.SOMETIMES);

        categoryTable.getSelectionModel().selectedItemProperty().addListener((obs, o, cat) -> {
            if (cat != null) {
                catNameField.setText(cat.getName());
                depMethodCombo.setValue(cat.getDepreciationMethod());
                usefulLifeField.setText(String.valueOf(cat.getUsefulLifeYears()));
                salvageRateField.setText(String.valueOf(cat.getSalvageValuePercent()));
            }
        });

        Button addCatBtn = new Button("Add Category");
        addCatBtn.getStyleClass().add("primary-button");
        addCatBtn.setOnAction(e -> addCategory());

        GridPane catForm = new GridPane();
        catForm.setHgap(10);
        catForm.setVgap(8);
        int row = 0;
        catForm.add(new Label("Name"), 0, row);
        catForm.add(catNameField, 1, row++);
        catForm.add(new Label("Depreciation Method"), 0, row);
        catForm.add(depMethodCombo, 1, row++);
        catForm.add(new Label("Useful Life (years)"), 0, row);
        catForm.add(usefulLifeField, 1, row++);
        catForm.add(new Label("Salvage Rate (%)"), 0, row);
        catForm.add(salvageRateField, 1, row++);

        VBox card = new VBox(10, catHeading, addCatBtn, categoryTable, catForm);
        card.getStyleClass().add("card");
        getChildren().add(card);
    }

    private void buildScheduleSection() {
        Label schedHeading = new Label("Depreciation Schedule");
        schedHeading.getStyleClass().add("section-title");

        TableColumn<DepreciationSchedule, String> periodCol = new TableColumn<>("Period");
        periodCol.setCellValueFactory(c -> {
            LocalDate start = c.getValue().getPeriodStart();
            LocalDate end = c.getValue().getPeriodEnd();
            if (start == null || end == null) return new SimpleStringProperty("");
            return new SimpleStringProperty(DateUtil.format(start) + " - " + DateUtil.format(end));
        });
        TableColumn<DepreciationSchedule, String> depAmtCol = new TableColumn<>("Depreciation");
        depAmtCol.setCellValueFactory(c -> new SimpleStringProperty(CurrencyUtil.format(c.getValue().getDepreciationAmount())));
        TableColumn<DepreciationSchedule, String> accDepCol = new TableColumn<>("Accumulated");
        accDepCol.setCellValueFactory(c -> new SimpleStringProperty(CurrencyUtil.format(c.getValue().getAccumulatedDepreciation())));
        TableColumn<DepreciationSchedule, String> nbvCol = new TableColumn<>("Net Book Value");
        nbvCol.setCellValueFactory(c -> new SimpleStringProperty(CurrencyUtil.format(c.getValue().getNetBookValue())));

        @SuppressWarnings("unchecked")
        var schedColumns = new TableColumn[]{periodCol, depAmtCol, accDepCol, nbvCol};
        scheduleTable.getColumns().addAll(schedColumns);
        scheduleTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        VBox.setVgrow(scheduleTable, Priority.ALWAYS);

        VBox card = new VBox(10, schedHeading, scheduleTable);
        card.getStyleClass().add("card");
        getChildren().add(card);
    }

    private void addAsset() {
        String code = codeField.getText().trim();
        String name = nameField.getText().trim();
        if (code.isEmpty() || name.isEmpty()) {
            AlertUtil.warn("Missing fields", "Asset Code and Name are required.");
            return;
        }
        AssetCategory category = categoryCombo.getValue();
        if (category == null) {
            AlertUtil.warn("Missing category", "Select an asset category.");
            return;
        }
        BigDecimal cost;
        try {
            cost = CurrencyConfig.money(costField.getText().trim());
        } catch (Exception e) {
            AlertUtil.warn("Invalid cost", "Enter a valid cost amount.");
            return;
        }
        if (cost.compareTo(BigDecimal.ZERO) <= 0) {
            AlertUtil.warn("Invalid cost", "Cost must be positive.");
            return;
        }
        LocalDate date = acquisitionDate.getValue();
        if (date == null) {
            AlertUtil.warn("Missing date", "Select an acquisition date.");
            return;
        }

        Asset asset = new Asset();
        asset.setAssetCode(code);
        asset.setName(name);
        asset.setCategoryId(category.getId());
        asset.setPurchaseCost(cost);
        asset.setPurchaseDate(date);
        asset.setLocation(locationField.getText().trim());
        asset.setStatus(statusCombo.getValue() != null ? statusCombo.getValue() : Asset.AssetStatus.IN_USE);
        asset.setCurrentValue(cost);
        asset.setDescription(notesArea.getText().trim());

        store.getAssets().add(asset);
        clearAssetForm();
        refresh();
        AlertUtil.info("Asset Added", "Asset \"" + name + "\" added successfully.");
    }

    private void disposeAsset() {
        Asset asset = assetTable.getSelectionModel().getSelectedItem();
        if (asset == null) {
            AlertUtil.warn("No selection", "Select an asset to dispose.");
            return;
        }
        if (!AlertUtil.confirm("Confirm Disposal", "Dispose asset \"" + asset.getName() + "\"?")) {
            return;
        }
        asset.setStatus(Asset.AssetStatus.DISPOSED);
        asset.setCurrentValue(CurrencyConfig.zero());
        assetTable.refresh();
        AlertUtil.info("Disposed", "Asset \"" + asset.getName() + "\" marked as disposed.");
    }

    private void viewSchedule() {
        Asset asset = assetTable.getSelectionModel().getSelectedItem();
        if (asset == null) {
            AlertUtil.warn("No selection", "Select an asset to view its depreciation schedule.");
            return;
        }
        List<DepreciationSchedule> schedules = store.findSchedulesByAssetId(asset.getId());
        scheduleTable.getItems().setAll(schedules);
    }

    private void runDepreciation() {
        int count = 0;
        for (Asset asset : store.getAssets()) {
            if (asset.getStatus() != Asset.AssetStatus.IN_USE) continue;

            String catId = asset.getCategoryId();
            if (catId == null) continue;

            Optional<AssetCategory> catOpt = store.getAssetCategories().stream()
                    .filter(c -> c.getId().equals(catId))
                    .findFirst();
            if (catOpt.isEmpty()) continue;

            AssetCategory category = catOpt.get();
            if (category.getUsefulLifeYears() <= 0) continue;

            BigDecimal cost = asset.getPurchaseCost();
            BigDecimal salvagePct = BigDecimal.valueOf(category.getSalvageValuePercent() / 100.0);
            BigDecimal salvageValue = cost.multiply(salvagePct).setScale(CurrencyConfig.SCALE, CurrencyConfig.ROUNDING);
            BigDecimal depreciableBase = cost.subtract(salvageValue);
            if (depreciableBase.compareTo(BigDecimal.ZERO) <= 0) continue;

            BigDecimal depreciationAmount;
            if (category.getDepreciationMethod() == AssetCategory.DepreciationMethod.STRAIGHT_LINE) {
                int totalMonths = category.getUsefulLifeYears() * 12;
                depreciationAmount = depreciableBase.divide(BigDecimal.valueOf(totalMonths), CurrencyConfig.SCALE, CurrencyConfig.ROUNDING);
            } else {
                BigDecimal rate = BigDecimal.ONE.divide(BigDecimal.valueOf(category.getUsefulLifeYears()), CurrencyConfig.SCALE, CurrencyConfig.ROUNDING);
                rate = rate.multiply(BigDecimal.valueOf(2));
                BigDecimal currentValue = asset.getCurrentValue();
                depreciationAmount = currentValue.multiply(rate).setScale(CurrencyConfig.SCALE, CurrencyConfig.ROUNDING);
                if (depreciationAmount.compareTo(currentValue.subtract(salvageValue)) > 0) {
                    depreciationAmount = currentValue.subtract(salvageValue);
                }
            }

            if (depreciationAmount.compareTo(BigDecimal.ZERO) <= 0) continue;

            List<DepreciationSchedule> existingSchedules = store.findSchedulesByAssetId(asset.getId());
            BigDecimal accumulated = existingSchedules.stream()
                    .map(DepreciationSchedule::getDepreciationAmount)
                    .reduce(CurrencyConfig.zero(), BigDecimal::add);
            accumulated = accumulated.add(depreciationAmount);

            BigDecimal newValue = asset.getCurrentValue().subtract(depreciationAmount);
            if (newValue.compareTo(salvageValue) < 0) {
                newValue = salvageValue;
            }

            LocalDate now = LocalDate.now();
            LocalDate periodStart = now.withDayOfMonth(1);
            LocalDate periodEnd = periodStart.plusMonths(1).minusDays(1);

            DepreciationSchedule schedule = new DepreciationSchedule();
            schedule.setAssetId(asset.getId());
            schedule.setPeriodStart(periodStart);
            schedule.setPeriodEnd(periodEnd);
            schedule.setDepreciationAmount(depreciationAmount);
            schedule.setAccumulatedDepreciation(accumulated);
            schedule.setNetBookValue(newValue);
            store.getDepreciationSchedules().add(schedule);

            asset.setCurrentValue(newValue);

            String reference = "DEPR-" + asset.getAssetCode() + "-" + periodStart.toString();

            FinancialTransaction tx = new FinancialTransaction();
            tx.setDate(now);
            tx.setType(TransactionType.DEPRECIATION);
            tx.setReference(reference);
            tx.setDescription("Depreciation for " + asset.getName() + " (" + periodStart + " to " + periodEnd + ")");
            tx.setCreatedBy("system");
            ledgerStore.addTransaction(tx);

            LedgerEntry depExpenseEntry = new LedgerEntry();
            depExpenseEntry.setDate(now);
            depExpenseEntry.setAccountType(AccountType.GENERAL_EXPENSES);
            depExpenseEntry.setVoteheadCode("DEPR");
            depExpenseEntry.setReference(reference);
            depExpenseEntry.setDescription("Depreciation expense - " + asset.getName());
            depExpenseEntry.setDebit(depreciationAmount);
            depExpenseEntry.setCredit(CurrencyConfig.zero());
            depExpenseEntry.setTransactionId(tx.getId());
            ledgerStore.addLedgerEntry(depExpenseEntry);

            LedgerEntry accDepEntry = new LedgerEntry();
            accDepEntry.setDate(now);
            accDepEntry.setAccountType(AccountType.FIXED_ASSETS);
            accDepEntry.setVoteheadCode("ACC_DEPR");
            accDepEntry.setReference(reference);
            accDepEntry.setDescription("Accumulated depreciation - " + asset.getName());
            accDepEntry.setDebit(CurrencyConfig.zero());
            accDepEntry.setCredit(depreciationAmount);
            accDepEntry.setTransactionId(tx.getId());
            ledgerStore.addLedgerEntry(accDepEntry);

            count++;
        }

        if (count > 0) {
            assetTable.refresh();
            AlertUtil.info("Depreciation Complete", "Processed depreciation for " + count + " asset(s).");
        } else {
            AlertUtil.info("No Depreciation", "No active assets eligible for depreciation.");
        }
    }

    private void addCategory() {
        String name = catNameField.getText().trim();
        if (name.isEmpty()) {
            AlertUtil.warn("Missing name", "Category name is required.");
            return;
        }
        int usefulLife;
        try {
            usefulLife = Integer.parseInt(usefulLifeField.getText().trim());
            if (usefulLife <= 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            AlertUtil.warn("Invalid life", "Enter a positive integer for useful life.");
            return;
        }
        double salvageRate;
        try {
            salvageRate = Double.parseDouble(salvageRateField.getText().trim());
            if (salvageRate < 0 || salvageRate > 100) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            AlertUtil.warn("Invalid rate", "Enter a salvage rate between 0 and 100.");
            return;
        }

        AssetCategory category = new AssetCategory();
        category.setName(name);
        category.setDepreciationMethod(depMethodCombo.getValue() != null
                ? depMethodCombo.getValue() : AssetCategory.DepreciationMethod.STRAIGHT_LINE);
        category.setUsefulLifeYears(usefulLife);
        category.setSalvageValuePercent(salvageRate);

        store.getAssetCategories().add(category);
        catNameField.clear();
        usefulLifeField.clear();
        salvageRateField.clear();
        categoryTable.setItems(store.getAssetCategories());
        categoryTable.refresh();
        AlertUtil.info("Category Added", "Category \"" + name + "\" added.");
    }

    private void clearAssetForm() {
        codeField.clear();
        nameField.clear();
        categoryCombo.setValue(null);
        costField.clear();
        acquisitionDate.setValue(LocalDate.now());
        locationField.clear();
        statusCombo.setValue(Asset.AssetStatus.IN_USE);
        notesArea.clear();
    }

    @Override
    public void refresh() {
        assetTable.setItems(store.getAssets());
        categoryTable.setItems(store.getAssetCategories());
        if (selectedAsset != null) {
            scheduleTable.getItems().setAll(store.findSchedulesByAssetId(selectedAsset.getId()));
        }
    }
}
