package com.schaccs.service.importer;

import com.schaccs.config.AppConfig;
import com.schaccs.config.CurrencyConfig;
import com.schaccs.enums.BoardingStatus;
import com.schaccs.enums.StudentStatus;
import com.schaccs.model.student.Student;
import com.schaccs.model.student.StudentFeeLedger;
import com.schaccs.repository.PersistenceService;
import com.schaccs.service.audit.AuditService;
import com.schaccs.store.StudentStore;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses a "FEES BALANCE AS AT …" workbook produced by the school. The workbook
 * has one sheet per class/stream and the layout varies between sheets (single
 * name column, names split across two columns, boarding marker columns that are
 * sometimes labelled, extra columns like PENALTY, two tables side by side on one
 * sheet, totals rows, title rows, ...). This service heuristically locates each
 * table's header and column mapping, stages every student row for review, and
 * then applies the staged rows: unmatched students are created in the registry
 * and each student's fee ledger gets the opening balance (arrears, or advance
 * when the balance is a credit). The source file is never modified.
 */
public class FeesBalanceImportService {

    private static final Pattern FORM_TITLE = Pattern.compile(
            "(?i)FORM\\s+(ONE|TWO|THREE|FOUR|1|2|3|4)(?:\\s+([A-Z]+))?");
    private static final Pattern SHEET_NAME = Pattern.compile("(?i)^F(\\d)([A-Z]*)(\\d*)$");

    private final StudentStore studentStore;
    private final AuditService auditService;

    public FeesBalanceImportService() {
        this(StudentStore.getInstance(), new AuditService());
    }

    public FeesBalanceImportService(StudentStore studentStore, AuditService auditService) {
        this.studentStore = studentStore;
        this.auditService = auditService;
    }

    /**
     * Parse the whole workbook into staged rows (one per student entry found on
     * any sheet). No validation is performed here — callers review via
     * {@link #scrutinize(List)} before applying.
     */
    public List<FeesBalanceRow> parseWorkbook(Path path) {
        try (Workbook workbook = new XSSFWorkbook(Files.newInputStream(path))) {
            return parseWorkbook(workbook);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read the workbook: " + e.getMessage(), e);
        }
    }

    public List<FeesBalanceRow> parseWorkbook(Workbook workbook) {
        List<FeesBalanceRow> rows = new ArrayList<>();
        DataFormatter formatter = new DataFormatter();
        formatter.setUseCachedValuesForFormulaCells(true);
        for (int s = 0; s < workbook.getNumberOfSheets(); s++) {
            Sheet sheet = workbook.getSheetAt(s);
            List<FeesBalanceRow> sheetRows = parseSheet(sheet, formatter);
            if (sheetRows.isEmpty() && hasContent(sheet, formatter)) {
                rows.add(skippedRow(sheet.getSheetName(), "No student table found (sheet skipped)"));
            } else {
                rows.addAll(sheetRows);
            }
        }
        return rows;
    }

    /**
     * Cross-check every staged row against the student registry and the numbers
     * on the sheet: match status, missing details, inconsistent totals, penalty
     * and credit balances. Populates each row's status and warnings.
     */
    public void scrutinize(List<FeesBalanceRow> rows) {
        Map<String, FeesBalanceRow> seenAdm = new HashMap<>();
        for (FeesBalanceRow row : rows) {
            List<String> warnings = row.getWarnings();
            warnings.clear();
            if (row.getName() == null || row.getName().isBlank()) {
                warnings.add("No name");
            }
            if (row.getAdmissionNumber() == null || row.getAdmissionNumber().isBlank()) {
                warnings.add("No admission number");
            }
            if (row.getFormClass() == null || row.getFormClass().isBlank()) {
                warnings.add("Class not inferred - edit Form column");
            }
            checkTotals(row, warnings);
            if (row.getPenalty().signum() > 0) {
                warnings.add("PENALTY " + format(row.getPenalty()) + " is added to the balance");
            }
            if (row.getTotalFees().signum() < 0) {
                warnings.add("Credit balance - imported as advance");
            }
            if (row.getBalance().signum() == 0
                    && row.getCurrentFees().signum() == 0
                    && row.getArrears().signum() == 0
                    && row.getPenalty().signum() == 0) {
                warnings.add("Zero balance");
            }
            String admKey = row.getAdmissionNumber() == null ? "" : row.getAdmissionNumber().trim();
            if (!admKey.isEmpty()) {
                FeesBalanceRow first = seenAdm.putIfAbsent(admKey, row);
                if (first != null) {
                    warnings.add("Duplicate admission number in file (also row " + first.getRowNumber()
                            + " on " + first.getSheetName() + ")");
                }
            }
            Student existing = findExisting(row);
            if (existing != null) {
                row.setMatchStatus("Existing");
                boolean admMatch = row.getAdmissionNumber() != null
                        && row.getAdmissionNumber().equalsIgnoreCase(existing.getAdmissionNumber());
                if (!admMatch) {
                    warnings.add("Matched existing student by name (admission differs)");
                }
            } else {
                row.setMatchStatus("New");
            }
        }
    }

