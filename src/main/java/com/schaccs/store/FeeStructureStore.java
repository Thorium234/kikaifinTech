package com.schaccs.store;

import com.schaccs.enums.BoardingStatus;
import com.schaccs.model.fee.FeeStructure;
import com.schaccs.model.finance.Votehead;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.util.Optional;

public final class FeeStructureStore {

    private static final FeeStructureStore INSTANCE = new FeeStructureStore();

    private final ObservableList<FeeStructure> structures = FXCollections.observableArrayList();
    private final ObservableList<Votehead> voteheads = FXCollections.observableArrayList();

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

    public synchronized void addStructure(FeeStructure structure) {
        structures.add(structure);
    }

    public synchronized void addVotehead(Votehead votehead) {
        voteheads.add(votehead);
    }

    public synchronized void removeVotehead(Votehead votehead) {
        voteheads.remove(votehead);
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

    public synchronized void clear() {
        structures.clear();
        voteheads.clear();
    }
}
