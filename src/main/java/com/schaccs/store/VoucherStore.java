package com.schaccs.store;

import com.schaccs.model.voucher.Commitment;
import com.schaccs.model.voucher.Creditor;
import com.schaccs.model.voucher.PaymentVoucher;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.util.Optional;

public final class VoucherStore {

    private static final VoucherStore INSTANCE = new VoucherStore();

    private final ObservableList<Creditor> creditors = FXCollections.observableArrayList();
    private final ObservableList<Commitment> commitments = FXCollections.observableArrayList();
    private final ObservableList<PaymentVoucher> vouchers = FXCollections.observableArrayList();

    private VoucherStore() {
    }

    public static VoucherStore getInstance() {
        return INSTANCE;
    }

    public ObservableList<Creditor> getCreditors() {
        return creditors;
    }

    public ObservableList<Commitment> getCommitments() {
        return commitments;
    }

    public ObservableList<PaymentVoucher> getVouchers() {
        return vouchers;
    }

    public void addCreditor(Creditor c) {
        creditors.add(c);
    }

    public void addCommitment(Commitment c) {
        commitments.add(0, c);
    }

    public void addVoucher(PaymentVoucher v) {
        vouchers.add(0, v);
    }

    public Optional<Creditor> findCreditor(String id) {
        return creditors.stream().filter(c -> c.getId().equals(id)).findFirst();
    }

    public Optional<Commitment> findCommitment(String id) {
        return commitments.stream().filter(c -> c.getId().equals(id)).findFirst();
    }

    public Optional<PaymentVoucher> findVoucher(String id) {
        return vouchers.stream().filter(v -> v.getId().equals(id)).findFirst();
    }

    public void clear() {
        creditors.clear();
        commitments.clear();
        vouchers.clear();
    }
}
