package com.schaccs.ui.payments;

import com.schaccs.model.finance.Votehead;
import com.schaccs.model.student.Student;
import com.schaccs.model.student.StudentFeeLedger;
import com.schaccs.store.FeeStructureStore;
import com.schaccs.store.SchoolCustomStore;
import com.schaccs.store.StudentStore;
import com.schaccs.ui.component.SearchBar;
import com.schaccs.ui.layout.MainLayout;
import com.schaccs.ui.layout.Sidebar;
import com.schaccs.ui.receipts.ReceiptView;
import com.schaccs.util.CurrencyUtil;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Pay workspace: browse all students, filter by form/grade or stream, search,
 * preview a learner's details and fee status (charged, paid, balance, arrears),
 * then proceed straight into receipting with that student pre-selected.
 */
public class PayView extends VBox implements MainLayout.Refreshable {

    private static final String ALL_FORMS = "All Forms";
    private static final String ALL_STREAMS = "All Streams";

    private final StudentStore studentStore = StudentStore.getInstance();
    private final MainLayout layout;

    private final ComboBox<String> formBox = new ComboBox<>();
    private final ComboBox<String> streamBox = new ComboBox<>();
    private final SearchBar searchBar = new SearchBar("Search by admission no, name or class…");
    private final TableView<Student> table = new TableView<>();
    private final FilteredList<Student> filtered;

    private final StackPane previewStack = new StackPane();
    private final Label placeholder = new Label("Select a student to preview their details and fee status.");
    private final VBox previewContent = new VBox();

    private final Label admValue = new Label();
    private final Label nameValue = new Label();
    private final Label classValue = new Label();
    private final Label streamValue = new Label();
    private final Label genderValue = new Label();
    private final Label boardingValue = new Label();
    private final Label phoneValue = new Label();
    private final Label parentValue = new Label();
    private final Label yearValue = new Label();
    private final Label academicYearValue = new Label();

    private final Label chargedValue = new Label();
    private final Label paidValue = new Label();
    private final Label balanceValue = new Label();
    private final Label arrearsValue = new Label();
    private final Label advanceValue = new Label();

    private final TableView<VoteheadRow> voteheadTable = new TableView<>();

    private Student selected;

    public PayView(MainLayout layout) {
        this.layout = layout;
        filtered = new FilteredList<>(studentStore.getStudents(), s -> true);
        setSpacing(14);
        setPadding(new Insets(4));

        Label heading = new Label("Pay \u2014 Student Fee Intake");
        heading.getStyleClass().add("section-title");

        Label sub = new Label("Browse all students, filter by form/grade or stream, search, preview a "
                + "learner's fee status, then proceed to receipting with their details already picked.");
        sub.getStyleClass().add("muted");
        sub.setWrapText(true);

        populateFilters();
        setupTable();

        Button clearFilters = new Button("Clear Filters");
        clearFilters.getStyleClass().add("secondary-button");
        clearFilters.setOnAction(e -> {
            formBox.setValue(ALL_FORMS);
            streamBox.setValue(ALL_STREAMS);
            searchBar.clear();
        });

        HBox filterBar = new HBox(10, new Label("Form/Grade:"), formBox,
                new Label("Stream:"), streamBox, searchBar, clearFilters);
        filterBar.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(searchBar, Priority.ALWAYS);
        formBox.setPrefWidth(140);
        streamBox.setPrefWidth(110);

        VBox tableCard = new VBox(10, filterBar, table);
        tableCard.getStyleClass().add("card");
        VBox.setVgrow(table, Priority.ALWAYS);
        table.setPrefHeight(420);

        VBox previewCard = buildPreviewCard();

        HBox workspace = new HBox(14, tableCard, previewCard);
        workspace.setFillHeight(true);
        HBox.setHgrow(tableCard, Priority.ALWAYS);

        VBox content = new VBox(12, heading, sub, workspace);
        content.setPadding(new Insets(4));
        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(true);
        scrollPane.setPannable(true);
        scrollPane.getStyleClass().add("content-scroll");
        VBox.setVgrow(scrollPane, Priority.ALWAYS);
        getChildren().add(scrollPane);

        showPlaceholder();
        applyFilters();
    }

