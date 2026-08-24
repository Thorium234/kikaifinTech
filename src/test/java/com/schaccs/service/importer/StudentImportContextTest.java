package com.schaccs.service.importer;

import com.schaccs.enums.BoardingStatus;
import com.schaccs.model.student.Student;
import com.schaccs.repository.PersistenceService;
import com.schaccs.service.importer.StudentImportService.ImportContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Batch import context: the bursar picks the academic year first, then the
 * class this intake belongs to (Form N or Grade N), optionally a stream.
 * Applying it must fill the staged rows, keep raw rows in sync so validation
 * clears, and default a blank Year of Admission to the batch year.
 */
class StudentImportContextTest {

    private StudentImportService service;

    @BeforeEach
    void setUp() {
        PersistenceService.getInstance().clearAll();
        service = new StudentImportService();
    }

    @AfterEach
    void tearDown() {
        PersistenceService.getInstance().clearAll();
    }

    private Student student(String adm, String formClass, String stream,
                            Integer academicYear, Integer admissionYear) {
        Student s = new Student();
        s.setAdmissionNumber(adm);
        s.setName("Batch Student " + adm);
        s.setFormClass(formClass);
        s.setStream(stream);
        s.setGender("Male");
        s.setBoardingStatus(BoardingStatus.DAY);
        s.setAcademicYear(academicYear);
        s.setYearOfAdmission(admissionYear);
        return s;
    }

    private Map<String, String> raw(String formClass, String stream, String academicYear) {
        Map<String, String> row = new HashMap<>();
        row.put("admissionnumber", "");
        row.put("formclass", formClass == null ? "" : formClass);
        row.put("stream", stream == null ? "" : stream);
        row.put("academicyear", academicYear == null ? "" : academicYear);
        return row;
    }

    @Test
    @DisplayName("Fill-blank mode sets missing class/stream/year but keeps values already present")
    void fillsBlanksOnly() {
        List<Student> students = List.of(
                student("A001", "", "", null, null),
                student("A002", "Form 2", "B", 2025, 2024));
        List<Map<String, String>> rawRows = new ArrayList<>(List.of(
                raw("", "", ""), raw("Form 2", "B", "2025")));

        int changed = service.applyImportContext(rawRows, new ArrayList<>(students),
                new ImportContext(2026, "Grade 10", "A", false));

        assertEquals(1, changed, "Only the blank row is touched in fill-blank mode");
        assertEquals("Grade 10", students.get(0).getFormClass());
        assertEquals("A", students.get(0).getStream());
        assertEquals(2026, students.get(0).getAcademicYear());
        assertEquals(2026, students.get(0).getYearOfAdmission(),
                "Blank Year of Admission defaults to the batch year");

        assertEquals("Form 2", students.get(1).getFormClass(), "Existing class kept");
        assertEquals("B", students.get(1).getStream(), "Existing stream kept");
        assertEquals(2025, students.get(1).getAcademicYear(), "Existing academic year kept");
        assertEquals(2024, students.get(1).getYearOfAdmission(),
                "Existing Year of Admission is never overwritten");

        assertEquals("Grade 10", rawRows.get(0).get("formclass"));
        assertEquals("2026", rawRows.get(0).get("academicyear"));
        assertEquals("Form 2", rawRows.get(1).get("formclass"), "Raw row untouched when nothing changed");
    }

    @Test
    @DisplayName("Overwrite mode replaces every class/stream/year cell")
    void overwritesEverything() {
        List<Student> students = List.of(
                student("A001", "Form 2", "B", 2025, 2024),
                student("A002", "Grade 9", "C", 2025, 2024));
        List<Map<String, String>> rawRows = new ArrayList<>(List.of(
                raw("Form 2", "B", "2025"), raw("Grade 9", "C", "2025")));

        int changed = service.applyImportContext(rawRows, students,
                new ImportContext(2026, "Grade 11", "A", true));

        assertEquals(2, changed);
        students.forEach(s -> {
            assertEquals("Grade 11", s.getFormClass());
            assertEquals("A", s.getStream());
            assertEquals(2026, s.getAcademicYear());
            assertEquals(2024, s.getYearOfAdmission(),
                    "Year of Admission stays historical even in overwrite mode");
        });
        assertEquals("Grade 11", rawRows.get(1).get("formclass"));
    }

    @Test
    @DisplayName("Validation errors clear once the context fills the required fields")
    void validationClearsAfterApply() {
        List<Student> students = new ArrayList<>(List.of(student("A001", "", "", null, null)));
        List<Map<String, String>> rawRows = new ArrayList<>(List.of(raw("", "", "")));

        List<String> before = service.validateRow(rawRows.get(0), students.get(0), List.of());
        assertTrue(before.stream().anyMatch(m -> m.contains("Class / Form is required")));

        service.applyImportContext(rawRows, students, new ImportContext(2026, "Grade 10", null, false));

        List<String> after = service.validateRow(rawRows.get(0), students.get(0), List.of());
        assertTrue(after.isEmpty(), () -> "Expected no errors, got: " + after);
    }

    @Test
    @DisplayName("Empty context and already-matching cells are no-ops")
    void noOpsReturnZero() {
        List<Student> students = new ArrayList<>(List.of(
                student("A001", "Grade 10", "A", 2026, 2026)));
        List<Map<String, String>> rawRows = new ArrayList<>(List.of(raw("Grade 10", "A", "2026")));

        assertEquals(0, service.applyImportContext(rawRows, students,
                new ImportContext(2026, "Grade 10", "A", false)),
                "Matching values are not re-applied or counted");
        assertEquals(0, service.applyImportContext(rawRows, students,
                new ImportContext(null, null, null, true)), "Empty context changes nothing");
        assertEquals("Grade 10", students.get(0).getFormClass(), "Student object untouched by no-ops");
    }

    @Test
    @DisplayName("describe summarises the batch, e.g. '2026 · Grade 10 · Stream A'")
    void describeSummarisesContext() {
        assertEquals("2026 · Grade 10 · Stream A",
                new ImportContext(2026, "Grade 10", "A", false).describe());
        assertEquals("2027 · Form 3", new ImportContext(2027, "Form 3", "", false).describe());
        assertTrue(new ImportContext(2028, null, null, false).describe().equals("2028"));
    }
}
