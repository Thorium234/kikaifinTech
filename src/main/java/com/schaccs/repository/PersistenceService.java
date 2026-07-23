package com.schaccs.repository;

import com.schaccs.config.AppConfig;
import com.schaccs.config.CurrencyConfig;
import com.schaccs.config.SchoolProfile;
import com.schaccs.enums.AcademicTerm;
import com.schaccs.enums.AccountType;
import com.schaccs.enums.BoardingStatus;
import com.schaccs.enums.NormalBalance;
import com.schaccs.enums.StatementCategory;
import com.schaccs.enums.PaymentMode;
import com.schaccs.enums.StudentStatus;
import com.schaccs.enums.TransactionType;
import com.schaccs.enums.VoucherStatus;
import com.schaccs.model.fee.FeeStructure;
import com.schaccs.model.fee.FeeStructureItem;
import com.schaccs.model.finance.Account;
import com.schaccs.model.finance.Asset;
import com.schaccs.model.finance.AssetCategory;
import com.schaccs.model.finance.Budget;
import com.schaccs.model.finance.BudgetLine;
import com.schaccs.model.finance.DepreciationSchedule;
import com.schaccs.model.finance.FinancialTransaction;
import com.schaccs.model.finance.FiscalYear;
import com.schaccs.model.finance.LedgerEntry;
import com.schaccs.model.finance.Votehead;
import com.schaccs.model.receipt.Receipt;
import com.schaccs.model.receipt.ReceiptLine;
import com.schaccs.model.student.Student;
import com.schaccs.model.student.StudentFeeLedger;
import com.schaccs.model.voucher.Commitment;
import com.schaccs.model.voucher.Creditor;
import com.schaccs.model.voucher.PaymentVoucher;
import com.schaccs.model.voucher.Lpo;
import com.schaccs.model.voucher.Invoice;
import com.schaccs.model.voucher.Imprest;
import com.schaccs.store.AccountStore;
import com.schaccs.store.FeeStructureStore;
import com.schaccs.store.LedgerStore;
import com.schaccs.store.ReceiptStore;
import com.schaccs.store.StudentStore;
import com.schaccs.store.AuditStore;
import com.schaccs.store.BankReconciliationStore;
import com.schaccs.store.SchoolCustomStore;
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
             ResultSet rs = st.executeQuery("SELECT value FROM meta WHERE key = 'initialized'")) {
            if (rs.next() && "true".equals(rs.getString("value"))) {
                return true;
            }
        } catch (SQLException ignored) {
            // meta table may not exist on very old databases; fall back to student count
        }
        try (Statement st = Database.getInstance().getConnection().createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM students")) {
            return rs.next() && rs.getInt(1) > 0;
        } catch (SQLException e) {
            return false;
        }
    }

    public synchronized void saveAll() {
        transactional(conn -> {
            saveSettings(conn);
            markInitialized(conn);
            saveVoteheads(conn);
            saveFeeStructures(conn);
            saveStudents(conn);
            saveReceipts(conn);
            saveLedger(conn);
            saveCreditors(conn);
            saveCommitments(conn);
            saveVouchers(conn);
            saveLpos(conn);
            saveInvoices(conn);
            saveImprests(conn);
            saveAuditLog(conn);
            saveBankReconciliation(conn);
            saveSchoolCustom(conn);
            saveAccountStoreEntities(conn);
        });
    }

    public synchronized void loadAll() {
        try {
            Connection conn = Database.getInstance().getConnection();
            AccountStore.getInstance().clear();
            StudentStore.getInstance().clear();
            FeeStructureStore.getInstance().clear();
            ReceiptStore.getInstance().clear();
            LedgerStore.getInstance().clear();
            VoucherStore.getInstance().clear();
            AuditStore.getInstance().clear();
            BankReconciliationStore.getInstance().clear();
            SchoolCustomStore.getInstance().clear();
            loadSettings(conn);
            loadVoteheads(conn);
            loadFeeStructures(conn);
            loadStudents(conn);
            loadReceipts(conn);
            loadLedger(conn);
            loadCreditors(conn);
            loadCommitments(conn);
            loadVouchers(conn);
            loadLpos(conn);
            loadInvoices(conn);
            loadImprests(conn);
            loadAuditLog(conn);
            loadBankReconciliation(conn);
            loadSchoolCustom(conn);
            loadAccountStoreEntities(conn);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load data: " + e.getMessage(), e);
        }
    }

    public void transactional(Database.SqlRunnable action) {
        try {
            Database.getInstance().inTransaction(action);
        } catch (SQLException e) {
            throw new RuntimeException("Transactional operation failed: " + e.getMessage(), e);
        }
    }

    public <T> T transactionalResult(Database.SqlFunction<T> action) {
        try {
            return Database.getInstance().inTransaction(action);
        } catch (SQLException e) {
            throw new RuntimeException("Transactional operation failed: " + e.getMessage(), e);
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
            st.executeUpdate("DELETE FROM lpos");
            st.executeUpdate("DELETE FROM invoices");
            st.executeUpdate("DELETE FROM imprests");
            st.executeUpdate("DELETE FROM audit_log");
            st.executeUpdate("DELETE FROM bank_reconciliation_items");
            st.executeUpdate("DELETE FROM bank_reconciliation");
            st.executeUpdate("DELETE FROM school_form_classes");
            st.executeUpdate("DELETE FROM school_streams");
            st.executeUpdate("DELETE FROM depreciation_schedules");
            st.executeUpdate("DELETE FROM assets");
            st.executeUpdate("DELETE FROM asset_categories");
            st.executeUpdate("DELETE FROM budget_lines");
            st.executeUpdate("DELETE FROM budgets");
            st.executeUpdate("DELETE FROM fiscal_years");
            st.executeUpdate("DELETE FROM accounts");
        }
    }

    private void saveSettings(Connection conn) throws SQLException {
        SchoolProfile p = AppConfig.getInstance().getSchoolProfile();
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO school_settings (id, school_name, location, ministry, principal,
                    bank_name, bank_account, pay_bill, pay_bill_account, cash_policy,
                    academic_year, next_receipt_number, next_voucher_number, current_user,
                    sibling_discount_enabled, sibling_discount_rate, logo_path, stamp_path, signature_path,
                    pdf_stamp_enabled)
                VALUES (1,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                ON CONFLICT(id) DO UPDATE SET
                    school_name=excluded.school_name, location=excluded.location, ministry=excluded.ministry,
                    principal=excluded.principal, bank_name=excluded.bank_name, bank_account=excluded.bank_account,
                    pay_bill=excluded.pay_bill, pay_bill_account=excluded.pay_bill_account, cash_policy=excluded.cash_policy,
                    academic_year=excluded.academic_year, next_receipt_number=excluded.next_receipt_number,
                    next_voucher_number=excluded.next_voucher_number, current_user=excluded.current_user,
                    sibling_discount_enabled=excluded.sibling_discount_enabled,
                    sibling_discount_rate=excluded.sibling_discount_rate,
                    logo_path=excluded.logo_path,
                    stamp_path=excluded.stamp_path,
                    signature_path=excluded.signature_path,
                    pdf_stamp_enabled=excluded.pdf_stamp_enabled
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
            ps.setInt(14, p.isSiblingDiscountEnabled() ? 1 : 0);
            ps.setString(15, money(p.getSiblingDiscountRate()));
            ps.setString(16, p.getLogoPath());
            ps.setString(17, p.getStampPath());
            ps.setString(18, p.getSignaturePath());
            ps.setInt(19, p.isPdfStampEnabled() ? 1 : 0);
            ps.executeUpdate();
        }
    }

    private void markInitialized(Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO meta (key, value) VALUES ('initialized', 'true') "
                        + "ON CONFLICT(key) DO UPDATE SET value = 'true'")) {
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
            p.setSiblingDiscountEnabled(rs.getInt("sibling_discount_enabled") == 1);
            p.setSiblingDiscountRate(parseMoney(rs.getString("sibling_discount_rate")));
            p.setLogoPath(rs.getString("logo_path"));
            p.setStampPath(rs.getString("stamp_path"));
            p.setSignaturePath(rs.getString("signature_path"));
            p.setPdfStampEnabled(rs.getInt("pdf_stamp_enabled") != 0);
            String user = rs.getString("current_user");
            if (user != null && !user.isBlank()) {
                AppConfig.getInstance().setCurrentUser(user);
            }
        }
    }

    private void saveVoteheads(Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO voteheads (code, id, name, account_type, priority, active, annual_budget, termly_budget) "
                        + "VALUES (?,?,?,?,?,?,?,?) "
                        + "ON CONFLICT(code) DO UPDATE SET id=excluded.id, name=excluded.name, "
                        + "account_type=excluded.account_type, priority=excluded.priority, active=excluded.active, "
                        + "annual_budget=excluded.annual_budget, termly_budget=excluded.termly_budget")) {
            for (Votehead v : FeeStructureStore.getInstance().getVoteheads()) {
                ps.setString(1, v.getCode());
                ps.setString(2, v.getId());
                ps.setString(3, v.getName());
                ps.setString(4, enumName(v.getAccountType()));
                ps.setInt(5, v.getPriority());
                ps.setInt(6, v.isActive() ? 1 : 0);
                ps.setString(7, money(v.getAnnualBudget()));
                ps.setString(8, money(v.getTermlyBudget()));
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
                v.setAnnualBudget(parseMoney(rs.getString("annual_budget")));
                v.setTermlyBudget(parseMoney(rs.getString("termly_budget")));
                store.addVotehead(v);
            }
        }
    }

    private void saveFeeStructures(Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO fee_structures (id, academic_year, form_class, boarding_status, name) VALUES (?,?,?,?,?) "
                        + "ON CONFLICT(id) DO UPDATE SET academic_year=excluded.academic_year, "
                        + "form_class=excluded.form_class, boarding_status=excluded.boarding_status, name=excluded.name");
             PreparedStatement itemPs = conn.prepareStatement(
                     "INSERT INTO fee_structure_items (id, structure_id, votehead_code, votehead_name, term, boarding_status, amount) "
                             + "VALUES (?,?,?,?,?,?,?) ON CONFLICT(id) DO UPDATE SET structure_id=excluded.structure_id, "
                             + "votehead_code=excluded.votehead_code, votehead_name=excluded.votehead_name, "
                             + "term=excluded.term, boarding_status=excluded.boarding_status, amount=excluded.amount")) {
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
                    boarding_status, parent_name, guardian_phone, guardian_id, guardian_key, phone, avatar_path, year_of_admission, academic_year, status)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                ON CONFLICT(id) DO UPDATE SET admission_number=excluded.admission_number, upi=excluded.upi,
                    name=excluded.name, gender=excluded.gender, form_class=excluded.form_class, stream=excluded.stream,
                    boarding_status=excluded.boarding_status, parent_name=excluded.parent_name, guardian_phone=excluded.guardian_phone, guardian_id=excluded.guardian_id, guardian_key=excluded.guardian_key, phone=excluded.phone,
                    avatar_path=excluded.avatar_path, year_of_admission=excluded.year_of_admission, academic_year=excluded.academic_year, status=excluded.status
                """);
             PreparedStatement ledPs = conn.prepareStatement(
                     "INSERT INTO student_ledgers (student_id, arrears, advance, current_term) VALUES (?,?,?,?) "
                             + "ON CONFLICT(student_id) DO UPDATE SET arrears=excluded.arrears, "
                             + "advance=excluded.advance, current_term=excluded.current_term");
             PreparedStatement linePs = conn.prepareStatement(
                     "INSERT INTO student_ledger_lines (student_id, votehead_code, kind, amount) VALUES (?,?,?,?) "
                             + "ON CONFLICT(student_id, votehead_code, kind) DO UPDATE SET amount=excluded.amount");
             PreparedStatement clearLines = conn.prepareStatement(
                     "DELETE FROM student_ledger_lines WHERE student_id = ?")) {
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
                ps.setString(10, s.getGuardianPhone());
                ps.setString(11, s.getGuardianId());
                ps.setString(12, s.getGuardianKey());
                ps.setString(13, s.getPhone());
                ps.setString(14, s.getAvatarPath());
                ps.setObject(15, s.getYearOfAdmission());
                ps.setObject(16, s.getAcademicYear());
                ps.setString(17, enumName(s.getStatus()));
                ps.addBatch();

                StudentFeeLedger ledger = store.getLedger(s.getId());
                ledPs.setString(1, s.getId());
                ledPs.setString(2, money(ledger.getArrears()));
                ledPs.setString(3, money(ledger.getAdvance()));
                ledPs.setString(4, enumName(ledger.getCurrentTerm()));
                ledPs.addBatch();

                clearLines.setString(1, s.getId());
                clearLines.addBatch();

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
            clearLines.executeBatch();
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
                s.setGuardianPhone(rs.getString("guardian_phone"));
                s.setGuardianId(rs.getString("guardian_id"));
                s.setGuardianKey(rs.getString("guardian_key"));
                s.setPhone(rs.getString("phone"));
                s.setAvatarPath(rs.getString("avatar_path"));
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
                ledger.setAdvance(parseMoney(rs.getString("advance")));
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
                    class_label, amount, payment_mode, bank_reference, received_by, notes, created_at, reversed, verification_hash)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                ON CONFLICT(id) DO UPDATE SET receipt_number=excluded.receipt_number, date=excluded.date,
                    student_id=excluded.student_id, admission_number=excluded.admission_number,
                    student_name=excluded.student_name, class_label=excluded.class_label, amount=excluded.amount,
                    payment_mode=excluded.payment_mode, bank_reference=excluded.bank_reference,
                    received_by=excluded.received_by, notes=excluded.notes, created_at=excluded.created_at,
                    reversed=excluded.reversed, verification_hash=excluded.verification_hash
                """);
             PreparedStatement linePs = conn.prepareStatement(
                     "INSERT INTO receipt_lines (id, receipt_id, votehead_code, votehead_name, amount) "
                             + "VALUES (?,?,?,?,?) ON CONFLICT(id) DO UPDATE SET receipt_id=excluded.receipt_id, "
                             + "votehead_code=excluded.votehead_code, votehead_name=excluded.votehead_name, "
                             + "amount=excluded.amount")) {
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
                ps.setInt(14, r.isReversed() ? 1 : 0);
                ps.setString(15, r.getVerificationHash());
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
                r.setReversed(rs.getInt("reversed") == 1);
                r.setVerificationHash(rs.getString("verification_hash"));
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
                ON CONFLICT(id) DO UPDATE SET date=excluded.date, type=excluded.type, account_type=excluded.account_type,
                    votehead_code=excluded.votehead_code, reference=excluded.reference, description=excluded.description,
                    debit=excluded.debit, credit=excluded.credit, student_id=excluded.student_id,
                    receipt_id=excluded.receipt_id, voucher_id=excluded.voucher_id, created_by=excluded.created_by,
                    created_at=excluded.created_at
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
                ON CONFLICT(id) DO UPDATE SET date=excluded.date, account_type=excluded.account_type,
                    votehead_code=excluded.votehead_code, reference=excluded.reference, description=excluded.description,
                    debit=excluded.debit, credit=excluded.credit, balance=excluded.balance,
                    transaction_id=excluded.transaction_id
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
                "INSERT INTO creditors (id, name, phone, description) VALUES (?,?,?,?) "
                        + "ON CONFLICT(id) DO UPDATE SET name=excluded.name, phone=excluded.phone, "
                        + "description=excluded.description")) {
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
                ON CONFLICT(id) DO UPDATE SET date=excluded.date, creditor_id=excluded.creditor_id,
                    creditor_name=excluded.creditor_name, votehead_code=excluded.votehead_code,
                    votehead_name=excluded.votehead_name, account_type=excluded.account_type,
                    description=excluded.description, amount=excluded.amount, amount_paid=excluded.amount_paid,
                    status=excluded.status, reference=excluded.reference
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
                ON CONFLICT(id) DO UPDATE SET voucher_number=excluded.voucher_number, date=excluded.date,
                    creditor_id=excluded.creditor_id, creditor_name=excluded.creditor_name,
                    commitment_id=excluded.commitment_id, votehead_code=excluded.votehead_code,
                    votehead_name=excluded.votehead_name, account_type=excluded.account_type, amount=excluded.amount,
                    description=excluded.description, status=excluded.status, payment_mode=excluded.payment_mode,
                    bank_reference=excluded.bank_reference, prepared_by=excluded.prepared_by,
                    approved_by=excluded.approved_by, notes=excluded.notes, created_at=excluded.created_at
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

    private void saveLpos(Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO lpos (id, lpo_number, date, creditor_id, creditor_name, votehead_code,
                    votehead_name, account_type, description, amount, status)
                VALUES (?,?,?,?,?,?,?,?,?,?,?)
                ON CONFLICT(id) DO UPDATE SET lpo_number=excluded.lpo_number, date=excluded.date,
                    creditor_id=excluded.creditor_id, creditor_name=excluded.creditor_name,
                    votehead_code=excluded.votehead_code, votehead_name=excluded.votehead_name,
                    account_type=excluded.account_type, description=excluded.description, amount=excluded.amount,
                    status=excluded.status
                """)) {
            for (Lpo l : VoucherStore.getInstance().getLpos()) {
                ps.setString(1, l.getId());
                ps.setString(2, l.getLpoNumber());
                ps.setString(3, date(l.getDate()));
                ps.setString(4, l.getCreditorId());
                ps.setString(5, l.getCreditorName());
                ps.setString(6, l.getVoteheadCode());
                ps.setString(7, l.getVoteheadName());
                ps.setString(8, enumName(l.getAccountType()));
                ps.setString(9, l.getDescription());
                ps.setString(10, money(l.getAmount()));
                ps.setString(11, l.getStatus());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private void loadLpos(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM lpos")) {
            while (rs.next()) {
                Lpo l = Lpo.withId(rs.getString("id"));
                l.setLpoNumber(rs.getString("lpo_number"));
                l.setDate(parseDate(rs.getString("date")));
                l.setCreditorId(rs.getString("creditor_id"));
                l.setCreditorName(rs.getString("creditor_name"));
                l.setVoteheadCode(rs.getString("votehead_code"));
                l.setVoteheadName(rs.getString("votehead_name"));
                String acct = rs.getString("account_type");
                if (acct != null) {
                    l.setAccountType(AccountType.valueOf(acct));
                }
                l.setDescription(rs.getString("description"));
                l.setAmount(parseMoney(rs.getString("amount")));
                l.setStatus(rs.getString("status"));
                VoucherStore.getInstance().addLpo(l);
            }
        }
    }

    private void saveInvoices(Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO invoices (id, invoice_number, date, creditor_id, creditor_name, lpo_id,
                    votehead_code, votehead_name, account_type, description, amount, status)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
                ON CONFLICT(id) DO UPDATE SET invoice_number=excluded.invoice_number, date=excluded.date,
                    creditor_id=excluded.creditor_id, creditor_name=excluded.creditor_name, lpo_id=excluded.lpo_id,
                    votehead_code=excluded.votehead_code, votehead_name=excluded.votehead_name,
                    account_type=excluded.account_type, description=excluded.description, amount=excluded.amount,
                    status=excluded.status
                """)) {
            for (Invoice i : VoucherStore.getInstance().getInvoices()) {
                ps.setString(1, i.getId());
                ps.setString(2, i.getInvoiceNumber());
                ps.setString(3, date(i.getDate()));
                ps.setString(4, i.getCreditorId());
                ps.setString(5, i.getCreditorName());
                ps.setString(6, i.getLpoId());
                ps.setString(7, i.getVoteheadCode());
                ps.setString(8, i.getVoteheadName());
                ps.setString(9, enumName(i.getAccountType()));
                ps.setString(10, i.getDescription());
                ps.setString(11, money(i.getAmount()));
                ps.setString(12, i.getStatus());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private void loadInvoices(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM invoices")) {
            while (rs.next()) {
                Invoice i = Invoice.withId(rs.getString("id"));
                i.setInvoiceNumber(rs.getString("invoice_number"));
                i.setDate(parseDate(rs.getString("date")));
                i.setCreditorId(rs.getString("creditor_id"));
                i.setCreditorName(rs.getString("creditor_name"));
                i.setLpoId(rs.getString("lpo_id"));
                i.setVoteheadCode(rs.getString("votehead_code"));
                i.setVoteheadName(rs.getString("votehead_name"));
                String acct = rs.getString("account_type");
                if (acct != null) {
                    i.setAccountType(AccountType.valueOf(acct));
                }
                i.setDescription(rs.getString("description"));
                i.setAmount(parseMoney(rs.getString("amount")));
                i.setStatus(rs.getString("status"));
                VoucherStore.getInstance().addInvoice(i);
            }
        }
    }

    private void saveImprests(Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO imprests (id, staff_name, date, amount, votehead_code, votehead_name,
                    account_type, purpose, status, surrendered_amount, surrender_date)
                VALUES (?,?,?,?,?,?,?,?,?,?,?)
                ON CONFLICT(id) DO UPDATE SET staff_name=excluded.staff_name, date=excluded.date,
                    amount=excluded.amount, votehead_code=excluded.votehead_code, votehead_name=excluded.votehead_name,
                    account_type=excluded.account_type, purpose=excluded.purpose, status=excluded.status,
                    surrendered_amount=excluded.surrendered_amount, surrender_date=excluded.surrender_date
                """)) {
            for (Imprest imp : VoucherStore.getInstance().getImprests()) {
                ps.setString(1, imp.getId());
                ps.setString(2, imp.getStaffName());
                ps.setString(3, date(imp.getDate()));
                ps.setString(4, money(imp.getAmount()));
                ps.setString(5, imp.getVoteheadCode());
                ps.setString(6, imp.getVoteheadName());
                ps.setString(7, enumName(imp.getAccountType()));
                ps.setString(8, imp.getPurpose());
                ps.setString(9, imp.getStatus());
                ps.setString(10, money(imp.getSurrenderedAmount()));
                ps.setString(11, date(imp.getSurrenderDate()));
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private void loadImprests(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM imprests")) {
            while (rs.next()) {
                Imprest imp = Imprest.withId(rs.getString("id"));
                imp.setStaffName(rs.getString("staff_name"));
                imp.setDate(parseDate(rs.getString("date")));
                imp.setAmount(parseMoney(rs.getString("amount")));
                imp.setVoteheadCode(rs.getString("votehead_code"));
                imp.setVoteheadName(rs.getString("votehead_name"));
                String acct = rs.getString("account_type");
                if (acct != null) {
                    imp.setAccountType(AccountType.valueOf(acct));
                }
                imp.setPurpose(rs.getString("purpose"));
                imp.setStatus(rs.getString("status"));
                imp.setSurrenderedAmount(parseMoney(rs.getString("surrendered_amount")));
                imp.setSurrenderDate(parseDate(rs.getString("surrender_date")));
                VoucherStore.getInstance().addImprest(imp);
            }
        }
    }

    private void saveAuditLog(Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO audit_log (id, timestamp, action_type, entity_type, entity_id, details_json, performed_by, field_name, old_value, new_value) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,?) ON CONFLICT(id) DO UPDATE SET "
                        + "timestamp=excluded.timestamp, action_type=excluded.action_type, "
                        + "entity_type=excluded.entity_type, entity_id=excluded.entity_id, "
                        + "details_json=excluded.details_json, performed_by=excluded.performed_by, "
                        + "field_name=excluded.field_name, old_value=excluded.old_value, new_value=excluded.new_value")) {
            for (var entry : AuditStore.getInstance().getEntries()) {
                ps.setString(1, entry.getId());
                ps.setString(2, dateTime(entry.getTimestamp()));
                ps.setString(3, entry.getActionType());
                ps.setString(4, entry.getEntityType());
                ps.setString(5, entry.getEntityId());
                ps.setString(6, entry.getDetailsJson());
                ps.setString(7, entry.getPerformedBy());
                ps.setString(8, entry.getFieldName());
                ps.setString(9, entry.getOldValue());
                ps.setString(10, entry.getNewValue());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private void loadAuditLog(Connection conn) throws SQLException {
        AuditStore store = AuditStore.getInstance();
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM audit_log ORDER BY timestamp DESC")) {
            while (rs.next()) {
                com.schaccs.model.audit.AuditLog entry = com.schaccs.model.audit.AuditLog.withId(rs.getString("id"));
                String ts = rs.getString("timestamp");
                if (ts != null) entry.setTimestamp(LocalDateTime.parse(ts));
                entry.setActionType(rs.getString("action_type"));
                entry.setEntityType(rs.getString("entity_type"));
                entry.setEntityId(rs.getString("entity_id"));
                entry.setDetailsJson(rs.getString("details_json"));
                entry.setPerformedBy(rs.getString("performed_by"));
                entry.setFieldName(rs.getString("field_name"));
                entry.setOldValue(rs.getString("old_value"));
                entry.setNewValue(rs.getString("new_value"));
                store.add(entry);
            }
        }
    }

    private void saveBankReconciliation(Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO bank_reconciliation (id, statement_date, statement_balance, book_balance, "
                        + "adjusted_balance, difference, status, created_at, reconciled_at, notes) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,?) ON CONFLICT(id) DO UPDATE SET "
                        + "statement_date=excluded.statement_date, statement_balance=excluded.statement_balance, "
                        + "book_balance=excluded.book_balance, adjusted_balance=excluded.adjusted_balance, "
                        + "difference=excluded.difference, status=excluded.status, "
                        + "created_at=excluded.created_at, reconciled_at=excluded.reconciled_at, notes=excluded.notes");
             PreparedStatement itemPs = conn.prepareStatement(
                     "INSERT INTO bank_reconciliation_items (id, reconciliation_id, type, reference, description, "
                             + "amount, cleared) VALUES (?,?,?,?,?,?,?) "
                             + "ON CONFLICT(id) DO UPDATE SET type=excluded.type, reference=excluded.reference, "
                             + "description=excluded.description, amount=excluded.amount, cleared=excluded.cleared")) {
            for (var rec : BankReconciliationStore.getInstance().getReconciliations()) {
                ps.setString(1, rec.getId());
                ps.setString(2, date(rec.getStatementDate()));
                ps.setString(3, money(rec.getStatementBalance()));
                ps.setString(4, money(rec.getBookBalance()));
                ps.setString(5, money(rec.getAdjustedBalance()));
                ps.setString(6, money(rec.getDifference()));
                ps.setString(7, rec.getStatus());
                ps.setString(8, dateTime(rec.getCreatedAt()));
                ps.setString(9, dateTime(rec.getReconciledAt()));
                ps.setString(10, rec.getNotes());
                ps.addBatch();
                for (var item : rec.getItems()) {
                    itemPs.setString(1, item.getId());
                    itemPs.setString(2, rec.getId());
                    itemPs.setString(3, item.getType());
                    itemPs.setString(4, item.getReference());
                    itemPs.setString(5, item.getDescription());
                    itemPs.setString(6, money(item.getAmount()));
                    itemPs.setInt(7, item.isCleared() ? 1 : 0);
                    itemPs.addBatch();
                }
            }
            ps.executeBatch();
            itemPs.executeBatch();
        }
    }

    private void loadBankReconciliation(Connection conn) throws SQLException {
        BankReconciliationStore store = BankReconciliationStore.getInstance();
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM bank_reconciliation ORDER BY created_at DESC")) {
            while (rs.next()) {
                var rec = com.schaccs.model.finance.BankReconciliation.withId(rs.getString("id"));
                rec.setStatementDate(parseDate(rs.getString("statement_date")));
                rec.setStatementBalance(parseMoney(rs.getString("statement_balance")));
                rec.setBookBalance(parseMoney(rs.getString("book_balance")));
                rec.setAdjustedBalance(parseMoney(rs.getString("adjusted_balance")));
                rec.setDifference(parseMoney(rs.getString("difference")));
                rec.setStatus(rs.getString("status"));
                String ca = rs.getString("created_at");
                if (ca != null) rec.setCreatedAt(LocalDateTime.parse(ca));
                String ra = rs.getString("reconciled_at");
                if (ra != null) rec.setReconciledAt(LocalDateTime.parse(ra));
                rec.setNotes(rs.getString("notes"));
                loadReconciliationItems(conn, rec);
                store.add(rec);
            }
        }
    }

    private void loadReconciliationItems(Connection conn, com.schaccs.model.finance.BankReconciliation rec) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT * FROM bank_reconciliation_items WHERE reconciliation_id = ?")) {
            ps.setString(1, rec.getId());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    var item = new com.schaccs.model.finance.BankReconciliation.ReconciliationItem();
                    item.setType(rs.getString("type"));
                    item.setReference(rs.getString("reference"));
                    item.setDescription(rs.getString("description"));
                    item.setAmount(parseMoney(rs.getString("amount")));
                    item.setCleared(rs.getInt("cleared") == 1);
                    rec.addItem(item);
                }
            }
        }
    }

    private void saveSchoolCustom(Connection conn) throws SQLException {
        SchoolCustomStore store = SchoolCustomStore.getInstance();
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO school_form_classes (id, name) VALUES (?,?) "
                        + "ON CONFLICT(id) DO UPDATE SET name=excluded.name")) {
            for (var fc : store.getFormClasses()) {
                ps.setString(1, fc.getId());
                ps.setString(2, fc.getName());
                ps.addBatch();
            }
            ps.executeBatch();
        }
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO school_streams (id, name) VALUES (?,?) "
                        + "ON CONFLICT(id) DO UPDATE SET name=excluded.name")) {
            for (var s : store.getStreams()) {
                ps.setString(1, s.getId());
                ps.setString(2, s.getName());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private void loadSchoolCustom(Connection conn) throws SQLException {
        SchoolCustomStore store = SchoolCustomStore.getInstance();
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM school_form_classes ORDER BY name")) {
            while (rs.next()) {
                store.addFormClass(com.schaccs.model.school.SchoolFormClass.withId(
                        rs.getString("id"), rs.getString("name")));
            }
        }
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM school_streams ORDER BY name")) {
            while (rs.next()) {
                store.addStream(com.schaccs.model.school.SchoolStream.withId(
                        rs.getString("id"), rs.getString("name")));
            }
        }
    }

    private void saveAccountStoreEntities(Connection conn) throws SQLException {
        AccountStore store = AccountStore.getInstance();
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO accounts (id, code, name, parent_id, normal_balance, statement_category, active, is_control_account)
                VALUES (?,?,?,?,?,?,?,?)
                ON CONFLICT(id) DO UPDATE SET code=excluded.code, name=excluded.name,
                    parent_id=excluded.parent_id,
                    normal_balance=excluded.normal_balance, statement_category=excluded.statement_category,
                    active=excluded.active, is_control_account=excluded.is_control_account
                """)) {
            for (Account a : store.getAccounts()) {
                ps.setString(1, a.getId());
                ps.setString(2, a.getCode());
                ps.setString(3, a.getName());
                ps.setString(4, a.getParentId());
                ps.setString(5, enumName(a.getNormalBalance()));
                ps.setString(6, enumName(a.getStatementCategory()));
                ps.setInt(7, a.isActive() ? 1 : 0);
                ps.setInt(8, a.isControlAccount() ? 1 : 0);
                ps.addBatch();
            }
            ps.executeBatch();
        }
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO fiscal_years (id, year, start_date, end_date, is_open, is_closed, closed_at, closed_by)
                VALUES (?,?,?,?,?,?,?,?)
                ON CONFLICT(id) DO UPDATE SET year=excluded.year, start_date=excluded.start_date,
                    end_date=excluded.end_date, is_open=excluded.is_open, is_closed=excluded.is_closed,
                    closed_at=excluded.closed_at, closed_by=excluded.closed_by
                """)) {
            for (FiscalYear fy : store.getFiscalYears()) {
                ps.setString(1, fy.getId());
                ps.setInt(2, fy.getYear());
                ps.setString(3, date(fy.getStartDate()));
                ps.setString(4, date(fy.getEndDate()));
                ps.setInt(5, fy.isOpen() ? 1 : 0);
                ps.setInt(6, fy.isClosed() ? 1 : 0);
                ps.setString(7, dateTime(fy.getClosedAt()));
                ps.setString(8, fy.getClosedBy());
                ps.addBatch();
            }
            ps.executeBatch();
        }
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO budgets (id, fiscal_year_id, name, is_approved, approved_at, approved_by)
                VALUES (?,?,?,?,?,?)
                ON CONFLICT(id) DO UPDATE SET fiscal_year_id=excluded.fiscal_year_id,
                    name=excluded.name, is_approved=excluded.is_approved,
                    approved_at=excluded.approved_at, approved_by=excluded.approved_by
                """)) {
            for (Budget b : store.getBudgets()) {
                ps.setString(1, b.getId());
                ps.setString(2, b.getFiscalYearId());
                ps.setString(3, b.getName());
                ps.setInt(4, b.isApproved() ? 1 : 0);
                ps.setString(5, dateTime(b.getApprovedAt()));
                ps.setString(6, b.getApprovedBy());
                ps.addBatch();
            }
            ps.executeBatch();
        }
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO budget_lines (id, budget_id, account_id, votehead_code, allocated_amount, spent_amount, committed_amount)
                VALUES (?,?,?,?,?,?,?)
                ON CONFLICT(id) DO UPDATE SET budget_id=excluded.budget_id,
                    account_id=excluded.account_id, votehead_code=excluded.votehead_code,
                    allocated_amount=excluded.allocated_amount, spent_amount=excluded.spent_amount,
                    committed_amount=excluded.committed_amount
                """)) {
            for (BudgetLine bl : store.getBudgetLines()) {
                ps.setString(1, bl.getId());
                ps.setString(2, bl.getBudgetId());
                ps.setString(3, bl.getAccountId());
                ps.setString(4, bl.getVoteheadCode());
                ps.setString(5, money(bl.getAllocatedAmount()));
                ps.setString(6, money(bl.getSpentAmount()));
                ps.setString(7, money(bl.getCommittedAmount()));
                ps.addBatch();
            }
            ps.executeBatch();
        }
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO asset_categories (id, name, depreciation_method, useful_life_years, salvage_value_percent)
                VALUES (?,?,?,?,?)
                ON CONFLICT(id) DO UPDATE SET name=excluded.name,
                    depreciation_method=excluded.depreciation_method,
                    useful_life_years=excluded.useful_life_years,
                    salvage_value_percent=excluded.salvage_value_percent
                """)) {
            for (AssetCategory ac : store.getAssetCategories()) {
                ps.setString(1, ac.getId());
                ps.setString(2, ac.getName());
                ps.setString(3, enumName(ac.getDepreciationMethod()));
                ps.setInt(4, ac.getUsefulLifeYears());
                ps.setDouble(5, ac.getSalvageValuePercent());
                ps.addBatch();
            }
            ps.executeBatch();
        }
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO assets (id, category_id, asset_code, name, description, purchase_date,
                    purchase_cost, current_value, salvage_value, location, condition, status)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
                ON CONFLICT(id) DO UPDATE SET category_id=excluded.category_id,
                    asset_code=excluded.asset_code, name=excluded.name, description=excluded.description,
                    purchase_date=excluded.purchase_date, purchase_cost=excluded.purchase_cost,
                    current_value=excluded.current_value, salvage_value=excluded.salvage_value,
                    location=excluded.location, condition=excluded.condition, status=excluded.status
                """)) {
            for (Asset a : store.getAssets()) {
                ps.setString(1, a.getId());
                ps.setString(2, a.getCategoryId());
                ps.setString(3, a.getAssetCode());
                ps.setString(4, a.getName());
                ps.setString(5, a.getDescription());
                ps.setString(6, date(a.getPurchaseDate()));
                ps.setString(7, money(a.getPurchaseCost()));
                ps.setString(8, money(a.getCurrentValue()));
                ps.setString(9, money(a.getSalvageValue()));
                ps.setString(10, a.getLocation());
                ps.setString(11, a.getCondition());
                ps.setString(12, enumName(a.getStatus()));
                ps.addBatch();
            }
            ps.executeBatch();
        }
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO depreciation_schedules (id, asset_id, period_start, period_end,
                    depreciation_amount, accumulated_depreciation, net_book_value)
                VALUES (?,?,?,?,?,?,?)
                ON CONFLICT(id) DO UPDATE SET asset_id=excluded.asset_id,
                    period_start=excluded.period_start, period_end=excluded.period_end,
                    depreciation_amount=excluded.depreciation_amount,
                    accumulated_depreciation=excluded.accumulated_depreciation,
                    net_book_value=excluded.net_book_value
                """)) {
            for (DepreciationSchedule ds : store.getDepreciationSchedules()) {
                ps.setString(1, ds.getId());
                ps.setString(2, ds.getAssetId());
                ps.setString(3, date(ds.getPeriodStart()));
                ps.setString(4, date(ds.getPeriodEnd()));
                ps.setString(5, money(ds.getDepreciationAmount()));
                ps.setString(6, money(ds.getAccumulatedDepreciation()));
                ps.setString(7, money(ds.getNetBookValue()));
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private void loadAccountStoreEntities(Connection conn) throws SQLException {
        AccountStore store = AccountStore.getInstance();
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM accounts ORDER BY code")) {
            while (rs.next()) {
                Account a = Account.withId(rs.getString("id"));
                a.setCode(rs.getString("code"));
                a.setName(rs.getString("name"));
                a.setParentId(rs.getString("parent_id"));
                String code = rs.getString("code");
                AccountType at = resolveAccountTypeByCode(code);
                if (at != null) {
                    a.setAccountType(at);
                }
                String nb = rs.getString("normal_balance");
                if (nb != null && at == null) a.setNormalBalance(NormalBalance.valueOf(nb));
                String sc = rs.getString("statement_category");
                if (sc != null && at == null) a.setStatementCategory(StatementCategory.valueOf(sc));
                a.setActive(rs.getInt("active") != 0);
                a.setControlAccount(rs.getInt("is_control_account") != 0);
                store.getAccounts().add(a);
            }
        }
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM fiscal_years ORDER BY year DESC")) {
            while (rs.next()) {
                FiscalYear fy = FiscalYear.withId(rs.getString("id"));
                fy.setYear(rs.getInt("year"));
                fy.setStartDate(parseDate(rs.getString("start_date")));
                fy.setEndDate(parseDate(rs.getString("end_date")));
                fy.setOpen(rs.getInt("is_open") != 0);
                fy.setClosed(rs.getInt("is_closed") != 0);
                fy.setClosedAt(parseDateTime(rs.getString("closed_at")));
                fy.setClosedBy(rs.getString("closed_by"));
                store.getFiscalYears().add(fy);
            }
        }
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM budgets ORDER BY name")) {
            while (rs.next()) {
                Budget b = Budget.withId(rs.getString("id"));
                b.setFiscalYearId(rs.getString("fiscal_year_id"));
                b.setName(rs.getString("name"));
                b.setApproved(rs.getInt("is_approved") != 0);
                b.setApprovedAt(parseDateTime(rs.getString("approved_at")));
                b.setApprovedBy(rs.getString("approved_by"));
                store.getBudgets().add(b);
            }
        }
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM budget_lines")) {
            while (rs.next()) {
                BudgetLine bl = BudgetLine.withId(rs.getString("id"));
                bl.setBudgetId(rs.getString("budget_id"));
                bl.setAccountId(rs.getString("account_id"));
                bl.setVoteheadCode(rs.getString("votehead_code"));
                bl.setAllocatedAmount(parseMoney(rs.getString("allocated_amount")));
                bl.setSpentAmount(parseMoney(rs.getString("spent_amount")));
                bl.setCommittedAmount(parseMoney(rs.getString("committed_amount")));
                store.getBudgetLines().add(bl);
            }
        }
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM asset_categories ORDER BY name")) {
            while (rs.next()) {
                AssetCategory ac = AssetCategory.withId(rs.getString("id"));
                ac.setName(rs.getString("name"));
                String dm = rs.getString("depreciation_method");
                if (dm != null) ac.setDepreciationMethod(AssetCategory.DepreciationMethod.valueOf(dm));
                ac.setUsefulLifeYears(rs.getInt("useful_life_years"));
                ac.setSalvageValuePercent(rs.getDouble("salvage_value_percent"));
                store.getAssetCategories().add(ac);
            }
        }
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM assets ORDER BY name")) {
            while (rs.next()) {
                Asset a = Asset.withId(rs.getString("id"));
                a.setCategoryId(rs.getString("category_id"));
                a.setAssetCode(rs.getString("asset_code"));
                a.setName(rs.getString("name"));
                a.setDescription(rs.getString("description"));
                a.setPurchaseDate(parseDate(rs.getString("purchase_date")));
                a.setPurchaseCost(parseMoney(rs.getString("purchase_cost")));
                a.setCurrentValue(parseMoney(rs.getString("current_value")));
                a.setSalvageValue(parseMoney(rs.getString("salvage_value")));
                a.setLocation(rs.getString("location"));
                a.setCondition(rs.getString("condition"));
                String status = rs.getString("status");
                if (status != null) a.setStatus(Asset.AssetStatus.valueOf(status));
                store.getAssets().add(a);
            }
        }
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM depreciation_schedules")) {
            while (rs.next()) {
                DepreciationSchedule ds = DepreciationSchedule.withId(rs.getString("id"));
                ds.setAssetId(rs.getString("asset_id"));
                ds.setPeriodStart(parseDate(rs.getString("period_start")));
                ds.setPeriodEnd(parseDate(rs.getString("period_end")));
                ds.setDepreciationAmount(parseMoney(rs.getString("depreciation_amount")));
                ds.setAccumulatedDepreciation(parseMoney(rs.getString("accumulated_depreciation")));
                ds.setNetBookValue(parseMoney(rs.getString("net_book_value")));
                store.getDepreciationSchedules().add(ds);
            }
        }
    }

    private static LocalDateTime parseDateTime(String s) {
        return s == null || s.isBlank() ? null : LocalDateTime.parse(s);
    }

    private static AccountType resolveAccountTypeByCode(String code) {
        if (code == null) return null;
        for (AccountType at : AccountType.values()) {
            if (at.getCode().equals(code)) return at;
        }
        return null;
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
