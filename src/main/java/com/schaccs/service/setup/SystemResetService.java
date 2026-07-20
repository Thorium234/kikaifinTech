package com.schaccs.service.setup;

import com.schaccs.repository.Database;
import com.schaccs.repository.PersistenceService;
import com.schaccs.store.AuditStore;
import com.schaccs.store.BankReconciliationStore;
import com.schaccs.store.FeeStructureStore;
import com.schaccs.store.LedgerStore;
import com.schaccs.store.ReceiptStore;
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
                    st.execute("PRAGMA foreign_keys=ON");
                }
            });

            // Vacuum to reclaim space
            try (Statement st = db.getConnection().createStatement()) {
                st.execute("VACUUM");
            }

            // Clear all in-memory stores
            StudentStore.getInstance().clear();
            FeeStructureStore.getInstance().clear();
            ReceiptStore.getInstance().clear();
            LedgerStore.getInstance().clear();
            VoucherStore.getInstance().clear();
            AuditStore.getInstance().clear();
            BankReconciliationStore.getInstance().clear();
            SchoolCustomStore.getInstance().clear();

            // Persist the empty state so next loadAll picks up an empty DB
            // (this also ensures the meta 'initialized' flag is reset)
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
