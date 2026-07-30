package com.schaccs.service;

import com.schaccs.accounting.AccountingEngine;
import com.schaccs.config.CurrencyConfig;
import com.schaccs.enums.AccountType;
import com.schaccs.model.finance.Votehead;
import com.schaccs.model.voucher.Creditor;
import com.schaccs.model.voucher.Imprest;
import com.schaccs.model.voucher.Invoice;
import com.schaccs.model.voucher.Lpo;
import com.schaccs.service.voucher.PaymentVoucherService;
import com.schaccs.store.FeeStructureStore;
import com.schaccs.store.VoucherStore;
import com.schaccs.repository.PersistenceService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertTrue;

class VoucherCoreTest {

    @BeforeEach
    void setUp() {
        PersistenceService.getInstance().clearAll();
        FeeStructureStore.getInstance().addVotehead(new Votehead("BOARD", "Boarding", AccountType.SCHOOL_FUND, 1));
    }

    @AfterEach
    void tearDown() {
        PersistenceService.getInstance().clearAll();
    }

    @Test
    @DisplayName("voucher status guards block invalid edits")
    void voucherStatusGuardsBlockInvalidEdits() {
        Votehead votehead = FeeStructureStore.getInstance().getVoteheads().getFirst();
        Creditor creditor = new Creditor("Guard Supplier", "0700000000");
        VoucherStore.getInstance().addCreditor(creditor);
        PaymentVoucherService service = new PaymentVoucherService(VoucherStore.getInstance(), new AccountingEngine());

        Lpo lpo = new Lpo();
        lpo.setCreditorId(creditor.getId());
        lpo.setCreditorName(creditor.getName());
        lpo.setVoteheadCode(votehead.getCode());
        lpo.setVoteheadName(votehead.getName());
        lpo.setStatus(Lpo.INVOICED);
        VoucherStore.getInstance().addLpo(lpo);
        assertTrue(service.updateLpo(lpo, creditor, votehead, CurrencyConfig.money("1000"), "LPO-1", "desc", LocalDate.now()).contains("Invoiced LPOs cannot be edited."));
        assertTrue(service.cancelLpo(lpo).contains("Invoiced LPOs cannot be cancelled."));
        assertTrue(service.deleteLpo(lpo).contains("Invoiced LPOs cannot be deleted."));

        Invoice invoice = new Invoice();
        invoice.setCreditorId(creditor.getId());
        invoice.setCreditorName(creditor.getName());
        invoice.setVoteheadCode(votehead.getCode());
        invoice.setVoteheadName(votehead.getName());
        invoice.setStatus(Invoice.PAID);
        VoucherStore.getInstance().addInvoice(invoice);
        assertTrue(service.updateInvoice(invoice, creditor, votehead, CurrencyConfig.money("1200"), "INV-1", "desc", LocalDate.now(), null).contains("Paid invoices cannot be edited."));
        assertTrue(service.cancelInvoice(invoice).contains("Paid invoices cannot be cancelled."));
        assertTrue(service.deleteInvoice(invoice).contains("Paid invoices cannot be deleted."));

        Imprest imprest = new Imprest();
        imprest.setStaffName("Officer");
        imprest.setVoteheadCode(votehead.getCode());
        imprest.setVoteheadName(votehead.getName());
        imprest.setStatus(Imprest.SURRENDERED);
        VoucherStore.getInstance().addImprest(imprest);
        assertTrue(service.updateImprest(imprest, "Officer", votehead, CurrencyConfig.money("800"), "Trip", LocalDate.now()).contains("Surrendered imprests cannot be edited."));
        assertTrue(service.surrenderImprest(imprest, CurrencyConfig.money("800"), LocalDate.now()).contains("Imprest is already surrendered."));
        assertTrue(service.deleteImprest(imprest).contains("Surrendered imprests cannot be deleted."));
    }
}
