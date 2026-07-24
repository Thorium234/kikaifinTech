package com.schaccs.service;

import com.schaccs.service.export.SpreadsheetExportService;
import com.schaccs.service.export.StudentTemplateService;
import com.schaccs.service.fee.FeeCalculationService;
import com.schaccs.service.importer.StudentImportService;
import com.schaccs.store.StudentStore;
import com.schaccs.repository.PersistenceService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ImportExportCoreTest {

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