    private void populateFilters() {
        formBox.getItems().clear();
        streamBox.getItems().clear();
        formBox.getItems().add(ALL_FORMS);
        streamBox.getItems().add(ALL_STREAMS);

        Set<String> forms = new TreeSet<>();
        Set<String> streams = new TreeSet<>();
        for (Student s : studentStore.getStudents()) {
            if (s.getFormClass() != null && !s.getFormClass().isBlank()) forms.add(s.getFormClass().trim());
            if (s.getStream() != null && !s.getStream().isBlank()) streams.add(s.getStream().trim());
        }
        SchoolCustomStore scs = SchoolCustomStore.getInstance();
        scs.getFormClasses().forEach(fc -> {
            if (fc.getName() != null && !fc.getName().isBlank()) forms.add(fc.getName().trim());
        });
        scs.getStreams().forEach(st -> {
            if (st.getName() != null && !st.getName().isBlank()) streams.add(st.getName().trim());
        });
        formBox.getItems().addAll(forms);
        streamBox.getItems().addAll(streams);

        formBox.setValue(ALL_FORMS);
        streamBox.setValue(ALL_STREAMS);

        formBox.setOnAction(e -> applyFilters());
        streamBox.setOnAction(e -> applyFilters());
        searchBar.textProperty().addListener((obs, o, q) -> applyFilters());
    }

