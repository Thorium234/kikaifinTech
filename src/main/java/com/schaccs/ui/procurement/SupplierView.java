package com.schaccs.ui.procurement;

import com.schaccs.model.procurement.Supplier;
import com.schaccs.service.Services;
import com.schaccs.service.procurement.SupplierService;
import com.schaccs.store.ProcurementStore;
import com.schaccs.ui.layout.MainLayout;
import com.schaccs.util.AlertUtil;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.List;

public class SupplierView extends VBox implements MainLayout.Refreshable {

    private final SupplierService supplierService = Services.getInstance().supplier();
    private final ProcurementStore store = ProcurementStore.getInstance();

    private final TextField searchField = new TextField();
    private final TextField businessNameField = new TextField();
    private final TextField contactPersonField = new TextField();
    private final TextField emailField = new TextField();
    private final TextField phoneField = new TextField();
    private final TextField kraPinField = new TextField();
    private final TextField regNumberField = new TextField();
    private final TextField addressField = new TextField();
    private final ComboBox<String> categoryBox = new ComboBox<>();
    private final TextArea notesArea = new TextArea();

    private final TableView<Supplier> table = new TableView<>();
    private final FilteredList<Supplier> filteredSuppliers = new FilteredList<>(store.getSuppliers(), p -> true);

    private Supplier selected;

    public SupplierView() {
        setSpacing(12);
        setPadding(new Insets(4));

        Label heading = new Label("Supplier Management");
        heading.getStyleClass().add("section-title");

        categoryBox.getItems().setAll("Goods", "Services", "Works", "Consultancy");
        categoryBox.setPrefWidth(200);
        notesArea.setPrefRowCount(2);
        notesArea.setWrapText(true);

        searchField.setPromptText("Search by name, number, or contact...");
        searchField.textProperty().addListener((obs, o, q) -> filterSuppliers(q));

        setupTable();

        Button addBtn = new Button("Add Supplier");
        addBtn.getStyleClass().add("btn-primary");
        addBtn.setOnAction(e -> addSupplier());

        Button updateBtn = new Button("Update");
        updateBtn.getStyleClass().add("btn-secondary");
        updateBtn.setOnAction(e -> updateSupplier());

        Button toggleBtn = new Button("Deactivate");
        toggleBtn.getStyleClass().add("btn-secondary");
        toggleBtn.setOnAction(e -> toggleActive());

        Button blacklistBtn = new Button("Blacklist");
        blacklistBtn.getStyleClass().add("btn-secondary");
        blacklistBtn.setOnAction(e -> toggleBlacklist());

        HBox buttons = new HBox(10, addBtn, updateBtn, toggleBtn, blacklistBtn);
        buttons.setAlignment(Pos.CENTER_LEFT);

        GridPane form = new GridPane();
        form.setHgap(10);
        form.setVgap(8);
        form.add(voucherLabel("Business Name"), 0, 0);
        form.add(voucherFieldBox(businessNameField, "Registered business name"), 1, 0);
        form.add(voucherLabel("Contact Person"), 0, 1);
        form.add(voucherFieldBox(contactPersonField, "Primary contact name"), 1, 1);
        form.add(voucherLabel("Email"), 0, 2);
        form.add(voucherFieldBox(emailField, "Business email address"), 1, 2);
        form.add(voucherLabel("Phone"), 0, 3);
        form.add(voucherFieldBox(phoneField, "Phone number"), 1, 3);
        form.add(voucherLabel("KRA PIN"), 0, 4);
        form.add(voucherFieldBox(kraPinField, "Tax identification number"), 1, 4);
        form.add(voucherLabel("Registration No."), 0, 5);
        form.add(voucherFieldBox(regNumberField, "Business registration number"), 1, 5);
        form.add(voucherLabel("Address"), 0, 6);
        form.add(voucherFieldBox(addressField, "Physical address"), 1, 6);
        form.add(voucherLabel("Category"), 0, 7);
        form.add(voucherFieldBox(categoryBox, "Supplier category"), 1, 7);
        form.add(voucherLabel("Notes"), 0, 8);
        form.add(voucherFieldBox(notesArea, "Additional notes"), 1, 8);
        businessNameField.setPrefWidth(280);

        Label formTitle = new Label("Supplier Details");
        formTitle.getStyleClass().add("card-title");
        VBox formCard = new VBox(10, formTitle, form, buttons);
        formCard.getStyleClass().add("card");
        formCard.setPadding(new Insets(12));

        Label tableTitle = new Label("Registered Suppliers");
        tableTitle.getStyleClass().add("card-title");
        VBox tableCard = new VBox(10, tableTitle, searchField, table);
        tableCard.getStyleClass().add("card");
        tableCard.setPadding(new Insets(12));
        VBox.setVgrow(table, Priority.ALWAYS);
        table.setPrefHeight(300);

        VBox content = new VBox(12, heading, formCard, tableCard);
        content.setPadding(new Insets(4));
        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(true);
        scrollPane.setPannable(true);
        scrollPane.getStyleClass().add("content-scroll");
        VBox.setVgrow(scrollPane, Priority.ALWAYS);
        getChildren().add(scrollPane);

        filterSuppliers("");
    }

