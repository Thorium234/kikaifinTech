package com.schaccs.update;

import java.util.prefs.Preferences;

public final class UpdateSettings {

    private static final String NODE = "/com/schaccs/update";
    private static final String KEY_AUTO_CHECK = "autoCheck";
    private static final String KEY_AUTO_DOWNLOAD = "autoDownload";
    private static final String KEY_CHANNEL = "channel";
    private static final String KEY_SKIPPED_VERSION = "skippedVersion";
    private static final String KEY_LAST_CHECK_TIME = "lastCheckTime";
    private static final String KEY_GITHUB_TOKEN = "githubToken";

    private final Preferences prefs;

    public UpdateSettings() {
        prefs = Preferences.userRoot().node(NODE);
    }

    public boolean isAutoCheckEnabled() {
        return prefs.getBoolean(KEY_AUTO_CHECK, true);
    }

    public void setAutoCheckEnabled(boolean enabled) {
        prefs.putBoolean(KEY_AUTO_CHECK, enabled);
    }

    public boolean isAutoDownloadEnabled() {
        return prefs.getBoolean(KEY_AUTO_DOWNLOAD, false);
    }

    public void setAutoDownloadEnabled(boolean enabled) {
        prefs.putBoolean(KEY_AUTO_DOWNLOAD, enabled);
    }

    public String getChannel() {
        return prefs.get(KEY_CHANNEL, "stable");
    }

    public void setChannel(String channel) {
        prefs.put(KEY_CHANNEL, channel);
    }

    public String getSkippedVersion() {
        return prefs.get(KEY_SKIPPED_VERSION, "");
    }

    public void setSkippedVersion(String version) {
        prefs.put(KEY_SKIPPED_VERSION, version);
    }

    public long getLastCheckTime() {
        return prefs.getLong(KEY_LAST_CHECK_TIME, 0);
    }

    public void setLastCheckTime(long millis) {
        prefs.putLong(KEY_LAST_CHECK_TIME, millis);
    }

    public String getGitHubToken() {
        return prefs.get(KEY_GITHUB_TOKEN, "");
    }

    public void setGitHubToken(String token) {
        prefs.put(KEY_GITHUB_TOKEN, token != null ? token : "");
    }
}
