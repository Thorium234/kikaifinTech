package com.schaccs.update;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import javafx.application.Platform;

public class UpdateService {

    private final GitHubApiClient apiClient;
    private final DownloadManager downloadManager;
    private final UpdateSettings settings;
    private final String currentVersion;

    public UpdateService(String currentVersion) {
        this.currentVersion = currentVersion;
        this.apiClient = new GitHubApiClient();
        this.downloadManager = new DownloadManager();
        this.settings = new UpdateSettings();
    }

    public UpdateSettings getSettings() {
        return settings;
    }

    public String getCurrentVersion() {
        return currentVersion;
    }

    public void checkForUpdates(boolean userInitiated) {
        if (!userInitiated && !settings.isAutoCheckEnabled()) {
            return;
        }

        apiClient.fetchLatestRelease().orTimeout(30, java.util.concurrent.TimeUnit.SECONDS).thenAccept(release -> {
            String tagVersion = release.tagName().replaceFirst("^v", "");
            String skipped = settings.getSkippedVersion();

            if (tagVersion.equals(skipped)) return;

            if (VersionComparator.isNewer(currentVersion, tagVersion)) {
                if (release.prerelease() && !"prerelease".equals(settings.getChannel())) {
                    return;
                }
                Platform.runLater(() -> showUpdateDialog(release));
            }
        }).exceptionally(ex -> {
            if (userInitiated) {
                Platform.runLater(() -> showError(ex.getMessage()));
            }
            return null;
        });
    }

    private void showUpdateDialog(GitHubRelease release) {
        UpdateDialog dialog = new UpdateDialog(release, this);
        dialog.showAndWait();
    }

    private void showError(String message) {
        UpdateDialog.showError(message);
    }

    public CompletableFuture<Path> downloadUpdate(
            GitHubRelease.Asset asset, UpdateDialog dialog) {
        return downloadManager.downloadInstaller(
            asset.browserDownloadUrl(), asset.size(), dialog::setProgress);
    }

    public void installUpdate(Path installerPath) {
        try {
            InstallerLauncher.launch(installerPath);
            Platform.exit();
        } catch (IOException e) {
            Platform.runLater(() -> showError(
                "Failed to launch installer: " + e.getMessage()));
        }
    }
}
