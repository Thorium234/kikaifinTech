package com.schaccs.service.finance;

import com.schaccs.config.CurrencyConfig;
import com.schaccs.model.finance.Budget;
import com.schaccs.model.finance.BudgetLine;
import com.schaccs.store.AccountStore;
import com.schaccs.repository.PersistenceService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BudgetServiceTest {

    private BudgetService service;

    @BeforeEach
    void setUp() {
        PersistenceService.getInstance().clearAll();
        AccountStore.getInstance().clear();
        service = new BudgetService(AccountStore.getInstance());
    }

    @AfterEach
    void tearDown() {
        PersistenceService.getInstance().clearAll();
        AccountStore.getInstance().clear();
    }

    private BudgetLine line(String code, BigDecimal allocated) {
        BudgetLine bl = new BudgetLine();
        bl.setVoteheadCode(code);
        bl.setAllocatedAmount(allocated);
        return bl;
    }

    @Test
    @DisplayName("createBudget stores budget with lines and sets budgetId")
    void createBudget() {
        List<BudgetLine> lines = new ArrayList<>();
        lines.add(line("TUITION", CurrencyConfig.money("100000")));
        lines.add(line("BOARDING", CurrencyConfig.money("50000")));

        Budget budget = service.createBudget("2026 Budget", "FY-2026", lines);

        assertNotNull(budget.getId());
        assertEquals("2026 Budget", budget.getName());
        assertEquals("FY-2026", budget.getFiscalYearId());
        assertEquals(2, AccountStore.getInstance().getBudgetLines().size());
        for (BudgetLine bl : AccountStore.getInstance().getBudgetLines()) {
            assertEquals(budget.getId(), bl.getBudgetId());
        }
    }

    @Test
    @DisplayName("approveBudget sets approved flag and timestamp")
    void approveBudget() {
        Budget budget = service.createBudget("Test", "FY", new ArrayList<>());
        assertFalse(budget.isApproved());
        assertNull(budget.getApprovedAt());

        service.approveBudget(budget);

        assertTrue(budget.isApproved());
        assertNotNull(budget.getApprovedAt());
    }

    @Test
    @DisplayName("checkBudget returns true when no lines exist for votehead")
    void checkBudget_noLines() {
        assertTrue(service.checkBudget("NONEXISTENT", CurrencyConfig.money("1000")));
    }

    @Test
    @DisplayName("checkBudget returns true when available >= amount")
    void checkBudget_sufficient() {
        Budget b = service.createBudget("Test", "FY", new ArrayList<>());
        List<BudgetLine> lines = new ArrayList<>();
        BudgetLine bl = line("TUITION", CurrencyConfig.money("100000"));
        lines.add(bl);
        service.createBudget("Test2", "FY", lines);

        assertTrue(service.checkBudget("TUITION", CurrencyConfig.money("50000")));
    }

    @Test
    @DisplayName("checkBudget returns false when available < amount")
    void checkBudget_insufficient() {
        List<BudgetLine> lines = new ArrayList<>();
        lines.add(line("TUITION", CurrencyConfig.money("1000")));
        service.createBudget("Test", "FY", lines);

        assertFalse(service.checkBudget("TUITION", CurrencyConfig.money("5000")));
    }

    @Test
    @DisplayName("recordSpend adds to first matching line's spent amount")
    void recordSpend() {
        List<BudgetLine> lines = new ArrayList<>();
        lines.add(line("TUITION", CurrencyConfig.money("100000")));
        service.createBudget("Test", "FY", lines);

        service.recordSpend("TUITION", CurrencyConfig.money("25000"));
        service.recordSpend("TUITION", CurrencyConfig.money("10000"));

        BudgetLine bl = AccountStore.getInstance().findBudgetLinesByVoteheadCode("TUITION").get(0);
        assertEquals(0, bl.getSpentAmount().compareTo(CurrencyConfig.money("35000")));
    }

    @Test
    @DisplayName("recordCommitment adds to first matching line's committed amount")
    void recordCommitment() {
        List<BudgetLine> lines = new ArrayList<>();
        lines.add(line("BOARDING", CurrencyConfig.money("80000")));
        service.createBudget("Test", "FY", lines);

        service.recordCommitment("BOARDING", CurrencyConfig.money("15000"));

        BudgetLine bl = AccountStore.getInstance().findBudgetLinesByVoteheadCode("BOARDING").get(0);
        assertEquals(0, bl.getCommittedAmount().compareTo(CurrencyConfig.money("15000")));
    }

    @Test
    @DisplayName("getVarianceReport returns rows with correct values")
    void getVarianceReport() {
        List<BudgetLine> lines = new ArrayList<>();
        lines.add(line("TUITION", CurrencyConfig.money("100000")));
        Budget budget = service.createBudget("2026", "FY", lines);

        service.recordSpend("TUITION", CurrencyConfig.money("30000"));
        service.recordCommitment("TUITION", CurrencyConfig.money("20000"));

        List<BudgetService.BudgetVarianceRow> rows = service.getVarianceReport(budget.getId());
        assertEquals(1, rows.size());
        BudgetService.BudgetVarianceRow row = rows.get(0);
        assertEquals("TUITION", row.getVoteheadCode());
        assertEquals(0, row.getAllocated().compareTo(CurrencyConfig.money("100000")));
        assertEquals(0, row.getSpent().compareTo(CurrencyConfig.money("30000")));
        assertEquals(0, row.getCommitted().compareTo(CurrencyConfig.money("20000")));
        assertEquals(0, row.getAvailable().compareTo(CurrencyConfig.money("50000")));
    }

    @Test
    @DisplayName("getOverallUtilization computes correct percentage")
    void getOverallUtilization() {
        List<BudgetLine> lines = new ArrayList<>();
        lines.add(line("TUITION", CurrencyConfig.money("100000")));
        service.createBudget("2026", "FY-2026", lines);

        service.recordSpend("TUITION", CurrencyConfig.money("40000"));
        service.recordCommitment("TUITION", CurrencyConfig.money("10000"));

        double util = service.getOverallUtilization("FY-2026");
        assertEquals(50.0, util, 0.01, "50000/100000 = 50%");
    }

    @Test
    @DisplayName("getOverallUtilization returns 0.0 when no budgets exist")
    void getOverallUtilization_empty() {
        assertEquals(0.0, service.getOverallUtilization("NONEXISTENT"));
    }

    @Test
    @DisplayName("getOverallUtilization ignores budgets from other fiscal years")
    void getOverallUtilization_wrongYear() {
        List<BudgetLine> lines = new ArrayList<>();
        lines.add(line("TUITION", CurrencyConfig.money("100000")));
        service.createBudget("2026", "FY-OTHER", lines);
        service.recordSpend("TUITION", CurrencyConfig.money("50000"));

        assertEquals(0.0, service.getOverallUtilization("FY-2026"));
    }
}