    /**
     * Apply the staged rows: create unmatched students in the registry and write
     * each included student's opening balance into their fee ledger (T/FEES plus
     * penalty as arrears; a credit balance becomes advance). Existing students
     * keep their registry details and only get the balance updated.
     */
    public ApplyResult apply(List<FeesBalanceRow> rows) {
        int created = 0;
        int existing = 0;
        int skipped = 0;
        int credits = 0;
        List<String> warnings = new ArrayList<>();
        int year = AppConfig.getInstance().getAcademicYear();
        for (FeesBalanceRow row : rows) {
            if (!row.isInclude()) {
                skipped++;
                continue;
            }
            if (row.getName() == null || row.getName().isBlank()) {
                warnings.add("Skipped " + label(row) + ": no name");
                skipped++;
                continue;
            }
            Student student = findExisting(row);
            if (student == null) {
                student = new Student();
                student.setAdmissionNumber(row.getAdmissionNumber());
                student.setName(row.getName());
                student.setFormClass(row.getFormClass());
                student.setStream(row.getStream());
                student.setBoardingStatus(row.getBoardingStatus() == null
                        ? BoardingStatus.BOARDING : row.getBoardingStatus());
                student.setGender("Male");
                student.setStatus(StudentStatus.ACTIVE);
                student.setAcademicYear(year);
                student.setYearOfAdmission(year);
                try {
                    studentStore.add(student);
                    created++;
                } catch (IllegalArgumentException e) {
                    warnings.add("Skipped " + label(row) + ": " + e.getMessage());
                    skipped++;
                    continue;
                }
            } else {
                existing++;
            }
            applyBalance(student, row);
            if (row.getBalance().signum() < 0) {
                credits++;
            }
        }
        PersistenceService.getInstance().saveAll();
        auditService.log("FEES_BALANCE_IMPORT", "System", "ALL",
                "{\"created\":" + created + ",\"existing\":" + existing + ",\"skipped\":" + skipped + "}");
        return new ApplyResult(created, existing, skipped, credits, warnings);
    }

    // ------------------------------------------------------------------ parse

    private List<FeesBalanceRow> parseSheet(Sheet sheet, DataFormatter formatter) {
        List<FeesBalanceRow> rows = new ArrayList<>();
        int lastRow = sheet.getLastRowNum();
        List<HeaderTable> tables = findHeaderTables(sheet, formatter, lastRow);
        if (tables.isEmpty()) {
            return rows;
        }
        for (HeaderTable table : tables) {
            String[] inferred = inferClassAndStream(sheet, table.nameCol);
            boolean markerColumn = sheetUsesBoardingMarkers(sheet, table, formatter, lastRow);
            for (int r = table.rowIndex + 1; r <= lastRow; r++) {
                Row row = sheet.getRow(r);
                if (row == null) {
                    continue;
                }
                parseDataRow(sheet.getSheetName(), row, table, inferred, markerColumn, formatter, rows);
            }
        }
        return rows;
    }

    private List<HeaderTable> findHeaderTables(Sheet sheet, DataFormatter formatter, int lastRow) {
        List<HeaderTable> tables = new ArrayList<>();
        int scanMax = Math.min(lastRow, 20);
        for (int r = 0; r <= scanMax; r++) {
            Row row = sheet.getRow(r);
            if (row == null) {
                continue;
            }
            Map<Integer, String> headers = new HashMap<>();
            boolean hasName = false;
            boolean hasAdm = false;
            int nameCol = -1;
            int lastCell = row.getLastCellNum();
            for (int c = 0; c <= lastCell; c++) {
                String normalized = normalize(cellText(row, c, formatter));
                if (normalized.isEmpty()) {
                    continue;
                }
                headers.put(c, normalized);
                if (normalized.contains("name")) {
                    hasName = true;
                    if (nameCol == -1) {
                        nameCol = c;
                    }
                }
                if (normalized.contains("adm")) {
                    hasAdm = true;
                }
            }
            if (!hasName || !hasAdm || nameCol < 0) {
                continue;
            }
            HeaderTable table = new HeaderTable(r, nameCol, headers);
            for (Map.Entry<Integer, String> e : headers.entrySet()) {
                classify(e.getKey(), e.getValue(), table);
            }
            tables.add(table);
        }
        return tables;
    }

