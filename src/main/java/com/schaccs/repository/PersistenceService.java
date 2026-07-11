package com.schaccs.repository;

import com.schaccs.config.AppConfig;
import com.schaccs.config.CurrencyConfig;
import com.schaccs.config.SchoolProfile;
import com.schaccs.enums.AcademicTerm;
import com.schaccs.enums.AccountType;
import com.schaccs.enums.BoardingStatus;
import com.schaccs.enums.PaymentMode;
import com.schaccs.enums.StudentStatus;
import com.schaccs.enums.TransactionType;
import com.schaccs.enums.VoucherStatus;
import com.schaccs.model.fee.FeeStructure;
import com.schaccs.model.fee.FeeStructureItem;
import com.schaccs.model.finance.FinancialTransaction;
import com.schaccs.model.finance.LedgerEntry;
import com.schaccs.model.finance.Votehead;
import com.schaccs.model.receipt.Receipt;
import com.schaccs.model.receipt.ReceiptLine;
import com.schaccs.model.student.Student;
import com.schaccs.model.student.StudentFeeLedger;
import com.schaccs.model.voucher.Commitment;
import com.schaccs.model.voucher.Creditor;
import com.schaccs.model.voucher.PaymentVoucher;
import com.schaccs.store.AccountStore;
import com.schaccs.store.FeeStructureStore;
import com.schaccs.store.LedgerStore;
import com.schaccs.store.ReceiptStore;
import com.schaccs.store.StudentStore;
import com.schaccs.store.VoucherStore;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Snapshot save/load of in-memory stores. Existing UI and engines stay unchanged.
 */
public final class PersistenceService {

    private static final PersistenceService INSTANCE = new PersistenceService();

    private PersistenceService() {
    }

    public static PersistenceService getInstance() {
        return INSTANCE;
    }

    public boolean hasData() {
        try (Statement st = Database.getInstance().getConnection().createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM students")) {
            return rs.next() && rs.getInt(1) > 0;
        } catch (SQLException e) {
            return false;
        }
    }

    public synchronized void saveAll() {
        try {
            Connection conn = Database.getInstance().getConnection();
            conn.setAutoCommit(false);
            try {
                clearTables(conn);
                saveSettings(conn);
                saveVoteheads(conn);
                saveFeeStructures(conn);
                saveStudents(conn);
                saveReceipts(conn);
                saveLedger(conn);
                saveCreditors(conn);
                saveCommitments(conn);
                saveVouchers(conn);
                conn.commit();
            } catch (Exception e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to save data: " + e.getMessage(), e);
        }
    }

    public synchronized void loadAll() {
        try {
            Connection conn = Database.getInstance().getConnection();
            AccountStore.getInstance().clearAll();
            VoucherStore.getInstance().clear();
            loadSettings(conn);
            loadVoteheads(conn);
            loadFeeStructures(conn);
            loadStudents(conn);
            loadReceipts(conn);
            loadLedger(conn);
            loadCreditors(conn);
            loadCommitments(conn);
            loadVouchers(conn);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load data: " + e.getMessage(), e);
        }
    }

    private void clearTables(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.executeUpdate("DELETE FROM receipt_lines");
            st.executeUpdate("DELETE FROM receipts");
            st.executeUpdate("DELETE FROM student_ledger_lines");
            st.executeUpdate("DELETE FROM student_ledgers");
            st.executeUpdate("DELETE FROM students");
            st.executeUpdate("DELETE FROM fee_structure_items");
            st.executeUpdate("DELETE FROM fee_structures");
            st.executeUpdate("DELETE FROM voteheads");
            st.executeUpdate("DELETE FROM transactions");
            st.executeUpdate("DELETE FROM ledger_entries");
            st.executeUpdate("DELETE FROM payment_vouchers");
            st.executeUpdate("DELETE FROM commitments");
            st.executeUpdate("DELETE FROM creditors");
            st.executeUpdate("DELETE FROM school_settings");
        }
    }

