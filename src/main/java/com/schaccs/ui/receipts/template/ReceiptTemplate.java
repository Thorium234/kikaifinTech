package com.schaccs.ui.receipts.template;

import com.schaccs.config.AppConfig;
import com.schaccs.config.SchoolProfile;
import com.schaccs.model.receipt.Receipt;
import com.schaccs.model.receipt.ReceiptLine;
import com.schaccs.store.StudentStore;
import com.schaccs.util.CurrencyUtil;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.IntStream;

/**
 * A reusable JavaFX receipt template component bound to the existing Receipt model.
 * - Pure Java (no FXML)
 * - Designed for A4 portrait printing
 * - Reads school configuration from AppConfig.getInstance().getSchoolProfile()
 * - Does not modify any business logic; purely presentation
 */
public class ReceiptTemplate extends BorderPane {

    private final VBox content = new VBox(8);
    private final SchoolProfile school = AppConfig.getInstance().getSchoolProfile();

    private Receipt receipt;

    public ReceiptTemplate() {
        getStyleClass().add("receipt-template-root");
        setPadding(new Insets(12));
        content.setPadding(new Insets(8));
        setCenter(content);
        setMaxWidth(Double.MAX_VALUE);
        buildEmpty();
    }

    private void buildEmpty() {
        content.getChildren().clear();
        VBox header = buildHeader(null);
        content.getChildren().addAll(header, new Separator());
        content.getChildren().add(new Label("No receipt selected"));
    }

    private VBox buildHeader(Receipt r) {
        HBox h = new HBox(12);
        h.setAlignment(Pos.CENTER_LEFT);
        // Logo
        ImageView logoView = new ImageView();
        logoView.getStyleClass().add("receipt-logo");
        try {
            String logo = school.getLogoPath();
            if (logo != null && !logo.isBlank()) {
                Path p = Path.of(logo);
                if (Files.exists(p)) {
                    try (InputStream in = new FileInputStream(p.toFile())) {
                        logoView.setImage(new Image(in));
                    }
                }
            }
        } catch (Exception ignored) {
        }
        logoView.setFitHeight(64);
        logoView.setPreserveRatio(true);

        VBox schoolBox = new VBox(2);
        Label ministry = new Label(safe(school.getMinistry()));
        ministry.getStyleClass().add("receipt-ministry");
        Label name = new Label(safe(school.getSchoolName()));
        name.getStyleClass().add("receipt-school-name");
        Label loc = new Label(safe(school.getLocation()));
        loc.getStyleClass().add("receipt-school-location");
        schoolBox.getChildren().addAll(ministry, name, loc);

        HBox right = new HBox();
        right.setAlignment(Pos.CENTER_RIGHT);
        Label title = new Label("SCHOOL OFFICIAL RECEIPT");
        title.getStyleClass().add("receipt-title");
        right.getChildren().add(title);
        HBox.setHgrow(right, Priority.ALWAYS);

        h.getChildren().addAll(logoView, schoolBox, right);

        VBox box = new VBox(4, h);
        box.getStyleClass().add("receipt-header");
        return box;
    }

    private GridPane buildInfoGrid(Receipt r) {
        GridPane g = new GridPane();
        g.getStyleClass().add("receipt-info-grid");
        g.setHgap(12);
        g.setVgap(6);
        g.add(new Label("Receipt No:"), 0, 0);
        g.add(labelOf(r != null ? r.getReceiptNumberDisplay() : ""), 1, 0);
        g.add(new Label("Date:"), 2, 0);
        g.add(labelOf(r != null ? com.schaccs.util.DateUtil.format(r.getDate()) : ""), 3, 0);

        g.add(new Label("Academic Year:"), 0, 1);
        g.add(labelOf(String.valueOf(AppConfig.getInstance().getAcademicYear())), 1, 1);
        g.add(new Label("Payment Mode:"), 2, 1);
        g.add(labelOf(r != null && r.getPaymentMode() != null ? r.getPaymentMode().getDisplayName() : ""), 3, 1);

        g.add(new Label("Student:"), 0, 2);
        g.add(labelOf(r != null ? r.getStudentName() : ""), 1, 2);
        g.add(new Label("Admission No:"), 2, 2);
        g.add(labelOf(r != null ? r.getAdmissionNumber() : ""), 3, 2);

        g.add(new Label("Class / Form:"), 0, 3);
        g.add(labelOf(r != null ? r.getClassLabel() : ""), 1, 3);
        g.add(new Label("Reference:"), 2, 3);
        g.add(labelOf(r != null ? r.getBankReference() : ""), 3, 3);

        return g;
    }