    private void classify(int col, String header, HeaderTable table) {
        if (header.contains("adm")) {
            table.admCol = col;
        } else if (header.equals("b") || header.equals("bd") || header.equals("db") || header.contains("board")) {
            table.boardingCol = col;
        } else if (header.contains("cfees") || (header.contains("fees") && header.contains("current"))) {
            table.cfeesCol = col;
        } else if (header.contains("arrear")) {
            table.arrearsCol = col;
        } else if (header.contains("tfees") || header.contains("totalfees")) {
            table.tfeesCol = col;
        } else if (header.contains("penalt")) {
            table.penaltyCol = col;
        } else if (header.equals("amount")) {
            table.tfeesCol = col;
        }
    }

    private void parseDataRow(String sheetName, Row row, HeaderTable table, String[] inferred,
                              boolean markerColumn, DataFormatter formatter, List<FeesBalanceRow> rows) {
        String name = readName(row, table, formatter);
        if (name.isBlank() || looksLikeTotal(name)) {
            return;
        }
        FeesBalanceRow out = new FeesBalanceRow(sheetName, row.getRowNum() + 1);
        out.setName(name);
        out.setFormClass(inferred[0]);
        out.setStream(inferred[1]);
        if (table.admCol >= 0) {
            out.setAdmissionNumber(cellText(row, table.admCol, formatter));
        }
        BoardingStatus boarding = readBoarding(row, table, markerColumn, formatter);
        if (boarding != null) {
            out.setBoardingStatus(boarding);
        } else if (markerColumn) {
            out.setBoardingStatus(BoardingStatus.DAY);
        }
        if (table.cfeesCol >= 0 || table.arrearsCol >= 0) {
            out.setHasBreakdown(true);
        }
        if (table.cfeesCol >= 0) {
            out.setCurrentFees(amount(row, table.cfeesCol, formatter));
        }
        if (table.arrearsCol >= 0) {
            out.setArrears(amount(row, table.arrearsCol, formatter));
        }
        if (table.tfeesCol >= 0) {
            out.setTotalFees(amount(row, table.tfeesCol, formatter));
        }
        if (table.penaltyCol >= 0) {
            out.setPenalty(amount(row, table.penaltyCol, formatter));
        }
        rows.add(out);
    }

    /**
     * Build the student name from the name column, expanding into neighbouring
     * cells when the sheet splits names across columns (e.g. FELIX | KIMTAI) or
     * leaves the header blank next to the NAMES column.
     */
    private String readName(Row row, HeaderTable table, DataFormatter formatter) {
        int nc = table.nameCol;
        StringBuilder sb = new StringBuilder();
        int c = nc - 1;
        while (c >= 0) {
            if (table.headers.containsKey(c)) {
                break;
            }
            String v = cellText(row, c, formatter);
            if (!isNameToken(v)) {
                break;
            }
            sb.insert(0, v + " ");
            c--;
        }
        String core = cellText(row, nc, formatter);
        if (!isNameToken(core)) {
            return sb.toString().trim();
        }
        sb.append(core);
        c = nc + 1;
        while (c <= row.getLastCellNum()) {
            if (table.headers.containsKey(c)) {
                break;
            }
            String v = cellText(row, c, formatter);
            if (!isNameToken(v) || isMarker(v)) {
                break;
            }
            sb.append(" ").append(v);
            c++;
        }
        return sb.toString().trim();
    }

    private BoardingStatus readBoarding(Row row, HeaderTable table, boolean markerColumn, DataFormatter formatter) {
        if (table.boardingCol >= 0) {
            return parseBoardingMarker(cellText(row, table.boardingCol, formatter));
        }
        if (!markerColumn) {
            return null;
        }
        int end = Math.min(table.nameCol + 5, row.getLastCellNum());
        for (int c = table.nameCol + 1; c <= end; c++) {
            BoardingStatus status = parseBoardingMarker(cellText(row, c, formatter));
            if (status != null) {
                return status;
            }
        }
        return null;
    }

