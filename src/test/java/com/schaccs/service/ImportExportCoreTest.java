package com.schaccs.service;

import com.schaccs.config.AppConfig;
import com.schaccs.config.CurrencyConfig;
import com.schaccs.enums.AcademicTerm;
import com.schaccs.enums.AccountType;
import com.schaccs.enums.BoardingStatus;
import com.schaccs.model.fee.FeeStructure;
import com.schaccs.model.fee.FeeStructureItem;
import com.schaccs.model.finance.Votehead;
import com.schaccs.model.student.StudentFeeLedger;
import com.schaccs.service.export.SpreadsheetExportService;
import com.schaccs.service.export.StudentTemplateService;
import com.schaccs.service.fee.FeeCalculationService;
import com.schaccs.service.importer.StudentImportService;
import com.schaccs.store.FeeStructureStore;
import com.schaccs.store.StudentStore;
import com.schaccs.repository.PersistenceService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ImportExportCoreTest {

    @BeforeEach
    void setUp() {
        PersistenceService.getInstance().clearAll();
        FeeStructureStore.getInstance().addVotehead(new Votehead("BOARD", "Boarding", AccountType.SCHOOL_FUND, 1));
        FeeStructure structure = new FeeStructure(AppConfig.getInstance().getAcademicYear(),
                "Form 2", BoardingStatus.BOARDING, "Import Test");
        structure.addItem(new FeeStructureItem("BOARD", "Boarding", AcademicTerm.TERM_1,
                BoardingStatus.BOARDING, CurrencyConfig.money("5000")));
        structure.addItem(new FeeStructureItem("BOARD", "Boarding", AcademicTerm.TERM_2,
                BoardingStatus.BOARDING, CurrencyConfig.money("4000")));
        FeeStructureStore.getInstance().addStructure(structure);
    }

    @AfterEach
    void tearDown() {
        PersistenceService.getInstance().clearAll();
    }

    @Test
    void studentImportRowsAddsStudents() {
        StudentImportService importService = new StudentImportService(new com.schaccs.service.student.StudentService(),
                new FeeCalculationService());
        String uniqueAdmission = "TEST-IMPORT-" + UUID.randomUUID();
        Map<String, String> row = new HashMap<>();
        row.put("admissionnumber", uniqueAdmission);
        row.put("fullname", "Imported Student");
        row.put("formclass", "Form 2");
        row.put("boardingstatus", "Day");

        StudentImportService.ImportResult result = importService.importRows(List.of(row));

        assertEquals(1, result.getImported());
        assertEquals(0, result.getSkipped());
        assertTrue(StudentStore.getInstance().findByAdmissionNumber(uniqueAdmission).isPresent());
    }

    @Test
    void studentImportChargesCurrentTermFeesConsistentlyWithManualAdd() {
        StudentImportService importService = new StudentImportService(new com.schaccs.service.student.StudentService(),
                new FeeCalculationService());
        String uniqueAdmission = "TEST-IMPORT-FEE-" + UUID.randomUUID();
        Map<String, String> row = new HashMap<>();
        row.put("admissionnumber", uniqueAdmission);
        row.put("fullname", "Imported Fee Student");
        row.put("formclass", "Form 2");
        row.put("boardingstatus", "Boarding");

        StudentImportService.ImportResult result = importService.importRows(List.of(row));

        assertEquals(1, result.getImported());
        StudentStore.getInstance().findByAdmissionNumber(uniqueAdmission).ifPresent(s -> {
            StudentFeeLedger ledger = StudentStore.getInstance().getLedger(s.getId());
            assertEquals(CurrencyConfig.money("5000"), ledger.getCharged("BOARD"),
                    "Imported student must be charged only the current term (TERM_1 = 5000), "
                            + "not the full annual total (9000), to match manual student add");
        });
    }

    @Test
    void exportServiceWritesCsvAndTemplate() throws Exception {
        SpreadsheetExportService exportService = new SpreadsheetExportService();
        Path csv = Files.createTempFile("students-export", ".csv");
        exportService.export(csv, "Students", List.of("A", "B"), List.of(List.of("1", "2")));
        String content = Files.readString(csv);
        assertTrue(content.contains("A,B"));
        assertTrue(content.contains("1,2"));

        Path template = Files.createTempFile("student-template", ".xlsx");
        new StudentTemplateService(exportService).generateTemplate(template);
        assertTrue(Files.size(template) > 0);
    }
}
