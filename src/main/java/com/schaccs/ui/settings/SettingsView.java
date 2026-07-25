package com.schaccs.ui.settings;

import com.schaccs.config.AppConfig;
import com.schaccs.config.CurrencyConfig;
import com.schaccs.config.SchoolProfile;
import com.schaccs.config.db.DatasourceManager;
import com.schaccs.repository.Database;
import com.schaccs.repository.PersistenceService;
import com.schaccs.service.setup.DemoDataSeeder;
import com.schaccs.service.setup.SystemResetService;
import com.schaccs.store.StudentStore;
import com.schaccs.ui.layout.MainLayout;
import com.schaccs.util.AlertUtil;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.beans.property.SimpleStringProperty;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

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
    private final javafx.scene.control.CheckBox siblingDiscount = new javafx.scene.control.CheckBox("Enable sibling discount");
    private final TextField siblingRate = new TextField();
    private final TextField logoPath = new TextField();
    private final TextField stampPath = new TextField();
    private final TextField signaturePath = new TextField();
    private final javafx.scene.control.CheckBox pdfStampToggle = new javafx.scene.control.CheckBox("Enable Digital Verification Stamp on PDF Documents");
    private final ImageView logoPreview = new ImageView();
    private final ImageView stampPreview = new ImageView();
    private final ImageView signaturePreview = new ImageView();
    private final ImageView receiptMockLogo = new ImageView();
    private final ImageView receiptMockStamp = new ImageView();
    private final ImageView receiptMockSignature = new ImageView();
    private final Label receiptMockSchool = new Label();
    private final Label receiptMockLocation = new Label();
    private final Label receiptMockPrincipal = new Label();
    private final Label logoWarning = new Label();
    private final Label stampWarning = new Label();
    private final Label signatureWarning = new Label();
    private final TableView<String[]> migrationTable = new TableView<>();
    private final TextField jdbcUrl = new TextField();
    private final TextField dbUser = new TextField();
    private final PasswordField dbPassword = new PasswordField();
    private String dbPasswordValue = "";
    private final Label dbStatusLabel = new Label("Not connected");

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
        grid.add(cashPolicy, 1, r++);
        grid.add(new Label("Sibling Discount"), 0, r);
        siblingRate.setPromptText("e.g. 0.10 = 10%");
        grid.add(new HBox(8, siblingDiscount, siblingRate), 1, r++);
        grid.add(new Label("PDF Stamp"), 0, r);
        grid.add(pdfStampToggle, 1, r++);
        grid.add(new Label("Receipt Logo"), 0, r);
        logoPath.setPromptText("Optional logo image path for receipt PDFs");
        Button browseLogo = new Button("Browse...");
        browseLogo.getStyleClass().add("secondary-button");
        browseLogo.setOnAction(e -> chooseImage(logoPath, logoPreview, receiptMockLogo, "Choose School Logo"));
        Button clearLogo = new Button("Clear");
        clearLogo.getStyleClass().add("secondary-button");
        clearLogo.setOnAction(e -> clearImage(logoPath, logoPreview, receiptMockLogo));
        grid.add(new VBox(6, new HBox(8, logoPath, browseLogo, clearLogo), buildPreviewBox("Logo Preview", logoPreview), logoWarning), 1, r++);
        grid.add(new Label("School Stamp"), 0, r);
        stampPath.setPromptText("Optional school stamp image path for receipt PDFs");
        Button browseStamp = new Button("Browse...");
        browseStamp.getStyleClass().add("secondary-button");
        browseStamp.setOnAction(e -> chooseImage(stampPath, stampPreview, receiptMockStamp, "Choose School Stamp"));
        Button clearStamp = new Button("Clear");
        clearStamp.getStyleClass().add("secondary-button");
        clearStamp.setOnAction(e -> clearImage(stampPath, stampPreview, receiptMockStamp));
        grid.add(new VBox(6, new HBox(8, stampPath, browseStamp, clearStamp), buildPreviewBox("Stamp Preview", stampPreview), stampWarning), 1, r++);
        grid.add(new Label("Signature Image"), 0, r);
        signaturePath.setPromptText("Optional signature image path for receipt PDFs");
        Button browseSignature = new Button("Browse...");
        browseSignature.getStyleClass().add("secondary-button");
        browseSignature.setOnAction(e -> chooseImage(signaturePath, signaturePreview, receiptMockSignature, "Choose Signature Image"));
        Button clearSignature = new Button("Clear");
        clearSignature.getStyleClass().add("secondary-button");
        clearSignature.setOnAction(e -> clearImage(signaturePath, signaturePreview, receiptMockSignature));
        grid.add(new VBox(6, new HBox(8, signaturePath, browseSignature, clearSignature), buildPreviewBox("Signature Preview", signaturePreview), signatureWarning), 1, r);

        schoolName.setPrefWidth(420);
        location.setPrefWidth(420);
        configureWarning(logoWarning);
        configureWarning(stampWarning);
        configureWarning(signatureWarning);
        configurePreview(logoPreview);
        configurePreview(stampPreview);
        configurePreview(signaturePreview);
        configureMockPreview(receiptMockLogo, 50, 90);
        configureMockPreview(receiptMockStamp, 36, 70);
        configureMockPreview(receiptMockSignature, 26, 90);

        Button save = new Button("Save Settings");
        save.getStyleClass().add("primary-button");
        save.setOnAction(e -> save());

        VBox card = new VBox(14, grid, buildReceiptBrandingMockup(), save);
        card.getStyleClass().add("card");

        setupMigrationTable();
        Button refreshHistory = new Button("Refresh Migration History");
        refreshHistory.getStyleClass().add("secondary-button");
        refreshHistory.setOnAction(e -> loadMigrationHistory());
        Button exportHistoryPdf = new Button("Export Migration History PDF");
        exportHistoryPdf.getStyleClass().add("secondary-button");
        exportHistoryPdf.setOnAction(e -> exportMigrationHistoryPdf());
        VBox historyCard = new VBox(10,
                new Label("Schema Migration History"),
                new Label("Debug/admin view of applied schema migrations."),
                new HBox(8, refreshHistory, exportHistoryPdf),
                migrationTable);
        historyCard.getStyleClass().add("card");
        VBox.setVgrow(migrationTable, Priority.ALWAYS);

        VBox dbCard = buildDatabaseConfigCard();
        VBox demoCard = buildDemoDataCard();
        VBox purgeCard = buildSystemPurgeCard();

        VBox allContent = new VBox(14, heading, card, historyCard, dbCard, demoCard, purgeCard);
        allContent.setPadding(new Insets(0, 0, 60, 0));
        ScrollPane mainScroll = new ScrollPane(allContent);
        mainScroll.setFitToWidth(true);
        mainScroll.setFitToHeight(false);
        mainScroll.getStyleClass().add("inline-scroll-pane");
        VBox.setVgrow(mainScroll, Priority.ALWAYS);

        setupLivePreviewListeners();
        getChildren().add(mainScroll);
        load();
    }

    private VBox buildDatabaseConfigCard() {
        Label dbHeading = new Label("Multi-Database Configuration");
        dbHeading.getStyleClass().add("section-title");
        Label dbSub = new Label("Paste your remote database JDBC URL for PostgreSQL, MySQL, or MariaDB centralised storage.");
        dbSub.getStyleClass().add("muted");

        jdbcUrl.setPromptText("jdbc:postgresql://host:5432/database?sslmode=require");
        dbUser.setPromptText("username");
        dbPassword.setPromptText("password");

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(10);
        int r = 0;
        grid.add(new Label("JDBC URL"), 0, r);
        grid.add(jdbcUrl, 1, r++);
        grid.add(new Label("Username"), 0, r);
        grid.add(dbUser, 1, r++);
        grid.add(new Label("Password"), 0, r);
        grid.add(dbPassword, 1, r++);

        jdbcUrl.setPrefWidth(500);
        dbUser.setPrefWidth(300);

        Button testBtn = new Button("Test Connection");
        testBtn.getStyleClass().add("secondary-button");
        testBtn.setOnAction(e -> testDbConnection());

        Button saveDbBtn = new Button("Save & Connect");
        saveDbBtn.getStyleClass().add("primary-button");
        saveDbBtn.setOnAction(e -> saveDbConfig());

        Button disconnectBtn = new Button("Disconnect");
        disconnectBtn.getStyleClass().add("danger-button");
        disconnectBtn.setOnAction(e -> {
            DatasourceManager.getInstance().disconnectRemote();
            dbStatusLabel.setText("Disconnected");
            dbStatusLabel.setStyle("-fx-text-fill: #b00020;");
        });

        dbStatusLabel.getStyleClass().add("muted");
        HBox statusBar = new HBox(10, testBtn, saveDbBtn, disconnectBtn, dbStatusLabel);
        statusBar.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(dbStatusLabel, Priority.ALWAYS);

        VBox card = new VBox(10, dbHeading, dbSub, grid, statusBar);
        card.getStyleClass().add("card");
        return card;
    }

    private void testDbConnection() {
        DatasourceManager.DbConfig config = readDbConfigFromForm();
        boolean ok = DatasourceManager.getInstance().connectRemote(config);
        if (ok) {
            dbStatusLabel.setText("Connection successful!");
            dbStatusLabel.setStyle("-fx-text-fill: #1a472a;");
        } else {
            dbStatusLabel.setText("Connection failed. Check settings and try again.");
            dbStatusLabel.setStyle("-fx-text-fill: #b00020;");
        }
    }

    private void saveDbConfig() {
        DatasourceManager.DbConfig config = readDbConfigFromForm();
        try {
            boolean ok = DatasourceManager.getInstance().connectRemote(config);
            Database.getInstance().saveDbConfig(config);
            if (ok) {
                dbStatusLabel.setText("Saved and connected successfully!");
                dbStatusLabel.setStyle("-fx-text-fill: #1a472a;");
            } else {
                dbStatusLabel.setText("Saved but connection failed. Will retry on startup.");
                dbStatusLabel.setStyle("-fx-text-fill: #e65100;");
            }
            PersistenceService.getInstance().saveAll();
        } catch (Exception ex) {
            dbStatusLabel.setText("Save failed: " + ex.getMessage());
            dbStatusLabel.setStyle("-fx-text-fill: #b00020;");
            AlertUtil.error("Database configuration failed", ex.getMessage());
        }
    }

    private DatasourceManager.DbConfig readDbConfigFromForm() {
        DatasourceManager.DbConfig config = new DatasourceManager.DbConfig();
        String url = jdbcUrl.getText().trim();
        config.setJdbcUrl(url);
        parseJdbcUrl(url, config);
        config.setUsername(dbUser.getText().trim());
        String enteredPassword = dbPassword.getText();
        config.setPassword((enteredPassword == null || enteredPassword.isBlank())
                ? dbPasswordValue
                : enteredPassword.trim());
        config.setActive(true);
        return config;
    }

    private void parseJdbcUrl(String url, DatasourceManager.DbConfig config) {
        if (url == null || url.isBlank()) return;
        String lower = url.toLowerCase();
        if (lower.startsWith("jdbc:postgresql://")) {
            config.setDbType("postgresql");
        } else if (lower.startsWith("jdbc:mysql://")) {
            config.setDbType("mysql");
        } else if (lower.startsWith("jdbc:mariadb://")) {
            config.setDbType("mariadb");
        } else {
            config.setDbType("postgresql");
            return;
        }
        String withoutPrefix = url.substring(url.indexOf("://") + 3);
        int paramIdx = withoutPrefix.indexOf('?');
        String hostPortDb = paramIdx >= 0 ? withoutPrefix.substring(0, paramIdx) : withoutPrefix;
        String params = paramIdx >= 0 ? withoutPrefix.substring(paramIdx + 1) : "";

        int slashIdx = hostPortDb.indexOf('/');
        String hostPort = slashIdx >= 0 ? hostPortDb.substring(0, slashIdx) : hostPortDb;
        String database = slashIdx >= 0 ? hostPortDb.substring(slashIdx + 1) : "";
        config.setDatabaseName(database);

        int colonIdx = hostPort.indexOf(':');
        if (colonIdx >= 0) {
            config.setHost(hostPort.substring(0, colonIdx));
            try { config.setPort(Integer.parseInt(hostPort.substring(colonIdx + 1))); }
            catch (NumberFormatException e) { setDefaultPort(config); }
        } else {
            config.setHost(hostPort);
            setDefaultPort(config);
        }

        if (!params.isBlank()) {
            String ssl = extractParam(params, "ssl");
            if (ssl == null) ssl = extractParam(params, "sslmode");
            if (ssl == null) ssl = extractParam(params, "useSSL");
            config.setSslMode(ssl != null ? ssl : "prefer");
            if (config.getUsername() == null || config.getUsername().isBlank()) {
                String u = extractParam(params, "user");
                if (u != null) config.setUsername(u);
            }
            if (config.getPassword() == null || config.getPassword().isBlank()) {
                String p = extractParam(params, "password");
                if (p != null) config.setPassword(p);
            }
        }
    }

    private void setDefaultPort(DatasourceManager.DbConfig config) {
        String t = config.getDbType();
        if ("mysql".equals(t)) config.setPort(3306);
        else if ("mariadb".equals(t)) config.setPort(3306);
        else config.setPort(5432);
    }

    private String extractParam(String query, String key) {
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0 && pair.substring(0, eq).equalsIgnoreCase(key)) {
                return pair.substring(eq + 1);
            }
        }
        return null;
    }

    private void loadDbConfig() {
        try {
            DatasourceManager.DbConfig config = Database.getInstance().loadDbConfig();
            if (config != null) {
                jdbcUrl.setText(config.getJdbcUrl() != null ? config.getJdbcUrl() : buildJdbcUrl(config));
                dbUser.setText(config.getUsername());
                dbPasswordValue = config.getPassword() == null ? "" : config.getPassword();
                dbPassword.clear();
                if (!dbPasswordValue.isBlank()) {
                    dbPassword.setPromptText("Password stored securely");
                } else {
                    dbPassword.setPromptText("password");
                }
                if (config.isActive() && DatasourceManager.getInstance().isOnline()) {
                    dbStatusLabel.setText("Connected");
                    dbStatusLabel.setStyle("-fx-text-fill: #1a472a;");
                }
            }
        } catch (Exception ex) {
            dbStatusLabel.setText("Failed to load DB config: " + ex.getMessage());
            dbStatusLabel.setStyle("-fx-text-fill: #b00020;");
        }
    }

    private String buildJdbcUrl(DatasourceManager.DbConfig config) {
        if (config.getDbType() == null || config.getHost() == null) return "";
        return switch (config.getDbType().toLowerCase()) {
            case "postgresql" -> "jdbc:postgresql://" + config.getHost() + ":" + config.getPort() + "/" + (config.getDatabaseName() != null ? config.getDatabaseName() : "");
            case "mysql" -> "jdbc:mysql://" + config.getHost() + ":" + config.getPort() + "/" + (config.getDatabaseName() != null ? config.getDatabaseName() : "");
            case "mariadb" -> "jdbc:mariadb://" + config.getHost() + ":" + config.getPort() + "/" + (config.getDatabaseName() != null ? config.getDatabaseName() : "");
            default -> "";
        };
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
        siblingDiscount.setSelected(p.isSiblingDiscountEnabled());
        siblingRate.setText(p.getSiblingDiscountRate().toPlainString());
        pdfStampToggle.setSelected(p.isPdfStampEnabled());
        logoPath.setText(p.getLogoPath() == null ? "" : p.getLogoPath());
        stampPath.setText(p.getStampPath() == null ? "" : p.getStampPath());
        signaturePath.setText(p.getSignaturePath() == null ? "" : p.getSignaturePath());
        refreshImagePreview(logoPath, logoPreview, receiptMockLogo, logoWarning, "logo");
        refreshImagePreview(stampPath, stampPreview, receiptMockStamp, stampWarning, "stamp");
        refreshImagePreview(signaturePath, signaturePreview, receiptMockSignature, signatureWarning, "signature");
        refreshReceiptBrandingMockup();
        loadMigrationHistory();
        loadDbConfig();
    }

    private void setupMigrationTable() {
        TableColumn<String[], String> versionCol = new TableColumn<>("Version");
        versionCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue()[0]));
        TableColumn<String[], String> nameCol = new TableColumn<>("Migration");
        nameCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue()[1]));
        TableColumn<String[], String> descCol = new TableColumn<>("Description");
        descCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue()[2]));
        TableColumn<String[], String> checksumCol = new TableColumn<>("Checksum");
        checksumCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue()[3]));
        TableColumn<String[], String> appliedCol = new TableColumn<>("Applied At");
        appliedCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue()[4]));
        @SuppressWarnings("unchecked")
        var columns1 = new TableColumn[]{versionCol, nameCol, descCol, checksumCol, appliedCol};
        migrationTable.getColumns().addAll(columns1);
        migrationTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        migrationTable.setPrefHeight(220);
    }

    private void loadMigrationHistory() {
        try {
            migrationTable.getItems().setAll(Database.getInstance().migrationHistory());
        } catch (Exception e) {
            migrationTable.getItems().clear();
        }
    }

    private void configureWarning(Label label) {
        label.getStyleClass().add("muted");
        label.setStyle("-fx-text-fill: #b00020;");
        label.setWrapText(true);
        label.setManaged(false);
        label.setVisible(false);
    }

    private void setupLivePreviewListeners() {
        schoolName.textProperty().addListener((obs, oldValue, value) -> refreshReceiptBrandingMockup());
        location.textProperty().addListener((obs, oldValue, value) -> refreshReceiptBrandingMockup());
        principal.textProperty().addListener((obs, oldValue, value) -> refreshReceiptBrandingMockup());
        logoPath.textProperty().addListener((obs, oldValue, value) -> refreshImagePreview(logoPath, logoPreview, receiptMockLogo, logoWarning, "logo"));
        stampPath.textProperty().addListener((obs, oldValue, value) -> refreshImagePreview(stampPath, stampPreview, receiptMockStamp, stampWarning, "stamp"));
        signaturePath.textProperty().addListener((obs, oldValue, value) -> refreshImagePreview(signaturePath, signaturePreview, receiptMockSignature, signatureWarning, "signature"));
    }

    private VBox buildPreviewBox(String title, ImageView imageView) {
        Label label = new Label(title);
        label.getStyleClass().add("muted");
        VBox box = new VBox(4, label, imageView);
        box.setPadding(new Insets(6));
        box.setStyle("-fx-border-color: -fx-box-border; -fx-border-radius: 6; -fx-background-radius: 6;");
        return box;
    }

    private VBox buildReceiptBrandingMockup() {
        Label title = new Label("Live Receipt Branding Preview");
        title.getStyleClass().add("section-title");

        receiptMockSchool.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #1a472a;");
        receiptMockLocation.getStyleClass().add("muted");
        receiptMockPrincipal.getStyleClass().add("muted");

        VBox header = new VBox(4, receiptMockLogo, receiptMockSchool, receiptMockLocation, receiptMockPrincipal);
        header.setAlignment(Pos.CENTER);

        Label banner = new Label("OFFICIAL FEE RECEIPT");
        banner.setMaxWidth(Double.MAX_VALUE);
        banner.setAlignment(Pos.CENTER);
        banner.setStyle("-fx-background-color: #1a472a; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 6 10 6 10;");

        Label tableHeader = new Label("Vote Head                     Amount (KSh)");
        tableHeader.setStyle("-fx-background-color: #e1efe5; -fx-text-fill: #1a472a; -fx-font-weight: bold; -fx-padding: 4 8 4 8; -fx-border-color: #b0b0b0;");
        Label tableRow = new Label("Boarding                              4,000.00");
        tableRow.setStyle("-fx-padding: 4 8 4 8; -fx-border-color: #d0d0d0;");
        Label total = new Label("TOTAL PAID: 4,000.00");
        total.setStyle("-fx-background-color: #f7e7ce; -fx-font-weight: bold; -fx-padding: 6 8 6 8; -fx-border-color: #b0b0b0;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox approvals = new HBox(8, receiptMockStamp, spacer, receiptMockSignature);
        approvals.setAlignment(Pos.CENTER_LEFT);

        VBox mock = new VBox(8, header, banner, tableHeader, tableRow, total, approvals);
        mock.setPadding(new Insets(12));
        mock.setStyle("-fx-background-color: white; -fx-border-color: #b8c9bb; -fx-border-radius: 6; -fx-background-radius: 6;");
        return new VBox(8, title, mock);
    }

    private void configurePreview(ImageView imageView) {
        imageView.setPreserveRatio(true);
        imageView.setFitHeight(80);
        imageView.setFitWidth(180);
        imageView.setSmooth(true);
    }

    private void configureMockPreview(ImageView imageView, double fitHeight, double fitWidth) {
        imageView.setPreserveRatio(true);
        imageView.setFitHeight(fitHeight);
        imageView.setFitWidth(fitWidth);
        imageView.setSmooth(true);
    }

    private void refreshImagePreview(TextField field, ImageView imageView, ImageView mockImageView,
                                     Label warningLabel, String assetName) {
        String value = field.getText();
        if (value == null || value.isBlank()) {
            imageView.setImage(null);
            mockImageView.setImage(null);
            setWarning(warningLabel, null);
            return;
        }
        Path path = Path.of(value);
        if (!Files.exists(path)) {
            imageView.setImage(null);
            mockImageView.setImage(null);
            setWarning(warningLabel, "The selected " + assetName + " file does not exist.");
            return;
        }
        try {
            Image image = new Image(path.toUri().toString(), true);
            imageView.setImage(image);
            mockImageView.setImage(image);
            setWarning(warningLabel, null);
        } catch (Exception ex) {
            imageView.setImage(null);
            mockImageView.setImage(null);
            setWarning(warningLabel, "Could not load the selected " + assetName + " image.");
        }
    }

    private void refreshReceiptBrandingMockup() {
        receiptMockSchool.setText(schoolName.getText() == null || schoolName.getText().isBlank()
                ? "School Name Preview" : schoolName.getText().trim());
        receiptMockLocation.setText(location.getText() == null || location.getText().isBlank()
                ? "Address / Location Preview" : location.getText().trim());
        receiptMockPrincipal.setText(principal.getText() == null || principal.getText().isBlank()
                ? "Principal Preview" : "Principal: " + principal.getText().trim());
    }

    private void chooseImage(TextField field, ImageView preview, ImageView mockPreview, String title) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(title);
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Image files", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp", "*.webp"),
                new FileChooser.ExtensionFilter("All files", "*.*")
        );
        File file = chooser.showOpenDialog(getScene() != null ? getScene().getWindow() : null);
        if (file != null) {
            field.setText(file.getAbsolutePath());
        }
    }

    private void setWarning(Label label, String message) {
        boolean visible = message != null && !message.isBlank();
        label.setText(visible ? message : "");
        label.setVisible(visible);
        label.setManaged(visible);
    }

    private void clearImage(TextField field, ImageView preview, ImageView mockPreview) {
        field.clear();
        preview.setImage(null);
        mockPreview.setImage(null);
    }

    private VBox buildDemoDataCard() {
        Label heading = new Label("Demo Data Seeding");
        heading.getStyleClass().add("section-title");
        Label sub = new Label("Seed a realistic 2-year dataset with 24 students, fee structures, payments, and deliberate arrears in Term 3 for testing.");
        sub.setWrapText(true);
        sub.getStyleClass().add("muted");

        Button seedBtn = new Button("Seed Demo Environment");
        seedBtn.getStyleClass().add("primary-button");
        Label statusLabel = new Label("");
        statusLabel.getStyleClass().add("muted");

        seedBtn.setOnAction(e -> {
            if (!AlertUtil.confirm("Seed Demo Environment",
                    "This will DELETE all existing data and create a fresh demo dataset.\n\n"
                            + "Current data will be permanently lost. Continue?")) {
                return;
            }
            seedBtn.setDisable(true);
            statusLabel.setText("Seeding in progress...");
            statusLabel.setStyle("-fx-text-fill: #e65100;");
            CompletableFuture.runAsync(() -> {
                try {
                    DemoDataSeeder.seed();
                    Platform.runLater(() -> {
                        seedBtn.setDisable(false);
                        statusLabel.setText("Demo data seeded successfully! " + StudentStore.getInstance().getStudents().size()
                                + " students loaded with fee structures and payment records.");
                        statusLabel.setStyle("-fx-text-fill: #1a472a;");
                        load();
                    });
                } catch (Exception ex) {
                    Platform.runLater(() -> {
                        seedBtn.setDisable(false);
                        statusLabel.setText("Seeding failed: " + ex.getMessage());
                        statusLabel.setStyle("-fx-text-fill: #b00020;");
                        AlertUtil.error("Seeding Failed", ex.getMessage());
                    });
                }
            });
        });

        VBox card = new VBox(10, heading, sub, seedBtn, statusLabel);
        card.getStyleClass().add("card");
        return card;
    }

    private VBox buildSystemPurgeCard() {
        Label heading = new Label("System Purge & Reset");
        heading.getStyleClass().add("section-title");
        Label sub = new Label("Completely wipe all dynamic data (students, fees, receipts, ledgers, vouchers, audit logs) and VACUUM the database. School settings, migration history, and DB config are preserved.");
        sub.setWrapText(true);
        sub.getStyleClass().add("muted");

        Button purgeBtn = new Button("Purge & Reset System");
        purgeBtn.getStyleClass().add("danger-button");
        Label statusLabel = new Label("");
        statusLabel.getStyleClass().add("muted");

        purgeBtn.setOnAction(e -> {
            if (!confirmPurge()) {
                return;
            }
            purgeBtn.setDisable(true);
            statusLabel.setText("Purging in progress...");
            statusLabel.setStyle("-fx-text-fill: #e65100;");
            CompletableFuture.runAsync(() -> {
                try {
                    SystemResetService.reset();
                    Platform.runLater(() -> {
                        purgeBtn.setDisable(false);
                        statusLabel.setText("System reset complete. All dynamic data cleared.");
                        statusLabel.setStyle("-fx-text-fill: #1a472a;");
                        load();
                    });
                } catch (Exception ex) {
                    Platform.runLater(() -> {
                        purgeBtn.setDisable(false);
                        statusLabel.setText("Reset failed: " + ex.getMessage());
                        statusLabel.setStyle("-fx-text-fill: #b00020;");
                        AlertUtil.error("Reset Failed", ex.getMessage());
                    });
                }
            });
        });

        VBox card = new VBox(10, heading, sub, purgeBtn, statusLabel);
        card.getStyleClass().add("card");
        return card;
    }

    private boolean confirmPurge() {
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("Confirm System Purge");
        dialog.setHeaderText("Type DELETE and click Confirm to purge all system data.\n"
                + "School settings will be preserved, but ALL other data will be lost.");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL);

        ButtonType confirmBtnType = new ButtonType("Confirm Purge");
        dialog.getDialogPane().getButtonTypes().add(confirmBtnType);

        PasswordField input = new PasswordField();
        input.setPromptText("Type DELETE here");
        dialog.getDialogPane().setContent(new VBox(8, new Label("Type DELETE to confirm:"), input));

        Node confirmBtn = dialog.getDialogPane().lookupButton(confirmBtnType);
        confirmBtn.setDisable(true);
        input.textProperty().addListener((obs, old, value) ->
                confirmBtn.setDisable(!"DELETE".equals(value.trim())));

        dialog.setResultConverter(btn -> {
            if (btn == confirmBtnType) {
                return input.getText();
            }
            return null;
        });

        return "DELETE".equals(dialog.showAndWait().orElse(null));
    }

    private void exportMigrationHistoryPdf() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export Migration History PDF");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF files", "*.pdf"));
        chooser.setInitialFileName("migration-history.pdf");
        File file = chooser.showSaveDialog(getScene() != null ? getScene().getWindow() : null);
        if (file == null) {
            return;
        }
        try {
            List<String> headers = List.of("Version", "Migration", "Description", "Checksum", "Applied At");
            List<List<String>> rows = migrationTable.getItems().stream()
                    .map(row -> Arrays.asList(row[0], row[1], row[2], row[3], row[4]))
                    .toList();
            new com.schaccs.service.export.PdfExportService().exportTable(file.toPath(), "Schema Migration History", headers, rows);
            AlertUtil.info("Export complete", "Migration history PDF exported to:\n" + file.getAbsolutePath());
        } catch (IOException e) {
            AlertUtil.error("Export failed", e.getMessage());
        }
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
            p.setSiblingDiscountEnabled(siblingDiscount.isSelected());
            p.setSiblingDiscountRate(CurrencyConfig.money(siblingRate.getText().trim().isEmpty()
                    ? "0.00" : siblingRate.getText().trim()));
            p.setPdfStampEnabled(pdfStampToggle.isSelected());
            p.setLogoPath(logoPath.getText().trim().isEmpty() ? null : logoPath.getText().trim());
            p.setStampPath(stampPath.getText().trim().isEmpty() ? null : stampPath.getText().trim());
            p.setSignaturePath(signaturePath.getText().trim().isEmpty() ? null : signaturePath.getText().trim());
            AppConfig.getInstance().setCurrentUser(currentUser.getText().trim());
            refreshReceiptBrandingMockup();
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
