package com.schaccs.service;

import com.schaccs.accounting.AccountingEngine;
import com.schaccs.config.AppConfig;
import com.schaccs.config.CurrencyConfig;
import com.schaccs.enums.*;
import com.schaccs.model.procurement.*;
import com.schaccs.service.audit.AuditService;
import com.schaccs.service.procurement.*;
import com.schaccs.store.LedgerStore;
import com.schaccs.store.ProcurementStore;
import com.schaccs.repository.Database;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ProcurementModuleTest {

    private ProcurementStore store;
    private SupplierService supplierService;
    private ProcurementService procurementService;
    private TenderService tenderService;
    private ContractService contractService;
    private ProcurementAccountingIntegration accounting;

    private AuditService audit;

    @BeforeEach
    void setUp() {
        store = ProcurementStore.getInstance();
        store.clear();
        LedgerStore.getInstance().clear();
        try (Connection conn = Database.getInstance().getConnection();
             Statement st = conn.createStatement()) {
            for (String table : List.of("procurement_approvals", "contract_milestones", "contracts",
                    "tender_awards", "tender_evaluations", "tender_bids", "tenders",
                    "procurement_requests", "suppliers", "transactions", "ledger_entries")) {
                st.executeUpdate("DELETE FROM " + table);
            }
        } catch (Exception ignored) {
        }
        audit = new AuditService();
        supplierService = new SupplierService(store, audit);
        procurementService = new ProcurementService(store, audit);
        tenderService = new TenderService(store, audit);
        contractService = new ContractService(store, audit);
        accounting = new ProcurementAccountingIntegration(new AccountingEngine());
        AppConfig.getInstance().getSchoolProfile().setAcademicYear(2026);
        AppConfig.getInstance().getSchoolProfile().setNextProcurementRequestNumber(1);
        AppConfig.getInstance().getSchoolProfile().setNextTenderNumber(1);
        AppConfig.getInstance().getSchoolProfile().setNextContractNumber(1);
        AppConfig.getInstance().getSchoolProfile().setNextSupplierNumber(1);
    }

    @AfterEach
    void tearDown() {
        store.clear();
        LedgerStore.getInstance().clear();
    }

    // ── Supplier Tests ────────────────────────────────────────────────

    @Test
    void supplierCreationGeneratesNumberAndAddsToStore() {
        Supplier s = new Supplier();
        s.setBusinessName("Kenya Supplies Ltd");
        s.setContactPerson("John Mwangi");
        s.setPhone("0712345678");
        s.setCategory("Goods");

        List<String> errors = supplierService.addSupplier(s);

        assertTrue(errors.isEmpty(), "Should create supplier without errors: " + errors);
        assertEquals("SUP-1", s.getSupplierNumber());
        assertTrue(store.getSuppliers().contains(s));
        assertTrue(s.isActive());
        assertFalse(s.isBlacklisted());
    }

    @Test
    void supplierCreationRejectsBlankName() {
        Supplier s = new Supplier();
        s.setBusinessName("");

        List<String> errors = supplierService.addSupplier(s);

        assertFalse(errors.isEmpty(), "Should reject blank business name");
    }

    @Test
    void supplierBlacklistingWorks() {
        Supplier s = new Supplier();
        s.setBusinessName("Problem Corp");
        supplierService.addSupplier(s);

        assertTrue(supplierService.blacklistSupplier(s, "Poor quality"));
        assertTrue(s.isBlacklisted());
        assertEquals("Poor quality", s.getBlacklistReason());

        assertTrue(supplierService.removeBlacklist(s));
        assertFalse(s.isBlacklisted());
        assertNull(s.getBlacklistReason());
    }

    @Test
    void supplierDeactivationWorks() {
        Supplier s = new Supplier();
        s.setBusinessName("Deactivatable Ltd");
        supplierService.addSupplier(s);

        assertTrue(s.isActive());
        assertTrue(supplierService.deactivateSupplier(s));
        assertFalse(s.isActive());

        assertTrue(supplierService.activateSupplier(s));
        assertTrue(s.isActive());
    }

    @Test
    void activeSuppliersExcludesBlacklistedAndInactive() {
        Supplier active = new Supplier();
        active.setBusinessName("Active Co");
        supplierService.addSupplier(active);

        Supplier blacklisted = new Supplier();
        blacklisted.setBusinessName("Blacklisted Co");
        supplierService.addSupplier(blacklisted);
        supplierService.blacklistSupplier(blacklisted, "fraud");

        Supplier inactive = new Supplier();
        inactive.setBusinessName("Inactive Co");
        supplierService.addSupplier(inactive);
        supplierService.deactivateSupplier(inactive);

        List<Supplier> result = supplierService.activeSuppliers();
        assertEquals(1, result.size());
        assertEquals("Active Co", result.getFirst().getBusinessName());
    }

    // ── Procurement Request Tests ─────────────────────────────────────

    @Test
    void requestCreationWorkflow() {
        ProcurementRequest req = new ProcurementRequest();
        req.setItemDescription("Laboratory Chemicals");
        req.setRequestedBy("Mr. Ochieng");
        req.setDepartment("Science Dept");
        req.setEstimatedCost(CurrencyConfig.money("150000"));
        req.setQuantity(50);

        List<String> errors = procurementService.createRequest(req);
        assertTrue(errors.isEmpty(), "Create should succeed: " + errors);
        assertEquals("PR-2026-0001", req.getRequestNumber());
        assertEquals(ProcurementRequestStatus.DRAFT, req.getStatus());

        errors = procurementService.submitRequest(req);
        assertTrue(errors.isEmpty(), "Submit should succeed: " + errors);
        assertEquals(ProcurementRequestStatus.SUBMITTED, req.getStatus());

        errors = procurementService.approveRequest(req, "Principal", "PRINCIPAL", "Approved for tender");
        assertTrue(errors.isEmpty(), "Approve should succeed: " + errors);
        assertEquals(ProcurementRequestStatus.APPROVED, req.getStatus());
    }

    @Test
    void requestRejectionWorkflow() {
        ProcurementRequest req = new ProcurementRequest();
        req.setItemDescription("Furniture");
        req.setRequestedBy("Mrs. Wanjiku");
        req.setEstimatedCost(CurrencyConfig.money("200000"));

        procurementService.createRequest(req);
        procurementService.submitRequest(req);

        List<String> errors = procurementService.rejectRequest(req, "Bursar", "BURSAR", "Over budget");
        assertTrue(errors.isEmpty());
        assertEquals(ProcurementRequestStatus.REJECTED, req.getStatus());
    }

    @Test
    void requestReturnToDraftWorkflow() {
        ProcurementRequest req = new ProcurementRequest();
        req.setItemDescription("Books");
        req.setRequestedBy("Mr. Kamau");
        req.setEstimatedCost(CurrencyConfig.money("50000"));

        procurementService.createRequest(req);
        procurementService.submitRequest(req);

        List<String> errors = procurementService.returnRequest(req, "Bursar", "BURSAR", "Need more details");
        assertTrue(errors.isEmpty());
        assertEquals(ProcurementRequestStatus.DRAFT, req.getStatus());
    }

    @Test
    void requestCannotSubmitWhenNotDraft() {
        ProcurementRequest req = new ProcurementRequest();
        req.setItemDescription("Test");
        req.setRequestedBy("Tester");
        req.setEstimatedCost(CurrencyConfig.money("10000"));
        procurementService.createRequest(req);
        procurementService.submitRequest(req);

        List<String> errors = procurementService.submitRequest(req);
        assertFalse(errors.isEmpty());
        assertTrue(errors.getFirst().contains("Only draft"));
    }

    @Test
    void requestCreationRejectsInvalidData() {
        ProcurementRequest req = new ProcurementRequest();
        req.setItemDescription("");

        List<String> errors = procurementService.createRequest(req);
        assertFalse(errors.isEmpty(), "Should reject blank description");
    }

    @Test
    void approvalIsRecorded() {
        ProcurementRequest req = new ProcurementRequest();
        req.setItemDescription("Computers");
        req.setRequestedBy("IT Dept");
        req.setEstimatedCost(CurrencyConfig.money("500000"));

        procurementService.createRequest(req);
        procurementService.submitRequest(req);
        procurementService.approveRequest(req, "Principal", "PRINCIPAL", "Go ahead");

        var approvals = store.approvalsForEntity("ProcurementRequest", req.getId());
        assertFalse(approvals.isEmpty(), "Approval should be recorded");
        assertEquals(ApprovalAction.SUBMIT, approvals.get(approvals.size() - 1).getAction());
    }

    // ── Tender Lifecycle Tests ─────────────────────────────────────────

    @Test
    void tenderFullLifecycle() {
        Tender tender = new Tender();
        tender.setTitle("Supply of Laboratory Equipment");
        tender.setDescription("For the new science lab");
        tender.setOpeningDate(LocalDate.now());
        tender.setClosingDate(LocalDate.now().plusDays(30));
        tender.setTenderType(TenderType.OPEN_TENDER);
        tender.setCategory(ProcurementCategory.GOODS);
        tender.setEstimatedBudget(CurrencyConfig.money("2000000"));

        List<String> errors = tenderService.createTender(tender);
        assertTrue(errors.isEmpty(), "Create should succeed: " + errors);
        assertEquals("TND-2026-0001", tender.getTenderNumber());
        assertEquals(TenderStatus.DRAFT, tender.getStatus());

        errors = tenderService.publishTender(tender);
        assertTrue(errors.isEmpty());
        assertEquals(TenderStatus.PUBLISHED, tender.getStatus());

        errors = tenderService.closeTender(tender);
        assertTrue(errors.isEmpty());
        assertEquals(TenderStatus.CLOSED, tender.getStatus());

        errors = tenderService.startEvaluation(tender);
        assertTrue(errors.isEmpty());
        assertEquals(TenderStatus.EVALUATION, tender.getStatus());
    }

    @Test
    void tenderCancellationWorks() {
        Tender tender = new Tender();
        tender.setTitle("Cancel Test");
        tender.setOpeningDate(LocalDate.now());
        tender.setClosingDate(LocalDate.now().plusDays(15));

        tenderService.createTender(tender);
        assertEquals(TenderStatus.DRAFT, tender.getStatus());

        List<String> errors = tenderService.cancelTender(tender);
        assertTrue(errors.isEmpty());
        assertEquals(TenderStatus.CANCELLED, tender.getStatus());
    }

    @Test
    void tenderRejectsInvalidDates() {
        Tender tender = new Tender();
        tender.setTitle("Bad Dates");
        tender.setOpeningDate(LocalDate.now().plusDays(10));
        tender.setClosingDate(LocalDate.now());

        List<String> errors = tenderService.createTender(tender);
        assertFalse(errors.isEmpty(), "Should reject closing before opening");
    }

    @Test
    void tenderRejectsBlankTitle() {
        Tender tender = new Tender();
        tender.setOpeningDate(LocalDate.now());
        tender.setClosingDate(LocalDate.now().plusDays(10));

        List<String> errors = tenderService.createTender(tender);
        assertFalse(errors.isEmpty(), "Should reject blank title");
    }

    // ── Bid Submission Tests ───────────────────────────────────────────

    @Test
    void bidSubmissionValidatesSupplierAndTender() {
        Supplier supplier = new Supplier();
        supplier.setBusinessName("Bidder Co");
        supplierService.addSupplier(supplier);

        Tender tender = new Tender();
        tender.setTitle("Bid Test");
        tender.setOpeningDate(LocalDate.now());
        tender.setClosingDate(LocalDate.now().plusDays(30));
        tenderService.createTender(tender);
        tenderService.publishTender(tender);

        TenderBid bid = new TenderBid();
        bid.setTenderId(tender.getId());
        bid.setSupplierId(supplier.getId());
        bid.setBidAmount(CurrencyConfig.money("500000"));
        bid.setSubmissionDate(LocalDate.now());

        List<String> errors = tenderService.submitBid(bid);
        assertTrue(errors.isEmpty(), "Bid should succeed: " + errors);
        assertEquals(BidStatus.SUBMITTED, bid.getStatus());
        assertEquals(1, store.getBids().size());
    }

    @Test
    void bidDuplicatePrevented() {
        Supplier supplier = new Supplier();
        supplier.setBusinessName("Duplicate Test Co");
        supplierService.addSupplier(supplier);

        Tender tender = new Tender();
        tender.setTitle("Dup Test");
        tender.setOpeningDate(LocalDate.now());
        tender.setClosingDate(LocalDate.now().plusDays(30));
        tenderService.createTender(tender);
        tenderService.publishTender(tender);

        TenderBid bid1 = new TenderBid();
        bid1.setTenderId(tender.getId());
        bid1.setSupplierId(supplier.getId());
        bid1.setBidAmount(CurrencyConfig.money("100000"));
        bid1.setSubmissionDate(LocalDate.now());
        tenderService.submitBid(bid1);

        TenderBid bid2 = new TenderBid();
        bid2.setTenderId(tender.getId());
        bid2.setSupplierId(supplier.getId());
        bid2.setBidAmount(CurrencyConfig.money("90000"));
        bid2.setSubmissionDate(LocalDate.now());

        List<String> errors = tenderService.submitBid(bid2);
        assertFalse(errors.isEmpty(), "Should prevent duplicate bid");
        assertTrue(errors.getFirst().contains("already submitted"));
    }

    @Test
    void bidRejectedForBlacklistedSupplier() {
        Supplier supplier = new Supplier();
        supplier.setBusinessName("Blacklisted Bidder");
        supplierService.addSupplier(supplier);
        supplierService.blacklistSupplier(supplier, "Fraud");

        Tender tender = new Tender();
        tender.setTitle("Blacklist Test");
        tender.setOpeningDate(LocalDate.now());
        tender.setClosingDate(LocalDate.now().plusDays(30));
        tenderService.createTender(tender);
        tenderService.publishTender(tender);

        TenderBid bid = new TenderBid();
        bid.setTenderId(tender.getId());
        bid.setSupplierId(supplier.getId());
        bid.setBidAmount(CurrencyConfig.money("100000"));
        bid.setSubmissionDate(LocalDate.now());

        List<String> errors = tenderService.submitBid(bid);
        assertFalse(errors.isEmpty(), "Should reject blacklisted supplier");
        assertTrue(errors.getFirst().contains("blacklisted"));
    }

    @Test
    void bidRejectedForClosedTender() {
        Supplier supplier = new Supplier();
        supplier.setBusinessName("Late Bidder");
        supplierService.addSupplier(supplier);

        Tender tender = new Tender();
        tender.setTitle("Closed Tender Test");
        tender.setOpeningDate(LocalDate.now());
        tender.setClosingDate(LocalDate.now().plusDays(30));
        tenderService.createTender(tender);
        tenderService.publishTender(tender);
        tenderService.closeTender(tender);

        TenderBid bid = new TenderBid();
        bid.setTenderId(tender.getId());
        bid.setSupplierId(supplier.getId());
        bid.setBidAmount(CurrencyConfig.money("100000"));
        bid.setSubmissionDate(LocalDate.now());

        List<String> errors = tenderService.submitBid(bid);
        assertFalse(errors.isEmpty(), "Should reject bid on closed tender");
    }

    // ── Evaluation & Ranking Tests ────────────────────────────────────

    @Test
    void bidRankingProducesCorrectOrder() {
        Supplier s1 = new Supplier();
        s1.setBusinessName("Rank A");
        supplierService.addSupplier(s1);
        Supplier s2 = new Supplier();
        s2.setBusinessName("Rank B");
        supplierService.addSupplier(s2);

        Tender tender = new Tender();
        tender.setTitle("Ranking Test");
        tender.setOpeningDate(LocalDate.now());
        tender.setClosingDate(LocalDate.now().plusDays(30));
        tenderService.createTender(tender);
        tenderService.publishTender(tender);

        TenderBid bid1 = new TenderBid();
        bid1.setTenderId(tender.getId());
        bid1.setSupplierId(s1.getId());
        bid1.setBidAmount(CurrencyConfig.money("200000"));
        bid1.setTechnicalScore(CurrencyConfig.money("90"));
        bid1.setFinancialScore(CurrencyConfig.money("80"));
        bid1.setSubmissionDate(LocalDate.now());
        tenderService.submitBid(bid1);

        TenderBid bid2 = new TenderBid();
        bid2.setTenderId(tender.getId());
        bid2.setSupplierId(s2.getId());
        bid2.setBidAmount(CurrencyConfig.money("150000"));
        bid2.setTechnicalScore(CurrencyConfig.money("70"));
        bid2.setFinancialScore(CurrencyConfig.money("95"));
        bid2.setSubmissionDate(LocalDate.now());
        tenderService.submitBid(bid2);

        List<String> errors = tenderService.rankBids(tender.getId(),
                CurrencyConfig.money("0.6"), CurrencyConfig.money("0.4"));
        assertTrue(errors.isEmpty(), "Ranking should succeed: " + errors);

        assertEquals(1, bid1.getRank(), "Bid1 (90*0.6 + 80*0.4 = 86) should be rank 1");
        assertEquals(2, bid2.getRank(), "Bid2 (70*0.6 + 95*0.4 = 80) should be rank 2");
    }

    // ── Tender Award Tests ────────────────────────────────────────────

    @Test
    void tenderAwardFlow() {
        Supplier supplier = new Supplier();
        supplier.setBusinessName("Winner Co");
        supplierService.addSupplier(supplier);

        Tender tender = new Tender();
        tender.setTitle("Award Test");
        tender.setOpeningDate(LocalDate.now());
        tender.setClosingDate(LocalDate.now().plusDays(30));
        tenderService.createTender(tender);
        tenderService.publishTender(tender);

        TenderBid bid = new TenderBid();
        bid.setTenderId(tender.getId());
        bid.setSupplierId(supplier.getId());
        bid.setBidAmount(CurrencyConfig.money("500000"));
        bid.setTechnicalScore(CurrencyConfig.money("85"));
        bid.setFinancialScore(CurrencyConfig.money("90"));
        bid.setSubmissionDate(LocalDate.now());
        tenderService.submitBid(bid);

        tenderService.closeTender(tender);
        tenderService.startEvaluation(tender);

        TenderAward award = new TenderAward();
        award.setSupplierId(supplier.getId());
        award.setAwardDate(LocalDate.now());
        award.setAwardAmount(CurrencyConfig.money("480000"));
        award.setAwardReason("Best value for money");
        award.setContractDurationMonths(12);

        List<String> errors = tenderService.awardTender(tender, award);
        assertTrue(errors.isEmpty(), "Award should succeed: " + errors);
        assertEquals(TenderStatus.AWARDED, tender.getStatus());
        assertEquals(supplier.getId(), tender.getAwardedSupplierId());
        assertEquals(CurrencyConfig.money("480000"), tender.getAwardedAmount());
    }

    @Test
    void tenderAwardRequiresEvaluationStatus() {
        Tender tender = new Tender();
        tender.setTitle("Wrong Status Award");
        tender.setOpeningDate(LocalDate.now());
        tender.setClosingDate(LocalDate.now().plusDays(30));
        tenderService.createTender(tender);
        tenderService.publishTender(tender);

        TenderAward award = new TenderAward();
        award.setSupplierId("some-supplier");
        award.setAwardAmount(CurrencyConfig.money("100000"));
        award.setAwardDate(LocalDate.now());

        List<String> errors = tenderService.awardTender(tender, award);
        assertFalse(errors.isEmpty(), "Should reject award when not in EVALUATION status");
    }

    // ── Contract Tests ─────────────────────────────────────────────────

    @Test
    void contractLifecycle() {
        Supplier supplier = new Supplier();
        supplier.setBusinessName("Contract Supplier");
        supplierService.addSupplier(supplier);

        Contract contract = new Contract();
        contract.setSupplierId(supplier.getId());
        contract.setStartDate(LocalDate.now());
        contract.setEndDate(LocalDate.now().plusMonths(12));
        contract.setContractValue(CurrencyConfig.money("1000000"));
        contract.setDeliverables("Monthly supply of chemicals");

        List<String> errors = contractService.createContract(contract);
        assertTrue(errors.isEmpty(), "Create should succeed: " + errors);
        assertEquals("CTR-2026-0001", contract.getContractNumber());
        assertEquals(ContractStatus.DRAFT, contract.getStatus());

        errors = contractService.activateContract(contract);
        assertTrue(errors.isEmpty());
        assertEquals(ContractStatus.ACTIVE, contract.getStatus());

        errors = contractService.completeContract(contract);
        assertTrue(errors.isEmpty());
        assertEquals(ContractStatus.COMPLETED, contract.getStatus());
    }

    @Test
    void contractExtensionWorks() {
        Supplier supplier = new Supplier();
        supplier.setBusinessName("Ext Supplier");
        supplierService.addSupplier(supplier);

        Contract contract = new Contract();
        contract.setSupplierId(supplier.getId());
        contract.setStartDate(LocalDate.now());
        contract.setEndDate(LocalDate.now().plusMonths(6));
        contract.setContractValue(CurrencyConfig.money("500000"));

        contractService.createContract(contract);
        contractService.activateContract(contract);

        List<String> errors = contractService.extendContract(contract, LocalDate.now().plusMonths(12));
        assertTrue(errors.isEmpty());
        assertEquals(ContractStatus.EXTENDED, contract.getStatus());
        assertEquals(LocalDate.now().plusMonths(12), contract.getEndDate());
    }

    @Test
    void contractTerminationWorks() {
        Supplier supplier = new Supplier();
        supplier.setBusinessName("Term Supplier");
        supplierService.addSupplier(supplier);

        Contract contract = new Contract();
        contract.setSupplierId(supplier.getId());
        contract.setStartDate(LocalDate.now());
        contract.setEndDate(LocalDate.now().plusMonths(12));
        contract.setContractValue(CurrencyConfig.money("300000"));

        contractService.createContract(contract);
        contractService.activateContract(contract);

        List<String> errors = contractService.terminateContract(contract, "Poor performance");
        assertTrue(errors.isEmpty());
        assertEquals(ContractStatus.TERMINATED, contract.getStatus());
    }

    @Test
    void contractRejectsInvalidData() {
        Contract contract = new Contract();
        contract.setSupplierId("");
        contract.setContractValue(CurrencyConfig.money("100000"));

        List<String> errors = contractService.createContract(contract);
        assertFalse(errors.isEmpty(), "Should reject missing supplier");
    }

    @Test
    void contractRejectsEndBeforeStart() {
        Contract contract = new Contract();
        contract.setSupplierId("sup-1");
        contract.setStartDate(LocalDate.now());
        contract.setEndDate(LocalDate.now().minusDays(5));
        contract.setContractValue(CurrencyConfig.money("100000"));

        List<String> errors = contractService.createContract(contract);
        assertFalse(errors.isEmpty(), "Should reject end before start");
    }

    // ── Contract Milestone Tests ───────────────────────────────────────

    @Test
    void milestoneLifecycle() {
        Supplier supplier = new Supplier();
        supplier.setBusinessName("Milestone Supplier");
        supplierService.addSupplier(supplier);

        Contract contract = new Contract();
        contract.setSupplierId(supplier.getId());
        contract.setStartDate(LocalDate.now());
        contract.setEndDate(LocalDate.now().plusMonths(6));
        contract.setContractValue(CurrencyConfig.money("600000"));
        contractService.createContract(contract);
        contractService.activateContract(contract);

        ContractMilestone m = new ContractMilestone();
        m.setContractId(contract.getId());
        m.setTitle("Phase 1 Delivery");
        m.setDescription("First batch of supplies");
        m.setDueDate(LocalDate.now().plusMonths(2));
        m.setAmount(CurrencyConfig.money("200000"));

        List<String> errors = contractService.addMilestone(m);
        assertTrue(errors.isEmpty(), "Add milestone should succeed: " + errors);
        assertFalse(m.isCompleted());
        assertFalse(m.isOverdue());

        errors = contractService.completeMilestone(m);
        assertTrue(errors.isEmpty());
        assertTrue(m.isCompleted());
        assertNotNull(m.getCompletedDate());
    }

    @Test
    void overdueMilestoneDetected() {
        Supplier supplier = new Supplier();
        supplier.setBusinessName("Overdue Supplier");
        supplierService.addSupplier(supplier);

        Contract contract = new Contract();
        contract.setSupplierId(supplier.getId());
        contract.setStartDate(LocalDate.now().minusMonths(3));
        contract.setEndDate(LocalDate.now().plusMonths(3));
        contract.setContractValue(CurrencyConfig.money("100000"));
        contractService.createContract(contract);
        contractService.activateContract(contract);

        ContractMilestone m = new ContractMilestone();
        m.setContractId(contract.getId());
        m.setTitle("Overdue Milestone");
        m.setDueDate(LocalDate.now().minusDays(10));
        m.setAmount(CurrencyConfig.money("50000"));
        contractService.addMilestone(m);

        assertTrue(m.isOverdue(), "Milestone past due date should be overdue");
    }

    // ── Accounting Integration Tests ───────────────────────────────────

    @Test
    void goodsReceivedPostsBalancedEntry() {
        int txBefore = LedgerStore.getInstance().getTransactions().size();

        accounting.postGoodsReceived("contract-123", "Kenya Supplies",
                CurrencyConfig.money("500000"), "SUPPLIES", "Lab chemicals");

        int txAfter = LedgerStore.getInstance().getTransactions().size();
        assertEquals(txBefore + 2, txAfter, "Should post 2 ledger entries (debit + credit)");

        BigDecimal totalDebits = LedgerStore.getInstance().getTransactions().stream()
                .map(t -> t.getDebit())
                .reduce(CurrencyConfig.zero(), BigDecimal::add);
        BigDecimal totalCredits = LedgerStore.getInstance().getTransactions().stream()
                .map(t -> t.getCredit())
                .reduce(CurrencyConfig.zero(), BigDecimal::add);
        assertEquals(0, totalDebits.compareTo(totalCredits), "Journal must be balanced");
    }

    @Test
    void supplierPaymentPostsBalancedEntry() {
        accounting.postSupplierPayment("contract-456", "Chemical Co",
                CurrencyConfig.money("300000"), "BOARD");

        BigDecimal totalDebits = LedgerStore.getInstance().getTransactions().stream()
                .map(t -> t.getDebit())
                .reduce(CurrencyConfig.zero(), BigDecimal::add);
        BigDecimal totalCredits = LedgerStore.getInstance().getTransactions().stream()
                .map(t -> t.getCredit())
                .reduce(CurrencyConfig.zero(), BigDecimal::add);
        assertEquals(0, totalDebits.compareTo(totalCredits), "Payment journal must be balanced");
    }

    @Test
    void multipleAccountingEntriesRemainBalanced() {
        accounting.postGoodsReceived("c1", "Supplier A", CurrencyConfig.money("100000"), "SUPPLIES", "Goods");
        accounting.postSupplierPayment("c1", "Supplier A", CurrencyConfig.money("100000"), "SUPPLIES");
        accounting.postGoodsReceived("c2", "Supplier B", CurrencyConfig.money("250000"), "UTILITIES", "Utilities");
        accounting.postSupplierPayment("c2", "Supplier B", CurrencyConfig.money("200000"), "UTILITIES");

        BigDecimal totalDebits = LedgerStore.getInstance().getTransactions().stream()
                .map(t -> t.getDebit())
                .reduce(CurrencyConfig.zero(), BigDecimal::add);
        BigDecimal totalCredits = LedgerStore.getInstance().getTransactions().stream()
                .map(t -> t.getCredit())
                .reduce(CurrencyConfig.zero(), BigDecimal::add);
        assertEquals(0, totalDebits.compareTo(totalCredits),
                "All procurement accounting entries must remain balanced");
    }

    // ── End-to-End Integration Test ───────────────────────────────────

    @Test
    void fullProcurementLifecycle() {
        // 1. Create supplier
        Supplier supplier = new Supplier();
        supplier.setBusinessName("Full Lifecycle Supplier");
        supplier.setKraPin("A123456789");
        supplier.setCategory("Goods");
        supplierService.addSupplier(supplier);
        assertNotNull(supplier.getSupplierNumber());

        // 2. Create and approve procurement request
        ProcurementRequest req = new ProcurementRequest();
        req.setItemDescription("New computers for computer lab");
        req.setRequestedBy("IT Department");
        req.setDepartment("ICT");
        req.setEstimatedCost(CurrencyConfig.money("3000000"));
        req.setQuantity(20);
        procurementService.createRequest(req);
        procurementService.submitRequest(req);
        procurementService.approveRequest(req, "Principal", "PRINCIPAL", "Approved");
        assertEquals(ProcurementRequestStatus.APPROVED, req.getStatus());

        // 3. Create tender
        Tender tender = new Tender();
        tender.setTitle("Supply of 20 Desktop Computers");
        tender.setDescription("For the new computer lab");
        tender.setOpeningDate(LocalDate.now());
        tender.setClosingDate(LocalDate.now().plusDays(21));
        tender.setTenderType(TenderType.REQUEST_FOR_QUOTATION);
        tender.setCategory(ProcurementCategory.GOODS);
        tender.setEstimatedBudget(CurrencyConfig.money("3000000"));
        tenderService.createTender(tender);
        procurementService.markTenderCreated(req, tender.getId());
        assertEquals(ProcurementRequestStatus.TENDER_CREATED, req.getStatus());

        // 4. Publish, receive bids, evaluate, rank
        tenderService.publishTender(tender);

        TenderBid bid = new TenderBid();
        bid.setTenderId(tender.getId());
        bid.setSupplierId(supplier.getId());
        bid.setBidAmount(CurrencyConfig.money("2800000"));
        bid.setTechnicalScore(CurrencyConfig.money("88"));
        bid.setFinancialScore(CurrencyConfig.money("92"));
        bid.setSubmissionDate(LocalDate.now());
        tenderService.submitBid(bid);

        tenderService.closeTender(tender);
        tenderService.startEvaluation(tender);

        tenderService.rankBids(tender.getId(), CurrencyConfig.money("0.6"), CurrencyConfig.money("0.4"));
        assertEquals(1, bid.getRank());

        // 5. Award
        TenderAward award = new TenderAward();
        award.setSupplierId(supplier.getId());
        award.setAwardDate(LocalDate.now());
        award.setAwardAmount(CurrencyConfig.money("2800000"));
        award.setAwardReason("Best technical and financial proposal");
        award.setContractDurationMonths(6);
        tenderService.awardTender(tender, award);
        assertEquals(TenderStatus.AWARDED, tender.getStatus());

        // 6. Create and activate contract
        Contract contract = new Contract();
        contract.setTenderId(tender.getId());
        contract.setSupplierId(supplier.getId());
        contract.setStartDate(LocalDate.now());
        contract.setEndDate(LocalDate.now().plusMonths(6));
        contract.setContractValue(CurrencyConfig.money("2800000"));
        contract.setDeliverables("20 desktop computers with monitors and keyboards");
        contractService.createContract(contract);
        contractService.activateContract(contract);
        assertEquals(ContractStatus.ACTIVE, contract.getStatus());

        // 7. Add milestone and complete
        ContractMilestone m = new ContractMilestone();
        m.setContractId(contract.getId());
        m.setTitle("Delivery and Installation");
        m.setDueDate(LocalDate.now().plusMonths(1));
        m.setAmount(CurrencyConfig.money("2800000"));
        contractService.addMilestone(m);

        // 8. Post accounting entries for goods received
        accounting.postGoodsReceived(contract.getId(), supplier.getBusinessName(),
                CurrencyConfig.money("2800000"), "SUPPLIES", "20 desktop computers");

        // 9. Verify accounting is balanced
        BigDecimal totalDebits = LedgerStore.getInstance().getTransactions().stream()
                .map(t -> t.getDebit())
                .reduce(CurrencyConfig.zero(), BigDecimal::add);
        BigDecimal totalCredits = LedgerStore.getInstance().getTransactions().stream()
                .map(t -> t.getCredit())
                .reduce(CurrencyConfig.zero(), BigDecimal::add);
        assertEquals(0, totalDebits.compareTo(totalCredits),
                "Full lifecycle accounting must remain balanced");

        // 10. Verify all entities exist in store
        assertEquals(1, store.getSuppliers().size());
        assertEquals(1, store.getProcurementRequests().size());
        assertEquals(1, store.getTenders().size());
        assertEquals(1, store.getBids().size());
        assertEquals(1, store.getAwards().size());
        assertEquals(1, store.getContracts().size());
        assertEquals(1, store.getMilestones().size());
    }

    // ── Query Tests ───────────────────────────────────────────────────

    @Test
    void queryMethodsReturnCorrectResults() {
        Tender t1 = new Tender();
        t1.setTitle("T1");
        t1.setOpeningDate(LocalDate.now());
        t1.setClosingDate(LocalDate.now().plusDays(10));
        tenderService.createTender(t1);

        Tender t2 = new Tender();
        t2.setTitle("T2");
        t2.setOpeningDate(LocalDate.now());
        t2.setClosingDate(LocalDate.now().plusDays(20));
        tenderService.createTender(t2);
        tenderService.publishTender(t2);

        assertEquals(2, tenderService.allTenders().size());
        assertEquals(1, tenderService.tendersByStatus(TenderStatus.DRAFT).size());
        assertEquals(1, tenderService.tendersByStatus(TenderStatus.PUBLISHED).size());
        assertTrue(tenderService.findByNumber(t1.getTenderNumber()).isPresent());

        ProcurementRequest r1 = new ProcurementRequest();
        r1.setItemDescription("Item 1");
        r1.setRequestedBy("User 1");
        r1.setEstimatedCost(CurrencyConfig.money("10000"));
        procurementService.createRequest(r1);
        assertEquals(1, procurementService.allRequests().size());
    }
}