    private VBox buildVoteheadTable(Receipt r) {
        GridPane g = new GridPane();
        g.getStyleClass().add("receipt-lines-grid");
        g.setHgap(8);
        g.setVgap(6);
        g.add(labelOf("Description"), 0, 0);
        g.add(labelOf("Amount (KSh)"), 1, 0);
        g.getColumnConstraints().addAll();

        if (r != null) {
            int row = 1;
            for (ReceiptLine line : r.getLines()) {
                g.add(labelOf(safe(line.getVoteheadName())), 0, row);
                g.add(labelOf(CurrencyUtil.formatPlain(line.getAmount())), 1, row);
                row++;
            }
            // totals row
            g.add(new Separator(), 0, row, 2, 1);
            row++;
            g.add(labelOf("TOTAL PAID"), 0, row);
            g.add(labelOf(CurrencyUtil.formatPlain(r.getAmount())), 1, row);
        }

        VBox box = new VBox(6, g);
        return box;
    }

    private VBox buildSummary(Receipt r) {
        VBox card = new VBox(6);
        card.getStyleClass().add("receipt-summary");
        StudentStore ss = StudentStore.getInstance();
        BigDecimal previousBalance = fetchPreviousBalance(r);
        BigDecimal currentBalance = fetchCurrentBalance(r);
        BigDecimal credit = fetchCarryForwardCredit(r);

        GridPane g = new GridPane();
        g.setHgap(8);
        g.setVgap(6);
        g.add(labelOf("Previous Balance"), 0, 0);
        g.add(labelOf(CurrencyUtil.formatPlain(previousBalance)), 1, 0);
        g.add(labelOf("Current Payment"), 0, 1);
        g.add(labelOf(CurrencyUtil.formatPlain(r != null ? r.getAmount() : BigDecimal.ZERO)), 1, 1);
        g.add(labelOf("Current Balance"), 0, 2);
        g.add(labelOf(CurrencyUtil.formatPlain(currentBalance)), 1, 2);
        g.add(labelOf("Carry Forward Credit"), 0, 3);
        g.add(labelOf(CurrencyUtil.formatPlain(credit)), 1, 3);
        card.getChildren().add(g);
        return card;
    }

    // --- placeholder hooks that read from stores; kept small and safe ---
    private BigDecimal fetchPreviousBalance(Receipt r) {
        if (r == null) return BigDecimal.ZERO;
        try {
            return StudentStore.getInstance().getLedger(r.getStudentId()).getBalance();
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    private BigDecimal fetchCurrentBalance(Receipt r) {
        if (r == null) return BigDecimal.ZERO;
        try {
            return StudentStore.getInstance().getLedger(r.getStudentId()).getBalance();
        } catch (Exception e) {
            return fetchPreviousBalance(r).subtract(r.getAmount());
        }
    }

    private BigDecimal fetchCarryForwardCredit(Receipt r) {
        if (r == null) return BigDecimal.ZERO;
        try {
            return StudentStore.getInstance().getLedger(r.getStudentId()).getAdvance();
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    private Label labelOf(String s) {
        Label l = new Label(safe(s));
        l.getStyleClass().add("receipt-label");
        return l;
    }

    private Label labelOf(BigDecimal n) {
        return labelOf(CurrencyUtil.formatPlain(n));
    }

    private String safe(String v) {
        return v == null ? "" : v;
    }

    public void setReceipt(Receipt r) {
        this.receipt = r;
        render();
    }

    private void render() {
        content.getChildren().clear();
        VBox header = buildHeader(receipt);
        GridPane info = buildInfoGrid(receipt);
        VBox voteheads = buildVoteheadTable(receipt);
        VBox summary = buildSummary(receipt);
        VBox footer = buildFooter();
        content.getChildren().addAll(header, new Separator(), info, new Separator(), voteheads, new Separator(), summary, new Separator(), footer);
    }

    private VBox buildFooter() {
        VBox f = new VBox(4);
        Label p = new Label(safe(school.getCashPolicy()));
        p.getStyleClass().add("receipt-footer-policy");
        Label footerText = new Label(safe(school.getReceiptFooter()));
        footerText.getStyleClass().add("receipt-footer");
        f.getChildren().addAll(p, footerText);
        return f;
    }

    public Node getPrintableNode() {
        return this;
    }
}
