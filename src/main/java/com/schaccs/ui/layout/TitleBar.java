package com.schaccs.ui.layout;

import com.schaccs.repository.AppBootstrap;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.stage.Stage;

public class TitleBar extends HBox {

    private double xOffset = 0;
    private double yOffset = 0;
    private double restoreX = 0;
    private double restoreY = 0;
    private double restoreWidth = 0;
    private double restoreHeight = 0;

    private final Button minButton;
    private final Button maxButton;
    private final Button closeButton;

    public TitleBar(Stage stage) {
        getStyleClass().add("title-bar");
        setAlignment(Pos.CENTER_RIGHT);
        setPrefHeight(32);
        setMinHeight(32);
        setMaxHeight(32);

        Region appLabel = new Region();
        HBox.setHgrow(appLabel, Priority.ALWAYS);

        minButton = createControlButton("min-btn", "\u2014");
        maxButton = createControlButton("max-btn", "\u25A1");
        closeButton = createControlButton("close-btn", "\u2715");

        minButton.setOnAction(e -> stage.setIconified(true));

        maxButton.setOnAction(e -> {
            if (stage.isMaximized()) {
                stage.setMaximized(false);
                stage.setX(restoreX);
                stage.setY(restoreY);
                stage.setWidth(restoreWidth);
                stage.setHeight(restoreHeight);
                maxButton.setId("max-btn");
            } else {
                restoreX = stage.getX();
                restoreY = stage.getY();
                restoreWidth = stage.getWidth();
                restoreHeight = stage.getHeight();
                stage.setMaximized(true);
                maxButton.setId("restore-btn");
            }
        });

        closeButton.setOnAction(e -> {
            AppBootstrap.shutdown();
            Platform.exit();
        });

        getChildren().addAll(appLabel, minButton, maxButton, closeButton);

        setOnMousePressed(event -> {
            if (event.getTarget() instanceof Button) return;
            xOffset = event.getSceneX();
            yOffset = event.getSceneY();
        });

        setOnMouseDragged(event -> {
            if (event.getTarget() instanceof Button) return;
            if (!stage.isMaximized()) {
                stage.setX(event.getScreenX() - xOffset);
                stage.setY(event.getScreenY() - yOffset);
            }
        });

        setOnMouseClicked(event -> {
            if (event.getTarget() instanceof Button) return;
            if (event.getClickCount() == 2) {
                maxButton.fire();
            }
        });
    }

    private Button createControlButton(String id, String symbol) {
        Button btn = new Button(symbol);
        btn.setId(id);
        btn.getStyleClass().add("window-control-btn");
        btn.setFocusTraversable(false);
        return btn;
    }
}
