package com.schaccs.ui.vouchers;

import com.schaccs.enums.PaymentMode;
import com.schaccs.model.finance.Votehead;
import com.schaccs.model.voucher.Commitment;
import com.schaccs.model.voucher.Creditor;
import com.schaccs.model.voucher.PaymentVoucher;
import com.schaccs.service.voucher.PaymentVoucherService;
import com.schaccs.store.FeeStructureStore;
import com.schaccs.store.VoucherStore;
import com.schaccs.ui.component.CurrencyField;
import com.schaccs.ui.layout.MainLayout;
import com.schaccs.util.AlertUtil;
import com.schaccs.util.CurrencyUtil;
import com.schaccs.util.DateUtil;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.util.List;

/**
 * Commitments + payment vouchers. Posts through PaymentVoucherService → AccountingEngine.
 */
public class VoucherView extends VBox implements MainLayout.Refreshable {

    private final PaymentVoucherService service = new PaymentVoucherService();
    private final VoucherStore store = VoucherStore.getInstance();

    private final TextField creditorName = new TextField();
    private final TextField creditorPhone = new TextField();
    private final ComboBox<Creditor> creditorBox = new ComboBox<>();
    private final ComboBox<Votehead> voteheadBox = new ComboBox<>();
    private final CurrencyField commitAmount = new CurrencyField();
    private final TextField commitDesc = new TextField();
    private final TextField commitRef = new TextField();
    private final DatePicker commitDate = new DatePicker(LocalDate.now());

    private final TableView<Commitment> commitmentTable = new TableView<>();
    private final TableView<PaymentVoucher> voucherTable = new TableView<>();
    private final CurrencyField payAmount = new CurrencyField();
    private final ComboBox<PaymentMode> payMode = new ComboBox<>();
    private final TextField payRef = new TextField();
    private final DatePicker payDate = new DatePicker(LocalDate.now());

    public VoucherView() {
        setSpacing(12);
        setPadding(new Insets(4));

        Label heading = new Label("Payment Vouchers & Commitments");
        heading.getStyleClass().add("section-title");

        Label note = new Label("Record supplier commitments, then pay via voucher. All payments post through AccountingEngine.");
        note.getStyleClass().add("muted");
        note.setWrapText(true);

        HBox top = new HBox(16, buildCreditorCard(), buildCommitmentForm());
        HBox.setHgrow(top.getChildren().get(1), Priority.ALWAYS);

        setupCommitmentTable();
        setupVoucherTable();

        VBox commitCard = new VBox(8, new Label("Open Commitments"), commitmentTable, buildPayBar());
        commitCard.getStyleClass().add("card");
        VBox.setVgrow(commitmentTable, Priority.ALWAYS);

        VBox voucherCard = new VBox(8, new Label("Paid Vouchers"), voucherTable);
        voucherCard.getStyleClass().add("card");
        VBox.setVgrow(voucherTable, Priority.ALWAYS);

        HBox lower = new HBox(16, commitCard, voucherCard);
        HBox.setHgrow(commitCard, Priority.ALWAYS);
        HBox.setHgrow(voucherCard, Priority.ALWAYS);
        VBox.setVgrow(lower, Priority.ALWAYS);

        getChildren().addAll(heading, note, top, lower);
        refresh();
    }

    private VBox buildCreditorCard() {
        Label t = new Label("Add Creditor");
        t.getStyleClass().add("section-title");
        creditorName.setPromptText("Supplier name");
        creditorPhone.setPromptText("Phone");
        Button add = new Button("Add Creditor");
        add.getStyleClass().add("primary-button");
        add.setOnAction(e -> {
            if (creditorName.getText() == null || creditorName.getText().isBlank()) {
                AlertUtil.warn("Validation", "Creditor name is required.");
                return;
            }
            service.addCreditor(creditorName.getText().trim(), creditorPhone.getText().trim(), null);
            creditorName.clear();
            creditorPhone.clear();
            refresh();
            AlertUtil.info("Saved", "Creditor added.");
        });
        VBox box = new VBox(8, t, creditorName, creditorPhone, add);
        box.getStyleClass().add("card");
        box.setPrefWidth(260);
        return box;
    }

