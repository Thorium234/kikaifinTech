package com.schaccs.ui.layout;

import com.schaccs.config.AppConfig;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

public class TopBar extends HBox {

    private final Label titleLabel;
    private final Label userLabel;

    public TopBar() {
        getStyleClass().add("top-bar");
        setAlignment(Pos.CENTER_LEFT);
        setSpacing(12);

        titleLabel = new Label("Dashboard");
        titleLabel.getStyleClass().add("top-title");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        userLabel = new Label();
        userLabel.getStyleClass().add("muted");
        refreshUser();

        getChildren().addAll(titleLabel, spacer, userLabel);
    }

    public void setTitle(String title) {
        titleLabel.setText(title);
    }

    public void refreshUser() {
        AppConfig cfg = AppConfig.getInstance();
        userLabel.setText(cfg.getCurrentUser() + "  ·  AY " + cfg.getAcademicYear());
    }
}
