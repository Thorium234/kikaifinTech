package com.schaccs.store;

import com.schaccs.enums.BoardingStatus;
import com.schaccs.model.fee.FeeStructure;
import com.schaccs.model.fee.FeeStructureTemplate;
import com.schaccs.model.fee.StudentCategory;
import com.schaccs.model.finance.Votehead;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.util.Optional;

public final class FeeStructureStore {

    private static final FeeStructureStore INSTANCE = new FeeStructureStore();

    private final ObservableList<FeeStructure> structures = FXCollections.observableArrayList();
    private final ObservableList<Votehead> voteheads = FXCollections.observableArrayList();
    private final ObservableList<FeeStructureTemplate> templates = FXCollections.observableArrayList();
    private final ObservableList<StudentCategory> categories = FXCollections.observableArrayList();

    private FeeStructureStore() {
    }

    public static FeeStructureStore getInstance() {
        return INSTANCE;
    }

    public ObservableList<FeeStructure> getStructures() {
        return structures;
    }

    public ObservableList<Votehead> getVoteheads() {
        return voteheads;
    }

    public ObservableList<FeeStructureTemplate> getTemplates() {
        return templates;
    }

    public ObservableList<StudentCategory> getCategories() {
        return categories;
    }

    public synchronized void addCategory(StudentCategory category) {
        if (categories.stream().noneMatch(c -> c.getId() == category.getId())) {
            categories.add(category);
        }
    }

    public Optional<StudentCategory> findCategoryById(int id) {
        return categories.stream().filter(c -> c.getId() == id).findFirst();
    }

    public Optional<StudentCategory> findCategoryByName(String name) {
        return categories.stream()
                .filter(c -> c.getName().equalsIgnoreCase(name))
                .findFirst();
    }

    public synchronized void addStructure(FeeStructure structure) {
        structures.add(structure);
    }

    public synchronized void addVotehead(Votehead votehead) {
        voteheads.add(votehead);
    }

    public synchronized void removeVotehead(Votehead votehead) {
        voteheads.remove(votehead);
    }

    public synchronized void addTemplate(FeeStructureTemplate template) {
        templates.add(template);
    }

    public synchronized void removeTemplate(FeeStructureTemplate template) {
        templates.remove(template);
    }

    public Optional<Votehead> findVoteheadByCode(String code) {
        return voteheads.stream()
                .filter(v -> v.getCode().equalsIgnoreCase(code))
                .findFirst();
    }

    public String voteheadName(String code) {
        return findVoteheadByCode(code).map(Votehead::getName).orElse(code);
    }

    public Optional<FeeStructure> findStructure(int year, BoardingStatus status) {
        return structures.stream()
                .filter(s -> s.getAcademicYear() == year && s.getBoardingStatus() == status)
                .findFirst();
    }

    public Optional<FeeStructure> findStructure(int year, int categoryId) {
        return structures.stream()
                .filter(s -> s.getAcademicYear() == year
                        && s.getCategoryId() != null && s.getCategoryId() == categoryId)
                .findFirst();
    }

    public Optional<FeeStructure> findStructureByName(int year, String categoryName) {
        return findCategoryByName(categoryName)
                .flatMap(cat -> findStructure(year, cat.getId()));
    }

    public synchronized void clear() {
        structures.clear();
        voteheads.clear();
        templates.clear();
        categories.clear();
    }
}
