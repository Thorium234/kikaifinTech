package com.schaccs.ui.calendar;

import com.schaccs.enums.AcademicTerm;
import com.schaccs.model.school.TermPeriod;
import com.schaccs.service.Services;
import com.schaccs.service.school.AcademicCalendarService;
import com.schaccs.ui.layout.MainLayout;
import com.schaccs.util.AlertUtil;
import com.schaccs.util.CurrencyUtil;
import com.schaccs.util.DateUtil;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.Spinner;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.util.List;

/**
 * Academic Calendar workspace: the school's term periods (Term | From | To),
 * fully customizable. Shows where the school is in the year and runs the
 * automatic end-of-term transition that rolls unpaid balances into arrears and
 * moves students to the next term/class.
 */
public class CalendarView extends VBox implements MainLayout.Refreshable {

    private final AcademicCalendarService service = Services.getInstance().academicCalendar();

    private final TableView<TermPeriod> table = new TableView<>();

    private final Label statusHeading = new Label();
    private final Label statusDetail = new Label();
    private final Label overdueDetail = new Label();
    private final Label lastRunDetail = new Label();

    public CalendarView() {
        setSpacing(14);
        setPadding(new Insets(4));

        Label heading = new Label("Academic Calendar");
        heading.getStyleClass().add("section-title");

        Label sub = new Label("Term | From | To dates the school runs on. When a term's end date passes, the "
                + "system knows the term has ended and can automatically carry each student's unpaid balance "
                + "into arrears, move them to the next term, and promote their class after Term 3.");
        sub.getStyleClass().add("muted");
        sub.setWrapText(true);

        getChildren().addAll(heading, sub, buildStatusCard(), buildTableCard());
    }

    private VBox buildStatusCard() {
        statusHeading.getStyleClass().add("section-title");
        statusDetail.getStyleClass().add("muted");
        statusDetail.setWrapText(true);
        overdueDetail.getStyleClass().add("pay-value");
        overdueDetail.setWrapText(true);
        lastRunDetail.getStyleClass().add("muted");
        lastRunDetail.setWrapText(true);

        Button rolloverBtn = new Button("Run End-of-Term Rollover");
        rolloverBtn.getStyleClass().add("primary-button");
        rolloverBtn.setMaxWidth(Double.MAX_VALUE);
        rolloverBtn.setOnAction(e -> runRollover());

        VBox card = new VBox(8, statusHeading, statusDetail, new Separator(),
                overdueDetail, lastRunDetail, rolloverBtn);
        card.getStyleClass().add("card");
        return card;
    }

    private VBox buildTableCard() {
        Button addBtn = new Button("Add Period");
        addBtn.getStyleClass().add("primary-button");
        addBtn.setOnAction(e -> showPeriodDialog(null));

        Button editBtn = new Button("Edit");
        editBtn.getStyleClass().add("secondary-button");
        editBtn.setOnAction(e -> {
            TermPeriod selected = table.getSelectionModel().getSelectedItem();
            if (selected == null) {
                AlertUtil.warn("No selection", "Select a period to edit.");
                return;
            }
            showPeriodDialog(selected);
        });

        Button deleteBtn = new Button("Delete");
        deleteBtn.getStyleClass().add("secondary-button");
        deleteBtn.setOnAction(e -> {
            TermPeriod selected = table.getSelectionModel().getSelectedItem();
            if (selected == null) {
                AlertUtil.warn("No selection", "Select a period to delete.");
                return;
            }
            if (AlertUtil.confirm("Delete", "Remove " + selected.getTerm().getDisplayName()
                    + " (" + DateUtil.format(selected.getFrom()) + " to " + DateUtil.format(selected.getTo()) + ")?")) {
                service.removePeriod(selected);
                refresh();
            }
        });

        Button yearBtn = new Button("Generate Year");
        yearBtn.getStyleClass().add("secondary-button");
        yearBtn.setOnAction(e -> showYearDialog());

        HBox toolbar = new HBox(10, addBtn, editBtn, deleteBtn, yearBtn);
        toolbar.setAlignment(Pos.CENTER_LEFT);

        setupTable();

        VBox card = new VBox(10, toolbar, table);
        card.getStyleClass().add("card");
        VBox.setVgrow(table, Priority.ALWAYS);
        return card;
    }

