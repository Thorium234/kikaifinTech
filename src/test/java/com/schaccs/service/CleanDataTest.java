package com.schaccs.service;

import com.schaccs.model.CleanDataEntry;
import com.schaccs.model.student.Student;
import com.schaccs.repository.PersistenceService;
import com.schaccs.service.importer.StudentImportService;
import com.schaccs.store.CleanDataCodec;
import com.schaccs.store.CleanDataStore;
import com.schaccs.store.StudentStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Clean Data: import rows that fail validation are held here (instead of being
 * dropped or blocking the whole import) until they are fixed, at which point
 * they auto-import. Rows persist across sessions, and re-importing the same
 * broken file replaces identical held rows instead of piling up duplicates.
 */
class CleanDataTest {

    @BeforeEach
    void setUp() {
        PersistenceService.getInstance().clearAll();
    }

    @AfterEach
    void tearDown() {
        PersistenceService.getInstance().clearAll();
    }

    private static Map<String, String> studentRow(String adm, String name, String formClass, String stream) {
        Map<String, String> row = new LinkedHashMap<>();
        row.put("admissionnumber", adm);
        row.put("fullname", name);
        row.put("formclass", formClass);
        row.put("stream", stream);
        return row;
    }

    @Test
    @DisplayName("Codec round-trips field maps losslessly including special characters")
    void codecRoundTripsSpecialCharacters() {
        Map<String, String> row = new LinkedHashMap<>();
        row.put("admissionnumber", "2026/100");
        row.put("fullname", "Ochieng Omondi");
        row.put("formclass", "Grade 8");
        row.put("stream", "A");
        row.put("note", "line1\nline2 with \\ backslash and = equals");

        String encoded = CleanDataCodec.encode(row);
        Map<String, String> decoded = CleanDataCodec.decode(encoded);

        assertEquals(row, decoded);
        assertNotEquals(row, CleanDataCodec.decode(encoded.split("\n")[0]),
                "Truncated payload must not accidentally equal the full row");
    }

    @Test
    @DisplayName("Codec handles empty and blank-only payloads")
    void codecHandlesEmptyPayload() {
        assertTrue(CleanDataCodec.decode("").isEmpty());
        assertTrue(CleanDataCodec.decode(null).isEmpty());
        assertEquals("", CleanDataCodec.encode(Map.of()));
    }

    @Test
    @DisplayName("AddRows replaces an identical held row instead of duplicating it")
    void addRowsDeduplicatesIdenticalRows() {
        CleanDataStore store = CleanDataStore.getInstance();
        Map<String, String> row = studentRow("2026/100", "Ochieng Omondi", "Form 2", "A");

        store.addRows(CleanDataEntry.Type.STUDENT, List.of(row));
        store.addRows(CleanDataEntry.Type.STUDENT, List.of(row));
        store.addRows(CleanDataEntry.Type.STUDENT, List.of(studentRow("2026/100", "Ochieng Omondi", "Form 2", "A")));

        assertEquals(1, store.forType(CleanDataEntry.Type.STUDENT).size(),
                "Repeated import of the same broken row must not pile up duplicates");

        store.addRows(CleanDataEntry.Type.STUDENT, List.of(studentRow("2026/101", "Other Student", "Form 3", "B")));
        assertEquals(2, store.forType(CleanDataEntry.Type.STUDENT).size());
        store.clear();
    }

    @Test
    @DisplayName("Types are kept apart: student and fees-balance rows never collide")
    void keepsStudentAndFeesRowsApart() {
        CleanDataStore store = CleanDataStore.getInstance();
        store.addRows(CleanDataEntry.Type.STUDENT, List.of(studentRow("2026/100", "Ochieng", "Form 1", "A")));
        store.addRows(CleanDataEntry.Type.FEES_BALANCE,
                List.of(Map.of("sheetName", "F2A", "admissionNumber", "2026/100")));

        assertEquals(1, store.forType(CleanDataEntry.Type.STUDENT).size());
        assertEquals(1, store.forType(CleanDataEntry.Type.FEES_BALANCE).size());
        assertEquals(2, store.getItems().size());
        store.clear();
    }

