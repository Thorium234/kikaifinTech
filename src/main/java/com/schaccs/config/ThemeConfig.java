package com.schaccs.config;

/**
 * Shared colour tokens for the desktop UI.
 * Static constants are light-mode defaults for backward compatibility.
 * Use {@code get(token)} for dynamic light/dark resolution.
 */
public final class ThemeConfig {

    public static final String PRIMARY = "#1B4F72";
    public static final String PRIMARY_DARK = "#0E2F44";
    public static final String ACCENT = "#1ABC9C";
    public static final String SUCCESS = "#27AE60";
    public static final String WARNING = "#F39C12";
    public static final String DANGER = "#E74C3C";
    public static final String SURFACE = "#F4F6F7";
    public static final String CARD = "#FFFFFF";
    public static final String TEXT = "#1C2833";
    public static final String MUTED = "#5D6D7E";
    public static final String BORDER = "#D5D8DC";
    public static final String SIDEBAR = "#0E2F44";
    public static final String SIDEBAR_HOVER = "#1B4F72";
    public static final String SIDEBAR_ACTIVE = "#1ABC9C";

    private ThemeConfig() {
    }

    /**
     * Returns the current theme colour for the given token.
     * Resolves dynamically based on the active light/dark theme.
     */
    public static String get(String token) {
        return ThemeManager.getInstance().getColor(token);
    }
}
