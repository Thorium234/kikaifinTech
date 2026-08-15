package com.schaccs.service.importer;

import com.schaccs.config.CurrencyConfig;
import com.schaccs.enums.BoardingStatus;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One staged student record read from a "FEES BALANCE" workbook sheet. Holds the
 * student details and the fee figures as they appeared on the sheet, plus the
 * review state (included, match status, warnings) used by the review dialog and
 * the import service.
 */
public class FeesBalanceRow {

    private final StringProperty sheetName = new SimpleStringProperty();
    private final StringProperty admissionNumber = new SimpleStringProperty();
    private final StringProperty name = new SimpleStringProperty();
    private final StringProperty formClass = new SimpleStringProperty();
    private final StringProperty stream = new SimpleStringProperty();
    private final ObjectProperty<BoardingStatus> boardingStatus = new SimpleObjectProperty<>();
    private final ObjectProperty<BigDecimal> currentFees = new SimpleObjectProperty<>(CurrencyConfig.zero());
    private final ObjectProperty<BigDecimal> arrears = new SimpleObjectProperty<>(CurrencyConfig.zero());
    private final ObjectProperty<BigDecimal> penalty = new SimpleObjectProperty<>(CurrencyConfig.zero());
    private final ObjectProperty<BigDecimal> totalFees = new SimpleObjectProperty<>(CurrencyConfig.zero());
    private final BooleanProperty include = new SimpleBooleanProperty(true);

    private int rowNumber;
    private String matchStatus = "New";
    private boolean hasBreakdown;
    private final List<String> warnings = new ArrayList<>();

    public FeesBalanceRow(String sheetName, int rowNumber) {
        this.sheetName.set(sheetName);
        this.rowNumber = rowNumber;
    }

    /** Total balance to import: the sheet's T/FEES plus any penalty column. */
    public BigDecimal getBalance() {
        return CurrencyConfig.money(totalFees.get().add(penalty.get()));
    }

    public String getSheetName() {
        return sheetName.get();
    }

    public StringProperty sheetNameProperty() {
        return sheetName;
    }

    public String getAdmissionNumber() {
        return admissionNumber.get();
    }

    public void setAdmissionNumber(String value) {
        admissionNumber.set(value == null ? "" : value.trim());
    }

    public StringProperty admissionNumberProperty() {
        return admissionNumber;
    }

    public String getName() {
        return name.get();
    }

    public void setName(String value) {
        name.set(value == null ? "" : value.trim());
    }

    public StringProperty nameProperty() {
        return name;
    }

    public String getFormClass() {
        return formClass.get();
    }

    public void setFormClass(String value) {
        formClass.set(value == null ? "" : value.trim());
    }

    public StringProperty formClassProperty() {
        return formClass;
    }

    public String getStream() {
        return stream.get();
    }

    public void setStream(String value) {
        stream.set(value == null ? "" : value.trim());
    }

    public StringProperty streamProperty() {
        return stream;
    }

    public BoardingStatus getBoardingStatus() {
        return boardingStatus.get();
    }

    public void setBoardingStatus(BoardingStatus value) {
        boardingStatus.set(value);
    }

    public ObjectProperty<BoardingStatus> boardingStatusProperty() {
        return boardingStatus;
    }

    public BigDecimal getCurrentFees() {
        return currentFees.get();
    }

    public void setCurrentFees(BigDecimal value) {
        currentFees.set(value == null ? CurrencyConfig.zero() : CurrencyConfig.money(value));
    }

    public ObjectProperty<BigDecimal> currentFeesProperty() {
        return currentFees;
    }

    public BigDecimal getArrears() {
        return arrears.get();
    }

    public void setArrears(BigDecimal value) {
        arrears.set(value == null ? CurrencyConfig.zero() : CurrencyConfig.money(value));
    }

    public ObjectProperty<BigDecimal> arrearsProperty() {
        return arrears;
    }

    public BigDecimal getPenalty() {
        return penalty.get();
    }

    public void setPenalty(BigDecimal value) {
        penalty.set(value == null ? CurrencyConfig.zero() : CurrencyConfig.money(value));
    }

    public ObjectProperty<BigDecimal> penaltyProperty() {
        return penalty;
    }

    public BigDecimal getTotalFees() {
        return totalFees.get();
    }

    public void setTotalFees(BigDecimal value) {
        totalFees.set(value == null ? CurrencyConfig.zero() : CurrencyConfig.money(value));
    }

