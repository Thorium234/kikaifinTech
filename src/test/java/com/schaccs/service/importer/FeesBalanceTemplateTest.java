package com.schaccs.service.importer;

import com.schaccs.config.CurrencyConfig;
import com.schaccs.model.student.Student;
import com.schaccs.model.student.StudentFeeLedger;
import com.schaccs.repository.PersistenceService;
import com.schaccs.service.export.FeesBalanceTemplateService;
import com.schaccs.store.StudentStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The official fees-balance template round trip: generate the template, fill
 * it (values as a bursar would type them), import — and every figure must
 * come back exactly as typed, with no heuristic guessing and no inflation.
 */
class FeesBalanceTemplateTest {

    @TempDir
    Path tempDir;

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

    private Path writeWorkbook() throws Exception {
        Path path = tempDir.resolve("fees-balance.xlsx");
        new FeesBalanceTemplateService().generateTemplate(path);
        return path;
    }

    @Test
    @DisplayName("Template values are read verbatim: class, stream, boarding and balance")
    void templateRoundTripsVerbatim() throws Exception {
        List<FeesBalanceRow> rows = service.parseWorkbook(writeWorkbook());

        assertEquals(2, rows.size(), "Exactly the two example rows, no phantom rows");

        FeesBalanceRow first = rows.get(0);
        assertEquals("2026/001", first.getAdmissionNumber());
        assertEquals("John Doe", first.getName());
        assertEquals("Grade 10", first.getFormClass());
        assertEquals("A", first.getStream());
        assertEquals(0, CurrencyConfig.money("9000").compareTo(first.getBalance()),
                "BALANCE is imported exactly as typed");

        FeesBalanceRow second = rows.get(1);
        assertEquals("Mary Wanjiku", second.getName());
        assertEquals("Form 3", second.getFormClass());
        assertEquals(0, CurrencyConfig.money("12500").compareTo(second.getBalance()));

        service.scrutinize(rows);
        assertTrue(rows.stream().noneMatch(FeesBalanceRow::requiresCleaning),
                () -> "Template rows must not be held: " + rows.stream()
                        .map(FeesBalanceRow::getWarningText).toList());
    }

    @Test
    @DisplayName("Applying a 9,000 balance yields exactly 9,000 arrears and zero charges")
    void appliedBalanceMatchesImportedFigure() throws Exception {
        List<FeesBalanceRow> rows = service.parseWorkbook(writeWorkbook());

        var result = service.apply(rows,
                FeesBalanceImportService.ImportContext.of(2026, "TESTER"));

        assertEquals(2, result.getCreated());
        Student john = StudentStore.getInstance().findByAdmissionNumber("2026/001").orElseThrow();
        StudentFeeLedger ledger = StudentStore.getInstance().getLedger(john.getId());
        assertEquals(0, CurrencyConfig.money("9000").compareTo(ledger.getArrears()),
                "The student shows the exact balance that was imported");
        assertEquals(0, BigDecimal.ZERO.compareTo(ledger.getTotalCharged()),
                "No term charge may inflate the imported balance");
        assertEquals("Grade 10", john.getFormClass());
        assertEquals(2026, studentYear(john));
    }

    private static int studentYear(Student student) {
        return student.getAcademicYear() == null ? -1 : student.getAcademicYear();
    }
}
