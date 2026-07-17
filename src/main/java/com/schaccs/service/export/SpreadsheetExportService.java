package com.schaccs.service.export;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class SpreadsheetExportService {

    public record SheetData(String name, List<String> headers, List<List<String>> rows) {}

    public void export(Path path, String sheetName, List<String> headers, List<List<String>> rows) throws IOException {
        String name = path.getFileName().toString().toLowerCase();
        if (name.endsWith(".csv")) {
            writeCsv(path, headers, rows);
            return;
        }
        if (name.endsWith(".xlsx")) {
            writeXlsx(path, sheetName, headers, rows);
            return;
        }
        throw new IOException("Unsupported export format. Use .csv or .xlsx.");
    }

    private void writeCsv(Path path, List<String> headers, List<List<String>> rows) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            writer.write(toCsv(headers));
            writer.newLine();
            for (List<String> row : rows) {
                writer.write(toCsv(row));
                writer.newLine();
            }
        }
    }

    public void exportWorkbook(Path path, List<SheetData> sheets) throws IOException {
        String name = path.getFileName().toString().toLowerCase();
        if (!name.endsWith(".xlsx")) {
            throw new IOException("Multi-sheet export requires a .xlsx file.");
        }
        try (Workbook workbook = new XSSFWorkbook()) {
            for (SheetData sheetData : sheets) {
                writeSheet(workbook, sheetData.name(), sheetData.headers(), sheetData.rows());
            }
            try (var out = Files.newOutputStream(path)) {
                workbook.write(out);
            }
        }
    }

    private void writeXlsx(Path path, String sheetName, List<String> headers, List<List<String>> rows) throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            writeSheet(workbook, sheetName, headers, rows);
            try (var out = Files.newOutputStream(path)) {
                workbook.write(out);
            }
        }
    }

    private void writeSheet(Workbook workbook, String sheetName, List<String> headers, List<List<String>> rows) {
        Sheet sheet = workbook.createSheet(sheetName == null || sheetName.isBlank() ? "Export" : sheetName);
        int rowIndex = 0;
        Row headerRow = sheet.createRow(rowIndex++);
        for (int i = 0; i < headers.size(); i++) {
            headerRow.createCell(i).setCellValue(headers.get(i));
        }
        for (List<String> row : rows) {
            Row sheetRow = sheet.createRow(rowIndex++);
            for (int i = 0; i < row.size(); i++) {
                sheetRow.createCell(i).setCellValue(row.get(i));
            }
        }
        for (int i = 0; i < headers.size(); i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private String toCsv(List<String> values) {
        return values.stream().map(this::escapeCsv).reduce((a, b) -> a + "," + b).orElse("");
    }

    private String escapeCsv(String value) {
        String safe = value == null ? "" : value;
        boolean needsQuotes = safe.contains(",") || safe.contains("\"") || safe.contains("\n") || safe.contains("\r");
        safe = safe.replace("\"", "\"\"");
        return needsQuotes ? "\"" + safe + "\"" : safe;
    }
}
