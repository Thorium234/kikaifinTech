package com.schaccs.config;

import javafx.scene.Scene;
import javafx.scene.paint.Color;

import java.util.Map;
import java.util.prefs.Preferences;

import static java.util.Map.entry;

public final class ThemeManager {

    private static final ThemeManager INSTANCE = new ThemeManager();
    private static final String NODE = "/com/schaccs/theme";
    private static final String KEY_DARK = "darkMode";

    private final Preferences prefs;
    private boolean dark;

    private ThemeManager() {
        prefs = Preferences.userRoot().node(NODE);
        dark = prefs.getBoolean(KEY_DARK, false);
    }

    public static ThemeManager getInstance() {
        return INSTANCE;
    }

    public boolean isDark() {
        return dark;
    }

    public void setDark(boolean value) {
        this.dark = value;
        prefs.putBoolean(KEY_DARK, value);
    }

    public void toggle() {
        setDark(!dark);
    }

    public void applyTheme(Scene scene) {
        var root = scene.getRoot();
        if (dark) {
            if (!root.getStyleClass().contains("dark")) {
                root.getStyleClass().add("dark");
            }
        } else {
            root.getStyleClass().remove("dark");
        }
    }

    public void toggleTheme(Scene scene) {
        toggle();
        applyTheme(scene);
    }

    // ----- Light / Dark color maps for Java code that reads colors directly -----

    private static final Map<String, String> LIGHT = Map.ofEntries(
        entry("SURFACE", "#F4F6F7"),
        entry("CARD", "#FFFFFF"),
        entry("TEXT", "#1C2833"),
        entry("MUTED", "#5D6D7E"),
        entry("BORDER", "#D5D8DC"),
        entry("PRIMARY", "#1B4F72"),
        entry("PRIMARY_DARK", "#0E2F44"),
        entry("ACCENT", "#1ABC9C"),
        entry("SUCCESS", "#27AE60"),
        entry("WARNING", "#F39C12"),
        entry("DANGER", "#E74C3C"),
        entry("SIDEBAR", "#0E2F44"),
        entry("SIDEBAR_HOVER", "#1B4F72"),
        entry("SIDEBAR_ACTIVE", "#1ABC9C"),
        entry("TOP_BAR", "#FFFFFF"),
        entry("STATUS_BAR", "#FFFFFF"),
        entry("TABLE_HEADER", "#EBF5FB"),
        entry("TABLE_SELECTED", "#D4EFDF"),
        entry("SECONDARY_BG", "#EAECEE")
    );

    private static final Map<String, String> DARK = Map.ofEntries(
        entry("SURFACE", "#121212"),
        entry("CARD", "#1E1E1E"),
        entry("TEXT", "#E0E0E0"),
        entry("MUTED", "#9E9E9E"),
        entry("BORDER", "#333333"),
        entry("PRIMARY", "#5DADE2"),
        entry("PRIMARY_DARK", "#5DADE2"),
        entry("ACCENT", "#1ABC9C"),
        entry("SUCCESS", "#27AE60"),
        entry("WARNING", "#F39C12"),
        entry("DANGER", "#E74C3C"),
        entry("SIDEBAR", "#0A1929"),
        entry("SIDEBAR_HOVER", "#1565C0"),
        entry("SIDEBAR_ACTIVE", "#1ABC9C"),
        entry("TOP_BAR", "#1A1A1A"),
        entry("STATUS_BAR", "#1A1A1A"),
        entry("TABLE_HEADER", "#252525"),
        entry("TABLE_SELECTED", "#1B3A2A"),
        entry("SECONDARY_BG", "#333333")
    );

    public String getColor(String token) {
        return dark ? DARK.getOrDefault(token, LIGHT.getOrDefault(token, "#000000"))
                    : LIGHT.getOrDefault(token, "#000000");
    }

    public Color getJavaColor(String token) {
        return Color.web(getColor(token));
    }
}
