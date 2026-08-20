package com.schaccs.update;

import java.nio.file.Path;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

public class UpdateDialog extends Dialog<ButtonType> {

    private final ProgressBar progressBar;
    private final Label statusLabel;
    private final Button downloadButton;
    private final Button installButton;
    private final Button cancelButton;
    private final ButtonType cancelBtnType;

    private Path downloadedFile;

    public UpdateDialog(GitHubRelease release, UpdateService service, boolean autoDownload) {
        progressBar = new ProgressBar(0);
        progressBar.setPrefWidth(400);
        progressBar.setVisible(false);

        statusLabel = new Label("A new version is available!");
        statusLabel.getStyleClass().add("muted");

        Label versionLabel = new Label(release.tagName().replaceFirst("^v", ""));
        versionLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");

        Label nameLabel = new Label(release.name());
        nameLabel.setWrapText(true);

        Label bodyLabel = new Label(release.body());
        bodyLabel.setWrapText(true);
        bodyLabel.setMaxHeight(200);
        bodyLabel.setStyle("-fx-font-size: 12px;");

        ScrollPane bodyScroll = new ScrollPane(bodyLabel);
        bodyScroll.setFitToWidth(true);
        bodyScroll.setPrefHeight(160);

        VBox content = new VBox(10,
            versionLabel,
            nameLabel,
            statusLabel,
            bodyScroll,
            progressBar
        );
        content.setPadding(new Insets(8));

        downloadButton = new Button("Download");
        downloadButton.getStyleClass().add("primary-button");
        installButton = new Button("Install Now");
        installButton.getStyleClass().add("primary-button");
        installButton.setVisible(false);
        cancelBtnType = new ButtonType("Skip This Version", ButtonBar.ButtonData.OTHER);
        cancelButton = new Button("Remind Me Later");
        cancelButton.getStyleClass().add("secondary-button");

        Button skipButton = new Button("Skip This Version");
        skipButton.getStyleClass().add("secondary-button");
        skipButton.setOnAction(e -> {
            service.getSettings().setSkippedVersion(
                release.tagName().replaceFirst("^v", ""));
            close();
        });

        ButtonBar buttonBar = new ButtonBar();
        buttonBar.getButtons().addAll(skipButton, cancelButton, downloadButton, installButton);

        getDialogPane().setContent(content);
        getDialogPane().setPrefWidth(500);
        getDialogPane().getButtonTypes().add(cancelBtnType);
        getDialogPane().lookupButton(cancelBtnType).setVisible(false);

        setTitle("Update Available - ThorCash");
        setHeaderText(null);
        initModality(Modality.APPLICATION_MODAL);
        initStyle(StageStyle.UTILITY);

        downloadButton.setOnAction(e -> startDownload(release, service));
        installButton.setOnAction(e -> {
            if (downloadedFile != null) {
                service.installUpdate(downloadedFile);
            }
        });
        cancelButton.setOnAction(e -> close());

        setResultConverter(btn -> btn);

        if (autoDownload) {
            Platform.runLater(this::autoStartDownload);
        }
    }

    private void autoStartDownload() {
        downloadButton.fire();
    }

    private void startDownload(GitHubRelease release, UpdateService service) {
        downloadButton.setDisable(true);
        cancelButton.setDisable(true);
        progressBar.setVisible(true);
        statusLabel.setText("Downloading...");

        GitHubRelease.Asset asset = findInstallerAsset(release);
        if (asset == null) {
            statusLabel.setText("No installer asset found for this release.");
            return;
        }

        service.downloadUpdate(asset, this).thenAccept(file -> {
            Platform.runLater(() -> {
                this.downloadedFile = file;
                statusLabel.setText("Download complete!");
                progressBar.setProgress(1.0);
                downloadButton.setVisible(false);
                installButton.setVisible(true);
                cancelButton.setText("Close");
                cancelButton.setDisable(false);
            });
        }).exceptionally(ex -> {
            Platform.runLater(() -> {
                statusLabel.setText("Download failed: " + ex.getMessage());
                downloadButton.setDisable(false);
                cancelButton.setDisable(false);
                progressBar.setVisible(false);
            });
            return null;
        });
    }

    private GitHubRelease.Asset findInstallerAsset(GitHubRelease release) {
        return release.assets().stream()
            .filter(a -> a.name().endsWith(".exe"))
            .findFirst()
            .orElse(null);
    }

    public void setProgress(double value) {
        Platform.runLater(() -> progressBar.setProgress(value));
    }

    public static void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Update Check Failed");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