    /**
     * Detect whether a sheet uses a boarding marker column (labelled or unlabelled
     * B/D values near the names). Sheets with markers treat a missing marker as a
     * Day student; sheets without one default everyone to Boarding.
     */
    private boolean sheetUsesBoardingMarkers(Sheet sheet, HeaderTable table, DataFormatter formatter, int lastRow) {
        if (table.boardingCol >= 0) {
            return true;
        }
        int scanned = 0;
        for (int r = table.rowIndex + 1; r <= lastRow && scanned < 40; r++, scanned++) {
            Row row = sheet.getRow(r);
            if (row == null) {
                continue;
            }
            int end = Math.min(table.nameCol + 5, row.getLastCellNum());
            for (int c = table.nameCol + 1; c <= end; c++) {
                if (parseBoardingMarker(cellText(row, c, formatter)) != null) {
                    return true;
                }
            }
        }
        return false;
    }

    private String[] inferClassAndStream(Sheet sheet, int nameCol) {
        String name = sheet.getSheetName().trim();
        String[] fromSheet = parseFormText(name);
        if (fromSheet != null) {
            return fromSheet;
        }
        String[] fromTitle = parseFormText(titleNearTable(sheet, nameCol));
        if (fromTitle != null) {
            return fromTitle;
        }
        if (!name.isEmpty() && Character.isLetter(name.charAt(0))) {
            return new String[]{"", String.valueOf(Character.toUpperCase(name.charAt(0)))};
        }
        return new String[]{"", ""};
    }

    private String titleNearTable(Sheet sheet, int nameCol) {
        DataFormatter formatter = new DataFormatter();
        int max = Math.min(sheet.getLastRowNum(), 5);
        for (int r = 0; r <= max; r++) {
            Row row = sheet.getRow(r);
            if (row == null) {
                continue;
            }
            for (int c = 0; c <= row.getLastCellNum(); c++) {
                String v = cellText(row, c, formatter).trim();
                if (c == nameCol && isFormTitle(v)) {
                    return v;
                }
            }
        }
        for (int r = 0; r <= max; r++) {
            Row row = sheet.getRow(r);
            if (row == null) {
                continue;
            }
            for (int c = 0; c <= row.getLastCellNum(); c++) {
                String v = cellText(row, c, formatter).trim();
                if (c >= nameCol - 3 && c <= nameCol + 3 && isFormTitle(v)) {
                    return v;
                }
            }
        }
        return "";
    }

    private boolean isFormTitle(String value) {
        return !value.isEmpty() && value.matches("(?i).*\\bFORM\\s+(ONE|TWO|THREE|FOUR|1|2|3|4)\\b.*");
    }

    private String[] parseFormText(String text) {
        if (text == null) {
            return null;
        }
        Matcher m = FORM_TITLE.matcher(text);
        if (m.find()) {
            String form = formWordToClass(m.group(1));
            String stream = m.group(2) == null ? "" : m.group(2).trim();
            return new String[]{form, stream};
        }
        Matcher sheet = SHEET_NAME.matcher(text);
        if (sheet.matches()) {
            String form = "Form " + sheet.group(1);
            String stream = sheet.group(2) == null ? "" : sheet.group(2).trim();
            return new String[]{form, stream};
        }
        return null;
    }

    private String formWordToClass(String token) {
        switch (token.toLowerCase(Locale.ROOT)) {
            case "one":
            case "1":
                return "Form 1";
            case "two":
            case "2":
                return "Form 2";
            case "three":
            case "3":
                return "Form 3";
            case "four":
            case "4":
                return "Form 4";
            default:
                return "Form " + token;
        }
    }

    private boolean hasContent(Sheet sheet, DataFormatter formatter) {
        int max = Math.min(sheet.getLastRowNum(), 20);
        for (int r = 0; r <= max; r++) {
            Row row = sheet.getRow(r);
            if (row == null) {
                continue;
            }
            for (int c = 0; c <= row.getLastCellNum(); c++) {
                if (!cellText(row, c, formatter).isBlank()) {
                    return true;
                }
            }
        }
        return false;
    }

    // ----------------------------------------------------------------- apply

    private Student findExisting(FeesBalanceRow row) {
        String adm = row.getAdmissionNumber() == null ? "" : row.getAdmissionNumber().trim();
        if (!adm.isEmpty()) {
            Student byAdm = studentStore.findByAdmissionNumber(adm).orElse(null);
            if (byAdm != null) {
                return byAdm;
            }
        }
        String normalizedName = normalizeName(row.getName());
        if (normalizedName.isEmpty()) {
            return null;
        }
        for (Student student : studentStore.getStudents()) {
            if (normalizedName.equals(normalizeName(student.getName()))) {
                return student;
            }
        }
        return null;
    }

