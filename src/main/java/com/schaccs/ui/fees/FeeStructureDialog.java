package com.schaccs.ui.fees;

import com.schaccs.config.AppConfig;
import com.schaccs.config.CurrencyConfig;
import com.schaccs.enums.AcademicTerm;
import com.schaccs.enums.BoardingStatus;
import com.schaccs.model.fee.FeeStructure;
import com.schaccs.model.fee.FeeStructureItem;
import com.schaccs.model.fee.FeeStructureTemplate;
import com.schaccs.model.fee.FeeStructureTemplateItem;
import com.schaccs.model.finance.Votehead;
import com.schaccs.repository.PersistenceService;
import com.schaccs.store.FeeStructureStore;
import com.schaccs.util.CurrencyUtil;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import javafx.stage.Window;
import javafx.util.StringConverter;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class FeeStructureDialog extends Stage {

    private final FeeStructureStore store;
    private final TextField nameField = new TextField();
    private final TextField yearField = new TextField();
    private final ComboBox<String> formClassBox = new ComboBox<>();
    private final ComboBox<FeeStructureTemplate> templateBox = new ComboBox<>();
    private final TableView<VoteheadRow> amountTable = new TableView<>();
    private final ObservableList<VoteheadRow> rows = FXCollections.observableArrayList();

    public FeeStructureDialog(FeeStructureStore store, Window owner) {
        this.store = store;
        setTitle("Create Fee Structure");
        initOwner(owner);
        initModality(javafx.stage.Modality.APPLICATION_MODAL);

        nameField.setPromptText("e.g. Boarding Fee Structure 2026");
        yearField.setText(String.valueOf(AppConfig.getInstance().getAcademicYear()));
        formClassBox.getItems().add("ALL");
        formClassBox.setValue("ALL");
        formClassBox.setEditable(true);

        templateBox.setItems(store.getTemplates());
        templateBox.setPromptText("Load from template...");
        templateBox.setPrefWidth(280);

        Button loadTemplateBtn = new Button("Apply Template");
        loadTemplateBtn.getStyleClass().add("secondary-button");
        loadTemplateBtn.setOnAction(e -> applyTemplate());

        HBox templateRow = new HBox(8, new Label("Template:"), templateBox, loadTemplateBtn);
        templateRow.setAlignment(Pos.CENTER_LEFT);

        GridPane form = new GridPane();
        form.setHgap(10);
        form.setVgap(8);
        form.add(new Label("Name:"), 0, 0);
        form.add(nameField, 1, 0);
        form.add(new Label("Academic Year:"), 0, 1);
        form.add(yearField, 1, 1);
        form.add(new Label("Form Class:"), 0, 2);
        form.add(formClassBox, 1, 2);

        setupAmountTable();

        Button copyBtn = new Button("Copy Term 1 → All");
        copyBtn.getStyleClass().add("secondary-button");
        copyBtn.setOnAction(e -> copyTerm1ToAll());

        Button createBoardingBtn = new Button("Create Boarding Structure");
        createBoardingBtn.getStyleClass().add("primary-button");
        createBoardingBtn.setOnAction(e -> createStructure(BoardingStatus.BOARDING));

        Button createDayBtn = new Button("Create Day Structure");
        createDayBtn.getStyleClass().add("primary-button");
        createDayBtn.setOnAction(e -> createStructure(BoardingStatus.DAY));

        Button createBothBtn = new Button("Create Both Day & Boarding");
        createBothBtn.getStyleClass().add("success-button");
        createBothBtn.setOnAction(e -> createBoth());

        Button cancelBtn = new Button("Cancel");
        cancelBtn.getStyleClass().add("secondary-button");
        cancelBtn.setOnAction(e -> close());

        HBox actions = new HBox(10, createBoardingBtn, createDayBtn, createBothBtn, cancelBtn);
        actions.setAlignment(Pos.CENTER_LEFT);

        VBox root = new VBox(12,
                new Label("Configure fee structure(s). Amounts are per-term per-votehead."),
                templateRow,
                form,
                new Label("Votehead Amounts per Term:"),
                copyBtn,
                amountTable,
                actions);
        root.setPadding(new Insets(16));
        root.setPrefSize(680, 520);
        setScene(new javafx.scene.Scene(root));
        loadVoteheads();
    }

    private void setupAmountTable() {
        TableColumn<VoteheadRow, String> codeCol = new TableColumn<>("Code");
        codeCol.setCellValueFactory(c -> c.getValue().codeProperty());
        codeCol.setPrefWidth(70);

        TableColumn<VoteheadRow, String> nameCol = new TableColumn<>("Name");
        nameCol.setCellValueFactory(c -> c.getValue().nameProperty());
        nameCol.setPrefWidth(140);

        StringConverter<BigDecimal> converter = new StringConverter<>() {
            @Override
            public String toString(BigDecimal obj) {
                return obj != null && obj.compareTo(BigDecimal.ZERO) != 0
                        ? CurrencyUtil.formatPlain(obj) : "";
            }
            @Override
            public BigDecimal fromString(String s) {
                try {
                    return CurrencyConfig.money(new BigDecimal(s.trim()));
                } catch (Exception e) {
                    return BigDecimal.ZERO;
                }
            }
        };
        for (AcademicTerm term : AcademicTerm.values()) {
            TableColumn<VoteheadRow, BigDecimal> col = new TableColumn<>(term.getDisplayName());
            col.setCellValueFactory(c -> c.getValue().amountProperty(term));
            col.setCellFactory(javafx.scene.control.cell.TextFieldTableCell.forTableColumn(converter));
            col.setOnEditCommit(e -> e.getRowValue().setAmount(term, e.getNewValue()));
            col.setPrefWidth(110);
            amountTable.getColumns().add(col);
        }

        amountTable.setItems(rows);
        amountTable.setEditable(true);
        amountTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        VBox.setVgrow(amountTable, Priority.ALWAYS);
    }

    private void loadVoteheads() {
        rows.clear();
        for (Votehead vh : store.getVoteheads()) {
            if (vh.isActive()) {
                rows.add(new VoteheadRow(vh.getCode(), vh.getName()));
            }
        }
    }

    private void applyTemplate() {
        FeeStructureTemplate t = templateBox.getValue();
        if (t == null) return;
        for (FeeStructureTemplateItem item : t.getItems()) {
            for (VoteheadRow row : rows) {
                if (row.getCode().equals(item.getVoteheadCode())) {
                    row.setAmount(item.getTerm(), item.getAmount());
                    break;
                }
            }
        }
        amountTable.refresh();
    }

    private void copyTerm1ToAll() {
        for (VoteheadRow row : rows) {
            BigDecimal t1 = row.getAmount(AcademicTerm.TERM_1);
            row.setAmount(AcademicTerm.TERM_2, t1);
            row.setAmount(AcademicTerm.TERM_3, t1);
        }
        amountTable.refresh();
    }

    private void createStructure(BoardingStatus status) {
        String name = nameField.getText().trim();
        if (name.isBlank()) {
            name = status == BoardingStatus.BOARDING ? "Boarding Fee Structure" : "Day Fee Structure";
            name += " " + yearField.getText().trim();
        }
        int year;
        try {
            year = Integer.parseInt(yearField.getText().trim());
        } catch (NumberFormatException e) {
            year = AppConfig.getInstance().getAcademicYear();
        }
        String formClass = formClassBox.getValue();
        if (formClass == null || formClass.isBlank()) formClass = "ALL";

        FeeStructure fs = new FeeStructure(year, formClass, status, name);
        for (VoteheadRow row : rows) {
            for (AcademicTerm term : AcademicTerm.values()) {
                BigDecimal amt = row.getAmount(term);
                if (amt != null && amt.compareTo(BigDecimal.ZERO) > 0) {
                    fs.addItem(new FeeStructureItem(
                            row.getCode(), row.getName(), term, status, amt));
                }
            }
        }
        store.addStructure(fs);
        PersistenceService.getInstance().saveAll();
        close();
    }

    private void createBoth() {
        String baseName = nameField.getText().trim();
        if (baseName.isBlank()) baseName = "Fee Structure";

        String yearStr = yearField.getText().trim();
        int year;
        try {
            year = Integer.parseInt(yearStr);
        } catch (NumberFormatException e) {
            year = AppConfig.getInstance().getAcademicYear();
        }
        String formClass = formClassBox.getValue();
        if (formClass == null || formClass.isBlank()) formClass = "ALL";

        for (BoardingStatus status : List.of(BoardingStatus.BOARDING, BoardingStatus.DAY)) {
            String name = status == BoardingStatus.BOARDING
                    ? "Boarding " + baseName + " " + year
                    : "Day " + baseName + " " + year;
            FeeStructure fs = new FeeStructure(year, formClass, status, name);
            for (VoteheadRow row : rows) {
                for (AcademicTerm term : AcademicTerm.values()) {
                    BigDecimal amt = row.getAmount(term);
                    if (amt != null && amt.compareTo(BigDecimal.ZERO) > 0) {
                        fs.addItem(new FeeStructureItem(
                                row.getCode(), row.getName(), term, status, amt));
                    }
                }
            }
            store.addStructure(fs);
        }
        PersistenceService.getInstance().saveAll();
        close();
    }

    public static final class VoteheadRow {
        private final SimpleStringProperty code;
        private final SimpleStringProperty name;
        private final java.util.Map<AcademicTerm, SimpleObjectProperty<BigDecimal>> amounts;

        public VoteheadRow(String code, String name) {
            this.code = new SimpleStringProperty(code);
            this.name = new SimpleStringProperty(name);
            this.amounts = new java.util.HashMap<>();
            for (AcademicTerm t : AcademicTerm.values()) {
                amounts.put(t, new SimpleObjectProperty<>(BigDecimal.ZERO));
            }
        }

        public String getCode() { return code.get(); }
        public SimpleStringProperty codeProperty() { return code; }
        public String getName() { return name.get(); }
        public SimpleStringProperty nameProperty() { return name; }

        public BigDecimal getAmount(AcademicTerm term) {
            return amounts.get(term).get();
        }

        public SimpleObjectProperty<BigDecimal> amountProperty(AcademicTerm term) {
            return amounts.get(term);
        }

        public void setAmount(AcademicTerm term, BigDecimal value) {
            amounts.get(term).set(value != null ? CurrencyConfig.money(value) : BigDecimal.ZERO);
        }
    }
}
