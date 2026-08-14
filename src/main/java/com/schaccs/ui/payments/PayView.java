package com.schaccs.ui.payments;

import com.schaccs.enums.PaymentMode;
import com.schaccs.model.finance.Votehead;
import com.schaccs.model.receipt.Receipt;
import com.schaccs.model.student.Student;
import com.schaccs.model.student.StudentFeeLedger;
import com.schaccs.service.receipt.ReceiptService;
import com.schaccs.service.student.PayPreviewService;
import com.schaccs.store.FeeStructureStore;
import com.schaccs.store.SchoolCustomStore;
import com.schaccs.store.StudentStore;
import com.schaccs.ui.component.CurrencyField;
import com.schaccs.ui.component.SearchBar;
import com.schaccs.ui.layout.MainLayout;
import com.schaccs.ui.layout.Sidebar;
import com.schaccs.ui.receipts.ReceiptView;
import com.schaccs.util.AlertUtil;
import com.schaccs.util.CurrencyUtil;
import com.schaccs.util.PrintUtil;
import com.schaccs.util.ReceiptPrinter;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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
    private final PayPreviewService payPreview = new PayPreviewService();
    private final ReceiptService receiptService = com.schaccs.service.Services.getInstance().receipt();
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

    private final Label expectedValue = new Label();
    private final Label chargedValue = new Label();
    private final Label paidValue = new Label();
    private final Label balanceValue = new Label();
    private final Label arrearsValue = new Label();
    private final Label advanceValue = new Label();
    private final Label feeHint = new Label();

    private final CurrencyField amountField = new CurrencyField();
    private final ComboBox<PaymentMode> modeBox = new ComboBox<>();
    private final TextField refField = new TextField();
    private final DatePicker datePicker = new DatePicker(LocalDate.now());
    private final Label paymentHint = new Label();

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
        searchBar.textProperty().addListener((obs, o, q) -> applyFilters());

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
        VBox.setVgrow(workspace, Priority.ALWAYS);

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
        String currentForm = formBox.getValue();
        String currentStream = streamBox.getValue();

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

        formBox.setValue(currentForm != null && formBox.getItems().contains(currentForm) ? currentForm : ALL_FORMS);
        streamBox.setValue(currentStream != null && streamBox.getItems().contains(currentStream) ? currentStream : ALL_STREAMS);

        formBox.setOnAction(e -> applyFilters());
        streamBox.setOnAction(e -> applyFilters());
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

        modeBox.getItems().setAll(PaymentMode.allowedModes());
        modeBox.setValue(PaymentMode.BANK_SLIP);
        refField.setPromptText("Bank slip / M-Pesa / cheque reference");
        amountField.setPrefWidth(150);
        modeBox.setPrefWidth(150);
        refField.setPrefWidth(150);
        datePicker.setPrefWidth(150);
        paymentHint.getStyleClass().add("muted");
        paymentHint.setWrapText(true);
        paymentHint.setText("Enter the amount received, then Receive & Print to post the official receipt.");

        Button receiveBtn = new Button("Receive & Print Receipt");
        receiveBtn.getStyleClass().add("success-button");
        receiveBtn.setMaxWidth(Double.MAX_VALUE);
        receiveBtn.setOnAction(e -> receiveAndPrint());

        Button proceedBtn = new Button("Proceed to Receipting");
        proceedBtn.getStyleClass().add("primary-button");
        proceedBtn.setMaxWidth(Double.MAX_VALUE);
        proceedBtn.setOnAction(e -> proceedToReceipting());

        Button cancelBtn = new Button("Cancel");
        cancelBtn.getStyleClass().add("secondary-button");
        cancelBtn.setMaxWidth(Double.MAX_VALUE);
        cancelBtn.setOnAction(e -> cancelPreview());

        previewContent.getChildren().addAll(previewTitle, previewHint, new Separator(),
                detailsGrid(), new Separator(), feeSummaryGrid(), feeHint,
                new Label("Collect Payment:"), paymentForm(), paymentHint, receiveBtn,
                new Separator(), voteheadTable, proceedBtn, cancelBtn);
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
        expectedValue.getStyleClass().add("pay-value");
        chargedValue.getStyleClass().add("pay-value");
        paidValue.getStyleClass().add("pay-value");
        balanceValue.getStyleClass().add("pay-value");
        arrearsValue.getStyleClass().add("pay-value");
        advanceValue.getStyleClass().add("pay-value");
        feeHint.getStyleClass().add("muted");
        feeHint.setWrapText(true);
        feeHint.setVisible(false);
        feeHint.setManaged(false);
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

    private void addDetailRow(GridPane grid, int row, String label, Node value) {
        Label l = new Label(label);
        l.getStyleClass().add("pay-label");
        grid.add(l, 0, row);
        grid.add(value, 1, row);
    }

    private GridPane feeSummaryGrid() {
        GridPane grid = new GridPane();
        grid.setHgap(16);
        grid.setVgap(6);
        addFeeRow(grid, 0, "Expected Term Fee (Structure)", expectedValue);
        addFeeRow(grid, 1, "Should Pay (Charged)", chargedValue);
        addFeeRow(grid, 2, "Amount Paid", paidValue);
        addFeeRow(grid, 3, "Balance Due", balanceValue);
        addFeeRow(grid, 4, "Arrears", arrearsValue);
        addFeeRow(grid, 5, "Advance / Credit", advanceValue);
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
        code.setPrefWidth(140);

        TableColumn<VoteheadRow, String> expected = new TableColumn<>("Expected");
        expected.setCellValueFactory(c -> new SimpleStringProperty(CurrencyUtil.format(c.getValue().expected())));
        expected.setPrefWidth(85);

        TableColumn<VoteheadRow, String> charged = new TableColumn<>("Charged");
        charged.setCellValueFactory(c -> new SimpleStringProperty(CurrencyUtil.format(c.getValue().charged())));
        charged.setPrefWidth(85);

        TableColumn<VoteheadRow, String> paid = new TableColumn<>("Paid");
        paid.setCellValueFactory(c -> new SimpleStringProperty(CurrencyUtil.format(c.getValue().paid())));
        paid.setPrefWidth(85);

        TableColumn<VoteheadRow, String> outstanding = new TableColumn<>("Outstanding");
        outstanding.setCellValueFactory(c -> new SimpleStringProperty(CurrencyUtil.format(c.getValue().outstanding())));
        outstanding.setPrefWidth(90);

        voteheadTable.getColumns().addAll(code, expected, charged, paid, outstanding);
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

        PayPreviewService.FeeStatus status = payPreview.feeStatus(s);
        expectedValue.setText(CurrencyUtil.format(status.expectedTerm()));
        chargedValue.setText(CurrencyUtil.format(status.charged()));
        paidValue.setText(CurrencyUtil.format(status.paid()));
        balanceValue.setText(CurrencyUtil.format(status.balance()));
        arrearsValue.setText(CurrencyUtil.format(status.arrears()));
        advanceValue.setText(CurrencyUtil.format(status.advance()));

        boolean hasStructure = payPreview.hasStructure(s);
        feeHint.setText("No fee structure configured for "
                + (s.getAcademicYear() != null ? s.getAcademicYear() : "this year")
                + " \u2014 " + (s.getBoardingStatus() != null ? s.getBoardingStatus().getDisplayName() : "?")
                + ". Expected fees are unavailable until a structure is set.");
        feeHint.setVisible(!hasStructure);
        feeHint.setManaged(!hasStructure);

        voteheadTable.getItems().setAll(voteheadRows(ledger, status.expectedByVotehead()));

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

    private GridPane paymentForm() {
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(6);
        addDetailRow(grid, 0, "Amount (KSh)", amountField);
        addDetailRow(grid, 1, "Payment Mode", modeBox);
        addDetailRow(grid, 2, "Reference", refField);
        addDetailRow(grid, 3, "Date", datePicker);
        return grid;
    }

    private void receiveAndPrint() {
        if (selected == null) {
            AlertUtil.warn("No student", "Select a student first.");
            return;
        }
        BigDecimal amount = amountField.getAmount();
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            AlertUtil.warn("Invalid amount", "Enter the amount received.");
            return;
        }
        PaymentMode mode = modeBox.getValue();
        if (mode == null) {
            AlertUtil.warn("Payment mode", "Select the payment mode.");
            return;
        }
        ReceiptService.Result result = receiptService.receivePayment(
                selected, amount, mode, refField.getText(), datePicker.getValue(), null);

        if (!result.isSuccess()) {
            AlertUtil.warn("Cannot receive payment", String.join("\n", result.getErrors()));
            return;
        }

        Receipt receipt = result.getReceipt();
        amountField.clear();
        refField.clear();
        datePicker.setValue(LocalDate.now());

        AlertUtil.info("Payment received",
                "Receipt No. " + receipt.getReceiptNumberDisplay() + " for "
                        + CurrencyUtil.format(receipt.getAmount()) + " posted successfully.");

        boolean printed = PrintUtil.printText("Official Fee Receipt — " + receipt.getReceiptNumberDisplay(),
                ReceiptPrinter.format(receipt),
                getScene() != null ? getScene().getWindow() : null);
        if (!printed) {
            AlertUtil.info("Not printed",
                    "The receipt was posted but printing was cancelled. Use Receipting or Reports to re-print it.");
        }

        showPreview(selected);
        applyFilters();
        table.refresh();
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

    private List<VoteheadRow> voteheadRows(StudentFeeLedger ledger, Map<String, BigDecimal> expected) {
        Set<String> codes = new LinkedHashSet<>();
        codes.addAll(expected.keySet());
        codes.addAll(ledger.getChargedByVotehead().keySet());
        codes.addAll(ledger.getPaidByVotehead().keySet());
        List<VoteheadRow> rows = new ArrayList<>();
        for (String code : codes) {
            BigDecimal e = expected.getOrDefault(code, BigDecimal.ZERO);
            BigDecimal c = ledger.getCharged(code);
            BigDecimal p = ledger.getPaid(code);
            if (e.compareTo(BigDecimal.ZERO) <= 0 && c.compareTo(BigDecimal.ZERO) <= 0
                    && p.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            rows.add(new VoteheadRow(voteheadDisplayName(code), e, c, p, c.subtract(p).max(BigDecimal.ZERO)));
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
        modeBox.getItems().setAll(PaymentMode.allowedModes());
        if (!modeBox.getItems().contains(modeBox.getValue())) {
            modeBox.setValue(modeBox.getItems().isEmpty() ? null : modeBox.getItems().get(0));
        }
        populateFilters();
        applyFilters();
        if (selected != null) {
            showPreview(selected);
        }
        table.refresh();
    }

    private record VoteheadRow(String name, BigDecimal expected, BigDecimal charged, BigDecimal paid,
                               BigDecimal outstanding) {
    }
}
