package com.schaccs.service.export;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public class StudentTemplateService {

    private final SpreadsheetExportService exportService;

    public StudentTemplateService() {
        this(new SpreadsheetExportService());
    }

    public StudentTemplateService(SpreadsheetExportService exportService) {
        this.exportService = exportService;
    }

    public void generateTemplate(Path path) throws IOException {
        List<String> headers = List.of(
                "Admission Number",
                "Full Name",
                "Gender",
                "Form Class",
                "Stream",
                "Boarding Status",
                "Parent Name",
                "Guardian Phone",
                "Guardian ID",
                "Guardian Key",
                "Phone",
                "UPI",
                "Academic Year",
                "Year Of Admission",
                "Student Status"
        );
        List<List<String>> rows = List.of(
                List.of("2026/001", "John Doe", "Male", "Form 1", "A", "Boarding",
                        "Jane Doe", "0712345678", "ID123456", "FAM-001", "0712345678", "UPI001", "2026", "2026", "Active")
        );
        exportService.export(path, "Students Template", headers, rows);
    }
}