    private void saveSettings(Connection conn) throws SQLException {
        SchoolProfile p = AppConfig.getInstance().getSchoolProfile();
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO school_settings (id, school_name, location, ministry, principal,
                    bank_name, bank_account, pay_bill, pay_bill_account, cash_policy,
                    academic_year, next_receipt_number, next_voucher_number, current_user)
                VALUES (1,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """)) {
            ps.setString(1, p.getSchoolName());
            ps.setString(2, p.getLocation());
            ps.setString(3, p.getMinistry());
            ps.setString(4, p.getPrincipal());
            ps.setString(5, p.getBankName());
            ps.setString(6, p.getBankAccount());
            ps.setString(7, p.getPayBill());
            ps.setString(8, p.getPayBillAccount());
            ps.setString(9, p.getCashPolicy());
            ps.setInt(10, p.getAcademicYear());
            ps.setLong(11, p.getNextReceiptNumber());
            ps.setLong(12, p.getNextVoucherNumber());
            ps.setString(13, AppConfig.getInstance().getCurrentUser());
            ps.executeUpdate();
        }
    }

    private void loadSettings(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM school_settings WHERE id = 1")) {
            if (!rs.next()) {
                return;
            }
            SchoolProfile p = AppConfig.getInstance().getSchoolProfile();
            p.setSchoolName(rs.getString("school_name"));
            p.setLocation(rs.getString("location"));
            p.setMinistry(rs.getString("ministry"));
            p.setPrincipal(rs.getString("principal"));
            p.setBankName(rs.getString("bank_name"));
            p.setBankAccount(rs.getString("bank_account"));
            p.setPayBill(rs.getString("pay_bill"));
            p.setPayBillAccount(rs.getString("pay_bill_account"));
            p.setCashPolicy(rs.getString("cash_policy"));
            p.setAcademicYear(rs.getInt("academic_year"));
            p.setNextReceiptNumber(rs.getLong("next_receipt_number"));
            p.setNextVoucherNumber(rs.getLong("next_voucher_number"));
            String user = rs.getString("current_user");
            if (user != null && !user.isBlank()) {
                AppConfig.getInstance().setCurrentUser(user);
            }
        }
    }

    private void saveVoteheads(Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO voteheads (code, id, name, account_type, priority, active) VALUES (?,?,?,?,?,?)")) {
            for (Votehead v : FeeStructureStore.getInstance().getVoteheads()) {
                ps.setString(1, v.getCode());
                ps.setString(2, v.getId());
                ps.setString(3, v.getName());
                ps.setString(4, enumName(v.getAccountType()));
                ps.setInt(5, v.getPriority());
                ps.setInt(6, v.isActive() ? 1 : 0);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private void loadVoteheads(Connection conn) throws SQLException {
        FeeStructureStore store = FeeStructureStore.getInstance();
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM voteheads ORDER BY priority")) {
            while (rs.next()) {
                Votehead v = new Votehead(
                        rs.getString("code"),
                        rs.getString("name"),
                        AccountType.valueOf(rs.getString("account_type")),
                        rs.getInt("priority"));
                v.setActive(rs.getInt("active") == 1);
                store.addVotehead(v);
            }
        }
    }

    private void saveFeeStructures(Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO fee_structures (id, academic_year, form_class, boarding_status, name) VALUES (?,?,?,?,?)");
             PreparedStatement itemPs = conn.prepareStatement(
                     "INSERT INTO fee_structure_items (id, structure_id, votehead_code, votehead_name, term, boarding_status, amount) VALUES (?,?,?,?,?,?,?)")) {
            for (FeeStructure s : FeeStructureStore.getInstance().getStructures()) {
                ps.setString(1, s.getId());
                ps.setInt(2, s.getAcademicYear());
                ps.setString(3, s.getFormClass());
                ps.setString(4, enumName(s.getBoardingStatus()));
                ps.setString(5, s.getName());
                ps.addBatch();
                for (FeeStructureItem item : s.getItems()) {
                    itemPs.setString(1, item.getId());
                    itemPs.setString(2, s.getId());
                    itemPs.setString(3, item.getVoteheadCode());
                    itemPs.setString(4, item.getVoteheadName());
                    itemPs.setString(5, enumName(item.getTerm()));
                    itemPs.setString(6, enumName(item.getBoardingStatus()));
                    itemPs.setString(7, money(item.getAmount()));
                    itemPs.addBatch();
                }
            }
            ps.executeBatch();
            itemPs.executeBatch();
        }
    }

    private void loadFeeStructures(Connection conn) throws SQLException {
        FeeStructureStore store = FeeStructureStore.getInstance();
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM fee_structures")) {
            while (rs.next()) {
                FeeStructure s = new FeeStructure(
                        rs.getInt("academic_year"),
                        rs.getString("form_class"),
                        BoardingStatus.valueOf(rs.getString("boarding_status")),
                        rs.getString("name"));
                // FeeStructure generates its own id — reload items keyed by DB id via temp map
                store.addStructure(s);
                loadItemsForStructure(conn, rs.getString("id"), s);
            }
        }
    }

    private void loadItemsForStructure(Connection conn, String structureId, FeeStructure s) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT * FROM fee_structure_items WHERE structure_id = ?")) {
            ps.setString(1, structureId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    FeeStructureItem item = new FeeStructureItem(
                            rs.getString("votehead_code"),
                            rs.getString("votehead_name"),
                            AcademicTerm.valueOf(rs.getString("term")),
                            BoardingStatus.valueOf(rs.getString("boarding_status")),
                            parseMoney(rs.getString("amount")));
                    s.addItem(item);
                }
            }
        }
    }

    private void saveStudents(Connection conn) throws SQLException {
        StudentStore store = StudentStore.getInstance();
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO students (id, admission_number, upi, name, gender, form_class, stream,
                    boarding_status, parent_name, phone, year_of_admission, academic_year, status)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)
                """);
             PreparedStatement ledPs = conn.prepareStatement(
                     "INSERT INTO student_ledgers (student_id, arrears, current_term) VALUES (?,?,?)");
             PreparedStatement linePs = conn.prepareStatement(
                     "INSERT INTO student_ledger_lines (student_id, votehead_code, kind, amount) VALUES (?,?,?,?)")) {
            for (Student s : store.getStudents()) {
                ps.setString(1, s.getId());
                ps.setString(2, s.getAdmissionNumber());
                ps.setString(3, s.getUpi());
                ps.setString(4, s.getName());
                ps.setString(5, s.getGender());
                ps.setString(6, s.getFormClass());
                ps.setString(7, s.getStream());
                ps.setString(8, enumName(s.getBoardingStatus()));
                ps.setString(9, s.getParentName());
                ps.setString(10, s.getPhone());
                ps.setObject(11, s.getYearOfAdmission());
                ps.setObject(12, s.getAcademicYear());
                ps.setString(13, enumName(s.getStatus()));
                ps.addBatch();

                StudentFeeLedger ledger = store.getLedger(s.getId());
                ledPs.setString(1, s.getId());
                ledPs.setString(2, money(ledger.getArrears()));
                ledPs.setString(3, enumName(ledger.getCurrentTerm()));
                ledPs.addBatch();

                for (Map.Entry<String, BigDecimal> e : ledger.getChargedByVotehead().entrySet()) {
                    linePs.setString(1, s.getId());
                    linePs.setString(2, e.getKey());
                    linePs.setString(3, "CHARGED");
                    linePs.setString(4, money(e.getValue()));
                    linePs.addBatch();
                }
                for (Map.Entry<String, BigDecimal> e : ledger.getPaidByVotehead().entrySet()) {
                    linePs.setString(1, s.getId());
                    linePs.setString(2, e.getKey());
                    linePs.setString(3, "PAID");
                    linePs.setString(4, money(e.getValue()));
                    linePs.addBatch();
                }
            }
            ps.executeBatch();
            ledPs.executeBatch();
            linePs.executeBatch();
        }
    }

    private void loadStudents(Connection conn) throws SQLException {
        StudentStore store = StudentStore.getInstance();
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM students ORDER BY admission_number")) {
            while (rs.next()) {
                Student s = Student.withId(rs.getString("id"));
                s.setAdmissionNumber(rs.getString("admission_number"));
                s.setUpi(rs.getString("upi"));
                s.setName(rs.getString("name"));
                s.setGender(rs.getString("gender"));
                s.setFormClass(rs.getString("form_class"));
                s.setStream(rs.getString("stream"));
                String board = rs.getString("boarding_status");
                if (board != null) {
                    s.setBoardingStatus(BoardingStatus.valueOf(board));
                }
                s.setParentName(rs.getString("parent_name"));
                s.setPhone(rs.getString("phone"));
                int yoa = rs.getInt("year_of_admission");
                if (!rs.wasNull()) {
                    s.setYearOfAdmission(yoa);
                }
                int ay = rs.getInt("academic_year");
                if (!rs.wasNull()) {
                    s.setAcademicYear(ay);
                }
                String status = rs.getString("status");
                if (status != null) {
                    s.setStatus(StudentStatus.valueOf(status));
                }
                store.add(s);
            }
        }
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM student_ledgers")) {
            while (rs.next()) {
                StudentFeeLedger ledger = store.getLedger(rs.getString("student_id"));
                ledger.setArrears(parseMoney(rs.getString("arrears")));
                String term = rs.getString("current_term");
                if (term != null) {
                    ledger.setCurrentTerm(AcademicTerm.valueOf(term));
                }
            }
        }
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM student_ledger_lines")) {
            while (rs.next()) {
                StudentFeeLedger ledger = store.getLedger(rs.getString("student_id"));
                String kind = rs.getString("kind");
                String code = rs.getString("votehead_code");
                BigDecimal amt = parseMoney(rs.getString("amount"));
                if ("CHARGED".equals(kind)) {
                    ledger.charge(code, amt);
                } else if ("PAID".equals(kind)) {
                    ledger.pay(code, amt);
                }
            }
        }
    }

    private void saveReceipts(Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO receipts (id, receipt_number, date, student_id, admission_number, student_name,
                    class_label, amount, payment_mode, bank_reference, received_by, notes, created_at)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)
                """);
             PreparedStatement linePs = conn.prepareStatement(
                     "INSERT INTO receipt_lines (id, receipt_id, votehead_code, votehead_name, amount) VALUES (?,?,?,?,?)")) {
            for (Receipt r : ReceiptStore.getInstance().getReceipts()) {
                ps.setString(1, r.getId());
                ps.setLong(2, r.getReceiptNumber());
                ps.setString(3, date(r.getDate()));
                ps.setString(4, r.getStudentId());
                ps.setString(5, r.getAdmissionNumber());
                ps.setString(6, r.getStudentName());
                ps.setString(7, r.getClassLabel());
                ps.setString(8, money(r.getAmount()));
                ps.setString(9, enumName(r.getPaymentMode()));
                ps.setString(10, r.getBankReference());
                ps.setString(11, r.getReceivedBy());
                ps.setString(12, r.getNotes());
                ps.setString(13, dateTime(r.getCreatedAt()));
                ps.addBatch();
                for (ReceiptLine line : r.getLines()) {
                    linePs.setString(1, line.getId());
                    linePs.setString(2, r.getId());
                    linePs.setString(3, line.getVoteheadCode());
                    linePs.setString(4, line.getVoteheadName());
                    linePs.setString(5, money(line.getAmount()));
                    linePs.addBatch();
                }
            }
            ps.executeBatch();
            linePs.executeBatch();
        }
    }

    private void loadReceipts(Connection conn) throws SQLException {
        ReceiptStore store = ReceiptStore.getInstance();
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM receipts ORDER BY receipt_number DESC")) {
            while (rs.next()) {
                Receipt r = Receipt.withId(rs.getString("id"));
                r.setReceiptNumber(rs.getLong("receipt_number"));
                r.setDate(parseDate(rs.getString("date")));
                r.setStudentId(rs.getString("student_id"));
                r.setAdmissionNumber(rs.getString("admission_number"));
                r.setStudentName(rs.getString("student_name"));
                r.setClassLabel(rs.getString("class_label"));
                r.setAmount(parseMoney(rs.getString("amount")));
                String mode = rs.getString("payment_mode");
                if (mode != null) {
                    r.setPaymentMode(PaymentMode.valueOf(mode));
                }
                r.setBankReference(rs.getString("bank_reference"));
                r.setReceivedBy(rs.getString("received_by"));
                r.setNotes(rs.getString("notes"));
                String created = rs.getString("created_at");
                if (created != null) {
                    r.setCreatedAt(LocalDateTime.parse(created));
                }
                loadReceiptLines(conn, r);
                store.add(r);
            }
        }
    }

    private void loadReceiptLines(Connection conn, Receipt r) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT * FROM receipt_lines WHERE receipt_id = ?")) {
            ps.setString(1, r.getId());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    r.addLine(new ReceiptLine(
                            rs.getString("votehead_code"),
                            rs.getString("votehead_name"),
                            parseMoney(rs.getString("amount"))));
                }
            }
        }
    }

    private void saveLedger(Connection conn) throws SQLException {
        LedgerStore store = LedgerStore.getInstance();
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO transactions (id, date, type, account_type, votehead_code, reference, description,
                    debit, credit, student_id, receipt_id, voucher_id, created_by, created_at)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """)) {
            for (FinancialTransaction tx : store.getTransactions()) {
                ps.setString(1, tx.getId());
                ps.setString(2, date(tx.getDate()));
                ps.setString(3, enumName(tx.getType()));
                ps.setString(4, enumName(tx.getAccountType()));
                ps.setString(5, tx.getVoteheadCode());
                ps.setString(6, tx.getReference());
                ps.setString(7, tx.getDescription());
                ps.setString(8, money(tx.getDebit()));
                ps.setString(9, money(tx.getCredit()));
                ps.setString(10, tx.getStudentId());
                ps.setString(11, tx.getReceiptId());
                ps.setString(12, tx.getVoucherId());
                ps.setString(13, tx.getCreatedBy());
                ps.setString(14, dateTime(tx.getCreatedAt()));
                ps.addBatch();
            }
            ps.executeBatch();
        }
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO ledger_entries (id, date, account_type, votehead_code, reference, description,
                    debit, credit, balance, transaction_id)
                VALUES (?,?,?,?,?,?,?,?,?,?)
                """)) {
            for (LedgerEntry e : store.getLedgerEntries()) {
                ps.setString(1, e.getId());
                ps.setString(2, date(e.getDate()));
                ps.setString(3, enumName(e.getAccountType()));
                ps.setString(4, e.getVoteheadCode());
                ps.setString(5, e.getReference());
                ps.setString(6, e.getDescription());
                ps.setString(7, money(e.getDebit()));
                ps.setString(8, money(e.getCredit()));
                ps.setString(9, money(e.getBalance()));
                ps.setString(10, e.getTransactionId());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private void loadLedger(Connection conn) throws SQLException {
        LedgerStore store = LedgerStore.getInstance();
        // Load chronological order (oldest first) so balances rebuild correctly
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT * FROM transactions ORDER BY created_at ASC, rowid ASC")) {
            while (rs.next()) {
                FinancialTransaction tx = FinancialTransaction.withId(rs.getString("id"));
                tx.setDate(parseDate(rs.getString("date")));
                String type = rs.getString("type");
                if (type != null) {
                    tx.setType(TransactionType.valueOf(type));
                }
                String acct = rs.getString("account_type");
                if (acct != null) {
                    tx.setAccountType(AccountType.valueOf(acct));
                }
                tx.setVoteheadCode(rs.getString("votehead_code"));
                tx.setReference(rs.getString("reference"));
                tx.setDescription(rs.getString("description"));
                tx.setDebit(parseMoney(rs.getString("debit")));
                tx.setCredit(parseMoney(rs.getString("credit")));
                tx.setStudentId(rs.getString("student_id"));
                tx.setReceiptId(rs.getString("receipt_id"));
                tx.setVoucherId(rs.getString("voucher_id"));
                tx.setCreatedBy(rs.getString("created_by"));
                String created = rs.getString("created_at");
                if (created != null) {
                    tx.setCreatedAt(LocalDateTime.parse(created));
                }
                store.addTransaction(tx);
            }
        }
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT * FROM ledger_entries ORDER BY rowid ASC")) {
            // Rebuild balances via store (addLedgerEntry updates accountBalances)
            // Entries were saved newest-first; reverse by loading rowid ASC from original insert order.
            // We stored newest-first from observable list; rebuild balances from debit/credit only.
            java.util.List<LedgerEntry> entries = new java.util.ArrayList<>();
            while (rs.next()) {
                LedgerEntry e = LedgerEntry.withId(rs.getString("id"));
                e.setDate(parseDate(rs.getString("date")));
                String acct = rs.getString("account_type");
                if (acct != null) {
                    e.setAccountType(AccountType.valueOf(acct));
                }
                e.setVoteheadCode(rs.getString("votehead_code"));
                e.setReference(rs.getString("reference"));
                e.setDescription(rs.getString("description"));
                e.setDebit(parseMoney(rs.getString("debit")));
                e.setCredit(parseMoney(rs.getString("credit")));
                e.setTransactionId(rs.getString("transaction_id"));
                entries.add(e);
            }
            // Apply oldest-first for correct running balances
            for (int i = entries.size() - 1; i >= 0; i--) {
                store.addLedgerEntry(entries.get(i));
            }
        }
    }

    private void saveCreditors(Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO creditors (id, name, phone, description) VALUES (?,?,?,?)")) {
            for (Creditor c : VoucherStore.getInstance().getCreditors()) {
                ps.setString(1, c.getId());
                ps.setString(2, c.getName());
                ps.setString(3, c.getPhone());
                ps.setString(4, c.getDescription());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private void loadCreditors(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM creditors")) {
            while (rs.next()) {
                Creditor c = Creditor.withId(rs.getString("id"));
                c.setName(rs.getString("name"));
                c.setPhone(rs.getString("phone"));
                c.setDescription(rs.getString("description"));
                VoucherStore.getInstance().addCreditor(c);
            }
        }
    }

    private void saveCommitments(Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO commitments (id, date, creditor_id, creditor_name, votehead_code, votehead_name,
                    account_type, description, amount, amount_paid, status, reference)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
                """)) {
            for (Commitment c : VoucherStore.getInstance().getCommitments()) {
                ps.setString(1, c.getId());
                ps.setString(2, date(c.getDate()));
                ps.setString(3, c.getCreditorId());
                ps.setString(4, c.getCreditorName());
                ps.setString(5, c.getVoteheadCode());
                ps.setString(6, c.getVoteheadName());
                ps.setString(7, enumName(c.getAccountType()));
                ps.setString(8, c.getDescription());
                ps.setString(9, money(c.getAmount()));
                ps.setString(10, money(c.getAmountPaid()));
                ps.setString(11, c.getStatus());
                ps.setString(12, c.getReference());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private void loadCommitments(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM commitments ORDER BY date DESC")) {
            while (rs.next()) {
                Commitment c = Commitment.withId(rs.getString("id"));
                c.setDate(parseDate(rs.getString("date")));
                c.setCreditorId(rs.getString("creditor_id"));
                c.setCreditorName(rs.getString("creditor_name"));
                c.setVoteheadCode(rs.getString("votehead_code"));
                c.setVoteheadName(rs.getString("votehead_name"));
                String acct = rs.getString("account_type");
                if (acct != null) {
                    c.setAccountType(AccountType.valueOf(acct));
                }
                c.setDescription(rs.getString("description"));
                c.setAmount(parseMoney(rs.getString("amount")));
                c.setAmountPaid(parseMoney(rs.getString("amount_paid")));
                c.setStatus(rs.getString("status"));
                c.setReference(rs.getString("reference"));
                VoucherStore.getInstance().addCommitment(c);
            }
        }
    }

    private void saveVouchers(Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO payment_vouchers (id, voucher_number, date, creditor_id, creditor_name, commitment_id,
                    votehead_code, votehead_name, account_type, amount, description, status, payment_mode,
                    bank_reference, prepared_by, approved_by, notes, created_at)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """)) {
            for (PaymentVoucher v : VoucherStore.getInstance().getVouchers()) {
                ps.setString(1, v.getId());
                ps.setLong(2, v.getVoucherNumber());
                ps.setString(3, date(v.getDate()));
                ps.setString(4, v.getCreditorId());
                ps.setString(5, v.getCreditorName());
                ps.setString(6, v.getCommitmentId());
                ps.setString(7, v.getVoteheadCode());
                ps.setString(8, v.getVoteheadName());
                ps.setString(9, enumName(v.getAccountType()));
                ps.setString(10, money(v.getAmount()));
                ps.setString(11, v.getDescription());
                ps.setString(12, enumName(v.getStatus()));
                ps.setString(13, enumName(v.getPaymentMode()));
                ps.setString(14, v.getBankReference());
                ps.setString(15, v.getPreparedBy());
                ps.setString(16, v.getApprovedBy());
                ps.setString(17, v.getNotes());
                ps.setString(18, dateTime(v.getCreatedAt()));
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private void loadVouchers(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM payment_vouchers ORDER BY voucher_number DESC")) {
            while (rs.next()) {
                PaymentVoucher v = PaymentVoucher.withId(rs.getString("id"));
                v.setVoucherNumber(rs.getLong("voucher_number"));
                v.setDate(parseDate(rs.getString("date")));
                v.setCreditorId(rs.getString("creditor_id"));
                v.setCreditorName(rs.getString("creditor_name"));
                v.setCommitmentId(rs.getString("commitment_id"));
                v.setVoteheadCode(rs.getString("votehead_code"));
                v.setVoteheadName(rs.getString("votehead_name"));
                String acct = rs.getString("account_type");
                if (acct != null) {
                    v.setAccountType(AccountType.valueOf(acct));
                }
                v.setAmount(parseMoney(rs.getString("amount")));
                v.setDescription(rs.getString("description"));
                String status = rs.getString("status");
                if (status != null) {
                    v.setStatus(VoucherStatus.valueOf(status));
                }
                String mode = rs.getString("payment_mode");
                if (mode != null) {
                    v.setPaymentMode(PaymentMode.valueOf(mode));
                }
                v.setBankReference(rs.getString("bank_reference"));
                v.setPreparedBy(rs.getString("prepared_by"));
                v.setApprovedBy(rs.getString("approved_by"));
                v.setNotes(rs.getString("notes"));
                String created = rs.getString("created_at");
                if (created != null) {
                    v.setCreatedAt(LocalDateTime.parse(created));
                }
                VoucherStore.getInstance().addVoucher(v);
            }
        }
    }

    private static String enumName(Enum<?> e) {
        return e == null ? null : e.name();
    }

    private static String money(BigDecimal v) {
        return CurrencyConfig.money(v).toPlainString();
    }

    private static BigDecimal parseMoney(String s) {
        if (s == null || s.isBlank()) {
            return CurrencyConfig.zero();
        }
        return CurrencyConfig.money(s);
    }

    private static String date(LocalDate d) {
        return d == null ? null : d.toString();
    }

    private static LocalDate parseDate(String s) {
        return s == null || s.isBlank() ? null : LocalDate.parse(s);
    }

    private static String dateTime(LocalDateTime d) {
        return d == null ? null : d.toString();
    }
}