    @Test
    @DisplayName("ReplaceRows makes the held rows of a type exactly the given rows")
    void replaceRowsOverwritesType() {
        CleanDataStore store = CleanDataStore.getInstance();
        store.addRows(CleanDataEntry.Type.STUDENT, List.of(studentRow("2026/100", "One", "Form 1", "A")));
        store.addRows(CleanDataEntry.Type.STUDENT, List.of(studentRow("2026/101", "Two", "Form 1", "A")));

        store.replaceRows(CleanDataEntry.Type.STUDENT, List.of(studentRow("2026/102", "Three", "Form 1", "A")));

        assertEquals(1, store.forType(CleanDataEntry.Type.STUDENT).size());
        assertEquals("Three", store.forType(CleanDataEntry.Type.STUDENT).get(0).getName());
        store.clear();
    }

    @Test
    @DisplayName("Held rows survive a save/load round-trip")
    void heldRowsSurviveSaveAndLoad() {
        CleanDataStore store = CleanDataStore.getInstance();
        store.addRows(CleanDataEntry.Type.STUDENT,
                List.of(studentRow("2026/100", "Ochieng Omondi", "Form 2", "A")));
        store.addRows(CleanDataEntry.Type.FEES_BALANCE,
                List.of(Map.of("sheetName", "G10A", "rowNumber", "7", "name", "GODGIVER WEKESA",
                        "admissionNumber", "4617", "formClass", "Grade 10", "stream", "")));

        PersistenceService.getInstance().saveAll();
        store.clear();
        assertEquals(0, store.getItems().size());

        PersistenceService.getInstance().loadAll();

        List<CleanDataEntry> students = store.forType(CleanDataEntry.Type.STUDENT);
        assertEquals(1, students.size());
        assertEquals("2026/100", students.get(0).getFields().get("admissionnumber"));
        assertEquals("Ochieng Omondi", students.get(0).getName());
        assertEquals("Form 2", students.get(0).getFields().get("formclass"));

        List<CleanDataEntry> fees = store.forType(CleanDataEntry.Type.FEES_BALANCE);
        assertEquals(1, fees.size());
        assertEquals("GODGIVER WEKESA", fees.get(0).getName());
        assertEquals("4617", fees.get(0).getFields().get("admissionNumber"));
        assertEquals("Grade 10", fees.get(0).getFields().get("formClass"));
    }

    @Test
    @DisplayName("A corrected Clean Data student row is committed into the student list")
    void correctedStudentRowGoesToStudentList() {
        StudentImportService service = new StudentImportService();

        Map<String, String> broken = studentRow("2026/200", "", "Form 2", "A");
        Student student = service.toStudent(broken);
        List<String> errors = service.validateRow(broken, student, List.of());
        assertFalse(errors.isEmpty(), "A broken row must stay in Clean Data");

        broken.put("fullname", "Fixed Name");
        student.setName("Fixed Name");
        errors = service.validateRow(broken, student, List.of());
        assertTrue(errors.isEmpty(), "A fixed row must validate clean: " + errors);

        List<String> commitErrors = service.commitStudent(student);
        assertTrue(commitErrors.isEmpty(), "Committing the fixed row must succeed: " + commitErrors);
        assertTrue(StudentStore.getInstance().findByAdmissionNumber("2026/200").isPresent(),
                "A fixed Clean Data row must appear in the student list");
    }

    @Test
    @DisplayName("Rows resolved in a fix session are gone after the next save/load")
    void resolvedRowsDoNotComeBack() {
        CleanDataStore store = CleanDataStore.getInstance();
        store.addRows(CleanDataEntry.Type.STUDENT, List.of(studentRow("2026/100", "Ochieng", "Form 1", "A")));
        PersistenceService.getInstance().saveAll();

        store.replaceRows(CleanDataEntry.Type.STUDENT, List.of());
        PersistenceService.getInstance().saveAll();
        store.clear();

        PersistenceService.getInstance().loadAll();

        assertTrue(store.forType(CleanDataEntry.Type.STUDENT).isEmpty(),
                "A row discarded during a fix session must stay gone after reload");
    }
}
