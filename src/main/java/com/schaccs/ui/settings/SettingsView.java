package com.schaccs.ui.settings;

import com.schaccs.config.AppConfig;
import com.schaccs.config.SchoolProfile;
import com.schaccs.repository.Database;
import com.schaccs.repository.PersistenceService;
import com.schaccs.ui.layout.MainLayout;
import com.schaccs.util.AlertUtil;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

public class SettingsView extends VBox implements MainLayout.Refreshable {

    private final TextField schoolName = new TextField();
    private final TextField location = new TextField();
    private final TextField principal = new TextField();
    private final TextField bankName = new TextField();
    private final TextField bankAccount = new TextField();
    private final TextField payBill = new TextField();
    private final TextField payBillAcc = new TextField();
    private final TextField academicYear = new TextField();
    private final TextField nextReceipt = new TextField();
    private final TextField currentUser = new TextField();
    private final TextArea cashPolicy = new TextArea();

    public SettingsView() {
        setSpacing(14);
        setPadding(new Insets(4));

        Label heading = new Label("Administration / Settings");
        heading.getStyleClass().add("section-title");

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(10);
        int r = 0;
        grid.add(new Label("School Name"), 0, r);
        grid.add(schoolName, 1, r++);
        grid.add(new Label("Location / Address"), 0, r);
        grid.add(location, 1, r++);
        grid.add(new Label("Principal"), 0, r);
        grid.add(principal, 1, r++);
        grid.add(new Label("Bank"), 0, r);
        grid.add(bankName, 1, r++);
        grid.add(new Label("Bank A/C"), 0, r);
        grid.add(bankAccount, 1, r++);
        grid.add(new Label("Pay Bill"), 0, r);
        grid.add(payBill, 1, r++);
        grid.add(new Label("Pay Bill Account"), 0, r);
        grid.add(payBillAcc, 1, r++);
        grid.add(new Label("Academic Year"), 0, r);
        grid.add(academicYear, 1, r++);
        grid.add(new Label("Next Receipt No"), 0, r);
        grid.add(nextReceipt, 1, r++);
        grid.add(new Label("Current User"), 0, r);
        grid.add(currentUser, 1, r++);
        grid.add(new Label("Cash Policy"), 0, r);
        cashPolicy.setPrefRowCount(3);
        cashPolicy.setWrapText(true);
        grid.add(cashPolicy, 1, r);

        schoolName.setPrefWidth(420);
        location.setPrefWidth(420);

        Button save = new Button("Save Settings");
        save.getStyleClass().add("primary-button");
        save.setOnAction(e -> save());

        VBox card = new VBox(14, grid, save);
        card.getStyleClass().add("card");

        getChildren().addAll(heading, card);
        load();
    }

    private void load() {
        SchoolProfile p = AppConfig.getInstance().getSchoolProfile();
        schoolName.setText(p.getSchoolName());
        location.setText(p.getLocation());
        principal.setText(p.getPrincipal());
        bankName.setText(p.getBankName());
        bankAccount.setText(p.getBankAccount());
        payBill.setText(p.getPayBill());
        payBillAcc.setText(p.getPayBillAccount());
        academicYear.setText(String.valueOf(p.getAcademicYear()));
        nextReceipt.setText(String.valueOf(p.getNextReceiptNumber()));
        currentUser.setText(AppConfig.getInstance().getCurrentUser());
        cashPolicy.setText(p.getCashPolicy());
    }

    private void save() {
        try {
            SchoolProfile p = AppConfig.getInstance().getSchoolProfile();
            p.setSchoolName(schoolName.getText().trim());
            p.setLocation(location.getText().trim());
            p.setPrincipal(principal.getText().trim());
            p.setBankName(bankName.getText().trim());
            p.setBankAccount(bankAccount.getText().trim());
            p.setPayBill(payBill.getText().trim());
            p.setPayBillAccount(payBillAcc.getText().trim());
            p.setAcademicYear(Integer.parseInt(academicYear.getText().trim()));
            p.setNextReceiptNumber(Long.parseLong(nextReceipt.getText().trim()));
            p.setCashPolicy(cashPolicy.getText().trim());
            AppConfig.getInstance().setCurrentUser(currentUser.getText().trim());
            PersistenceService.getInstance().saveAll();
            AlertUtil.info("Saved", "Settings updated and stored in "
                    + Database.getInstance().getDatabasePath());
        } catch (NumberFormatException ex) {
            AlertUtil.error("Invalid input", "Academic year and receipt number must be numeric.");
        }
    }

    @Override
    public void refresh() {
        load();
    }
}
