package com.schaccs.update;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import javafx.application.Platform;

public class UpdateService {

    private final GitHubApiClient apiClient;
    private final DownloadManager downloadManager;
    private final UpdateSettings settings;
    private final String currentVersion;

    public UpdateService(String currentVersion) {
        this.currentVersion = currentVersion;
        this.settings = new UpdateSettings();
        this.apiClient = new GitHubApiClient();
        this.downloadManager = new DownloadManager();
    }

    public UpdateSettings getSettings() {
        return settings;
    }

    public String getCurrentVersion() {
        return currentVersion;
    }

    private static final long RATE_LIMIT_MS = 24 * 60 * 60 * 1000; // 24 hours

    public void checkForUpdates(boolean userInitiated) {
        checkForUpdates(userInitiated, null);
    }

    public void checkForUpdates(boolean userInitiated, Consumer<String> resultCallback) {
        if (!userInitiated && !settings.isAutoCheckEnabled()) {
            return;
        }

        if (!userInitiated && currentVersion.equals(settings.getLastCheckedVersion())) {
            return;
        }

        long now = System.currentTimeMillis();
        long lastCheck = settings.getLastCheckTime();
        if (!userInitiated && (now - lastCheck) < RATE_LIMIT_MS) {
            return;
        }

        settings.setLastCheckTime(now);

        apiClient.fetchLatestRelease().orTimeout(30, java.util.concurrent.TimeUnit.SECONDS).thenAccept(release -> {
            String tagVersion = release.tagName().replaceFirst("^v", "");
            String skipped = settings.getSkippedVersion();

            if (tagVersion.equals(skipped)) {
                if (resultCallback != null) resultCallback.accept("up-to-date");
                return;
            }

            if (VersionComparator.isNewer(currentVersion, tagVersion)) {
                if (release.prerelease() && !"prerelease".equals(settings.getChannel())) {
                    settings.setLastCheckedVersion(currentVersion);
                    if (resultCallback != null) resultCallback.accept("up-to-date");
                    return;
                }
                if (resultCallback != null) resultCallback.accept("update-available");
                Platform.runLater(() -> showUpdateDialog(release));
            } else {
                settings.setLastCheckedVersion(currentVersion);
                if (resultCallback != null) resultCallback.accept("up-to-date");
            }
        }).exceptionally(ex -> {
            if (userInitiated) {
                String msg = ex.getMessage();
                if (resultCallback != null) {
                    Platform.runLater(() -> resultCallback.accept(msg));
                } else {
                    Platform.runLater(() -> showError(msg));
                }
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