    private void setupTable() {
        TableColumn<TermPeriod, String> termCol = new TableColumn<>("Term");
        termCol.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getTerm() != null ? c.getValue().getTerm().getDisplayName() : ""));
        termCol.setPrefWidth(140);

        TableColumn<TermPeriod, String> fromCol = new TableColumn<>("From");
        fromCol.setCellValueFactory(c -> new SimpleStringProperty(DateUtil.format(c.getValue().getFrom())));
        fromCol.setPrefWidth(140);

        TableColumn<TermPeriod, String> toCol = new TableColumn<>("To");
        toCol.setCellValueFactory(c -> new SimpleStringProperty(DateUtil.format(c.getValue().getTo())));
        toCol.setPrefWidth(140);

        TableColumn<TermPeriod, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(c -> new SimpleStringProperty(statusOf(c.getValue())));
        statusCol.setPrefWidth(120);

        table.getColumns().addAll(termCol, fromCol, toCol, statusCol);
        table.setItems(service.getPeriods());
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(new Label("No periods configured. Add Term 1, Term 2 and Term 3 dates."));
        table.setRowFactory(tv -> {
            TableRow<TermPeriod> row = new TableRow<>();
            row.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !row.isEmpty()) {
                    showPeriodDialog(row.getItem());
                }
            });
            return row;
        });
    }

    private String statusOf(TermPeriod p) {
        return p.getStatus() != null ? p.getStatus().getDisplayName() : "";
    }

    private void showPeriodDialog(TermPeriod existing) {
        boolean isEdit = existing != null;
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(isEdit ? "Edit Term Period" : "Add Term Period");
        dialog.setHeaderText(isEdit ? "Update the term dates" : "Enter the term and its start/end dates");

        ComboBox<AcademicTerm> termBox = new ComboBox<>();
        termBox.getItems().addAll(AcademicTerm.values());
        termBox.setPrefWidth(220);

        DatePicker fromPicker = new DatePicker();
        DatePicker toPicker = new DatePicker();
        fromPicker.setPrefWidth(220);
        toPicker.setPrefWidth(220);

        if (isEdit) {
            termBox.setValue(existing.getTerm());
            fromPicker.setValue(existing.getFrom());
            toPicker.setValue(existing.getTo());
        }

        VBox content = new VBox(10,
                new Label("Term:"), termBox,
                new Label("From:"), fromPicker,
                new Label("To:"), toPicker);
        content.setPadding(new Insets(10));
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, ButtonType.OK);

        dialog.showAndWait().filter(ButtonType.OK::equals).ifPresent(btn -> {
            AcademicTerm term = termBox.getValue();
            LocalDate from = fromPicker.getValue();
            LocalDate to = toPicker.getValue();
            List<String> errors = isEdit
                    ? service.updatePeriod(existing, term, from, to)
                    : service.addPeriod(term, from, to);
            if (!errors.isEmpty()) {
                AlertUtil.warn("Validation", String.join("\n", errors));
                return;
            }
            refresh();
        });
    }

    private void showYearDialog() {
        Spinner<Integer> year = new Spinner<>(2000, 2100, LocalDate.now().getYear());
        year.setEditable(true);
        year.setPrefWidth(220);

        VBox content = new VBox(10,
                new Label("Academic year (e.g. 2020):"),
                year,
                new Label("Generates Term 1, Term 2 and Term 3 for the year if the calendar "
                        + "has none. Existing (customized) periods are never overwritten."));
        content.setPadding(new Insets(10));

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Generate Academic Year");
        dialog.setHeaderText("Scaffold the three standard terms for a year");
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, ButtonType.OK);
        dialog.showAndWait().filter(ButtonType.OK::equals).ifPresent(btn -> {
            Integer selected = year.getValue();
            if (selected == null) {
                return;
            }
            boolean created = service.ensureYearCalendar(selected);
            AlertUtil.info(created ? "Year generated" : "No changes",
                    created
                            ? "Term 1, Term 2 and Term 3 for " + selected
                                    + " were added as ended periods. Edit their dates if needed."
                            : "The calendar already has periods for " + selected + ".");
            refresh();
        });
    }

    private void runRollover() {
        LocalDate today = LocalDate.now();
        AcademicCalendarService.RolloverPreview preview = service.overduePreview(today);
        if (preview.studentsOverdue() == 0) {
            AlertUtil.info("Nothing to roll over",
                    "No student is past the end of their current term. The rollover runs automatically "
                            + "on startup once a term's end date passes.");
            return;
        }
        if (!AlertUtil.confirm("Run End-of-Term Rollover",
                "Move " + preview.studentsOverdue() + " student(s) to the next term? Unpaid balances totaling "
                        + CurrencyUtil.format(preview.totalUnpaid())
                        + " will be carried forward as arrears.")) {
            return;
        }
        AcademicCalendarService.RolloverResult result = service.rolloverIfDue(today);
        AlertUtil.info("Rollover complete",
                "Students moved to the next term: " + result.studentsRolled() + "\n"
                        + "Arrears carried forward: " + CurrencyUtil.format(result.arrearsRolled()) + "\n"
                        + "Classes promoted (after Term 3): " + result.classPromotions());
        refresh();
    }

    @Override
    public void refresh() {
        LocalDate today = LocalDate.now();
        service.reconcileStatuses(today);
        service.checkCompletions(today);
        var current = service.currentOrNextPeriod(today);
        if (current.isEmpty()) {
            statusHeading.setText("No term periods configured");
            statusDetail.setText("Add the school's Term | From | To dates to enable automatic end-of-term "
                    + "transition and arrears calculation.");
            overdueDetail.setText("");
            lastRunDetail.setText("");
        } else {
            TermPeriod p = current.get();
            boolean active = service.periodFor(today).map(x -> x.getId().equals(p.getId())).orElse(false);
            statusHeading.setText(active ? p.getTerm().getDisplayName() + " — In Session"
                    : p.getTerm().getDisplayName() + " — Next Upcoming");
            statusDetail.setText(DateUtil.format(p.getFrom()) + " to " + DateUtil.format(p.getTo())
                    + (active ? " (" + service.daysRemaining(today) + " days remaining)"
                    : " (starts in " + service.daysRemaining(today) + " days)"));
            service.nextTermStart(today)
                    .ifPresentOrElse(n -> statusDetail.setText(statusDetail.getText()
                            + " · Next term starts " + DateUtil.format(n)), () -> {});
        }

        AcademicCalendarService.RolloverPreview preview = service.overduePreview(today);
        overdueDetail.setText(preview.studentsOverdue() > 0
                ? preview.studentsOverdue() + " student(s) past their term end — "
                        + CurrencyUtil.format(preview.totalUnpaid())
                        + " of unpaid fees will roll into arrears."
                : "No student is past their term end. Arrears will roll automatically when a term ends.");
        lastRunDetail.setText(service.getLastRolloverDate() != null
                ? "Last rollover ran on " + DateUtil.format(service.getLastRolloverDate()) + "."
                : "");

        table.refresh();
    }
}