    private void setupTable() {
        TableColumn<Student, String> adm = new TableColumn<>("Adm No");
        adm.setCellValueFactory(c -> c.getValue().admissionNumberProperty());
        adm.setPrefWidth(100);

        TableColumn<Student, String> name = new TableColumn<>("Name");
        name.setCellValueFactory(c -> c.getValue().nameProperty());
        name.setPrefWidth(200);

        TableColumn<Student, String> cls = new TableColumn<>("Form");
        cls.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getFormClass() == null ? "" : c.getValue().getFormClass()));
        cls.setPrefWidth(90);

        TableColumn<Student, String> stream = new TableColumn<>("Stream");
        stream.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getStream() == null ? "" : c.getValue().getStream()));
        stream.setPrefWidth(70);

        TableColumn<Student, String> bal = new TableColumn<>("Balance");
        bal.setCellValueFactory(c -> new SimpleStringProperty(
                CurrencyUtil.format(studentStore.getLedger(c.getValue().getId()).getBalance())));
        bal.setPrefWidth(110);

        table.getColumns().addAll(adm, name, cls, stream, bal);
        table.setItems(filtered);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(new Label("No students match the current filters."));
        table.getSelectionModel().selectedItemProperty().addListener((obs, o, s) -> {
            if (s != null) {
                showPreview(s);
            }
        });
    }

    private VBox buildPreviewCard() {
        Label previewTitle = new Label("Student Preview");
        previewTitle.getStyleClass().add("section-title");
        Label previewHint = new Label("Fee status for the selected learner. Proceed to receipting to collect payment.");
        previewHint.getStyleClass().add("muted");
        previewHint.setWrapText(true);

        placeholder.getStyleClass().add("muted");
        placeholder.setWrapText(true);
        placeholder.setAlignment(Pos.CENTER);

        styleValues();
        setupVoteheadTable();

        Button proceedBtn = new Button("Proceed to Receipting");
        proceedBtn.getStyleClass().add("success-button");
        proceedBtn.setMaxWidth(Double.MAX_VALUE);
        proceedBtn.setOnAction(e -> proceedToReceipting());

        Button cancelBtn = new Button("Cancel");
        cancelBtn.getStyleClass().add("secondary-button");
        cancelBtn.setMaxWidth(Double.MAX_VALUE);
        cancelBtn.setOnAction(e -> cancelPreview());

        HBox actions = new HBox(10, proceedBtn, cancelBtn);
        HBox.setHgrow(proceedBtn, Priority.ALWAYS);
        HBox.setHgrow(cancelBtn, Priority.ALWAYS);

        previewContent.getChildren().addAll(previewTitle, previewHint, new Separator(),
                detailsGrid(), new Separator(), feeSummaryGrid(), voteheadTable, actions);
        previewContent.setSpacing(10);

        previewStack.getChildren().setAll(placeholder, previewContent);
        previewStack.setAlignment(Pos.TOP_LEFT);

        VBox card = new VBox(10, previewStack);
        card.getStyleClass().add("card");
        card.setPrefWidth(430);
        card.setMinWidth(360);
        VBox.setVgrow(previewStack, Priority.ALWAYS);
        return card;
    }

    private void styleValues() {
        admValue.getStyleClass().add("pay-value");
        nameValue.getStyleClass().add("pay-value");
        classValue.getStyleClass().add("pay-value");
        streamValue.getStyleClass().add("pay-value");
        genderValue.getStyleClass().add("pay-value");
        boardingValue.getStyleClass().add("pay-value");
        phoneValue.getStyleClass().add("pay-value");
        parentValue.getStyleClass().add("pay-value");
        yearValue.getStyleClass().add("pay-value");
        academicYearValue.getStyleClass().add("pay-value");
        chargedValue.getStyleClass().add("pay-value");
        paidValue.getStyleClass().add("pay-value");
        balanceValue.getStyleClass().add("pay-value");
        arrearsValue.getStyleClass().add("pay-value");
        advanceValue.getStyleClass().add("pay-value");
    }

    private GridPane detailsGrid() {
        GridPane grid = new GridPane();
        grid.setHgap(16);
        grid.setVgap(6);
        addDetailRow(grid, 0, "Adm No", admValue);
        addDetailRow(grid, 1, "Name", nameValue);
        addDetailRow(grid, 2, "Form", classValue);
        addDetailRow(grid, 3, "Stream", streamValue);
        addDetailRow(grid, 4, "Gender", genderValue);
        addDetailRow(grid, 5, "Boarding", boardingValue);
        addDetailRow(grid, 6, "Phone", phoneValue);
        addDetailRow(grid, 7, "Parent / Guardian", parentValue);
        addDetailRow(grid, 8, "Year of Admission", yearValue);
        addDetailRow(grid, 9, "Academic Year", academicYearValue);
        return grid;
    }

    private void addDetailRow(GridPane grid, int row, String label, Label value) {
        Label l = new Label(label);
        l.getStyleClass().add("pay-label");
        grid.add(l, 0, row);
        grid.add(value, 1, row);
    }

    private GridPane feeSummaryGrid() {
        GridPane grid = new GridPane();
        grid.setHgap(16);
        grid.setVgap(6);
        addFeeRow(grid, 0, "Should Pay (Charged)", chargedValue);
        addFeeRow(grid, 1, "Amount Paid", paidValue);
        addFeeRow(grid, 2, "Balance Due", balanceValue);
        addFeeRow(grid, 3, "Arrears", arrearsValue);
        addFeeRow(grid, 4, "Advance / Credit", advanceValue);
        return grid;
    }

    private void addFeeRow(GridPane grid, int row, String label, Label value) {
        Label l = new Label(label);
        l.getStyleClass().add("pay-label");
        grid.add(l, 0, row);
        grid.add(value, 1, row);
    }

    private void setupVoteheadTable() {
        TableColumn<VoteheadRow, String> code = new TableColumn<>("Vote Head");
        code.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().name()));
        code.setPrefWidth(170);

        TableColumn<VoteheadRow, String> charged = new TableColumn<>("Charged");
        charged.setCellValueFactory(c -> new SimpleStringProperty(CurrencyUtil.format(c.getValue().charged())));
        charged.setPrefWidth(90);

        TableColumn<VoteheadRow, String> paid = new TableColumn<>("Paid");
        paid.setCellValueFactory(c -> new SimpleStringProperty(CurrencyUtil.format(c.getValue().paid())));
        paid.setPrefWidth(90);

        TableColumn<VoteheadRow, String> outstanding = new TableColumn<>("Outstanding");
        outstanding.setCellValueFactory(c -> new SimpleStringProperty(CurrencyUtil.format(c.getValue().outstanding())));
        outstanding.setPrefWidth(100);

        voteheadTable.getColumns().addAll(code, charged, paid, outstanding);
        voteheadTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        voteheadTable.setPrefHeight(170);
    }

    private void showPreview(Student s) {
        selected = s;
        if (s == null) {
            showPlaceholder();
            return;
        }
        StudentFeeLedger ledger = studentStore.getLedger(s.getId());
        admValue.setText(nullToEmpty(s.getAdmissionNumber()));
        nameValue.setText(nullToEmpty(s.getName()));
        classValue.setText(nullToEmpty(s.getFormClass()));
        streamValue.setText(nullToEmpty(s.getStream()));
        genderValue.setText(nullToEmpty(s.getGender()));
        boardingValue.setText(s.getBoardingStatus() != null ? s.getBoardingStatus().getDisplayName() : "");
        phoneValue.setText(nullToEmpty(s.getPhone()));
        parentValue.setText(nullToEmpty(s.getParentName()));
        yearValue.setText(s.getYearOfAdmission() != null ? String.valueOf(s.getYearOfAdmission()) : "");
        academicYearValue.setText(s.getAcademicYear() != null ? String.valueOf(s.getAcademicYear()) : "");

        chargedValue.setText(CurrencyUtil.format(ledger.getTotalCharged()));
        paidValue.setText(CurrencyUtil.format(ledger.getTotalPaid()));
        balanceValue.setText(CurrencyUtil.format(ledger.getBalance()));
        arrearsValue.setText(CurrencyUtil.format(ledger.getArrears()));
        advanceValue.setText(CurrencyUtil.format(ledger.getAdvance()));

        voteheadTable.getItems().setAll(voteheadRows(ledger));

        placeholder.setVisible(false);
        placeholder.setManaged(false);
        previewContent.setVisible(true);
        previewContent.setManaged(true);
        table.refresh();
    }

    private void showPlaceholder() {
        placeholder.setText("Select a student to preview their details and fee status.");
        placeholder.setVisible(true);
        placeholder.setManaged(true);
        previewContent.setVisible(false);
        previewContent.setManaged(false);
    }

    private void cancelPreview() {
        table.getSelectionModel().clearSelection();
        selected = null;
        showPlaceholder();
    }

    private void proceedToReceipting() {
        if (selected == null) {
            return;
        }
        final Student s = selected;
        layout.show(Sidebar.RECEIPTS, node -> {
            if (node instanceof ReceiptView receiptView) {
                receiptView.preselect(s);
            }
        });
    }

    private List<VoteheadRow> voteheadRows(StudentFeeLedger ledger) {
        Set<String> codes = new LinkedHashSet<>();
        codes.addAll(ledger.getChargedByVotehead().keySet());
        codes.addAll(ledger.getPaidByVotehead().keySet());
        List<VoteheadRow> rows = new ArrayList<>();
        for (String code : codes) {
            BigDecimal c = ledger.getCharged(code);
            BigDecimal p = ledger.getPaid(code);
            if (c.compareTo(BigDecimal.ZERO) <= 0 && p.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            rows.add(new VoteheadRow(voteheadDisplayName(code), c, p, c.subtract(p).max(BigDecimal.ZERO)));
        }
        return rows;
    }

    private String voteheadDisplayName(String code) {
        if ("ARREARS".equals(code)) return "Arrears";
        if (StudentFeeLedger.ADVANCE_CODE.equals(code)) return "Advance";
        return FeeStructureStore.getInstance().findVoteheadByCode(code)
                .map(Votehead::getName)
                .orElse(code);
    }

    private void applyFilters() {
        String form = formBox.getValue();
        String stream = streamBox.getValue();
        String query = searchBar.getText();
        String formFilter = (form == null || ALL_FORMS.equals(form)) ? null : form;
        String streamFilter = (stream == null || ALL_STREAMS.equals(stream)) ? null : stream;
        filtered.setPredicate(s -> matchesFilters(s, formFilter, streamFilter, query));
    }

    public static boolean matchesFilters(Student s, String form, String stream, String query) {
        if (s == null) return false;
        if (form != null && !form.isBlank()
                && !form.trim().equalsIgnoreCase(s.getFormClass() == null ? "" : s.getFormClass().trim())) {
            return false;
        }
        if (stream != null && !stream.isBlank()
                && !stream.trim().equalsIgnoreCase(s.getStream() == null ? "" : s.getStream().trim())) {
            return false;
        }
        if (query != null && !query.isBlank() && !s.matchesSearch(query)) {
            return false;
        }
        return true;
    }

    private static String nullToEmpty(String v) {
        return v == null ? "" : v;
    }

    @Override
    public void refresh() {
        populateFilters();
        applyFilters();
        if (selected != null) {
            showPreview(selected);
        }
        table.refresh();
    }

    private record VoteheadRow(String name, BigDecimal charged, BigDecimal paid, BigDecimal outstanding) {
    }
}