    public ObjectProperty<BigDecimal> totalFeesProperty() {
        return totalFees;
    }

    public boolean isInclude() {
        return include.get();
    }

    public void setInclude(boolean value) {
        include.set(value);
    }

    public BooleanProperty includeProperty() {
        return include;
    }

    public int getRowNumber() {
        return rowNumber;
    }

    public void setRowNumber(int rowNumber) {
        this.rowNumber = rowNumber;
    }

    public String getMatchStatus() {
        return matchStatus;
    }

    public void setMatchStatus(String matchStatus) {
        this.matchStatus = matchStatus;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public boolean isHasBreakdown() {
        return hasBreakdown;
    }

    public void setHasBreakdown(boolean hasBreakdown) {
        this.hasBreakdown = hasBreakdown;
    }

    public String getWarningText() {
        return warnings.isEmpty() ? "" : String.join("; ", warnings);
    }

    /**
     * True when this row carries a mistake that blocks a clean auto-import and
     * should be corrected in the Clean Data section: missing name or admission
     * number, a class that could not be inferred, or a duplicate admission
     * number in the file. Informational notes (credit balance, penalty, totals
     * mismatch, name match) do not block import.
     */
    public boolean requiresCleaning() {
        for (String warning : warnings) {
            if (warning.startsWith("No name")
                    || warning.startsWith("No admission number")
                    || warning.startsWith("Class not inferred")
                    || warning.contains("Duplicate admission number in file")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Snapshot the row as a flat string map so it can be persisted in the Clean
     * Data list and rebuilt later without re-reading the source workbook.
     */
    public Map<String, String> toFields() {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("sheetName", getSheetName());
        fields.put("rowNumber", rowNumber == 0 ? "" : String.valueOf(rowNumber));
        fields.put("admissionNumber", getAdmissionNumber());
        fields.put("name", getName());
        fields.put("formClass", getFormClass());
        fields.put("stream", getStream());
        fields.put("boardingStatus", getBoardingStatus() == null ? "" : getBoardingStatus().name());
        fields.put("currentFees", plain(getCurrentFees()));
        fields.put("arrears", plain(getArrears()));
        fields.put("penalty", plain(getPenalty()));
        fields.put("totalFees", plain(getTotalFees()));
        fields.put("include", String.valueOf(isInclude()));
        fields.put("matchStatus", getMatchStatus());
        fields.put("hasBreakdown", String.valueOf(isHasBreakdown()));
        return fields;
    }

    /** Rebuild a row from a snapshot produced by {@link #toFields()}. */
    public static FeesBalanceRow fromFields(Map<String, String> fields) {
        FeesBalanceRow row = new FeesBalanceRow(
                value(fields, "sheetName"),
                parseInt(fields.get("rowNumber")));
        row.setAdmissionNumber(value(fields, "admissionNumber"));
        row.setName(value(fields, "name"));
        row.setFormClass(value(fields, "formClass"));
        row.setStream(value(fields, "stream"));
        String boarding = fields.get("boardingStatus");
        if (boarding != null && !boarding.isBlank()) {
            try {
                row.setBoardingStatus(BoardingStatus.valueOf(boarding));
            } catch (IllegalArgumentException ignored) {
                // Unknown marker: leave unset, scrutiny will fall back to defaults.
            }
        }
        row.setCurrentFees(parse(fields.get("currentFees")));
        row.setArrears(parse(fields.get("arrears")));
        row.setPenalty(parse(fields.get("penalty")));
        row.setTotalFees(parse(fields.get("totalFees")));
        row.setInclude(!"false".equalsIgnoreCase(fields.get("include")));
        row.setMatchStatus(value(fields, "matchStatus", "New"));
        row.setHasBreakdown(Boolean.parseBoolean(fields.get("hasBreakdown")));
        return row;
    }

    private static String plain(BigDecimal value) {
        return value == null ? "" : value.toPlainString();
    }

    private static BigDecimal parse(String value) {
        if (value == null || value.isBlank()) {
            return CurrencyConfig.zero();
        }
        try {
            return CurrencyConfig.money(value);
        } catch (NumberFormatException e) {
            return CurrencyConfig.zero();
        }
    }

    private static int parseInt(String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String value(Map<String, String> m, String key, String fallback) {
        String v = m.get(key);
        return v == null || v.isBlank() ? fallback : v;
    }

    private static String value(Map<String, String> m, String key) {
        return value(m, key, "");
    }
}
