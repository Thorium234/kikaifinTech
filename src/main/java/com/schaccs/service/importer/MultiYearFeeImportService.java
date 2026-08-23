package com.schaccs.service.importer;

import com.schaccs.config.CurrencyConfig;
import com.schaccs.enums.AcademicTerm;
import com.schaccs.enums.BoardingStatus;
import com.schaccs.model.fee.FeeStructure;
import com.schaccs.model.fee.FeeStructureItem;
import com.schaccs.model.fee.StudentCategory;
import com.schaccs.service.school.AcademicCalendarService;
import com.schaccs.store.FeeStructureStore;
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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Parses a multi-year fee structure Excel/CSV file.
 * <p>Expected columns: Academic Year, Student Category, Votehead / Account,
 * Term 1 Fee, Term 2 Fee, Term 3 Fee, Total Annual.</p>
 * <p>For each unique (year, category) pair the engine creates a
 * {@link FeeStructure} with one {@link FeeStructureItem} per votehead. It also
 * auto-scaffolds missing academic calendars via
 * {@link AcademicCalendarService#ensureYearCalendar(int)}.</p>
 */
public class MultiYearFeeImportService {

    private final FeeStructureStore feeStore;
    private final AcademicCalendarService calendarService;

    public MultiYearFeeImportService() {
        this(FeeStructureStore.getInstance(), new AcademicCalendarService());
    }

    public MultiYearFeeImportService(FeeStructureStore feeStore, AcademicCalendarService calendarService) {
        this.feeStore = feeStore;
        this.calendarService = calendarService;
    }

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
                if (line.isBlank()) continue;
                List<String> values = splitCsv(line);
                Map<String, String> row = new HashMap<>();
                for (int i = 0; i < headers.size(); i++) {
                    row.put(normalize(headers.get(i)), i < values.size() ? values.get(i).trim() : "");
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
            if (sheet == null) throw new IllegalArgumentException("The selected workbook has no sheets.");
            Row headerRow = sheet.getRow(sheet.getFirstRowNum());
            if (headerRow == null) throw new IllegalArgumentException("The selected workbook is empty.");
            List<String> headers = new ArrayList<>();
            for (Cell cell : headerRow) {
                headers.add(formatter.formatCellValue(cell));
            }
            for (int i = sheet.getFirstRowNum() + 1; i <= sheet.getLastRowNum(); i++) {
                Row rowObj = sheet.getRow(i);
                if (rowObj == null) continue;
                Map<String, String> row = new HashMap<>();
                boolean any = false;
                for (int j = 0; j < headers.size(); j++) {
                    Cell cell = rowObj.getCell(j, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                    String value = cell == null ? "" : formatter.formatCellValue(cell);
                    if (!value.isBlank()) any = true;
                    row.put(normalize(headers.get(j)), value.trim());
                }
                if (any) rows.add(row);
            }
        }
        return rows;
    }

    /**
     * Parse rows into structured fee structures grouped by (year, category).
     * Scaffolds missing academic calendars. Returns an import result with
     * created structures and any warnings.
     */
    public MultiImportResult parseAndBuild(List<Map<String, String>> rows) {
        List<FeeStructure> structures = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        Map<String, FeeStructure> structureKeyMap = new HashMap<>();

        int rowNum = 2;
        for (Map<String, String> row : rows) {
            String yearRaw = value(row, "academicyear", "year", "academic_year");
            String categoryRaw = value(row, "studentcategory", "studentcategory", "category",
                    "boardingstatus", "boarding_status", "type");
            String voteheadCode = value(row, "code", "voteheadcode", "accountcode", "votehead_code");
            String voteheadName = value(row, "votehead", "name", "voteheadname",
                    "voteheadname", "account", "votehead_name", "account_name");
            String t1Raw = value(row, "term1fee", "term1", "term_1_fee", "term1fee",
                    "term1amount", "term1_amount");
            String t2Raw = value(row, "term2fee", "term2", "term_2_fee", "term2fee",
                    "term2amount", "term2_amount");
            String t3Raw = value(row, "term3fee", "term3", "term_3_fee", "term3fee",
                    "term3amount", "term3_amount");

            int year;
            try {
                year = Integer.parseInt(yearRaw.replaceAll("[^0-9]", "").trim());
            } catch (Exception e) {
                warnings.add("Row " + rowNum + ": invalid year '" + yearRaw + "' — skipped.");
                rowNum++;
                continue;
            }

            String normalizedCategory = normalizeCategory(categoryRaw);
            if (normalizedCategory == null) {
                warnings.add("Row " + rowNum + ": unrecognized category '" + categoryRaw
                        + "' (use Boarding or Day) — skipped.");
                rowNum++;
                continue;
            }

            if (voteheadCode.isBlank() && voteheadName.isBlank()) {
                warnings.add("Row " + rowNum + ": missing votehead code/name — skipped.");
                rowNum++;
                continue;
            }

            if (voteheadCode.isBlank()) {
                voteheadCode = feeStore.findVoteheadByCode(voteheadName)
                        .map(v -> v.getCode())
                        .orElse(normalize(voteheadName).toUpperCase(Locale.ROOT));
            }

            if (voteheadName.isBlank()) {
                voteheadName = feeStore.voteheadName(voteheadCode);
                if (voteheadName.equals(voteheadCode)) {
                    voteheadName = voteheadCode;
                }
            }

            BigDecimal t1 = parseAmount(t1Raw);
            BigDecimal t2 = parseAmount(t2Raw);
            BigDecimal t3 = parseAmount(t3Raw);
            if (t1 == null) t1 = CurrencyConfig.zero();
            if (t2 == null) t2 = CurrencyConfig.zero();
            if (t3 == null) t3 = CurrencyConfig.zero();

            String structureKey = year + "|" + normalizedCategory;
            FeeStructure structure = structureKeyMap.computeIfAbsent(structureKey, k -> {
                BoardingStatus boardingStatus = "BOARDING".equals(normalizedCategory)
                        ? BoardingStatus.BOARDING : BoardingStatus.DAY;
                FeeStructure fs = new FeeStructure(year, "ALL", boardingStatus,
                        normalizedCategory + " Fee Structure " + year);

                StudentCategory cat = feeStore.findCategoryByName(normalizedCategory)
                        .orElseGet(() -> {
                            int catId = feeStore.getCategories().stream()
                                    .mapToInt(StudentCategory::getId).max().orElse(0) + 1;
                            StudentCategory newCat = new StudentCategory(catId, normalizedCategory);
                            feeStore.addCategory(newCat);
                            return newCat;
                        });
                fs.setCategoryId(cat.getId());
                fs.setCreatedAt(LocalDateTime.now());
                return fs;
            });

            String finalVoteheadCode = voteheadCode;
            boolean alreadyExists = structure.getItems().stream()
                    .anyMatch(i -> i.getVoteheadCode().equalsIgnoreCase(finalVoteheadCode));
            if (!alreadyExists) {
                structure.addItem(new FeeStructureItem(voteheadCode, voteheadName,
                        structure.getBoardingStatus(), t1, t2, t3));
            } else {
                warnings.add("Row " + rowNum + ": duplicate votehead '" + voteheadCode
                        + "' for year " + year + " / " + normalizedCategory + " — skipped.");
            }
            rowNum++;
        }

        for (FeeStructure fs : structureKeyMap.values()) {
            feeStore.addStructure(fs);
            calendarService.ensureYearCalendar(fs.getAcademicYear());
        }

        structures.addAll(structureKeyMap.values());
        return new MultiImportResult(structures, warnings);
    }

    private String normalizeCategory(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String n = normalize(raw);
        if (n.contains("boarding") || n.equals("b") || n.equals("boardingstudent")) {
            return "BOARDING";
        }
        if (n.contains("day") || n.equals("d") || n.equals("daystudent")) {
            return "DAY";
        }
        return null;
    }

    private BigDecimal parseAmount(String raw) {
        if (raw == null || raw.isBlank()) return null;
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
            if (v != null && !v.isBlank()) return v.trim();
        }
        return "";
    }

    public record MultiImportResult(List<FeeStructure> structures, List<String> warnings) {
    }
}
