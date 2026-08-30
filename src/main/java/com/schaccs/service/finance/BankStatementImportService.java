package com.schaccs.service.finance;

import com.schaccs.config.CurrencyConfig;
import com.schaccs.model.finance.BankStatementEntry;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses an exported National Bank statement into {@link BankStatementEntry}
 * rows for the reconciliation workflow. Supports the two formats the Bursar
 * can download: .xlsx (Apache POI) and .csv (manual).
 *
 * <p>Column layout is detected heuristically: {@code date}, {@code narration},
 * {@code reference}, {@code withdrawal} (money out), {@code deposit} (money in)
 * and {@code balance}. A header row of non-parsable text is skipped.</p>
 */
public class BankStatementImportService {

    private static final DataFormatter FORMATTER = new DataFormatter();
    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("d/M/yyyy"),
            DateTimeFormatter.ISO_LOCAL_DATE);

    public List<BankStatementEntry> importFile(Path path) throws IOException {
        String name = path.getFileName().toString().toLowerCase();
        if (name.endsWith(".csv")) {
            List<String[]> grid = readCsv(path);
            return parseGrid(grid);
        }
        return readExcel(path);
    }

    private List<BankStatementEntry> readExcel(Path path) throws IOException {
        List<String[]> grid = new ArrayList<>();
        try (var wb = new XSSFWorkbook(Files.newInputStream(path))) {
            Sheet sheet = wb.getSheetAt(0);
            for (Row row : sheet) {
                if (row == null) continue;
                List<String> cells = new ArrayList<>();
                for (int c = 0; c < row.getLastCellNum(); c++) {
                    Cell cell = row.getCell(c);
                    cells.add(cell == null ? "" : FORMATTER.formatCellValue(cell).trim());
                }
                grid.add(cells.toArray(new String[0]));
            }
        }
        return parseGrid(grid);
    }

    private List<BankStatementEntry> parseGrid(List<String[]> grid) {
        List<BankStatementEntry> out = new ArrayList<>();
        int rows = 0;
        for (String[] row : grid) {
            List<String> values = new ArrayList<>();
            for (String v : row) values.add(v == null ? "" : v.trim());
            while (!values.isEmpty() && values.get(values.size() - 1).isEmpty()) {
                values.remove(values.size() - 1);
            }
            if (values.isEmpty()) continue;

            LocalDate date = parseDate(get(values, 0));
            if (date == null) {
                continue; // header row or total/summary line
            }
            String narration = get(values, 1);
            String reference = get(values, 2);
            BigDecimal withdrawal = moneyOrZero(get(values, 3));
            BigDecimal deposit = moneyOrZero(get(values, 4));
            BigDecimal balance = moneyOrNull(get(values, 5));

            BankStatementEntry e = new BankStatementEntry();
            e.setStatementDate(date);
            e.setDescription(narration);
            e.setReference(reference);
            e.setDebit(withdrawal);
            e.setCredit(deposit);
            if (balance != null) e.setBalance(balance);
            out.add(e);
            rows++;
        }
        return out;
    }

    private BigDecimal moneyOrZero(String s) {
        BigDecimal v = moneyOrNull(s);
        return v == null ? CurrencyConfig.zero() : v;
    }

    private BigDecimal moneyOrNull(String s) {
        if (s == null || s.isBlank()) return null;
        String cleaned = s.replaceAll("[^0-9.\\-]", "");
        if (cleaned.isEmpty() || "-".equals(cleaned) || ".".equals(cleaned)) return null;
        try {
            return new BigDecimal(cleaned);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private LocalDate parseDate(String s) {
        if (s == null || s.isBlank()) return null;
        String clean = s.trim();
        for (DateTimeFormatter fmt : DATE_FORMATS) {
            try {
                return LocalDate.parse(clean, fmt);
            } catch (Exception ignored) {
                // try next format
            }
        }
        return null;
    }

    private List<String[]> readCsv(Path path) throws IOException {
        List<String[]> rows = new ArrayList<>();
        for (String line : Files.readAllLines(path)) {
            if (line.isBlank()) continue;
            rows.add(splitCsv(line));
        }
        return rows;
    }

    private String[] splitCsv(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                out.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        out.add(cur.toString());
        return out.toArray(new String[0]);
    }

    private static String get(List<String> row, int i) {
        return i < row.size() ? row.get(i) : "";
    }
}
