package com.schaccs.service;

import com.schaccs.enums.BoardingStatus;
import com.schaccs.model.student.Student;
import com.schaccs.repository.PersistenceService;
import com.schaccs.store.StudentStore;
import com.schaccs.ui.payments.PayView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies the two lookup paths the Pay workspace (and receipting search) rely
 * on: StudentStore.search (used verbatim by ReceiptView's filterStudents) and
 * PayView's form/stream/search filter predicate.
 */
class PayFeatureTest {

    @BeforeEach
    void setUp() {
        PersistenceService.getInstance().clearAll();
        addStudent("S001", "John Kamau", "Form 1", "A", "0700111111");
        addStudent("S002", "Mary Wanjiru", "Form 1", "B", "0700222222");
        addStudent("S003", "Peter Otieno", "Form 2", "A", "0700333333");
        addStudent("S004", "Grace Akinyi", "Form 3", "W", "0700444444");
    }

    @AfterEach
    void tearDown() {
        PersistenceService.getInstance().clearAll();
    }

    private Student addStudent(String adm, String name, String form, String stream, String phone) {
        Student s = new Student();
        s.setAdmissionNumber(adm);
        s.setName(name);
        s.setFormClass(form);
        s.setStream(stream);
        s.setPhone(phone);
        s.setBoardingStatus(BoardingStatus.DAY);
        StudentStore.getInstance().add(s);
        return s;
    }

    @Test
    @DisplayName("ReceiptView search: blank returns all students")
    void searchBlankReturnsAll() {
        assertEquals(4, StudentStore.getInstance().search("").size());
        assertEquals(4, StudentStore.getInstance().search(null).size());
        assertEquals(4, StudentStore.getInstance().search("   ").size());
    }

    @Test
    @DisplayName("ReceiptView search: admission number finds the learner")
    void searchByAdmissionNumber() {
        List<Student> result = StudentStore.getInstance().search("S003");
        assertEquals(1, result.size());
        assertEquals("S003", result.get(0).getAdmissionNumber());
    }

    @Test
    @DisplayName("ReceiptView search: name substring is case-insensitive")
    void searchByNameSubstring() {
        List<Student> result = StudentStore.getInstance().search("wanjiru");
        assertEquals(1, result.size());
        assertEquals("S002", result.get(0).getAdmissionNumber());
    }

    @Test
    @DisplayName("ReceiptView search: class label is searchable")
    void searchByClassLabel() {
        assertEquals(2, StudentStore.getInstance().search("Form 1").size());
    }

    @Test
    @DisplayName("PayView filter: form narrows the list")
    void filterByForm() {
        long form1 = StudentStore.getInstance().getStudents().stream()
                .filter(s -> PayView.matchesFilters(s, "Form 1", null, null)).count();
        assertEquals(2, form1);
        long form2 = StudentStore.getInstance().getStudents().stream()
                .filter(s -> PayView.matchesFilters(s, "Form 2", null, null)).count();
        assertEquals(1, form2);
    }

    @Test
    @DisplayName("PayView filter: stream narrows the list")
    void filterByStream() {
        long streamA = StudentStore.getInstance().getStudents().stream()
                .filter(s -> PayView.matchesFilters(s, null, "A", null)).count();
        assertEquals(2, streamA);
    }

    @Test
    @DisplayName("PayView filter: form + stream combine")
    void filterByFormAndStream() {
        long combined = StudentStore.getInstance().getStudents().stream()
                .filter(s -> PayView.matchesFilters(s, "Form 1", "A", null)).count();
        assertEquals(1, combined);
    }

    @Test
    @DisplayName("PayView filter: query applies on top of form and stream")
    void filterCombinedWithQuery() {
        long result = StudentStore.getInstance().getStudents().stream()
                .filter(s -> PayView.matchesFilters(s, "Form 1", null, "S002")).count();
        assertEquals(1, result);
    }

    @Test
    @DisplayName("PayView filter: no filters and blank query keep everyone")
    void noFiltersKeepsEveryone() {
        assertEquals(4, StudentStore.getInstance().getStudents().stream()
                .filter(s -> PayView.matchesFilters(s, null, null, "")).count());
    }
}