    private void setupTable() {
        TableColumn<Supplier, String> numCol = new TableColumn<>("Supplier No");
        numCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getSupplierNumber()));
        numCol.setPrefWidth(110);

        TableColumn<Supplier, String> nameCol = new TableColumn<>("Business Name");
        nameCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getBusinessName()));
        nameCol.setPrefWidth(180);

        TableColumn<Supplier, String> contactCol = new TableColumn<>("Contact Person");
        contactCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getContactPerson()));
        contactCol.setPrefWidth(140);

        TableColumn<Supplier, String> phoneCol = new TableColumn<>("Phone");
        phoneCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getPhone()));
        phoneCol.setPrefWidth(120);

        TableColumn<Supplier, String> catCol = new TableColumn<>("Category");
        catCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getCategory()));
        catCol.setPrefWidth(100);

        TableColumn<Supplier, String> activeCol = new TableColumn<>("Active");
        activeCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().isActive() ? "Yes" : "No"));
        activeCol.setPrefWidth(60);

        TableColumn<Supplier, String> blCol = new TableColumn<>("Blacklisted");
        blCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().isBlacklisted() ? "Yes" : "No"));
        blCol.setPrefWidth(80);

        @SuppressWarnings("unchecked")
        var columns = new TableColumn[]{numCol, nameCol, contactCol, phoneCol, catCol, activeCol, blCol};
        table.getColumns().addAll(columns);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.getSelectionModel().selectedItemProperty().addListener((obs, o, s) -> selectSupplier(s));
    }

    private void filterSuppliers(String query) {
        String q = query == null ? "" : query.toLowerCase();
        filteredSuppliers.setPredicate(s -> q.isEmpty()
                || (s.getBusinessName() != null && s.getBusinessName().toLowerCase().contains(q))
                || (s.getSupplierNumber() != null && s.getSupplierNumber().toLowerCase().contains(q))
                || (s.getContactPerson() != null && s.getContactPerson().toLowerCase().contains(q))
                || (s.getPhone() != null && s.getPhone().contains(q)));
        table.setItems(filteredSuppliers);
    }

    private void selectSupplier(Supplier s) {
        selected = s;
        if (s == null) {
            clearForm();
            return;
        }
        businessNameField.setText(s.getBusinessName());
        contactPersonField.setText(s.getContactPerson());
        emailField.setText(s.getEmail());
        phoneField.setText(s.getPhone());
        kraPinField.setText(s.getKraPin());
        regNumberField.setText(s.getRegistrationNumber());
        addressField.setText(s.getAddress());
        categoryBox.setValue(s.getCategory());
        notesArea.setText(s.getNotes());
    }

    private void addSupplier() {
        if (!AlertUtil.confirm("Add Supplier", "Create new supplier?")) return;
        Supplier s = new Supplier();
        applyFormFields(s);
        List<String> errors = supplierService.addSupplier(s);
        if (!errors.isEmpty()) {
            AlertUtil.error("Validation Error", String.join("\n", errors));
            return;
        }
        AlertUtil.info("Success", "Supplier " + s.getSupplierNumber() + " created.");
        clearForm();
        filterSuppliers(searchField.getText());
    }

    private void updateSupplier() {
        if (selected == null) {
            AlertUtil.warn("No Selection", "Select a supplier to update.");
            return;
        }
        if (!AlertUtil.confirm("Update Supplier", "Update supplier details?")) return;
        Supplier s = Supplier.withId(selected.getId());
        s.setSupplierNumber(selected.getSupplierNumber());
        s.setActive(selected.isActive());
        s.setBlacklisted(selected.isBlacklisted());
        s.setBlacklistReason(selected.getBlacklistReason());
        applyFormFields(s);
        List<String> errors = supplierService.updateSupplier(s);
        if (!errors.isEmpty()) {
            AlertUtil.error("Validation Error", String.join("\n", errors));
            return;
        }
        AlertUtil.info("Success", "Supplier updated.");
        filterSuppliers(searchField.getText());
    }

    private void toggleActive() {
        if (selected == null) {
            AlertUtil.warn("No Selection", "Select a supplier first.");
            return;
        }
        if (selected.isActive()) {
            if (AlertUtil.confirm("Deactivate", "Deactivate supplier " + selected.getBusinessName() + "?")) {
                supplierService.deactivateSupplier(selected);
            }
        } else {
            if (AlertUtil.confirm("Activate", "Activate supplier " + selected.getBusinessName() + "?")) {
                supplierService.activateSupplier(selected);
            }
        }
        filterSuppliers(searchField.getText());
    }

    private void toggleBlacklist() {
        if (selected == null) {
            AlertUtil.warn("No Selection", "Select a supplier first.");
            return;
        }
        if (selected.isBlacklisted()) {
            if (AlertUtil.confirm("Remove Blacklist", "Remove blacklist from " + selected.getBusinessName() + "?")) {
                supplierService.removeBlacklist(selected);
            }
        } else {
            TextInputDialog dialog = new TextInputDialog();
            dialog.setTitle("Blacklist Supplier");
            dialog.setHeaderText("Provide blacklist reason for " + selected.getBusinessName());
            dialog.setContentText("Reason:");
            dialog.showAndWait().ifPresent(reason -> supplierService.blacklistSupplier(selected, reason));
        }
        filterSuppliers(searchField.getText());
    }

    private void applyFormFields(Supplier s) {
        s.setBusinessName(businessNameField.getText());
        s.setContactPerson(contactPersonField.getText());
        s.setEmail(emailField.getText());
        s.setPhone(phoneField.getText());
        s.setKraPin(kraPinField.getText());
        s.setRegistrationNumber(regNumberField.getText());
        s.setAddress(addressField.getText());
        s.setCategory(categoryBox.getValue());
        s.setNotes(notesArea.getText());
    }

    private void clearForm() {
        businessNameField.clear();
        contactPersonField.clear();
        emailField.clear();
        phoneField.clear();
        kraPinField.clear();
        regNumberField.clear();
        addressField.clear();
        categoryBox.setValue(null);
        notesArea.clear();
    }

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
        filterSuppliers(searchField.getText());
        table.refresh();
    }
}
