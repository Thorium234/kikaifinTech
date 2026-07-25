package com.schaccs.ui.fees;

import com.schaccs.enums.AccountType;
import com.schaccs.model.finance.Votehead;
import com.schaccs.repository.PersistenceService;
import com.schaccs.store.FeeStructureStore;
import com.schaccs.util.AlertUtil;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

public class VoteheadDialog extends Stage {

    private final FeeStructureStore store;
    private final TextField codeField = new TextField();
    private final TextField nameField = new TextField();
    private final ComboBox<AccountType> accountBox = new ComboBox<>();
    private final TextField priorityField = new TextField("100");

    public VoteheadDialog(FeeStructureStore store, Window owner) {
        this.store = store;
        setTitle("Add Vote Head");
        initOwner(owner);
        initModality(Modality.APPLICATION_MODAL);

        codeField.setPromptText("e.g. TUIT");
        nameField.setPromptText("e.g. Tuition Fees");
        accountBox.getItems().setAll(AccountType.values());
        accountBox.setValue(AccountType.SCHOOL_FUND);
        priorityField.setPromptText("100");

        GridPane form = new GridPane();
        form.setHgap(10);
        form.setVgap(8);
        form.add(new Label("Code:"), 0, 0);
        form.add(codeField, 1, 0);
        form.add(new Label("Name:"), 0, 1);
        form.add(nameField, 1, 1);
        form.add(new Label("Account Type:"), 0, 2);
        form.add(accountBox, 1, 2);
        form.add(new Label("Priority:"), 0, 3);
        form.add(priorityField, 1, 3);

        Button saveBtn = new Button("Save");
        saveBtn.getStyleClass().add("primary-button");
        saveBtn.setOnAction(e -> save());

        Button cancelBtn = new Button("Cancel");
        cancelBtn.getStyleClass().add("secondary-button");
        cancelBtn.setOnAction(e -> close());

        HBox actions = new HBox(10, saveBtn, cancelBtn);
        actions.setAlignment(Pos.CENTER_RIGHT);

        VBox root = new VBox(12, new Label("Enter vote head details:"), form, actions);
        root.setPadding(new Insets(16));
        root.setPrefSize(400, 260);
        setScene(new javafx.scene.Scene(root));
    }

    private void save() {
        String code = codeField.getText().trim();
        String name = nameField.getText().trim();
        if (code.isBlank()) {
            AlertUtil.warn("Missing", "Enter a vote head code.");
            return;
        }
        if (name.isBlank()) {
            AlertUtil.warn("Missing", "Enter a vote head name.");
            return;
        }
        if (store.findVoteheadByCode(code).isPresent()) {
            AlertUtil.warn("Duplicate", "A vote head with code '" + code + "' already exists.");
            return;
        }
        int priority;
        try {
            priority = Integer.parseInt(priorityField.getText().trim());
        } catch (NumberFormatException e) {
            priority = 100;
        }
        Votehead vh = new Votehead(code, name, accountBox.getValue(), priority);
        store.addVotehead(vh);
        PersistenceService.getInstance().saveAll();
        close();
    }
}
