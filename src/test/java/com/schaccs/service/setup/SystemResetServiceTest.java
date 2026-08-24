package com.schaccs.service.setup;

import com.schaccs.repository.Database;
import com.schaccs.repository.PersistenceService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression: Purge &amp; Reset used to abort with
 * "[SQLITE_ERROR] no such table: academic_calender_periods" because it
 * referenced stale table names (academic_calendar_periods, deleted_students,
 * fee_structure_templates / _items). The reset must wipe the real schema end
 * to end — including student_term_balances and student_categories, which it
 * previously missed entirely.
 */
class SystemResetServiceTest {

    @AfterEach
    void tearDown() {
        PersistenceService.getInstance().clearAll();
        Database.getInstance().close();
    }

    @Test
    @DisplayName("Reset purges every real table, including renamed and previously missed ones")
    void resetPurgesRealSchema() throws Exception {
        Connection conn = Database.getInstance().getConnection();
        try (Statement st = conn.createStatement()) {
            st.execute("INSERT INTO academic_calendar (id, term, from_date, to_date, status) "
                    + "VALUES ('cal-1', 'TERM_1', '2026-01-01', '2026-04-30', 'ACTIVE')");
            st.execute("INSERT INTO recycle_bin (id, admission_number, name) "
                    + "VALUES ('rb-1', 'R/001', 'Deleted Student')");
            st.execute("INSERT INTO fee_templates (id, name) VALUES ('ft-1', 'Boarding Template')");
            st.execute("INSERT INTO fee_template_items (id, template_id, votehead_code, "
                    + "votehead_name, term, boarding_status, amount) "
                    + "VALUES ('fti-1', 'ft-1', 'TUITION', 'Tuition', 'TERM_1', 'BOARDING', '1000')");
        }

        assertDoesNotThrow(SystemResetService::reset,
                "Reset must not reference tables that do not exist");

        try (Statement st2 = Database.getInstance().getConnection().createStatement()) {
            assertEquals(0, count(st2, "academic_calendar"));
            assertEquals(0, count(st2, "recycle_bin"));
            assertEquals(0, count(st2, "fee_templates"));
            assertEquals(0, count(st2, "fee_template_items"));
            assertEquals(0, count(st2, "student_term_balances"));
            assertEquals(0, count(st2, "student_categories"));
        }
    }

    private static int count(Statement st, String table) throws Exception {
        try (ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM " + table)) {
            rs.next();
            return rs.getInt(1);
        }
    }
}
