package com.schaccs.service;

import com.schaccs.config.CurrencyConfig;
import com.schaccs.enums.BoardingStatus;
import com.schaccs.enums.StudentStatus;
import com.schaccs.model.student.Student;
import com.schaccs.model.student.StudentFeeLedger;
import com.schaccs.repository.PersistenceService;
import com.schaccs.service.importer.FeesBalanceImportService;
import com.schaccs.service.importer.FeesBalanceRow;
import com.schaccs.store.StudentStore;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class FeesBalanceImportServiceTest {

    private static final Path REAL_FILE = Path.of(
            "FEES BALANCE AS AT 12TH JAN 2026.xlsx");

    private FeesBalanceImportService service;

    @BeforeEach
    void setUp() {
        PersistenceService.getInstance().clearAll();
        service = new FeesBalanceImportService();
    }

    @AfterEach
    void tearDown() {
        PersistenceService.getInstance().clearAll();
    }

    // ---------------------------------------------------------- real workbook

    @Test
    void parsesTheRealSchoolWorkbook() throws Exception {
        assumeTrue(Files.exists(REAL_FILE), "Real fees-balance workbook not present");
        List<FeesBalanceRow> rows = service.parseWorkbook(REAL_FILE);
        assertFalse(rows.isEmpty());

        FeesBalanceRow collins = rowByName(rows, "COLLINS KIBOMA");
        assertNotNull(collins, "2018 legacy sheet row must parse");
        assertEquals("3411", collins.getAdmissionNumber());
        assertEquals(CurrencyConfig.money("23464"), collins.getTotalFees());
        assertEquals("Form 4", collins.getFormClass());
        assertEquals("Y", collins.getStream());
        assertEquals(BoardingStatus.BOARDING, collins.getBoardingStatus());

        FeesBalanceRow felix = rowByName(rows, "FELIX KIMTAI");
        assertNotNull(felix, "Split-first-last-name sheet row must be joined");
        assertEquals("4009", felix.getAdmissionNumber());
        assertEquals(BoardingStatus.BOARDING, felix.getBoardingStatus());
        assertEquals(CurrencyConfig.money("62100"), felix.getTotalFees());

        FeesBalanceRow carlos = rowByName(rows, "CARLOS JUMA WAFULA");
        assertNotNull(carlos);
        assertEquals(BoardingStatus.DAY, carlos.getBoardingStatus(),
                "Missing B marker on a marker-using sheet must mean Day");

        FeesBalanceRow godgiver = rowByName(rows, "GODGIVER WEKESA");
        assertNotNull(godgiver, "Grade 10 sheet must parse");
        assertEquals("4617", godgiver.getAdmissionNumber());
        assertEquals(BoardingStatus.DAY, godgiver.getBoardingStatus());
        assertEquals(CurrencyConfig.money("9000"), godgiver.getTotalFees());

        assertTrue(rows.stream().anyMatch(r -> r.getMatchStatus().equals("Skipped")
                        && r.getSheetName().equalsIgnoreCase("colo")),
                "Payroll-only sheet must be reported as skipped");
    }

    @Test
    void scrutinizesTheRealWorkbook() throws Exception {
        assumeTrue(Files.exists(REAL_FILE), "Real fees-balance workbook not present");
        List<FeesBalanceRow> rows = service.parseWorkbook(REAL_FILE);
        service.scrutinize(rows);
        assertTrue(rows.stream().anyMatch(r -> !r.getWarnings().isEmpty()),
                "Scrutiny must surface at least one anomaly in the real data");
        assertTrue(rows.stream().allMatch(r -> r.getMatchStatus() != null));
    }

    // ------------------------------------------------------- synthetic layouts

    @Test
    void parsesSingleTableWithUnlabelledBoardingMarker() {
        Workbook wb = new XSSFWorkbook();
        Sheet sheet = wb.createSheet("F4E");
        title(sheet, 0, 1, "FORM FOUR EAST FEES BALANCES AS AT 11TH FEB. 2026");
        header(sheet, 1, "NO.", "NAMES", "", "ADM. NO.", "C/FEES", "ARREARS", "T/FEES");
        data(sheet, 2, "MARU KIPKOECH KWENG'WA", "B", 4490, 40500, 40540, 81040);
        data(sheet, 3, "SHADRACK KIBET", "B", 4345, 40500, 29425, 69925);
        data(sheet, 4, "CARLOS JUMA WAFULA", "", 4353, 11000, 20500, 31500);
        row(sheet, 5, null, "TOTAL", null, null, null, null, null);

        List<FeesBalanceRow> rows = service.parseWorkbook(wb);

        assertEquals(3, rows.size(), "TOTAL row and title row must be skipped");
        FeesBalanceRow maru = rows.get(0);
        assertEquals("MARU KIPKOECH KWENG'WA", maru.getName());
        assertEquals("4490", maru.getAdmissionNumber());
        assertEquals(BoardingStatus.BOARDING, maru.getBoardingStatus());
        assertEquals(CurrencyConfig.money("81040"), maru.getTotalFees());
        assertEquals(CurrencyConfig.money("40540"), maru.getArrears());
        assertEquals("Form 4", maru.getFormClass());
        assertEquals("E", maru.getStream());
        assertEquals(BoardingStatus.DAY, rows.get(2).getBoardingStatus());
    }

    @Test
    void joinsNamesSplitAcrossColumns() {
        Workbook wb = new XSSFWorkbook();
        Sheet sheet = wb.createSheet("F4W23");
        title(sheet, 0, 0, "FORM FOUR W FEES BALANCES AS AT 19TH OCT 2023");
        header(sheet, 1, "NO", "NAMES", "", "", "ADM.", "C/FEES", "ARREARS", "T/FEES");
        splitRow(sheet, 2, "FELIX", "KIMTAI", "B", 4009, 25000, 37100, 62100);
        splitRow(sheet, 3, "JOHN", "WANJALA", "B", 4074, 25000, 14000, 39000);

        List<FeesBalanceRow> rows = service.parseWorkbook(wb);

        assertEquals(2, rows.size());
        FeesBalanceRow felix = rows.get(0);
        assertEquals("FELIX KIMTAI", felix.getName());
        assertEquals("4009", felix.getAdmissionNumber());
        assertEquals(BoardingStatus.BOARDING, felix.getBoardingStatus());
        assertEquals("Form 4", felix.getFormClass());
        assertEquals("W", felix.getStream());
    }

    @Test
    void parsesTwoTablesSideBySideOnOneSheet() {
        Workbook wb = new XSSFWorkbook();
        Sheet sheet = wb.createSheet("F3W");
        row(sheet, 0, "NO.", "NAMES", "B", "ADM. NO.", "C/FEES", "ARREARS", "T/FEES",
                null, null, "FORM THREE W FEES BALANCE AS AT 11TH FEB. 2026");
        Row r1 = sheet.createRow(1);
        cell(r1, 1, "HAGGAI KIPROTICH NDIEMA");
        cell(r1, 2, "B");
        cell(r1, 3, 4408);
        cell(r1, 4, 30000);
        cell(r1, 5, 7820);
        cell(r1, 6, 37820);
        cell(r1, 8, "NO.");
        cell(r1, 9, "NAMES");
        cell(r1, 13, "B");
        cell(r1, 14, "ADM.NO");
        cell(r1, 15, "C/FEES");
        cell(r1, 16, "ARREARS");
        cell(r1, 17, "T/FEES");
        Row r2 = sheet.createRow(2);
        cell(r2, 1, "MORGAN ROTICH SIYOI");
        cell(r2, 2, "B");
        cell(r2, 3, 4427);
        cell(r2, 4, 21660);
        cell(r2, 5, 0);
        cell(r2, 6, 21660);
        cell(r2, 8, 1);
        cell(r2, 9, "ALVIN KIPTOO");
        cell(r2, 13, "B");
        cell(r2, 14, 4450);
        cell(r2, 15, 40500);
        cell(r2, 16, 28230);
        cell(r2, 17, 68730);

        List<FeesBalanceRow> rows = service.parseWorkbook(wb);

        assertEquals(3, rows.size(), "Both side-by-side tables must be read");
        FeesBalanceRow haggai = rowByName(rows, "HAGGAI KIPROTICH NDIEMA");
        assertNotNull(haggai);
        assertEquals("4408", haggai.getAdmissionNumber());
        assertEquals("Form 3", haggai.getFormClass());
        assertEquals("W", haggai.getStream());
        FeesBalanceRow alvin = rowByName(rows, "ALVIN KIPTOO");
        assertNotNull(alvin);
        assertEquals("4450", alvin.getAdmissionNumber());
        assertEquals(CurrencyConfig.money("68730"), alvin.getTotalFees());
        assertNotNull(rowByName(rows, "MORGAN ROTICH SIYOI"));
    }

    @Test
    void parsesGrade10StyleWithDayBoardingColumnAndAmount() {
        Workbook wb = new XSSFWorkbook();
        Sheet sheet = wb.createSheet("G 10-26");
        title(sheet, 0, 0, "GRADE 10 FEES BALANCES AS AT 24TH 2026");
        header(sheet, 1, "NO.", "NAME OF STUDENT", "ADM NO.", "D/B", "AMOUNT");
        Row r2 = sheet.createRow(2);
        cell(r2, 0, 1);
        cell(r2, 1, "GODGIVER WEKESA");
        cell(r2, 2, 4617);
        cell(r2, 3, "D");
        cell(r2, 4, 9000);

        List<FeesBalanceRow> rows = service.parseWorkbook(wb);

        assertEquals(1, rows.size());
        FeesBalanceRow godgiver = rows.get(0);
        assertEquals("GODGIVER WEKESA", godgiver.getName());
        assertEquals("4617", godgiver.getAdmissionNumber());
        assertEquals(BoardingStatus.DAY, godgiver.getBoardingStatus());
        assertEquals(CurrencyConfig.money("9000"), godgiver.getTotalFees());
        assertEquals("Grade 10", godgiver.getFormClass());
        assertEquals("", godgiver.getStream());
    }

    @Test
    void parsesFormFiveAndSixSheetsAndTitles() {
        Workbook wb = new XSSFWorkbook();
        Sheet sheet = wb.createSheet("F5B");
        title(sheet, 0, 1, "FORM FIVE B FEES BALANCES AS AT 11TH FEB. 2026");
        header(sheet, 1, "NO.", "NAMES", "", "ADM. NO.", "T/FEES");
        data(sheet, 2, "JOHN KAMAU", "B", 5001, 20000, 0, 20000);
        Sheet sheet2 = wb.createSheet("F6");
        title(sheet2, 0, 1, "FORM SIX FEES BALANCES AS AT 11TH FEB. 2026");
        header(sheet2, 1, "NO.", "NAMES", "", "ADM. NO.", "T/FEES");
        data(sheet2, 2, "JANE WANGARI", "B", 6001, 20000, 0, 20000);

        List<FeesBalanceRow> rows = service.parseWorkbook(wb);

        FeesBalanceRow john = rowByName(rows, "JOHN KAMAU");
        assertNotNull(john);
        assertEquals("Form 5", john.getFormClass());
        assertEquals("B", john.getStream());
        FeesBalanceRow jane = rowByName(rows, "JANE WANGARI");
        assertNotNull(jane);
        assertEquals("Form 6", jane.getFormClass());
        assertEquals("", jane.getStream());
    }

    @Test
    void parsesGradeEightElevenAndTwelveSheets() {
        Workbook wb = new XSSFWorkbook();
        Sheet s8 = wb.createSheet("G8A");
        title(s8, 0, 1, "GRADE 8 FEES BALANCES AS AT 11TH FEB. 2026");
        header(s8, 1, "NO.", "NAMES", "", "ADM. NO.", "T/FEES");
        data(s8, 2, "BRIAN OTIENO", "D", 8001, 10000, 0, 10000);
        Sheet s11 = wb.createSheet("G11");
        title(s11, 0, 1, "GRADE 11 FEES BALANCES AS AT 11TH FEB. 2026");
        header(s11, 1, "NO.", "NAMES", "", "ADM. NO.", "T/FEES");
        data(s11, 2, "KEVIN KIPROTICH", "B", 11001, 15000, 0, 15000);
        Sheet s12 = wb.createSheet("G12");
        title(s12, 0, 1, "GRADE TWELVE FEES BALANCES AS AT 11TH FEB. 2026");
        header(s12, 1, "NO.", "NAMES", "", "ADM. NO.", "T/FEES");
        data(s12, 2, "AKINYI OMONDI", "D", 12001, 18000, 0, 18000);

        List<FeesBalanceRow> rows = service.parseWorkbook(wb);

        FeesBalanceRow brian = rowByName(rows, "BRIAN OTIENO");
        assertNotNull(brian);
        assertEquals("Grade 8", brian.getFormClass());
        assertEquals("A", brian.getStream());
        FeesBalanceRow kevin = rowByName(rows, "KEVIN KIPROTICH");
        assertNotNull(kevin);
        assertEquals("Grade 11", kevin.getFormClass());
        FeesBalanceRow akin = rowByName(rows, "AKINYI OMONDI");
        assertNotNull(akin);
        assertEquals("Grade 12", akin.getFormClass());
    }

    @Test
    void reportsNonStudentSheetAsSkippedAndIgnoresEmptySheets() {
        Workbook wb = new XSSFWorkbook();
        Sheet payroll = wb.createSheet("colo");
        title(payroll, 0, 1, "FRIENDS SCHOOL KIKAI BOYS SEC. SCHOOL");
        header(payroll, 1, "DATE", "MONTH", "GROSS", "DEDUCTION", "NET PAY");
        Row r2 = payroll.createRow(2);
        cell(r2, 0, "03-May-2020");
        cell(r2, 1, "Jan-2020");
        cell(r2, 2, 7000);
        cell(r2, 3, 0);
        cell(r2, 4, 7000);
        wb.createSheet("FO");

        List<FeesBalanceRow> rows = service.parseWorkbook(wb);

        assertEquals(1, rows.size());
        assertEquals("colo", rows.get(0).getSheetName());
        assertEquals("Skipped", rows.get(0).getMatchStatus());
        assertFalse(rows.get(0).isInclude());
    }

    @Test
    void readsAmountsWithThousandSeparatorsAndPenalty() {
        Workbook wb = new XSSFWorkbook();
        Sheet sheet = wb.createSheet("F4E23");
        title(sheet, 0, 0, "FORM FOUR E FEES BALANCES AS AT 19TH OCT 2023");
        header(sheet, 1, "NO.", "NAMES", "", "ADM. NO.", "C/FEES", "ARREARS", "T/FEES", "PENALTY");
        Row r2 = sheet.createRow(2);
        cell(r2, 1, "JOSEPH WAFULA");
        cell(r2, 2, "B");
        cell(r2, 3, 4046);
        cell(r2, 4, "25,000");
        cell(r2, 5, 16360);
        cell(r2, 6, "41,360");
        cell(r2, 7, 3000);

        List<FeesBalanceRow> rows = service.parseWorkbook(wb);

        assertEquals(1, rows.size());
        FeesBalanceRow joseph = rows.get(0);
        assertEquals(CurrencyConfig.money("25000"), joseph.getCurrentFees());
        assertEquals(CurrencyConfig.money("41360"), joseph.getTotalFees());
        assertEquals(CurrencyConfig.money("3000"), joseph.getPenalty());
        assertEquals(CurrencyConfig.money("44360"), joseph.getBalance(),
                "Penalty must be included in the imported balance");
    }

    // ------------------------------------------------------------- scrutiny

    @Test
    void scrutinyFlagsInconsistentTotalsDuplicatesAndCredits() {
        FeesBalanceRow bad = new FeesBalanceRow("F4E", 3);
        bad.setName("BRIAN");
        bad.setAdmissionNumber("4001");
        bad.setHasBreakdown(true);
        bad.setCurrentFees(new BigDecimal("10000"));
        bad.setArrears(new BigDecimal("5000"));
        bad.setTotalFees(new BigDecimal("12000"));

        FeesBalanceRow credit = new FeesBalanceRow("F4E", 4);
        credit.setName("JANE");
        credit.setAdmissionNumber("4002");
        credit.setTotalFees(new BigDecimal("-3000"));

        FeesBalanceRow dup = new FeesBalanceRow("F4W", 5);
        dup.setName("DUPLICATE");
        dup.setAdmissionNumber("4001");
        dup.setTotalFees(new BigDecimal("100"));

        service.scrutinize(List.of(bad, credit, dup));

        assertTrue(bad.getWarnings().stream().anyMatch(w -> w.contains("!=")),
                "Totals mismatch must be flagged");
        assertTrue(credit.getWarnings().stream().anyMatch(w -> w.contains("advance")),
                "Credit balance must be flagged");
        assertTrue(dup.getWarnings().stream().anyMatch(w -> w.contains("Duplicate admission number")),
                "Duplicate admission must be flagged");
    }

    // ---------------------------------------------------------------- apply

    @Test
    void applyCreatesNewStudentsAndWritesLedgerBalances() {
        Student existing = new Student("4408", "HAGGAI KIPROTICH NDIEMA", "Form 3", "W",
                BoardingStatus.BOARDING, "");
        StudentStore.getInstance().add(existing);

        Workbook wb = new XSSFWorkbook();
        Sheet sheet = wb.createSheet("F4E");
        title(sheet, 0, 1, "FORM FOUR EAST FEES BALANCES AS AT 11TH FEB. 2026");
        header(sheet, 1, "NO.", "NAMES", "", "ADM. NO.", "C/FEES", "ARREARS", "T/FEES");
        data(sheet, 2, "HAGGAI KIPROTICH NDIEMA", "B", 4408, 40500, 7820, 48320);
        data(sheet, 3, "CARLOS JUMA WAFULA", "", 4353, 11000, 20500, 31500);

        List<FeesBalanceRow> rows = service.parseWorkbook(wb);
        FeesBalanceImportService.ApplyResult result = service.apply(rows);

        assertEquals(1, result.getCreated(), "Only Carlos is new");
        assertEquals(1, result.getExisting(), "Haggai matched by admission");

        Student haggai = StudentStore.getInstance().findByAdmissionNumber("4408").orElseThrow();
        StudentFeeLedger haggaiLedger = StudentStore.getInstance().getLedger(haggai.getId());
        assertEquals(CurrencyConfig.money("48320"), haggaiLedger.getArrears(),
                "Existing student's ledger must be updated from the sheet");

        Student carlos = StudentStore.getInstance().findByAdmissionNumber("4353").orElseThrow();
        assertEquals(BoardingStatus.DAY, carlos.getBoardingStatus());
        StudentFeeLedger carlosLedger = StudentStore.getInstance().getLedger(carlos.getId());
        assertEquals(CurrencyConfig.money("31500"), carlosLedger.getArrears());
        assertEquals(CurrencyConfig.zero(), carlosLedger.getAdvance());
    }

    @Test
    void reUploadUpdatesExistingStudentBalanceWithoutDuplicating() {
        Student existing = new Student("4408", "HAGGAI KIPROTICH NDIEMA", "Form 3", "W",
                BoardingStatus.BOARDING, "");
        StudentStore.getInstance().add(existing);
        StudentStore.getInstance().getLedger(existing.getId()).setArrears(CurrencyConfig.money("1000"));

        Workbook wb = new XSSFWorkbook();
        Sheet sheet = wb.createSheet("F4E");
        title(sheet, 0, 1, "FORM FOUR EAST FEES BALANCES AS AT 11TH FEB. 2026");
        header(sheet, 1, "NO.", "NAMES", "", "ADM. NO.", "C/FEES", "ARREARS", "T/FEES");
        data(sheet, 2, "HAGGAI KIPROTICH NDIEMA", "B", 4408, 40500, 7820, 48320);

        List<FeesBalanceRow> rows = service.parseWorkbook(wb);
        FeesBalanceImportService.ApplyResult result = service.apply(rows);

        assertEquals(0, result.getCreated(), "Re-upload must not create a duplicate");
        assertEquals(1, result.getExisting(), "Re-upload must match the existing student");
        assertEquals(1, StudentStore.getInstance().getStudents().stream()
                        .filter(s -> "4408".equals(s.getAdmissionNumber())).count(),
                "Exactly one student may hold admission 4408");

        Student haggai = StudentStore.getInstance().findByAdmissionNumber("4408").orElseThrow();
        assertEquals(CurrencyConfig.money("48320"),
                StudentStore.getInstance().getLedger(haggai.getId()).getArrears(),
                "Re-upload must replace the old balance with the new one");
    }

    @Test
    void applyTurnsCreditBalanceIntoAdvance() {
        Workbook wb = new XSSFWorkbook();
        Sheet sheet = wb.createSheet("F4E");
        title(sheet, 0, 1, "FORM FOUR EAST FEES BALANCES");
        header(sheet, 1, "NO.", "NAMES", "", "ADM. NO.", "T/FEES");
        Row r2 = sheet.createRow(2);
        cell(r2, 1, "OVERPAID STUDENT");
        cell(r2, 3, 4601);
        cell(r2, 4, -5000);

        List<FeesBalanceRow> rows = service.parseWorkbook(wb);
        service.apply(rows);

        Student student = StudentStore.getInstance().findByAdmissionNumber("4601").orElseThrow();
        StudentFeeLedger ledger = StudentStore.getInstance().getLedger(student.getId());
        assertEquals(CurrencyConfig.zero(), ledger.getArrears());
        assertEquals(CurrencyConfig.money("5000"), ledger.getAdvance());
    }

    @Test
    void applySkipsUncheckedAndNamelessRows() {
        Workbook wb = new XSSFWorkbook();
        Sheet sheet = wb.createSheet("F4E");
        title(sheet, 0, 1, "FORM FOUR EAST FEES BALANCES");
        header(sheet, 1, "NO.", "NAMES", "", "ADM. NO.", "T/FEES");
        Row r2 = sheet.createRow(2);
        cell(r2, 1, "KEEP ME");
        cell(r2, 3, 4602);
        cell(r2, 4, 9000);

        List<FeesBalanceRow> rows = service.parseWorkbook(wb);
        rows.get(0).setInclude(false);

        FeesBalanceImportService.ApplyResult result = service.apply(rows);

        assertEquals(0, result.getCreated());
        assertEquals(1, result.getSkipped());
        assertTrue(StudentStore.getInstance().findByAdmissionNumber("4602").isEmpty());
    }

    @Test
    void applyWithYearContextCreatesStudentsForTheTargetYear() {
        Workbook wb = new XSSFWorkbook();
        Sheet sheet = wb.createSheet("F4E");
        title(sheet, 0, 1, "FORM FOUR EAST FEES BALANCES AS AT 11TH FEB. 2020");
        header(sheet, 1, "NO.", "NAMES", "", "ADM. NO.", "T/FEES");
        data(sheet, 2, "LEGACY STUDENT", "B", 4901, 10000, 20000, 30000);

        List<FeesBalanceRow> rows = service.parseWorkbook(wb);
        FeesBalanceImportService.ImportContext context =
                FeesBalanceImportService.ImportContext.of(2020, "Bursar");
        FeesBalanceImportService.ApplyResult result = service.apply(rows, context);

        assertEquals(1, result.getCreated());
        Student student = StudentStore.getInstance().findByAdmissionNumber("4901").orElseThrow();
        assertEquals(2020, student.getAcademicYear(),
                "Imported student must belong to the batch year, not the current year");
        assertEquals(2020, student.getYearOfAdmission());
        assertEquals(StudentStatus.ACTIVE, student.getStatus());
        assertEquals(CurrencyConfig.money("10000"),
                StudentStore.getInstance().getLedger(student.getId()).getArrears(),
                "T/FEES (first mapped column) becomes the imported balance");
    }

    // ---------------------------------------------------------------- helpers

    private static FeesBalanceRow rowByName(List<FeesBalanceRow> rows, String name) {
        return rows.stream()
                .filter(r -> name.equalsIgnoreCase(r.getName()))
                .findFirst()
                .orElse(null);
    }

    private static void title(Sheet sheet, int rowIdx, int col, String text) {
        cell(sheet.getRow(rowIdx) != null ? sheet.getRow(rowIdx) : sheet.createRow(rowIdx), col, text);
    }

    private static void header(Sheet sheet, int rowIdx, String... headers) {
        Row row = sheet.createRow(rowIdx);
        for (int i = 0; i < headers.length; i++) {
            if (headers[i] != null && !headers[i].isEmpty()) {
                cell(row, i, headers[i]);
            }
        }
    }

    private static void data(Sheet sheet, int rowIdx, String name, String marker,
                             int adm, int cfees, int arrears, int tfees) {
        Row row = sheet.createRow(rowIdx);
        cell(row, 0, rowIdx);
        cell(row, 1, name);
        if (marker != null && !marker.isEmpty()) {
            cell(row, 2, marker);
        }
        cell(row, 3, adm);
        cell(row, 4, cfees);
        cell(row, 5, arrears);
        cell(row, 6, tfees);
    }

    private static void splitRow(Sheet sheet, int rowIdx, String first, String last, String marker,
                                 int adm, int cfees, int arrears, int tfees) {
        Row row = sheet.createRow(rowIdx);
        cell(row, 0, rowIdx);
        cell(row, 1, first);
        cell(row, 2, last);
        if (marker != null && !marker.isEmpty()) {
            cell(row, 3, marker);
        }
        cell(row, 4, adm);
        cell(row, 5, cfees);
        cell(row, 6, arrears);
        cell(row, 7, tfees);
    }

    private static void row(Sheet sheet, int rowIdx, Object... values) {
        Row row = sheet.createRow(rowIdx);
        for (int i = 0; i < values.length; i++) {
            if (values[i] != null) {
                cell(row, i, values[i]);
            }
        }
    }

    private static void cell(Row row, int col, Object value) {
        Cell cell = row.createCell(col);
        if (value instanceof Number n) {
            cell.setCellValue(n.doubleValue());
        } else {
            cell.setCellValue(String.valueOf(value));
        }
    }
}
