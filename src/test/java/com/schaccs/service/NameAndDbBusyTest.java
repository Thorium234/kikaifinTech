package com.schaccs.service;

import com.schaccs.enums.BoardingStatus;
import com.schaccs.enums.StudentStatus;
import com.schaccs.model.student.Student;
import com.schaccs.repository.Database;
import com.schaccs.repository.PersistenceService;
import com.schaccs.store.StudentStore;
import com.schaccs.validation.StudentValidator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;

class NameAndDbBusyTest {

    private final StudentValidator validator = new StudentValidator(StudentStore.getInstance());

    @BeforeEach
    void setUp() {
        PersistenceService.getInstance().clearAll();
    }

    @AfterEach
    void tearDown() {
        PersistenceService.getInstance().clearAll();
    }

    private Student base(String adm, String name) {
        Student s = new Student();
        s.setAdmissionNumber(adm);
        s.setName(name);
        s.setFormClass("Form 1");
        s.setStream("A");
        s.setBoardingStatus(BoardingStatus.DAY);
        s.setGender("M");
        s.setPhone("0700000000");
        s.setStatus(StudentStatus.ACTIVE);
        return s;
    }

    @Test
    void nameAcceptsLettersAndCommonPunctuation() {
        assertTrue(validator.validate(base("N-1", "Alice Njeri Mwangi"), false).isEmpty());
        assertTrue(validator.validate(base("N-2", "Jean d'Arc O'Connor"), false).isEmpty());
        assertTrue(validator.validate(base("N-3", "Samuel A. Ochieng Jr."), false).isEmpty());
    }

    @Test
    void nameRejectsSpecialCharactersAndDigits() {
        assertFalse(validator.validate(base("N-4", "Student#123"), false).isEmpty());
        String digitErr = validator.validate(base("N-5", "Bob2"), false).stream()
                .filter(e -> e.contains("invalid characters")).findFirst().orElse(null);
        assertNotNull(digitErr);
        assertFalse(validator.validate(base("N-6", "name@bad"), false).isEmpty());
    }

    @Test
    void busyRetrySucceedsAfterTransientLock() throws Exception {
        Database db = Database.getInstance();
        final int[] calls = {0};
        db.runWithBusyRetry(() -> {
            calls[0]++;
            if (calls[0] < 3) {
                throw new SQLException("database is locked", "SQLITE_BUSY", 5);
            }
        });
        assertEquals(3, calls[0], "Retried until the transient busy cleared");
    }

    @Test
    void busyDetectionRecognizesLock() {
        assertTrue(Database.isBusy(new SQLException("database is locked", "SQLITE_BUSY", 5)));
        assertTrue(Database.isBusy(new SQLException("SQLITE_BUSY", "SQLITE_BUSY", 6)));
        assertFalse(Database.isBusy(new SQLException("syntax error", "X", 1)));
    }
}
