package com.schaccs.store;

import com.schaccs.model.payroll.PayrollItem;
import com.schaccs.model.payroll.PayrollRun;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.util.List;
import java.util.Optional;

public final class PayrollStore {

    private static final PayrollStore INSTANCE = new PayrollStore();

    private final ObservableList<PayrollRun> payrollRuns = FXCollections.observableArrayList();
    private final ObservableList<PayrollItem> payrollItems = FXCollections.observableArrayList();

    private PayrollStore() {}

    public static PayrollStore getInstance() { return INSTANCE; }

    public ObservableList<PayrollRun> getPayrollRuns() { return payrollRuns; }

    public ObservableList<PayrollItem> getPayrollItems() { return payrollItems; }

    public Optional<PayrollRun> findRunById(String id) {
        return payrollRuns.stream().filter(r -> id.equals(r.getId())).findFirst();
    }

    public List<PayrollItem> findItemsByRunId(String runId) {
        return payrollItems.stream()
                .filter(item -> runId.equals(item.getPayrollRunId()))
                .toList();
    }

    public Optional<PayrollRun> findRunByPeriod(int month, int year) {
        return payrollRuns.stream()
                .filter(r -> r.getMonth() == month && r.getYear() == year)
                .findFirst();
    }

    public Optional<PayrollRun> findLatestPostedRun() {
        return payrollRuns.stream()
                .filter(r -> r.getStatus() == PayrollRun.PayrollStatus.POSTED)
                .max((a, b) -> {
                    if (a.getYear() != b.getYear()) return Integer.compare(a.getYear(), b.getYear());
                    return Integer.compare(a.getMonth(), b.getMonth());
                });
    }

    public void clear() {
        payrollRuns.clear();
        payrollItems.clear();
    }
}
