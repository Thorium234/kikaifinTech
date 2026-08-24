package com.schaccs.service.export;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Generates the official fees-balance import template. Unlike the legacy
 * "FEES BALANCE AS AT" school workbooks (whose layout varies per sheet and is
 * read heuristically), this template has one fixed table that the importer
 * binds to strictly: BALANCE is exactly what the student still owes today.
 */
public class FeesBalanceTemplateService {

    private final SpreadsheetExportService exportService;

    public FeesBalanceTemplateService() {
        this(new SpreadsheetExportService());
    }

    public FeesBalanceTemplateService(SpreadsheetExportService exportService) {
        this.exportService = exportService;
    }

    public void generateTemplate(Path path) throws IOException {
        List<String> headers = List.of(
                "Adm No",
                "Name",
                "Class",
                "Stream",
                "Boarding",
                "Balance"
        );
        List<List<String>> rows = List.of(
                List.of("2026/001", "John Doe", "Grade 10", "A", "B", "9000"),
                List.of("2026/002", "Mary Wanjiku", "Form 3", "East", "D", "12500")
        );
        exportService.export(path, "Fees Balance", headers, rows);
    }
}
