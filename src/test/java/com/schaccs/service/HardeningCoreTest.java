package com.schaccs.service;

import com.schaccs.accounting.ReceiptAllocationEngine;
import com.schaccs.config.CurrencyConfig;
import com.schaccs.enums.AccountType;
import com.schaccs.enums.BoardingStatus;
import com.schaccs.model.fee.FeeAllocation;
import com.schaccs.model.finance.Votehead;
import com.schaccs.model.student.Student;
import com.schaccs.model.student.StudentFeeLedger;
import com.schaccs.repository.Database;
import com.schaccs.service.importer.StudentImportService;
import com.schaccs.validation.StudentValidator;
import com.schaccs.store.FeeStructureStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class HardeningCoreTest {

    @AfterEach
    void tearDown() {
        FeeStructureStore.getInstance().clear();
        Database.getInstance().close();
    }

    @Test
    void sqliteConnectionEnablesForeignKeysAndWal() throws Exception {
        Connection conn = Database.getInstance().getConnection();
        try (Statement st = conn.createStatement()) {
            try (ResultSet rs = st.executeQuery("PRAGMA foreign_keys")) {
                assertTrue(rs.next());
                assertEquals(1, rs.getInt(1));
            }
            try (ResultSet rs = st.executeQuery("PRAGMA journal_mode")) {
                assertTrue(rs.next());
                assertEquals("wal", rs.getString(1).toLowerCase());
            }
        }
    }

    @Test
    void validatorRejectsInvalidKenyanStudentFields() {
        Student student = new Student();
        student.setAdmissionNumber(" ");
        student.setName("Test");
        student.setFormClass("Form 1");
        student.setBoardingStatus(BoardingStatus.BOARDING);
        student.setPhone("99999");
        student.setUpi("bad!");

        List<String> errors = new StudentValidator().validate(student, true);

        assertTrue(errors.stream().anyMatch(e -> e.contains("Admission number is required")));
        assertTrue(errors.stream().anyMatch(e -> e.contains("Phone number must be Kenyan format")));
        assertTrue(errors.stream().anyMatch(e -> e.contains("UPI must be 8-20 alphanumeric characters")));
    }

    @Test
    void allocationPostsSurplusAsAdvanceCredit() {
        FeeStructureStore.getInstance().addVotehead(new Votehead("BOARD", "Boarding", AccountType.SCHOOL_FUND, 1));
        StudentFeeLedger ledger = new StudentFeeLedger("S1");
        ledger.charge("BOARD", CurrencyConfig.money("1000"));

        List<FeeAllocation> allocations = new ReceiptAllocationEngine(FeeStructureStore.getInstance())
                .allocate(ledger, CurrencyConfig.money("1500"));

        assertEquals(2, allocations.size());
        assertEquals("BOARD", allocations.get(0).getVoteheadCode());
        assertEquals(CurrencyConfig.money("1000.00"), allocations.get(0).getAllocated());
        assertEquals("ADVANCE", allocations.get(1).getVoteheadCode());
        assertEquals(CurrencyConfig.money("500.00"), allocations.get(1).getAllocated());
    }

    @Test
    void importResultIncludesRejectedRowReasons() {
        StudentImportService service = new StudentImportService();
        Map<String, String> badRow = new HashMap<>();
        badRow.put("admissionnumber", "");
        badRow.put("fullname", "Bad Student");
        badRow.put("formclass", "Form 1");
        badRow.put("phone", "123");
        badRow.put("upi", "**bad**");

        StudentImportService.ImportResult result = service.importRows(List.of(badRow), false);

        assertEquals(0, result.getImported());
        assertEquals(1, result.getRejected());
        assertEquals(1, result.getFailures().size());
        assertTrue(result.getFailures().getFirst().getReason().contains("Admission number is required"));
    }

    @Test
    void backupHelperCreatesDatabaseCopy() throws Exception {
        Connection conn = Database.getInstance().getConnection();
        assertNotNull(conn);
        Path backup = com.schaccs.util.DatabaseBackupUtil.backupNow();
        assertTrue(Files.exists(backup));
        assertTrue(Files.size(backup) >= 0);
    }
}