    private String normalizeName(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private void applyBalance(Student student, FeesBalanceRow row) {
        StudentFeeLedger ledger = studentStore.getLedger(student.getId());
        BigDecimal balance = row.getBalance();
        if (balance.signum() >= 0) {
            ledger.setArrears(balance);
            ledger.setAdvance(CurrencyConfig.zero());
        } else {
            ledger.setAdvance(balance.negate());
            ledger.setArrears(CurrencyConfig.zero());
        }
    }

    private void checkTotals(FeesBalanceRow row, List<String> warnings) {
        if (!row.isHasBreakdown()) {
            return;
        }
        BigDecimal c = row.getCurrentFees();
        BigDecimal a = row.getArrears();
        BigDecimal t = row.getTotalFees();
        if (t.signum() != 0 || c.signum() != 0 || a.signum() != 0) {
            if (t.compareTo(c.add(a)) != 0) {
                warnings.add("T/FEES " + format(t) + " != C/FEES " + format(c) + " + ARREARS " + format(a));
            }
        }
    }

    private String label(FeesBalanceRow row) {
        return row.getName() + (row.getAdmissionNumber().isBlank() ? "" : " (" + row.getAdmissionNumber() + ")");
    }

    // ---------------------------------------------------------------- helpers

    private String cellText(Row row, int col, DataFormatter formatter) {
        Cell cell = row.getCell(col, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        return cell == null ? "" : formatter.formatCellValue(cell).trim();
    }

    private BigDecimal amount(Row row, int col, DataFormatter formatter) {
        String raw = cellText(row, col, formatter).trim();
        if (raw.isEmpty()) {
            return CurrencyConfig.zero();
        }
        String clean = raw.replaceAll("[^0-9.\\-]", "");
        if (clean.isEmpty() || clean.equals("-") || clean.equals(".")) {
            return CurrencyConfig.zero();
        }
        try {
            return CurrencyConfig.money(clean);
        } catch (NumberFormatException e) {
            return CurrencyConfig.zero();
        }
    }

    private boolean looksLikeTotal(String name) {
        String upper = name.toUpperCase(Locale.ROOT);
        return upper.contains("TOTAL") || upper.contains("GRAND") || upper.contains("SUB TOTAL");
    }

    private boolean isNameToken(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        return value.matches(".*[A-Za-z].*");
    }

    private boolean isMarker(String value) {
        return value != null && value.length() == 1 && "BCD".indexOf(Character.toUpperCase(value.charAt(0))) >= 0;
    }

    private BoardingStatus parseBoardingMarker(String value) {
        String v = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (v.contains("DAY")) {
            return BoardingStatus.DAY;
        }
        if (v.equals("D")) {
            return BoardingStatus.DAY;
        }
        if (v.equals("B") || v.contains("BOARD")) {
            return BoardingStatus.BOARDING;
        }
        return null;
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private String format(BigDecimal value) {
        return value == null ? "0" : value.toPlainString();
    }

    /** A located table on a sheet: header row index plus mapped columns. */
    private static final class HeaderTable {
        private final int rowIndex;
        private final int nameCol;
        private final Map<Integer, String> headers;
        private int admCol = -1;
        private int boardingCol = -1;
        private int cfeesCol = -1;
        private int arrearsCol = -1;
        private int tfeesCol = -1;
        private int penaltyCol = -1;

        private HeaderTable(int rowIndex, int nameCol, Map<Integer, String> headers) {
            this.rowIndex = rowIndex;
            this.nameCol = nameCol;
            this.headers = headers;
        }
    }

    private FeesBalanceRow skippedRow(String sheetName, String reason) {
        FeesBalanceRow row = new FeesBalanceRow(sheetName, 0);
        row.setInclude(false);
        row.setMatchStatus("Skipped");
        row.getWarnings().add(reason);
        return row;
    }

    public static final class ApplyResult {
        private final int created;
        private final int existing;
        private final int skipped;
        private final int credits;
        private final List<String> warnings;

        public ApplyResult(int created, int existing, int skipped, int credits, List<String> warnings) {
            this.created = created;
            this.existing = existing;
            this.skipped = skipped;
            this.credits = credits;
            this.warnings = List.copyOf(warnings);
        }

        public int getCreated() {
            return created;
        }

        public int getExisting() {
            return existing;
        }

        public int getSkipped() {
            return skipped;
        }

        public int getCredits() {
            return credits;
        }

        public List<String> getWarnings() {
            return warnings;
        }
    }
}
