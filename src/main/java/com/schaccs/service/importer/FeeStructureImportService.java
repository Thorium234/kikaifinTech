package com.schaccs.service.importer;

import com.schaccs.config.CurrencyConfig;
import com.schaccs.enums.AcademicTerm;
import com.schaccs.enums.BoardingStatus;
import com.schaccs.model.fee.FeeStructureItem;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.BufferedReader;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Reads fee-structure CSV/XLSX files (Term, Code, Vote Head, Amount) into fee
 * structure items for a chosen boarding status.
 */
public class FeeStructureImportService {

    public List<Map<String, String>> parseFile(Path path) throws IOException {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".csv")) {
            return parseCsv(path);
        }
        if (name.endsWith(".xlsx")) {
            return parseXlsx(path);
        }
        throw new IllegalArgumentException("Unsupported file type. Use .csv or .xlsx.");
    }

    private List<Map<String, String>> parseCsv(Path path) throws IOException {
        List<Map<String, String>> rows = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String headerLine = reader.readLine();
            if (headerLine == null) {
                throw new IllegalArgumentException("The selected CSV file is empty.");
            }
            List<String> headers = splitCsv(headerLine);
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                List<String> values = splitCsv(line);
                Map<String, String> row = new HashMap<>();
                for (int i = 0; i < headers.size(); i++) {
                    String key = normalize(headers.get(i));
                    String value = i < values.size() ? values.get(i).trim() : "";
                    row.put(key, value);
                }
                rows.add(row);
            }
        }
        return rows;
    }

    private List<Map<String, String>> parseXlsx(Path path) throws IOException {
        List<Map<String, String>> rows = new ArrayList<>();
        DataFormatter formatter = new DataFormatter();
        try (Workbook workbook = new XSSFWorkbook(Files.newInputStream(path))) {
            Sheet sheet = workbook.getNumberOfSheets() > 0 ? workbook.getSheetAt(0) : null;
            if (sheet == null) {
                throw new IllegalArgumentException("The selected workbook has no sheets.");
            }
            Row headerRow = sheet.getRow(sheet.getFirstRowNum());
            if (headerRow == null) {
                throw new IllegalArgumentException("The selected workbook is empty.");
            }
            List<String> headers = new ArrayList<>();
            for (Cell cell : headerRow) {
                headers.add(formatter.formatCellValue(cell));
            }
            for (int i = sheet.getFirstRowNum() + 1; i <= sheet.getLastRowNum(); i++) {
                Row rowObj = sheet.getRow(i);
                if (rowObj == null) {
                    continue;
                }
                Map<String, String> row = new HashMap<>();
                boolean any = false;
                for (int j = 0; j < headers.size(); j++) {
                    Cell cell = rowObj.getCell(j, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                    String value = cell == null ? "" : formatter.formatCellValue(cell);
                    if (!value.isBlank()) {
                        any = true;
                    }
                    row.put(normalize(headers.get(j)), value.trim());
                }
                if (any) {
                    rows.add(row);
                }
            }
        }
        return rows;
    }

    /**
     * Convert parsed rows into fee structure items. Rows with an unrecognised
     * term, a missing code, or a non-numeric amount are skipped and reported.
     */
    public ImportResult parseItems(List<Map<String, String>> rows, BoardingStatus boardingStatus) {
        List<FeeStructureItem> items = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        int rowNumber = 2;
        for (Map<String, String> row : rows) {
            String termRaw = value(row, "term", "academicterm");
            String code = value(row, "code", "voteheadcode");
            String name = value(row, "votehead", "name", "voteheadname");
            String amountRaw = value(row, "amount", "fee", "charge");

            AcademicTerm term = parseTerm(termRaw);
            if (term == null) {
                warnings.add("Row " + rowNumber + ": term '" + termRaw + "' not recognised (use Term 1/2/3) — skipped.");
                rowNumber++;
                continue;
            }
            if (code.isBlank()) {
                warnings.add("Row " + rowNumber + ": missing code — skipped.");
                rowNumber++;
                continue;
            }
            BigDecimal amount = parseAmount(amountRaw);
            if (amount == null) {
                warnings.add("Row " + rowNumber + ": amount '" + amountRaw + "' is not a number — skipped.");
                rowNumber++;
                continue;
            }
            items.add(new FeeStructureItem(code, name.isBlank() ? code : name, term, boardingStatus, amount));
            rowNumber++;
        }
        return new ImportResult(items, warnings);
    }

    private AcademicTerm parseTerm(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String normalized = normalize(raw);
        if (normalized.equals("1") || normalized.equals("term1") || normalized.equals("firstterm")
                || normalized.equals("first")) {
            return AcademicTerm.TERM_1;
        }
        if (normalized.equals("2") || normalized.equals("term2") || normalized.equals("secondterm")
                || normalized.equals("second")) {
            return AcademicTerm.TERM_2;
        }
        if (normalized.equals("3") || normalized.equals("term3") || normalized.equals("thirdterm")
                || normalized.equals("third")) {
            return AcademicTerm.TERM_3;
        }
        return null;
    }

    private BigDecimal parseAmount(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            String cleaned = raw.replace(",", "").trim();
            BigDecimal value = new BigDecimal(cleaned);
            return value.compareTo(BigDecimal.ZERO) >= 0 ? CurrencyConfig.money(value) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private List<String> splitCsv(String line) {
        List<String> values = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (ch == ',' && !inQuotes) {
                values.add(current.toString());
                current.setLength(0);
            } else {
                current.append(ch);
            }
        }
        values.add(current.toString());
        return values;
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private String value(Map<String, String> row, String... keys) {
        for (String key : keys) {
            String v = row.get(key);
            if (v != null && !v.isBlank()) {
                return v.trim();
            }
        }
        return "";
    }

    public record ImportResult(List<FeeStructureItem> items, List<String> warnings) {
    }
}
