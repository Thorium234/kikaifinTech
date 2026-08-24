package com.schaccs.service.setup;

import com.schaccs.repository.Database;
import com.schaccs.repository.PersistenceService;
import com.schaccs.store.AccountStore;
import com.schaccs.store.AcademicCalendarStore;
import com.schaccs.store.AuditStore;
import com.schaccs.store.BankReconciliationStore;
import com.schaccs.store.CleanDataStore;
import com.schaccs.store.EmployeeStore;
import com.schaccs.store.FeeStructureStore;
import com.schaccs.store.LedgerStore;
import com.schaccs.store.MidTermEnrollmentStore;
import com.schaccs.store.PayrollStore;
import com.schaccs.store.ProcurementStore;
import com.schaccs.store.ReceiptStore;
import com.schaccs.store.RecycleBinStore;
import com.schaccs.store.SchoolCustomStore;
import com.schaccs.store.StudentStore;
import com.schaccs.store.VoucherStore;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public final class SystemResetService {

    private SystemResetService() {}

    public static void reset() {
        Database db = Database.getInstance();
        PersistenceService psvc = PersistenceService.getInstance();

        try {
            db.inTransaction((Connection conn) -> {
                try (Statement st = conn.createStatement()) {
                    st.execute("PRAGMA foreign_keys=OFF");

                    st.execute("DELETE FROM receipt_lines");
                    st.execute("DELETE FROM receipts");
                    st.execute("DELETE FROM student_ledger_lines");
                    st.execute("DELETE FROM student_ledgers");
                    st.execute("DELETE FROM students");
                    st.execute("DELETE FROM fee_structure_items");
                    st.execute("DELETE FROM fee_structures");
                    st.execute("DELETE FROM voteheads");
                    st.execute("DELETE FROM student_categories");
                    st.execute("DELETE FROM transactions");
                    st.execute("DELETE FROM ledger_entries");
                    st.execute("DELETE FROM payment_vouchers");
                    st.execute("DELETE FROM commitments");
                    st.execute("DELETE FROM creditors");
                    st.execute("DELETE FROM lpos");
                    st.execute("DELETE FROM invoices");
                    st.execute("DELETE FROM imprests");
                    st.execute("DELETE FROM audit_log");
                    st.execute("DELETE FROM bank_reconciliation_items");
                    st.execute("DELETE FROM bank_reconciliation");
                    st.execute("DELETE FROM school_form_classes");
                    st.execute("DELETE FROM school_streams");

                    st.execute("DELETE FROM accounts");
                    st.execute("DELETE FROM fiscal_years");
                    st.execute("DELETE FROM budget_lines");
                    st.execute("DELETE FROM budgets");
                    st.execute("DELETE FROM asset_categories");
                    st.execute("DELETE FROM assets");
                    st.execute("DELETE FROM depreciation_schedules");
                    st.execute("DELETE FROM salary_structures");
                    st.execute("DELETE FROM employees");
                    st.execute("DELETE FROM payroll_items");
                    st.execute("DELETE FROM payroll_runs");
                    st.execute("DELETE FROM procurement_approvals");
                    st.execute("DELETE FROM contract_milestones");
                    st.execute("DELETE FROM contracts");
                    st.execute("DELETE FROM tender_awards");
                    st.execute("DELETE FROM tender_evaluations");
                    st.execute("DELETE FROM tender_bids");
                    st.execute("DELETE FROM tenders");
                    st.execute("DELETE FROM procurement_requests");
                    st.execute("DELETE FROM suppliers");
                    st.execute("DELETE FROM mid_term_enrollments");
                    st.execute("DELETE FROM academic_calendar");
                    st.execute("DELETE FROM student_term_balances");
                    st.execute("DELETE FROM recycle_bin");
                    st.execute("DELETE FROM clean_data");
                    st.execute("DELETE FROM fee_template_items");
                    st.execute("DELETE FROM fee_templates");

                    st.execute("PRAGMA foreign_keys=ON");
                }
            });

            try (Statement st = db.getConnection().createStatement()) {
                st.execute("VACUUM");
            }

            StudentStore.getInstance().clear();
            FeeStructureStore.getInstance().clear();
            ReceiptStore.getInstance().clear();
            LedgerStore.getInstance().clear();
            VoucherStore.getInstance().clear();
            AuditStore.getInstance().clear();
            BankReconciliationStore.getInstance().clear();
            SchoolCustomStore.getInstance().clear();
            AccountStore.getInstance().clear();
            AcademicCalendarStore.getInstance().clear();
            EmployeeStore.getInstance().clear();
            PayrollStore.getInstance().clear();
            ProcurementStore.getInstance().clear();
            MidTermEnrollmentStore.getInstance().clear();
            RecycleBinStore.getInstance().clear();
            CleanDataStore.getInstance().clear();

            psvc.transactional(conn -> {
                try (Statement st = conn.createStatement()) {
                    st.execute("DELETE FROM meta WHERE key = 'initialized'");
                }
            });

            psvc.saveAll();

        } catch (SQLException e) {
            throw new RuntimeException("System reset failed: " + e.getMessage(), e);
        }
    }
}
