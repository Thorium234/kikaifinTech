package com.schaccs.service.procurement;

import com.schaccs.config.AppConfig;
import com.schaccs.config.CurrencyConfig;
import com.schaccs.enums.BidStatus;
import com.schaccs.enums.TenderStatus;
import com.schaccs.model.procurement.*;
import com.schaccs.repository.PersistenceService;
import com.schaccs.service.audit.AuditService;
import com.schaccs.store.ProcurementStore;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

public class TenderService {

    private final ProcurementStore store;
    private final AuditService audit;

    public TenderService(ProcurementStore store, AuditService audit) {
        this.store = store;
        this.audit = audit;
    }

    public TenderService() {
        this(ProcurementStore.getInstance(), new AuditService());
    }

    // ── Tender Lifecycle ────────────────────────────────────────────────

    public List<String> createTender(Tender tender) {
        List<String> errors = new ArrayList<>();

        if (tender.getTitle() == null || tender.getTitle().isBlank()) {
            errors.add("Tender title is required.");
        }
        if (tender.getOpeningDate() == null) {
            errors.add("Opening date is required.");
        }
        if (tender.getClosingDate() == null) {
            errors.add("Closing date is required.");
        }
        if (tender.getOpeningDate() != null && tender.getClosingDate() != null
                && !tender.getClosingDate().isAfter(tender.getOpeningDate())) {
            errors.add("Closing date must be after opening date.");
        }
        if (tender.getClosingDate() != null && !tender.getClosingDate().isAfter(LocalDate.now())) {
            errors.add("Closing date must be in the future.");
        }
        if (!errors.isEmpty()) return errors;

        int year = AppConfig.getInstance().getAcademicYear();
        long number = AppConfig.getInstance().getSchoolProfile().allocateTenderNumber();
        tender.setTenderNumber(String.format("TND-%d-%04d", year, number));
        tender.setStatus(TenderStatus.DRAFT);

        store.addTender(tender);
        persist();
        logAudit("CREATE", "Tender", tender.getId(),
                "Created tender " + tender.getTenderNumber());
        return errors;
    }

    public List<String> publishTender(Tender tender) {
        List<String> errors = validateStatus(tender, TenderStatus.DRAFT, "publish");
        if (!errors.isEmpty()) return errors;

        tender.setStatus(TenderStatus.PUBLISHED);
        persist();
        logAudit("PUBLISH", "Tender", tender.getId(),
                "Published tender " + tender.getTenderNumber());
        return errors;
    }

    public List<String> closeTender(Tender tender) {
        List<String> errors = validateStatus(tender, TenderStatus.PUBLISHED, "close");
        if (!errors.isEmpty()) return errors;

        tender.setStatus(TenderStatus.CLOSED);
        persist();
        logAudit("CLOSE", "Tender", tender.getId(),
                "Closed tender " + tender.getTenderNumber());
        return errors;
    }

    public List<String> startEvaluation(Tender tender) {
        List<String> errors = validateStatus(tender, TenderStatus.CLOSED, "start evaluation for");
        if (!errors.isEmpty()) return errors;

        tender.setStatus(TenderStatus.EVALUATION);
        persist();
        logAudit("START_EVALUATION", "Tender", tender.getId(),
                "Started evaluation for tender " + tender.getTenderNumber());
        return errors;
    }

    public List<String> cancelTender(Tender tender) {
        if (tender == null || tender.getStatus() == null) {
            return List.of("No tender selected.");
        }
        if (tender.getStatus() != TenderStatus.DRAFT && tender.getStatus() != TenderStatus.PUBLISHED) {
            return List.of("Can only cancel a tender in DRAFT or PUBLISHED status. Current: " + tender.getStatus());
        }

        tender.setStatus(TenderStatus.CANCELLED);
        persist();
        logAudit("CANCEL", "Tender", tender.getId(),
                "Cancelled tender " + tender.getTenderNumber());
        return List.of();
    }

    // ── Bid Management ──────────────────────────────────────────────────

    public List<String> submitBid(TenderBid bid) {
        List<String> errors = new ArrayList<>();

        if (bid.getTenderId() == null || bid.getSupplierId() == null) {
            errors.add("Tender ID and Supplier ID are required.");
            if (!errors.isEmpty()) return errors;
        }

        Optional<Tender> tenderOpt = store.findTenderById(bid.getTenderId());
        if (tenderOpt.isEmpty()) {
            errors.add("Tender not found.");
            return errors;
        }
        if (tenderOpt.get().getStatus() != TenderStatus.PUBLISHED) {
            errors.add("Tender is not open for bidding. Current status: " + tenderOpt.get().getStatus());
        }

        Optional<Supplier> supplierOpt = store.findSupplierById(bid.getSupplierId());
        if (supplierOpt.isEmpty()) {
            errors.add("Supplier not found.");
            return errors;
        }
        Supplier supplier = supplierOpt.get();
        if (!supplier.isActive()) {
            errors.add("Supplier is not active.");
        }
        if (supplier.isBlacklisted()) {
            errors.add("Supplier is blacklisted.");
        }

        if (store.hasDuplicateBid(bid.getTenderId(), bid.getSupplierId())) {
            errors.add("Supplier has already submitted a bid for this tender.");
        }

        if (!errors.isEmpty()) return errors;

        bid.setStatus(BidStatus.SUBMITTED);
        store.addBid(bid);
        persist();
        logAudit("SUBMIT_BID", "TenderBid", bid.getId(),
                "Bid submitted for tender " + bid.getTenderId() + " by supplier " + bid.getSupplierId());
        return errors;
    }

