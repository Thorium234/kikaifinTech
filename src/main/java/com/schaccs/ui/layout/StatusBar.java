package com.schaccs.ui.layout;

import com.schaccs.config.AppConfig;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

public class StatusBar extends HBox {

    private final Label messageLabel;

    public StatusBar() {
        getStyleClass().add("status-bar");
        setAlignment(Pos.CENTER_LEFT);
        setSpacing(12);

        messageLabel = new Label("Ready");
        messageLabel.getStyleClass().add("muted");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label school = new Label(AppConfig.getInstance().getSchoolProfile().getSchoolName());
        school.getStyleClass().add("muted");

        getChildren().addAll(messageLabel, spacer, school);
    }

    public void setMessage(String message) {
        messageLabel.setText(message);
    }
}
