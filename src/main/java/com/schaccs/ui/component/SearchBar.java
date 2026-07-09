package com.schaccs.ui.component;

import javafx.beans.property.StringProperty;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;

public class SearchBar extends HBox {

    private final TextField field;

    public SearchBar(String prompt) {
        setSpacing(10);
        setAlignment(Pos.CENTER_LEFT);
        Label icon = new Label("⌕");
        icon.setStyle("-fx-font-size: 16px; -fx-text-fill: #5D6D7E;");
        field = new TextField();
        field.setPromptText(prompt);
        field.setPrefWidth(320);
        HBox.setHgrow(field, Priority.ALWAYS);
        getChildren().addAll(icon, field);
    }

    public TextField getField() {
        return field;
    }

    public StringProperty textProperty() {
        return field.textProperty();
    }

    public String getText() {
        return field.getText();
    }

    public void clear() {
        field.clear();
    }
}
