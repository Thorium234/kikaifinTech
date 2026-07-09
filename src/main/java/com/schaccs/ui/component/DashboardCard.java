package com.schaccs.ui.component;

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

public class DashboardCard extends VBox {

    private final Label valueLabel;

    public DashboardCard(String title, String initialValue, String accentColor) {
        getStyleClass().add("kpi-card");
        setSpacing(6);
        setAlignment(Pos.CENTER_LEFT);
        setMinWidth(200);
        setPrefWidth(220);

        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("kpi-label");

        valueLabel = new Label(initialValue);
        valueLabel.getStyleClass().add("kpi-value");
        if (accentColor != null) {
            valueLabel.setStyle("-fx-text-fill: " + accentColor + ";");
        }

        getChildren().addAll(titleLabel, valueLabel);
    }

    public void setValue(String value) {
        valueLabel.setText(value);
    }
}
