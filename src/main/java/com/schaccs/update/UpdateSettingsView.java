package com.schaccs.update;

import javafx.geometry.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;

public class UpdateSettingsView extends VBox {

    private final UpdateService updateService;

    private final CheckBox autoCheck;
    private final CheckBox autoDownload;
    private final ComboBox<String> channelCombo;
    private final Label statusLabel;

    public UpdateSettingsView(UpdateService updateService) {
        this.updateService = updateService;
        UpdateSettings settings = updateService.getSettings();

        setSpacing(10);
        getStyleClass().add("card");

        Label heading = new Label("Updates");
        heading.getStyleClass().add("section-title");

        Label sub = new Label("Check for new ThorCash releases and configure automatic update behaviour.");
        sub.getStyleClass().add("muted");
        sub.setWrapText(true);

        autoCheck = new CheckBox("Check for updates automatically");
        autoCheck.setSelected(settings.isAutoCheckEnabled());

        autoDownload = new CheckBox("Download updates automatically (when found)");
        autoDownload.setSelected(settings.isAutoDownloadEnabled());

        channelCombo = new ComboBox<>();
        channelCombo.getItems().addAll("stable", "prerelease");
        channelCombo.setValue(settings.getChannel());

        Label channelLabel = new Label("Update channel:");
        HBox channelRow = new HBox(8, channelLabel, channelCombo);
        channelRow.setAlignment(Pos.CENTER_LEFT);

        Button checkNow = new Button("Check Now");
        checkNow.getStyleClass().add("primary-button");
        statusLabel = new Label("");
        statusLabel.getStyleClass().add("muted");

        HBox actionRow = new HBox(10, checkNow, statusLabel);
        actionRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(statusLabel, Priority.ALWAYS);

        checkNow.setOnAction(e -> {
            saveSettings();
            checkNow.setDisable(true);
            statusLabel.setText("Checking...");
            statusLabel.setStyle("-fx-text-fill: #e65100;");
            updateService.checkForUpdates(true);
            statusLabel.setText("Check complete.");
            statusLabel.setStyle("-fx-text-fill: #1a472a;");
            checkNow.setDisable(false);
        });

        getChildren().addAll(heading, sub, autoCheck, autoDownload, channelRow, actionRow);

        autoCheck.selectedProperty().addListener((obs, old, val) -> saveSettings());
        autoDownload.selectedProperty().addListener((obs, old, val) -> saveSettings());
        channelCombo.valueProperty().addListener((obs, old, val) -> saveSettings());
    }

    private void saveSettings() {
        UpdateSettings settings = updateService.getSettings();
        settings.setAutoCheckEnabled(autoCheck.isSelected());
        settings.setAutoDownloadEnabled(autoDownload.isSelected());
        settings.setChannel(channelCombo.getValue());
    }
}