    public List<String> recordEvaluation(TenderEvaluation evaluation) {
        List<String> errors = new ArrayList<>();

        if (evaluation.getTenderId() == null || evaluation.getBidId() == null) {
            errors.add("Tender ID and Bid ID are required.");
            return errors;
        }
        if (store.findTenderById(evaluation.getTenderId()).isEmpty()) {
            errors.add("Tender not found.");
        }
        if (store.findBidById(evaluation.getBidId()).isEmpty()) {
            errors.add("Bid not found.");
        }
        if (!errors.isEmpty()) return errors;

        store.addEvaluation(evaluation);
        persist();
        logAudit("RECORD_EVALUATION", "TenderEvaluation", evaluation.getId(),
                "Recorded evaluation for bid " + evaluation.getBidId());
        return errors;
    }

    // ── Ranking & Award ─────────────────────────────────────────────────

    public List<String> rankBids(String tenderId, BigDecimal technicalWeight, BigDecimal financialWeight) {
        List<String> errors = new ArrayList<>();

        if (tenderId == null) {
            errors.add("Tender ID is required.");
            return errors;
        }
        if (technicalWeight == null || financialWeight == null) {
            errors.add("Technical and financial weights are required.");
            return errors;
        }

        List<TenderBid> bids = store.bidsForTender(tenderId).stream()
                .filter(b -> b.getStatus() != BidStatus.WITHDRAWN)
                .collect(Collectors.toList());

        if (bids.isEmpty()) {
            errors.add("No bids found for tender to rank.");
            return errors;
        }

        for (TenderBid bid : bids) {
            bid.computeWeightedScore(technicalWeight, financialWeight);
        }

        bids.sort((a, b) -> b.getWeightedScore().compareTo(a.getWeightedScore()));

        int rank = 1;
        for (TenderBid bid : bids) {
            bid.setRank(rank++);
        }

        persist();
        logAudit("RANK_BIDS", "Tender", tenderId,
                "Ranked " + bids.size() + " bids with weights tech=" + technicalWeight + " fin=" + financialWeight);
        return errors;
    }

    public List<String> awardTender(Tender tender, TenderAward award) {
        List<String> errors = new ArrayList<>();

        if (tender == null) {
            errors.add("No tender selected.");
            return errors;
        }
        if (tender.getStatus() != TenderStatus.EVALUATION) {
            errors.add("Tender must be in EVALUATION status to award. Current: " + tender.getStatus());
        }
        if (store.bidsForTender(tender.getId()).isEmpty()) {
            errors.add("No bids exist for this tender.");
        }
        if (award == null) {
            errors.add("No award provided.");
            return errors;
        }
        if (!errors.isEmpty()) return errors;

        tender.setStatus(TenderStatus.AWARDED);
        tender.setAwardedSupplierId(award.getSupplierId());
        tender.setAwardedAmount(award.getAwardAmount());
        tender.setAwardDate(award.getAwardDate());

        award.setTenderId(tender.getId());
        store.addAward(award);
        persist();
        logAudit("AWARD", "Tender", tender.getId(),
                "Awarded tender " + tender.getTenderNumber() + " to supplier " + award.getSupplierId());
        return errors;
    }

    // ── Queries ─────────────────────────────────────────────────────────

    public List<Tender> tendersByStatus(TenderStatus status) {
        return store.getTenders().stream()
                .filter(t -> t.getStatus() == status)
                .collect(Collectors.toList());
    }

    public Optional<Tender> findById(String id) {
        return store.findTenderById(id);
    }

    public Optional<Tender> findByNumber(String number) {
        return store.findTenderByNumber(number);
    }

    public List<Tender> allTenders() {
        return new ArrayList<>(store.getTenders());
    }

    public List<TenderBid> bidsForTender(String tenderId) {
        return store.bidsForTender(tenderId);
    }

    public List<TenderEvaluation> evaluationsForTender(String tenderId) {
        return store.evaluationsForTender(tenderId);
    }

    public Optional<TenderAward> awardForTender(String tenderId) {
        return store.awardsForTender(tenderId).stream().findFirst();
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    private List<String> validateStatus(Tender tender, TenderStatus expected, String action) {
        if (tender == null) return List.of("No tender selected.");
        if (tender.getStatus() != expected) {
            return List.of("Cannot " + action + " tender. Expected status " + expected
                    + " but current is " + tender.getStatus() + ".");
        }
        return List.of();
    }

    private void persist() {
        PersistenceService.getInstance().saveAll();
    }

    private void logAudit(String action, String entityType, String entityId, String details) {
        audit.log(action, entityType, entityId, details);
    }
}
