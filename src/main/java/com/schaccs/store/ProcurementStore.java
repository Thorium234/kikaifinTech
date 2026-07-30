package com.schaccs.store;

import com.schaccs.model.procurement.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.util.Optional;

/**
 * Singleton store for all procurement module entities.
 */
public final class ProcurementStore {

    private static final ProcurementStore INSTANCE = new ProcurementStore();

    private final ObservableList<Supplier> suppliers = FXCollections.observableArrayList();
    private final ObservableList<ProcurementRequest> procurementRequests = FXCollections.observableArrayList();
    private final ObservableList<Tender> tenders = FXCollections.observableArrayList();
    private final ObservableList<TenderBid> bids = FXCollections.observableArrayList();
    private final ObservableList<TenderEvaluation> evaluations = FXCollections.observableArrayList();
    private final ObservableList<TenderAward> awards = FXCollections.observableArrayList();
    private final ObservableList<Contract> contracts = FXCollections.observableArrayList();
    private final ObservableList<ContractMilestone> milestones = FXCollections.observableArrayList();
    private final ObservableList<ProcurementApproval> approvals = FXCollections.observableArrayList();

    private ProcurementStore() {
    }

    public static ProcurementStore getInstance() {
        return INSTANCE;
    }

    // ---- Suppliers ----

    public ObservableList<Supplier> getSuppliers() {
        return suppliers;
    }

    public synchronized void addSupplier(Supplier s) {
        suppliers.add(0, s);
    }

    public synchronized void removeSupplier(Supplier s) {
        suppliers.remove(s);
    }

    public Optional<Supplier> findSupplierById(String id) {
        return suppliers.stream().filter(s -> s.getId().equals(id)).findFirst();
    }

    public Optional<Supplier> findSupplierByNumber(String number) {
        return suppliers.stream().filter(s -> s.getSupplierNumber().equals(number)).findFirst();
    }

    // ---- Procurement Requests ----

    public ObservableList<ProcurementRequest> getProcurementRequests() {
        return procurementRequests;
    }

    public synchronized void addProcurementRequest(ProcurementRequest r) {
        procurementRequests.add(0, r);
    }

    public synchronized void removeProcurementRequest(ProcurementRequest r) {
        procurementRequests.remove(r);
    }

    public Optional<ProcurementRequest> findProcurementRequestById(String id) {
        return procurementRequests.stream().filter(r -> r.getId().equals(id)).findFirst();
    }

    public Optional<ProcurementRequest> findProcurementRequestByNumber(String number) {
        return procurementRequests.stream().filter(r -> r.getRequestNumber().equals(number)).findFirst();
    }

    // ---- Tenders ----

    public ObservableList<Tender> getTenders() {
        return tenders;
    }

    public synchronized void addTender(Tender t) {
        tenders.add(0, t);
    }

    public synchronized void removeTender(Tender t) {
        tenders.remove(t);
    }

    public Optional<Tender> findTenderById(String id) {
        return tenders.stream().filter(t -> t.getId().equals(id)).findFirst();
    }

    public Optional<Tender> findTenderByNumber(String number) {
        return tenders.stream().filter(t -> t.getTenderNumber().equals(number)).findFirst();
    }

    // ---- Bids ----

    public ObservableList<TenderBid> getBids() {
        return bids;
    }

    public synchronized void addBid(TenderBid b) {
        bids.add(0, b);
    }

    public synchronized void removeBid(TenderBid b) {
        bids.remove(b);
    }

    public Optional<TenderBid> findBidById(String id) {
        return bids.stream().filter(b -> b.getId().equals(id)).findFirst();
    }

    public ObservableList<TenderBid> bidsForTender(String tenderId) {
        return FXCollections.observableArrayList(
                bids.stream().filter(b -> b.getTenderId().equals(tenderId)).toList());
    }

    public ObservableList<TenderBid> bidsForSupplier(String supplierId) {
        return FXCollections.observableArrayList(
                bids.stream().filter(b -> b.getSupplierId().equals(supplierId)).toList());
    }

    public boolean hasDuplicateBid(String tenderId, String supplierId) {
        return bids.stream().anyMatch(b ->
                b.getTenderId().equals(tenderId) && b.getSupplierId().equals(supplierId)
                        && !"WITHDRAWN".equals(b.getStatus().name()));
    }

    // ---- Evaluations ----

    public ObservableList<TenderEvaluation> getEvaluations() {
        return evaluations;
    }

    public synchronized void addEvaluation(TenderEvaluation e) {
        evaluations.add(0, e);
    }

    public ObservableList<TenderEvaluation> evaluationsForBid(String bidId) {
        return FXCollections.observableArrayList(
                evaluations.stream().filter(e -> e.getBidId().equals(bidId)).toList());
    }

    public ObservableList<TenderEvaluation> evaluationsForTender(String tenderId) {
        return FXCollections.observableArrayList(
                evaluations.stream().filter(e -> e.getTenderId().equals(tenderId)).toList());
    }

    // ---- Awards ----

    public ObservableList<TenderAward> getAwards() {
        return awards;
    }

    public synchronized void addAward(TenderAward a) {
        awards.add(0, a);
    }

    public Optional<TenderAward> findAwardById(String id) {
        return awards.stream().filter(a -> a.getId().equals(id)).findFirst();
    }

    public ObservableList<TenderAward> awardsForTender(String tenderId) {
        return FXCollections.observableArrayList(
                awards.stream().filter(a -> a.getTenderId().equals(tenderId)).toList());
    }

    // ---- Contracts ----

    public ObservableList<Contract> getContracts() {
        return contracts;
    }

    public synchronized void addContract(Contract c) {
        contracts.add(0, c);
    }

    public synchronized void removeContract(Contract c) {
        contracts.remove(c);
    }

    public Optional<Contract> findContractById(String id) {
        return contracts.stream().filter(c -> c.getId().equals(id)).findFirst();
    }

    public Optional<Contract> findContractByNumber(String number) {
        return contracts.stream().filter(c -> c.getContractNumber().equals(number)).findFirst();
    }

    // ---- Milestones ----

    public ObservableList<ContractMilestone> getMilestones() {
        return milestones;
    }

    public synchronized void addMilestone(ContractMilestone m) {
        milestones.add(0, m);
    }

    public synchronized void removeMilestone(ContractMilestone m) {
        milestones.remove(m);
    }

    public ObservableList<ContractMilestone> milestonesForContract(String contractId) {
        return FXCollections.observableArrayList(
                milestones.stream().filter(m -> m.getContractId().equals(contractId)).toList());
    }

    // ---- Approvals ----

    public ObservableList<ProcurementApproval> getApprovals() {
        return approvals;
    }

    public synchronized void addApproval(ProcurementApproval a) {
        approvals.add(0, a);
    }

    public ObservableList<ProcurementApproval> approvalsForEntity(String entityType, String entityId) {
        return FXCollections.observableArrayList(
                approvals.stream().filter(a ->
                        a.getEntityType().equals(entityType) && a.getEntityId().equals(entityId)).toList());
    }

    public synchronized void clear() {
        suppliers.clear();
        procurementRequests.clear();
        tenders.clear();
        bids.clear();
        evaluations.clear();
        awards.clear();
        contracts.clear();
        milestones.clear();
        approvals.clear();
    }
}
