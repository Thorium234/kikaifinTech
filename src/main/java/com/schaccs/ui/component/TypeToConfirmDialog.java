package com.schaccs.ui.component;

import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;

/**
 * Confirmation dialog for destructive actions. The confirm button stays disabled
 * until the user types the exact confirmation word (default "DELETE").
 */
public class TypeToConfirmDialog extends Dialog<Boolean> {

    private static final ButtonType CANCEL =
            new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);

    private final ButtonType confirmType;

    public TypeToConfirmDialog(String title, String message, String confirmWord, String confirmLabel) {
        setTitle(title);
        initModality(Modality.APPLICATION_MODAL);
        setHeaderText(message);

        String word = confirmWord == null || confirmWord.isBlank() ? "DELETE" : confirmWord;
        confirmType = new ButtonType(confirmLabel == null || confirmLabel.isBlank() ? "Delete" : confirmLabel,
                ButtonBar.ButtonData.OK_DONE);
        getDialogPane().getButtonTypes().addAll(CANCEL, confirmType);

        Label instruction = new Label("Type \"" + word + "\" below to confirm.");
        instruction.getStyleClass().add("muted");
        TextField field = new TextField();
        field.setPromptText(word);
        field.setMaxWidth(Double.MAX_VALUE);

        VBox content = new VBox(10, instruction, field);
        content.setPadding(new Insets(4));
        getDialogPane().setContent(content);

        Button confirmButton = (Button) getDialogPane().lookupButton(confirmType);
        confirmButton.getStyleClass().add("danger-button");
        confirmButton.disableProperty().bind(field.textProperty()
                .map(t -> !word.equals(t == null ? "" : t.trim())));

        setResultConverter(button -> button == confirmType ? Boolean.TRUE : Boolean.FALSE);
    }

    public ButtonType getConfirmButtonType() {
        return confirmType;
    }
}
