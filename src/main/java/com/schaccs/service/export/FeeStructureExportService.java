package com.schaccs.service.export;

import com.schaccs.enums.AcademicTerm;
import com.schaccs.model.fee.FeeStructure;
import com.schaccs.model.fee.FeeStructureItem;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Exports fee structures to CSV/XLSX (and generates a blank fill-in template).
 * Header layout: Term, Code, Vote Head, Amount — the same layout the
 * {@link com.schaccs.service.importer.FeeStructureImportService} reads back.
 */
public class FeeStructureExportService {

    private static final List<String> HEADERS = List.of("Term", "Code", "Vote Head", "Amount");

    private static final List<String> MULTI_YEAR_HEADERS = List.of(
            "Academic Year", "Student Category", "Votehead / Account",
            "Term 1 Fee", "Term 2 Fee", "Term 3 Fee", "Total Annual");

    private final SpreadsheetExportService exportService;

    public FeeStructureExportService() {
        this(new SpreadsheetExportService());
    }

    public FeeStructureExportService(SpreadsheetExportService exportService) {
        this.exportService = exportService;
    }

    public void exportStructure(Path path, FeeStructure structure) throws IOException {
        exportService.export(path, sheetName(structure), HEADERS, rowsFor(structure));
    }

    /**
     * Export several structures. For .xlsx each structure becomes its own sheet;
     * a single structure is written as a plain single-sheet export.
     */
    public void exportStructures(Path path, List<FeeStructure> structures) throws IOException {
        if (structures.size() == 1) {
            exportStructure(path, structures.get(0));
            return;
        }
        List<SpreadsheetExportService.SheetData> sheets = structures.stream()
                .map(s -> new SpreadsheetExportService.SheetData(sheetName(s), HEADERS, rowsFor(s)))
                .toList();
        exportService.exportWorkbook(path, sheets);
    }

    public void generateTemplate(Path path) throws IOException {
        exportService.export(path, "Fee Structure Template", HEADERS, List.of(
                List.of("Term 1", "BOARD", "Boarding", "5000"),
                List.of("Term 2", "BOARD", "Boarding", "5000"),
                List.of("Term 3", "BOARD", "Boarding", "4500")));
    }

    private String sheetName(FeeStructure structure) {
        String base = structure.getName() == null ? "Fee Structure" : structure.getName();
        return base.length() > 28 ? base.substring(0, 28) : base;
    }

    private List<List<String>> rowsFor(FeeStructure structure) {
        return structure.getItems().stream()
                .map(this::rowFor)
                .toList();
    }

    private List<String> rowFor(FeeStructureItem item) {
        return List.of(
                item.getTerm() != null ? item.getTerm().getDisplayName() : "",
                safe(item.getVoteheadCode()),
                safe(item.getVoteheadName()),
                item.getAmount() != null ? item.getAmount().toPlainString() : "");
    }

    public void exportMultiYear(Path path, List<FeeStructure> structures) throws IOException {
        List<List<String>> rows = new java.util.ArrayList<>();
        for (FeeStructure s : structures) {
            String category = s.getBoardingStatus() != null
                    ? s.getBoardingStatus().getDisplayName() : "";
            for (FeeStructureItem item : s.getItems()) {
                rows.add(List.of(
                        String.valueOf(s.getAcademicYear()),
                        category,
                        safe(item.getVoteheadCode()),
                        item.getTerm1Amount().toPlainString(),
                        item.getTerm2Amount().toPlainString(),
                        item.getTerm3Amount().toPlainString(),
                        item.annualTotal().toPlainString()));
            }
        }
        exportService.export(path, "Multi-Year Fee Matrix", MULTI_YEAR_HEADERS, rows);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
