package com.schaccs.style;

import com.schaccs.config.ThemeConfig;

/**
 * Programmatic CSS applied to the scene root.
 */
public final class AppStyles {

    private AppStyles() {
    }

    public static String stylesheet() {
        return """
                .root {
                    -fx-font-family: "Segoe UI", "Ubuntu", "Noto Sans", sans-serif;
                    -fx-font-size: 13px;
                    -fx-background-color: %s;
                    -fx-text-fill: %s;
                }
                .sidebar {
                    -fx-background-color: %s;
                    -fx-padding: 0;
                    -fx-min-width: 220;
                    -fx-pref-width: 220;
                }
                .sidebar-title {
                    -fx-text-fill: white;
                    -fx-font-size: 16px;
                    -fx-font-weight: bold;
                    -fx-padding: 18 16 4 16;
                }
                .sidebar-sub {
                    -fx-text-fill: #AAB7B8;
                    -fx-font-size: 11px;
                    -fx-padding: 0 16 16 16;
                }
                .nav-button {
                    -fx-background-color: transparent;
                    -fx-text-fill: #D5D8DC;
                    -fx-alignment: CENTER_LEFT;
                    -fx-padding: 12 18;
                    -fx-cursor: hand;
                    -fx-font-size: 13px;
                    -fx-background-radius: 0;
                    -fx-border-width: 0;
                }
                .nav-button:hover {
                    -fx-background-color: %s;
                    -fx-text-fill: white;
                }
                .nav-button-active {
                    -fx-background-color: %s;
                    -fx-text-fill: white;
                    -fx-font-weight: bold;
                }
                .top-bar {
                    -fx-background-color: white;
                    -fx-border-color: %s;
                    -fx-border-width: 0 0 1 0;
                    -fx-padding: 12 20;
                }
                .top-title {
                    -fx-font-size: 18px;
                    -fx-font-weight: bold;
                    -fx-text-fill: %s;
                }
                .status-bar {
                    -fx-background-color: white;
                    -fx-border-color: %s;
                    -fx-border-width: 1 0 0 0;
                    -fx-padding: 6 16;
                }
                .content-area {
                    -fx-background-color: %s;
                    -fx-padding: 18;
                }
                .card {
                    -fx-background-color: white;
                    -fx-background-radius: 10;
                    -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.08), 10, 0, 0, 2);
                    -fx-padding: 16;
                }
                .kpi-card {
                    -fx-background-color: white;
                    -fx-background-radius: 10;
                    -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.08), 10, 0, 0, 2);
                    -fx-padding: 16;
                    -fx-min-width: 180;
                }
                .kpi-label {
                    -fx-text-fill: %s;
                    -fx-font-size: 12px;
                }
                .kpi-value {
                    -fx-font-size: 22px;
                    -fx-font-weight: bold;
                    -fx-text-fill: %s;
                }
                .section-title {
                    -fx-font-size: 16px;
                    -fx-font-weight: bold;
                    -fx-text-fill: %s;
                }
                .primary-button {
                    -fx-background-color: %s;
                    -fx-text-fill: white;
                    -fx-background-radius: 6;
                    -fx-padding: 8 16;
                    -fx-cursor: hand;
                    -fx-font-weight: bold;
                }
                .primary-button:hover {
                    -fx-background-color: %s;
                }
                .success-button {
                    -fx-background-color: %s;
                    -fx-text-fill: white;
                    -fx-background-radius: 6;
                    -fx-padding: 8 16;
                    -fx-cursor: hand;
                    -fx-font-weight: bold;
                }
                .secondary-button {
                    -fx-background-color: #EAECEE;
                    -fx-text-fill: %s;
                    -fx-background-radius: 6;
                    -fx-padding: 8 16;
                    -fx-cursor: hand;
                }
                .danger-button {
                    -fx-background-color: %s;
                    -fx-text-fill: white;
                    -fx-background-radius: 6;
                    -fx-padding: 8 16;
                    -fx-cursor: hand;
                }
                .text-field, .combo-box, .date-picker, .text-area {
                    -fx-background-radius: 6;
                    -fx-border-radius: 6;
                }
                .table-view {
                    -fx-background-color: white;
                    -fx-background-radius: 8;
                    -fx-border-color: %s;
                    -fx-border-radius: 8;
                }
                .table-view .column-header-background {
                    -fx-background-color: #EBF5FB;
                }
                .table-row-cell:selected {
                    -fx-background-color: #D4EFDF;
                }
                .muted {
                    -fx-text-fill: %s;
                }
                .policy-banner {
                    -fx-background-color: #FDEBD0;
                    -fx-text-fill: #7E5109;
                    -fx-padding: 8 12;
                    -fx-background-radius: 6;
                }
                """.formatted(
                ThemeConfig.SURFACE,
                ThemeConfig.TEXT,
                ThemeConfig.SIDEBAR,
                ThemeConfig.SIDEBAR_HOVER,
                ThemeConfig.SIDEBAR_ACTIVE,
                ThemeConfig.BORDER,
                ThemeConfig.PRIMARY_DARK,
                ThemeConfig.BORDER,
                ThemeConfig.SURFACE,
                ThemeConfig.MUTED,
                ThemeConfig.PRIMARY,
                ThemeConfig.PRIMARY_DARK,
                ThemeConfig.PRIMARY,
                ThemeConfig.PRIMARY_DARK,
                ThemeConfig.SUCCESS,
                ThemeConfig.TEXT,
                ThemeConfig.DANGER,
                ThemeConfig.BORDER,
                ThemeConfig.MUTED
        );
    }
}
