package com.schaccs.ui.component;

import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.VBox;

public class DashboardCard extends VBox {

    private final Label valueLabel;
    private final Label hintLabel;

    public DashboardCard(String title, String initialValue, String accentColor) {
        getStyleClass().add("kpi-card");
        setSpacing(6);
        setAlignment(Pos.CENTER_LEFT);
        setMinWidth(200);
        setPrefWidth(220);
        setCursor(Cursor.HAND);

        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("kpi-label");

        valueLabel = new Label(initialValue);
        valueLabel.getStyleClass().add("kpi-value");
        if (accentColor != null) {
            valueLabel.setStyle("-fx-text-fill: " + accentColor + ";");
        }

        hintLabel = new Label();
        hintLabel.getStyleClass().add("muted");
        hintLabel.setStyle("-fx-font-size: 11px;");
        hintLabel.setManaged(false);
        hintLabel.setVisible(false);

        getChildren().addAll(titleLabel, valueLabel, hintLabel);
    }

    public void setValue(String value) {
        valueLabel.setText(value);
    }

    public void setHint(String hint) {
        boolean hasHint = hint != null && !hint.isBlank();
        hintLabel.setText(hasHint ? hint : "");
        hintLabel.setManaged(hasHint);
        hintLabel.setVisible(hasHint);
        setTooltip(hasHint ? new Tooltip(hint) : null);
    }

    public void setOnNavigate(Runnable action) {
        setOnMouseClicked(event -> {
            if (action != null) {
                action.run();
            }
        });
    }
}
