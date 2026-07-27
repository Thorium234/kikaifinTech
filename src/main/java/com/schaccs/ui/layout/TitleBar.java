package com.schaccs.ui.layout;

import com.schaccs.repository.AppBootstrap;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.collections.ObservableList;
import javafx.scene.Cursor;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.stage.Screen;
import javafx.stage.Stage;

public class TitleBar extends HBox {

    private static final int BORDER_ZONE = 5;

    private final Stage stage;
    private boolean maximized = false;

    private double xOffset = 0;
    private double yOffset = 0;
    private double restoreX = 0;
    private double restoreY = 0;
    private double restoreWidth = 0;
    private double restoreHeight = 0;

    private double resizeAnchorX = 0;
    private double resizeAnchorY = 0;
    private Cursor activeResizeCursor = Cursor.DEFAULT;

    private final Button minButton;
    private final Button maxButton;
    private final Button closeButton;

    public TitleBar(Stage stage) {
        this.stage = stage;
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

        minButton.setOnAction(e -> {
            stage.setIconified(true);
        });

        maxButton.setOnAction(e -> toggleMaximize());

        closeButton.setOnAction(e -> {
            AppBootstrap.shutdown();
            Platform.exit();
        });

        getChildren().addAll(appLabel, minButton, maxButton, closeButton);

        setOnMousePressed(event -> {
            if (event.getTarget() instanceof Button) return;
            if (activeResizeCursor == Cursor.DEFAULT) {
                xOffset = event.getSceneX();
                yOffset = event.getSceneY();
            }
        });

        setOnMouseDragged(event -> {
            if (event.getTarget() instanceof Button) return;
            if (activeResizeCursor == Cursor.DEFAULT && !maximized) {
                stage.setX(event.getScreenX() - xOffset);
                stage.setY(event.getScreenY() - yOffset);
            }
        });

        setOnMouseClicked(event -> {
            if (event.getTarget() instanceof Button) return;
            if (event.getClickCount() == 2) {
                toggleMaximize();
            }
        });
    }

    public void attachResizeListeners(Scene scene) {
        scene.setOnMouseMoved(e -> handleMouseMoved(e, scene));
        scene.setOnMouseDragged(e -> handleMouseDragged(e));
        scene.setOnMousePressed(e -> handleMousePressed(e));
    }

    private void toggleMaximize() {
        if (maximized) {
            maximized = false;
            stage.setX(restoreX);
            stage.setY(restoreY);
            stage.setWidth(restoreWidth);
            stage.setHeight(restoreHeight);
            maxButton.setId("max-btn");
            maxButton.setText("\u25A1");
        } else {
            restoreX = stage.getX();
            restoreY = stage.getY();
            restoreWidth = stage.getWidth();
            restoreHeight = stage.getHeight();

            Rectangle2D stageBounds = new Rectangle2D(
                    restoreX != 0 ? restoreX : stage.getX(),
                    restoreY != 0 ? restoreY : stage.getY(),
                    restoreWidth > 0 ? restoreWidth : stage.getWidth(),
                    restoreHeight > 0 ? restoreHeight : stage.getHeight());
            Rectangle2D bounds = findScreenBounds(stageBounds);
            stage.setX(bounds.getMinX());
            stage.setY(bounds.getMinY());
            stage.setWidth(bounds.getWidth());
            stage.setHeight(bounds.getHeight());

            maximized = true;
            maxButton.setId("restore-btn");
            maxButton.setText("\u25A3");
        }
    }

    private void handleMouseMoved(MouseEvent event, Scene scene) {
        if (maximized) {
            scene.setCursor(Cursor.DEFAULT);
            activeResizeCursor = Cursor.DEFAULT;
            return;
        }

        double x = event.getX();
        double y = event.getY();
        double w = scene.getWidth();
        double h = scene.getHeight();

        boolean nearLeft = x < BORDER_ZONE;
        boolean nearRight = x > w - BORDER_ZONE;
        boolean nearTop = y < BORDER_ZONE;
        boolean nearBottom = y > h - BORDER_ZONE;

        if (nearLeft && nearTop) activeResizeCursor = Cursor.NW_RESIZE;
        else if (nearLeft && nearBottom) activeResizeCursor = Cursor.SW_RESIZE;
        else if (nearRight && nearTop) activeResizeCursor = Cursor.NE_RESIZE;
        else if (nearRight && nearBottom) activeResizeCursor = Cursor.SE_RESIZE;
        else if (nearLeft) activeResizeCursor = Cursor.W_RESIZE;
        else if (nearRight) activeResizeCursor = Cursor.E_RESIZE;
        else if (nearTop) activeResizeCursor = Cursor.N_RESIZE;
        else if (nearBottom) activeResizeCursor = Cursor.S_RESIZE;
        else activeResizeCursor = Cursor.DEFAULT;

        scene.setCursor(activeResizeCursor);
    }

    private void handleMousePressed(MouseEvent event) {
        resizeAnchorX = event.getScreenX();
        resizeAnchorY = event.getScreenY();
    }

    private void handleMouseDragged(MouseEvent event) {
        if (activeResizeCursor == Cursor.DEFAULT || maximized) return;

        double deltaX = event.getScreenX() - resizeAnchorX;
        double deltaY = event.getScreenY() - resizeAnchorY;

        double oldX = stage.getX();
        double oldY = stage.getY();
        double oldWidth = stage.getWidth();
        double oldHeight = stage.getHeight();

        double minW = stage.getMinWidth() > 0 ? stage.getMinWidth() : 150;
        double minH = stage.getMinHeight() > 0 ? stage.getMinHeight() : 100;

        if (activeResizeCursor == Cursor.E_RESIZE || activeResizeCursor == Cursor.NE_RESIZE || activeResizeCursor == Cursor.SE_RESIZE) {
            if (oldWidth + deltaX >= minW) stage.setWidth(oldWidth + deltaX);
        }
        if (activeResizeCursor == Cursor.W_RESIZE || activeResizeCursor == Cursor.NW_RESIZE || activeResizeCursor == Cursor.SW_RESIZE) {
            if (oldWidth - deltaX >= minW) {
                stage.setX(oldX + deltaX);
                stage.setWidth(oldWidth - deltaX);
            }
        }
        if (activeResizeCursor == Cursor.S_RESIZE || activeResizeCursor == Cursor.SW_RESIZE || activeResizeCursor == Cursor.SE_RESIZE) {
            if (oldHeight + deltaY >= minH) stage.setHeight(oldHeight + deltaY);
        }
        if (activeResizeCursor == Cursor.N_RESIZE || activeResizeCursor == Cursor.NW_RESIZE || activeResizeCursor == Cursor.NE_RESIZE) {
            if (oldHeight - deltaY >= minH) {
                stage.setY(oldY + deltaY);
                stage.setHeight(oldHeight - deltaY);
            }
        }

        resizeAnchorX = event.getScreenX();
        resizeAnchorY = event.getScreenY();
    }

    private Button createControlButton(String id, String symbol) {
        Button btn = new Button(symbol);
        btn.setId(id);
        btn.getStyleClass().add("window-control-btn");
        btn.setFocusTraversable(false);
        return btn;
    }

    private Rectangle2D findScreenBounds(Rectangle2D windowBounds) {
        ObservableList<Screen> screens = Screen.getScreensForRectangle(
                windowBounds.getMinX(), windowBounds.getMinY(),
                windowBounds.getWidth(), windowBounds.getHeight());
        Screen screen = screens.isEmpty() ? Screen.getPrimary() : screens.get(0);
        return screen.getVisualBounds();
    }
}