    private VBox buildCommitmentForm() {
        Label t = new Label("New Commitment");
        t.getStyleClass().add("section-title");

        creditorBox.setItems(store.getCreditors());
        creditorBox.setPrefWidth(220);
        voteheadBox.setItems(FeeStructureStore.getInstance().getVoteheads());
        voteheadBox.setPrefWidth(220);
        commitDesc.setPromptText("Description / invoice");
        commitRef.setPromptText("LPO / Invoice ref");

        Button create = new Button("Create Commitment");
        create.getStyleClass().add("success-button");
        create.setOnAction(e -> {
            List<String> errors = service.createCommitment(
                    creditorBox.getValue(),
                    voteheadBox.getValue(),
                    commitAmount.getAmount(),
                    commitDesc.getText(),
                    commitRef.getText(),
                    commitDate.getValue());
            if (!errors.isEmpty()) {
                AlertUtil.warn("Validation", String.join("\n", errors));
                return;
            }
            commitAmount.clear();
            commitDesc.clear();
            commitRef.clear();
            refresh();
            AlertUtil.info("Saved", "Commitment recorded.");
        });

        GridPane g = new GridPane();
        g.setHgap(10);
        g.setVgap(8);
        g.add(new Label("Creditor"), 0, 0);
        g.add(creditorBox, 1, 0);
        g.add(new Label("Votehead"), 0, 1);
        g.add(voteheadBox, 1, 1);
        g.add(new Label("Amount"), 0, 2);
        g.add(commitAmount, 1, 2);
        g.add(new Label("Reference"), 0, 3);
        g.add(commitRef, 1, 3);
        g.add(new Label("Description"), 0, 4);
        g.add(commitDesc, 1, 4);
        g.add(new Label("Date"), 0, 5);
        g.add(commitDate, 1, 5);

        VBox box = new VBox(10, t, g, create);
        box.getStyleClass().add("card");
        return box;
    }

    private void setupCommitmentTable() {
        TableColumn<Commitment, String> date = new TableColumn<>("Date");
        date.setCellValueFactory(c -> new SimpleStringProperty(DateUtil.format(c.getValue().getDate())));
        TableColumn<Commitment, String> cred = new TableColumn<>("Creditor");
        cred.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getCreditorName()));
        TableColumn<Commitment, String> vh = new TableColumn<>("Votehead");
        vh.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getVoteheadName()));
        TableColumn<Commitment, String> amt = new TableColumn<>("Amount");
        amt.setCellValueFactory(c -> new SimpleStringProperty(CurrencyUtil.format(c.getValue().getAmount())));
        TableColumn<Commitment, String> out = new TableColumn<>("Outstanding");
        out.setCellValueFactory(c -> new SimpleStringProperty(CurrencyUtil.format(c.getValue().getOutstanding())));
        TableColumn<Commitment, String> st = new TableColumn<>("Status");
        st.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getStatus()));
        commitmentTable.getColumns().addAll(date, cred, vh, amt, out, st);
        commitmentTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        commitmentTable.setPrefHeight(220);
        commitmentTable.getSelectionModel().selectedItemProperty().addListener((obs, o, c) -> {
            if (c != null) {
                payAmount.setAmount(c.getOutstanding());
            }
        });
    }

    private HBox buildPayBar() {
        payMode.getItems().setAll(PaymentMode.values());
        payMode.setValue(PaymentMode.BANK_SLIP);
        payRef.setPromptText("Bank ref / cheque no");
        payAmount.setPrefWidth(120);

        Button pay = new Button("Pay Selected (Post Voucher)");
        pay.getStyleClass().add("success-button");
        pay.setOnAction(e -> {
            Commitment c = commitmentTable.getSelectionModel().getSelectedItem();
            List<String> errors = service.payVoucher(
                    c, payAmount.getAmount(), payMode.getValue(),
                    payRef.getText(), payDate.getValue(), null);
            if (!errors.isEmpty()) {
                AlertUtil.warn("Cannot pay", String.join("\n", errors));
                return;
            }
            payRef.clear();
            refresh();
            AlertUtil.info("Paid", "Payment voucher posted to the ledger.");
        });

        HBox bar = new HBox(10,
                new Label("Pay:"), payAmount,
                new Label("Mode:"), payMode,
                new Label("Ref:"), payRef,
                new Label("Date:"), payDate,
                pay);
        bar.setAlignment(Pos.CENTER_LEFT);
        return bar;
    }

    private void setupVoucherTable() {
        TableColumn<PaymentVoucher, String> num = new TableColumn<>("PV #");
        num.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getVoucherNumberDisplay()));
        TableColumn<PaymentVoucher, String> date = new TableColumn<>("Date");
        date.setCellValueFactory(c -> new SimpleStringProperty(DateUtil.format(c.getValue().getDate())));
        TableColumn<PaymentVoucher, String> cred = new TableColumn<>("Creditor");
        cred.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getCreditorName()));
        TableColumn<PaymentVoucher, String> vh = new TableColumn<>("Votehead");
        vh.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getVoteheadName()));
        TableColumn<PaymentVoucher, String> amt = new TableColumn<>("Amount");
        amt.setCellValueFactory(c -> new SimpleStringProperty(CurrencyUtil.format(c.getValue().getAmount())));
        TableColumn<PaymentVoucher, String> st = new TableColumn<>("Status");
        st.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getStatus() != null ? c.getValue().getStatus().getDisplayName() : ""));
        voucherTable.getColumns().addAll(num, date, cred, vh, amt, st);
        voucherTable.setItems(store.getVouchers());
        voucherTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        voucherTable.setPrefHeight(220);
    }

    @Override
    public void refresh() {
        creditorBox.setItems(store.getCreditors());
        voteheadBox.setItems(FeeStructureStore.getInstance().getVoteheads());
        commitmentTable.getItems().setAll(store.getCommitments());
        voucherTable.setItems(store.getVouchers());
        voucherTable.refresh();
    }
}
