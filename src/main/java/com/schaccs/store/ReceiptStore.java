package com.schaccs.store;

import com.schaccs.model.receipt.Receipt;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public final class ReceiptStore {

    private static final ReceiptStore INSTANCE = new ReceiptStore();

    private final ObservableList<Receipt> receipts = FXCollections.observableArrayList();

    private ReceiptStore() {
    }

    public static ReceiptStore getInstance() {
        return INSTANCE;
    }

    public ObservableList<Receipt> getReceipts() {
        return receipts;
    }

    public synchronized void add(Receipt receipt) {
        receipts.add(0, receipt);
    }

    public Optional<Receipt> findByNumber(long number) {
        return receipts.stream().filter(r -> r.getReceiptNumber() == number).findFirst();
    }

    public Optional<Receipt> findById(String id) {
        return receipts.stream().filter(r -> r.getId().equals(id)).findFirst();
    }

    public List<Receipt> forStudent(String studentId) {
        return receipts.stream()
                .filter(r -> studentId.equals(r.getStudentId()))
                .collect(Collectors.toList());
    }

    public List<Receipt> forDate(LocalDate date) {
        return receipts.stream()
                .filter(r -> date.equals(r.getDate()))
                .collect(Collectors.toList());
    }

    public synchronized void clear() {
        receipts.clear();
    }
}
