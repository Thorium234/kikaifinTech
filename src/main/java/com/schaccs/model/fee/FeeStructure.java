package com.schaccs.model.fee;

import com.schaccs.config.CurrencyConfig;
import com.schaccs.enums.AcademicTerm;
import com.schaccs.enums.BoardingStatus;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.Objects;

public class FeeStructure {

    private final String id;
    private int academicYear;
    private String formClass; // e.g. Form 1, Form 2 — or "ALL"
    private BoardingStatus boardingStatus;
    private String name;
    private final ObservableList<FeeStructureItem> items = FXCollections.observableArrayList();

    public FeeStructure() {
        this.id = UUID.randomUUID().toString();
    }

    public FeeStructure(int academicYear, String formClass, BoardingStatus boardingStatus, String name) {
        this();
        this.academicYear = academicYear;
        this.formClass = formClass;
        this.boardingStatus = boardingStatus;
        this.name = name;
    }

    public String getId() {
        return id;
    }

    public int getAcademicYear() {
        return academicYear;
    }

    public void setAcademicYear(int academicYear) {
        this.academicYear = academicYear;
    }

    public String getFormClass() {
        return formClass;
    }

    public void setFormClass(String formClass) {
        this.formClass = formClass;
    }

    public BoardingStatus getBoardingStatus() {
        return boardingStatus;
    }

    public void setBoardingStatus(BoardingStatus boardingStatus) {
        this.boardingStatus = boardingStatus;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public ObservableList<FeeStructureItem> getItems() {
        return items;
    }

    public void addItem(FeeStructureItem item) {
        items.add(item);
    }

    public List<FeeStructureItem> itemsForTerm(AcademicTerm term) {
        return items.stream()
                .filter(i -> i.getTerm() == term)
                .collect(Collectors.toList());
    }

    public BigDecimal totalForTerm(AcademicTerm term) {
        return itemsForTerm(term).stream()
                .map(FeeStructureItem::getAmount)
                .reduce(CurrencyConfig.zero(), BigDecimal::add);
    }

    public BigDecimal grandTotal() {
        return items.stream()
                .map(FeeStructureItem::getAmount)
                .reduce(CurrencyConfig.zero(), BigDecimal::add);
    }

    @Override
    public String toString() {
        return name != null ? name : formClass + " " + boardingStatus;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FeeStructure that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
