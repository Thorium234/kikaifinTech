package com.schaccs.service;

import com.schaccs.config.AppConfig;
import com.schaccs.config.CurrencyConfig;
import com.schaccs.enums.PaymentMode;
import com.schaccs.model.receipt.Receipt;
import com.schaccs.model.student.Student;
import com.schaccs.model.student.StudentFeeLedger;
import com.schaccs.repository.Database;
import com.schaccs.repository.PersistenceService;
import com.schaccs.service.importer.FeesBalanceImportService;
import com.schaccs.service.importer.FeesBalanceRow;
import com.schaccs.service.receipt.ReceiptService;
import com.schaccs.store.ReceiptStore;
import com.schaccs.store.StudentStore;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Guards the school's end-to-end fees-balance flow: import the workbook →
 * persist → restart → pay fees for an imported student → persist → restart.
 * In particular, students whose admission number is blank in the workbook must
 * be imported (stored as SQL NULL, which the UNIQUE column accepts), their
 * balances must survive a save/load round trip, and posting a payment for them
 * must not fail a foreign key or unique constraint.
 */
class ImportedBalancesSurviveAndArePayableTest {

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
        Database.getInstance().close();
    }

    @Test
    void importedBlankAdmissionBalancesSurviveRoundTripAndPayment() {
        Workbook wb = new XSSFWorkbook();
        Sheet sheet = wb.createSheet("F4E");
        title(sheet, 0, 1, "FORM FOUR EAST FEES BALANCES AS AT 11TH FEB. 2026");
        header(sheet, 1, "NO.", "NAMES", "", "ADM. NO.", "C/FEES", "ARREARS", "T/FEES");
        data(sheet, 2, "JOHN BLANK ADM", "", 0, 12000, 0, 12000);
        data(sheet, 3, "CARLOS JUMA WAFULA", "D", 4353, 11000, 20500, 31500);

        List<FeesBalanceRow> rows = service.parseWorkbook(wb);
        assertEquals(2, rows.size());
        FeesBalanceRow blank = rows.get(0);
        assertTrue(blank.getAdmissionNumber() == null || blank.getAdmissionNumber().isBlank());

        service.apply(rows);

        // Simulate a restart: reload everything from the persisted DB.
        StudentStore.getInstance().clear();
        ReceiptStore.getInstance().clear();
        PersistenceService.getInstance().loadAll();

        Student blankStudent = StudentStore.getInstance().getStudents().stream()
                .filter(s -> s.getName().equals("JOHN BLANK ADM"))
                .findFirst().orElse(null);
        assertNotNull(blankStudent, "Blank-admission student must exist after restart");
        assertTrue(blankStudent.getAdmissionNumber() == null || blankStudent.getAdmissionNumber().isBlank(),
                "Blank admission number must stay blank after restart");
        StudentFeeLedger blankLedger = StudentStore.getInstance().getLedger(blankStudent.getId());
        assertEquals(0, blankLedger.getArrears().compareTo(CurrencyConfig.money("12000")),
                "Imported arrears must survive the restart");

        // Pay part of the imported balance: must not hit FK/UNIQUE errors.
        ReceiptService.Result result = new ReceiptService().receivePayment(
                blankStudent, CurrencyConfig.money("5000"), PaymentMode.MPESA, "BLANK-REF",
                LocalDate.now(), null);
        assertTrue(result.isSuccess(), () -> String.join(", ", result.getErrors()));
        assertEquals(0, blankLedger.getArrears().compareTo(CurrencyConfig.money("7000")),
                "Payment must reduce the imported arrears");
        String receiptId = result.getReceipt().getId();

        // Restart again: the receipt and the remaining arrears must both persist.
        StudentStore.getInstance().clear();
        ReceiptStore.getInstance().clear();
        PersistenceService.getInstance().loadAll();

        Student reloaded = StudentStore.getInstance().getStudents().stream()
                .filter(s -> s.getName().equals("JOHN BLANK ADM"))
                .findFirst().orElse(null);
        assertNotNull(reloaded);
        StudentFeeLedger reloadedLedger = StudentStore.getInstance().getLedger(reloaded.getId());
        assertEquals(0, reloadedLedger.getArrears().compareTo(CurrencyConfig.money("7000")),
                "Remaining arrears must survive the second restart");
        Receipt reloadedReceipt = ReceiptStore.getInstance().getReceipts().stream()
                .filter(r -> r.getId().equals(receiptId))
                .findFirst().orElse(null);
        assertNotNull(reloadedReceipt, "Payment receipt must survive the second restart");
        assertEquals(0, reloadedReceipt.getAmount().compareTo(CurrencyConfig.money("5000")));
    }

    @Test
    void realWorkbookImportSurvivesRestart() throws Exception {
        assumeTrue(Files.exists(REAL_FILE), "Real fees-balance workbook not present");
        List<FeesBalanceRow> rows = service.parseWorkbook(REAL_FILE);
        service.apply(rows);

        StudentStore.getInstance().clear();
        ReceiptStore.getInstance().clear();
        PersistenceService.getInstance().loadAll();

        int reloaded = StudentStore.getInstance().getStudents().size();
        assertTrue(reloaded >= 800, "Real import must persist across restart (got " + reloaded + ")");

        long withBalances = StudentStore.getInstance().getStudents().stream()
                .filter(s -> StudentStore.getInstance().getLedger(s.getId()).getArrears().signum() > 0
                        || StudentStore.getInstance().getLedger(s.getId()).getAdvance().signum() > 0)
                .count();
        assertTrue(withBalances > 500, "Imported fee balances must survive restart (got " + withBalances + ")");

        long blankAdmissions = StudentStore.getInstance().getStudents().stream()
                .filter(s -> s.getAdmissionNumber() == null || s.getAdmissionNumber().isBlank())
                .count();
        assertTrue(blankAdmissions >= 20,
                "Blank-admission students from the workbook must be imported (got " + blankAdmissions + ")");
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
        if (adm > 0) {
            cell(row, 3, adm);
        }
        cell(row, 4, cfees);
        cell(row, 5, arrears);
        cell(row, 6, tfees);
    }

    private static void cell(Row row, int col, Object value) {
        var cell = row.createCell(col);
        if (value instanceof Number n) {
            cell.setCellValue(n.doubleValue());
        } else {
            cell.setCellValue(String.valueOf(value));
        }
    }
}
