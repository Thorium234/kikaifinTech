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
import com.schaccs.enums.ApprovalAction;
import com.schaccs.enums.BidStatus;
import com.schaccs.enums.ContractStatus;
import com.schaccs.enums.ProcurementCategory;
import com.schaccs.enums.ProcurementRequestStatus;
import com.schaccs.enums.TenderStatus;
import com.schaccs.enums.TenderType;
import com.schaccs.model.fee.FeeStructure;
import com.schaccs.model.fee.FeeStructureItem;
import com.schaccs.model.fee.FeeStructureTemplate;
import com.schaccs.model.fee.FeeStructureTemplateItem;
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
import com.schaccs.model.payroll.Employee;
import com.schaccs.model.payroll.PayrollItem;
import com.schaccs.model.payroll.PayrollRun;
import com.schaccs.model.payroll.SalaryStructure;
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
import com.schaccs.model.procurement.Supplier;
import com.schaccs.model.procurement.ProcurementRequest;
import com.schaccs.model.procurement.Tender;
import com.schaccs.model.procurement.TenderBid;
import com.schaccs.model.procurement.TenderEvaluation;
import com.schaccs.model.procurement.TenderAward;
import com.schaccs.model.procurement.Contract;
import com.schaccs.model.procurement.ContractMilestone;
import com.schaccs.model.procurement.ProcurementApproval;
import com.schaccs.store.AccountStore;
import com.schaccs.store.AcademicCalendarStore;
import com.schaccs.store.FeeStructureStore;
import com.schaccs.store.LedgerStore;
import com.schaccs.store.ReceiptStore;
import com.schaccs.store.StudentStore;
import com.schaccs.store.AuditStore;
import com.schaccs.store.BankReconciliationStore;
import com.schaccs.store.SchoolCustomStore;
import com.schaccs.store.VoucherStore;
import com.schaccs.store.EmployeeStore;
import com.schaccs.store.PayrollStore;
import com.schaccs.store.ProcurementStore;

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

    public synchronized void clearAll() {
        transactional(conn -> {
            clearTables(conn);
        });
        AccountStore.getInstance().clear();
        StudentStore.getInstance().clear();
        FeeStructureStore.getInstance().clear();
        ReceiptStore.getInstance().clear();
        LedgerStore.getInstance().clear();
        VoucherStore.getInstance().clear();
        AuditStore.getInstance().clear();
        BankReconciliationStore.getInstance().clear();
        SchoolCustomStore.getInstance().clear();
        AcademicCalendarStore.getInstance().clear();
        EmployeeStore.getInstance().clear();
        PayrollStore.getInstance().clear();
        ProcurementStore.getInstance().clear();
    }

    public synchronized void saveAll() {
        transactional(conn -> {
            saveSettings(conn);
            markInitialized(conn);
            saveVoteheads(conn);
            saveFeeStructures(conn);
            saveFeeTemplates(conn);
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
            saveAcademicCalendar(conn);
            saveAccountStoreEntities(conn);
            saveEmployees(conn);
            saveSalaryStructures(conn);
            savePayrollRuns(conn);
            savePayrollItems(conn);
            saveSuppliers(conn);
            saveProcurementRequests(conn);
            saveTenders(conn);
            saveTenderBids(conn);
            saveTenderEvaluations(conn);
            saveTenderAwards(conn);
            saveContracts(conn);
            saveContractMilestones(conn);
            saveProcurementApprovals(conn);
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
            EmployeeStore.getInstance().clear();
            PayrollStore.getInstance().clear();
            ProcurementStore.getInstance().clear();
            loadSettings(conn);
            loadVoteheads(conn);
            loadFeeStructures(conn);
            loadFeeTemplates(conn);
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
            loadAcademicCalendar(conn);
            loadAccountStoreEntities(conn);
            loadEmployees(conn);
            loadSalaryStructures(conn);
            loadPayrollRuns(conn);
            loadPayrollItems(conn);
            loadSuppliers(conn);
            loadProcurementRequests(conn);
            loadTenders(conn);
            loadTenderBids(conn);
            loadTenderEvaluations(conn);
            loadTenderAwards(conn);
            loadContracts(conn);
            loadContractMilestones(conn);
            loadProcurementApprovals(conn);
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
            st.executeUpdate("DELETE FROM fee_template_items");
            st.executeUpdate("DELETE FROM fee_templates");
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
            st.executeUpdate("DELETE FROM academic_calendar");
            st.executeUpdate("DELETE FROM depreciation_schedules");
            st.executeUpdate("DELETE FROM assets");
            st.executeUpdate("DELETE FROM asset_categories");
            st.executeUpdate("DELETE FROM budget_lines");
            st.executeUpdate("DELETE FROM budgets");
            st.executeUpdate("DELETE FROM fiscal_years");
            st.executeUpdate("DELETE FROM accounts");
            st.executeUpdate("DELETE FROM payroll_items");
            st.executeUpdate("DELETE FROM payroll_runs");
            st.executeUpdate("DELETE FROM salary_structures");
            st.executeUpdate("DELETE FROM employees");
            st.executeUpdate("DELETE FROM procurement_approvals");
            st.executeUpdate("DELETE FROM contract_milestones");
            st.executeUpdate("DELETE FROM contracts");
            st.executeUpdate("DELETE FROM tender_awards");
            st.executeUpdate("DELETE FROM tender_evaluations");
            st.executeUpdate("DELETE FROM tender_bids");
            st.executeUpdate("DELETE FROM tenders");
            st.executeUpdate("DELETE FROM procurement_requests");
            st.executeUpdate("DELETE FROM suppliers");
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

    private void saveFeeTemplates(Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO fee_templates (id, name) VALUES (?,?) "
                        + "ON CONFLICT(id) DO UPDATE SET name=excluded.name");
             PreparedStatement itemPs = conn.prepareStatement(
                     "INSERT INTO fee_template_items (id, template_id, votehead_code, votehead_name, term, boarding_status, amount) "
                             + "VALUES (?,?,?,?,?,?,?) ON CONFLICT(id) DO UPDATE SET "
                             + "template_id=excluded.template_id, votehead_code=excluded.votehead_code, "
                             + "votehead_name=excluded.votehead_name, term=excluded.term, "
                             + "boarding_status=excluded.boarding_status, amount=excluded.amount")) {
            for (FeeStructureTemplate t : FeeStructureStore.getInstance().getTemplates()) {
                ps.setString(1, t.getId());
                ps.setString(2, t.getName());
                ps.addBatch();
                for (FeeStructureTemplateItem item : t.getItems()) {
                    itemPs.setString(1, item.getId());
                    itemPs.setString(2, t.getId());
                    itemPs.setString(3, item.getVoteheadCode());
                    itemPs.setString(4, item.getVoteheadName());
                    itemPs.setString(5, enumName(item.getTerm()));
                    itemPs.setString(6, "ALL");
                    itemPs.setString(7, money(item.getAmount()));
                    itemPs.addBatch();
                }
            }
            ps.executeBatch();
            itemPs.executeBatch();
        }
    }

    private void loadFeeTemplates(Connection conn) throws SQLException {
        FeeStructureStore store = FeeStructureStore.getInstance();
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM fee_templates")) {
            while (rs.next()) {
                FeeStructureTemplate t = new FeeStructureTemplate(rs.getString("name"));
                store.addTemplate(t);
                loadItemsForTemplate(conn, rs.getString("id"), t);
            }
        }
    }

    private void loadItemsForTemplate(Connection conn, String templateId, FeeStructureTemplate t) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT * FROM fee_template_items WHERE template_id = ?")) {
            ps.setString(1, templateId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    FeeStructureTemplateItem item = new FeeStructureTemplateItem(
                            rs.getString("votehead_code"),
                            rs.getString("votehead_name"),
                            AcademicTerm.valueOf(rs.getString("term")),
                            parseMoney(rs.getString("amount")));
                    t.addItem(item);
                }
            }
        }
    }

    private void saveStudents(Connection conn) throws SQLException {
        StudentStore store = StudentStore.getInstance();
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO students (id, admission_number, name, gender, form_class, stream,
                    boarding_status, parent_name, phone, avatar_path, year_of_admission, academic_year, status)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)
                ON CONFLICT(id) DO UPDATE SET admission_number=excluded.admission_number,
                    name=excluded.name, gender=excluded.gender, form_class=excluded.form_class, stream=excluded.stream,
                    boarding_status=excluded.boarding_status, parent_name=excluded.parent_name, phone=excluded.phone,
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
                ps.setString(3, s.getName());
                ps.setString(4, s.getGender());
                ps.setString(5, s.getFormClass());
                ps.setString(6, s.getStream());
                ps.setString(7, enumName(s.getBoardingStatus()));
                ps.setString(8, s.getParentName());
                ps.setString(9, s.getPhone());
                ps.setString(10, s.getAvatarPath());
                ps.setObject(11, s.getYearOfAdmission());
                ps.setObject(12, s.getAcademicYear());
                ps.setString(13, enumName(s.getStatus()));
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
                      "INSERT INTO receipt_lines (id, receipt_id, votehead_code, votehead_name, amount, outstanding_before) "
                              + "VALUES (?,?,?,?,?,?) ON CONFLICT(id) DO UPDATE SET receipt_id=excluded.receipt_id, "
                              + "votehead_code=excluded.votehead_code, votehead_name=excluded.votehead_name, "
                              + "amount=excluded.amount, outstanding_before=excluded.outstanding_before")) {
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
                    linePs.setString(6, money(line.getOutstandingBefore()));
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
                "SELECT * FROM receipt_lines WHERE receipt_id = ? ORDER BY rowid")) {
            ps.setString(1, r.getId());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ReceiptLine line = new ReceiptLine(
                            rs.getString("votehead_code"),
                            rs.getString("votehead_name"),
                            parseMoney(rs.getString("amount")));
                    String ob = rs.getString("outstanding_before");
                    if (ob != null && !ob.isBlank()) {
                        line.setOutstandingBefore(parseMoney(ob));
                    }
                    r.addLine(line);
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

    private void saveAcademicCalendar(Connection conn) throws SQLException {
        AcademicCalendarStore store = AcademicCalendarStore.getInstance();
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO academic_calendar (id, term, from_date, to_date) VALUES (?,?,?,?) "
                        + "ON CONFLICT(id) DO UPDATE SET term=excluded.term, "
                        + "from_date=excluded.from_date, to_date=excluded.to_date")) {
            for (com.schaccs.model.school.TermPeriod p : store.getPeriods()) {
                ps.setString(1, p.getId());
                ps.setString(2, enumName(p.getTerm()));
                ps.setString(3, date(p.getFrom()));
                ps.setString(4, date(p.getTo()));
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private void loadAcademicCalendar(Connection conn) throws SQLException {
        AcademicCalendarStore store = AcademicCalendarStore.getInstance();
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM academic_calendar ORDER BY from_date")) {
            while (rs.next()) {
                store.add(com.schaccs.model.school.TermPeriod.withId(
                        rs.getString("id"),
                        AcademicTerm.valueOf(rs.getString("term")),
                        parseDate(rs.getString("from_date")),
                        parseDate(rs.getString("to_date"))));
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

    private void saveEmployees(Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO employees (id, employee_number, first_name, last_name, national_id,
                    department, position, employment_date, employment_status, bank_name, bank_branch,
                    bank_account_number, kra_pin, nssf_number, shif_number, phone, email, address)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                ON CONFLICT(id) DO UPDATE SET employee_number=excluded.employee_number,
                    first_name=excluded.first_name, last_name=excluded.last_name, national_id=excluded.national_id,
                    department=excluded.department, position=excluded.position,
                    employment_date=excluded.employment_date, employment_status=excluded.employment_status,
                    bank_name=excluded.bank_name, bank_branch=excluded.bank_branch,
                    bank_account_number=excluded.bank_account_number, kra_pin=excluded.kra_pin,
                    nssf_number=excluded.nssf_number, shif_number=excluded.shif_number,
                    phone=excluded.phone, email=excluded.email, address=excluded.address
                """)) {
            for (Employee e : EmployeeStore.getInstance().getEmployees()) {
                ps.setString(1, e.getId());
                ps.setString(2, e.getEmployeeNumber());
                ps.setString(3, e.getFirstName());
                ps.setString(4, e.getLastName());
                ps.setString(5, e.getNationalId());
                ps.setString(6, e.getDepartment());
                ps.setString(7, e.getPosition());
                ps.setString(8, date(e.getEmploymentDate()));
                ps.setString(9, enumName(e.getEmploymentStatus()));
                ps.setString(10, e.getBankName());
                ps.setString(11, e.getBankBranch());
                ps.setString(12, e.getBankAccountNumber());
                ps.setString(13, e.getKraPin());
                ps.setString(14, e.getNssfNumber());
                ps.setString(15, e.getShifNumber());
                ps.setString(16, e.getPhone());
                ps.setString(17, e.getEmail());
                ps.setString(18, e.getAddress());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private void loadEmployees(Connection conn) throws SQLException {
        EmployeeStore store = EmployeeStore.getInstance();
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM employees ORDER BY employee_number")) {
            while (rs.next()) {
                Employee e = Employee.withId(rs.getString("id"));
                e.setEmployeeNumber(rs.getString("employee_number"));
                e.setFirstName(rs.getString("first_name"));
                e.setLastName(rs.getString("last_name"));
                e.setNationalId(rs.getString("national_id"));
                e.setDepartment(rs.getString("department"));
                e.setPosition(rs.getString("position"));
                e.setEmploymentDate(parseDate(rs.getString("employment_date")));
                String status = rs.getString("employment_status");
                if (status != null) {
                    e.setEmploymentStatus(Employee.EmploymentStatus.valueOf(status));
                }
                e.setBankName(rs.getString("bank_name"));
                e.setBankBranch(rs.getString("bank_branch"));
                e.setBankAccountNumber(rs.getString("bank_account_number"));
                e.setKraPin(rs.getString("kra_pin"));
                e.setNssfNumber(rs.getString("nssf_number"));
                e.setShifNumber(rs.getString("shif_number"));
                e.setPhone(rs.getString("phone"));
                e.setEmail(rs.getString("email"));
                e.setAddress(rs.getString("address"));
                store.getEmployees().add(e);
            }
        }
    }

    private void saveSalaryStructures(Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO salary_structures (id, employee_id, basic_salary, house_allowance,
                    responsibility_allowance, transport_allowance, other_earnings,
                    staff_loan_repayment, salary_advance_recovery, welfare_contribution,
                    effective_date, active)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
                ON CONFLICT(id) DO UPDATE SET employee_id=excluded.employee_id,
                    basic_salary=excluded.basic_salary, house_allowance=excluded.house_allowance,
                    responsibility_allowance=excluded.responsibility_allowance,
                    transport_allowance=excluded.transport_allowance, other_earnings=excluded.other_earnings,
                    staff_loan_repayment=excluded.staff_loan_repayment,
                    salary_advance_recovery=excluded.salary_advance_recovery,
                    welfare_contribution=excluded.welfare_contribution,
                    effective_date=excluded.effective_date, active=excluded.active
                """)) {
            for (SalaryStructure s : EmployeeStore.getInstance().getSalaryStructures()) {
                ps.setString(1, s.getId());
                ps.setString(2, s.getEmployeeId());
                ps.setString(3, money(s.getBasicSalary()));
                ps.setString(4, money(s.getHouseAllowance()));
                ps.setString(5, money(s.getResponsibilityAllowance()));
                ps.setString(6, money(s.getTransportAllowance()));
                ps.setString(7, money(s.getOtherEarnings()));
                ps.setString(8, money(s.getStaffLoanRepayment()));
                ps.setString(9, money(s.getSalaryAdvanceRecovery()));
                ps.setString(10, money(s.getWelfareContribution()));
                ps.setString(11, date(s.getEffectiveDate()));
                ps.setInt(12, s.isActive() ? 1 : 0);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private void loadSalaryStructures(Connection conn) throws SQLException {
        EmployeeStore store = EmployeeStore.getInstance();
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM salary_structures")) {
            while (rs.next()) {
                SalaryStructure s = SalaryStructure.withId(rs.getString("id"));
                s.setEmployeeId(rs.getString("employee_id"));
                s.setBasicSalary(parseMoney(rs.getString("basic_salary")));
                s.setHouseAllowance(parseMoney(rs.getString("house_allowance")));
                s.setResponsibilityAllowance(parseMoney(rs.getString("responsibility_allowance")));
                s.setTransportAllowance(parseMoney(rs.getString("transport_allowance")));
                s.setOtherEarnings(parseMoney(rs.getString("other_earnings")));
                s.setStaffLoanRepayment(parseMoney(rs.getString("staff_loan_repayment")));
                s.setSalaryAdvanceRecovery(parseMoney(rs.getString("salary_advance_recovery")));
                s.setWelfareContribution(parseMoney(rs.getString("welfare_contribution")));
                s.setEffectiveDate(parseDate(rs.getString("effective_date")));
                s.setActive(rs.getInt("active") == 1);
                store.getSalaryStructures().add(s);
            }
        }
    }

    private void savePayrollRuns(Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO payroll_runs (id, run_number, month, year, period_start, period_end,
                    status, total_gross_pay, total_deductions, total_net_pay, total_paye, total_nssf,
                    total_shif, total_pension, employee_count, prepared_by, approved_by, posted_by,
                    prepared_at, approved_at, posted_at, journal_id, reversal_of_id, notes, created_at)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                ON CONFLICT(id) DO UPDATE SET run_number=excluded.run_number,
                    month=excluded.month, year=excluded.year, period_start=excluded.period_start,
                    period_end=excluded.period_end, status=excluded.status,
                    total_gross_pay=excluded.total_gross_pay, total_deductions=excluded.total_deductions,
                    total_net_pay=excluded.total_net_pay, total_paye=excluded.total_paye,
                    total_nssf=excluded.total_nssf, total_shif=excluded.total_shif,
                    total_pension=excluded.total_pension, employee_count=excluded.employee_count,
                    prepared_by=excluded.prepared_by, approved_by=excluded.approved_by,
                    posted_by=excluded.posted_by, prepared_at=excluded.prepared_at,
                    approved_at=excluded.approved_at, posted_at=excluded.posted_at,
                    journal_id=excluded.journal_id, reversal_of_id=excluded.reversal_of_id,
                    notes=excluded.notes, created_at=excluded.created_at
                """)) {
            for (PayrollRun r : PayrollStore.getInstance().getPayrollRuns()) {
                ps.setString(1, r.getId());
                ps.setString(2, r.getRunNumber());
                ps.setInt(3, r.getMonth());
                ps.setInt(4, r.getYear());
                ps.setString(5, date(r.getPeriodStart()));
                ps.setString(6, date(r.getPeriodEnd()));
                ps.setString(7, enumName(r.getStatus()));
                ps.setString(8, money(r.getTotalGrossPay()));
                ps.setString(9, money(r.getTotalDeductions()));
                ps.setString(10, money(r.getTotalNetPay()));
                ps.setString(11, money(r.getTotalPAYE()));
                ps.setString(12, money(r.getTotalNSSF()));
                ps.setString(13, money(r.getTotalSHIF()));
                ps.setString(14, money(r.getTotalPension()));
                ps.setInt(15, r.getEmployeeCount());
                ps.setString(16, r.getPreparedBy());
                ps.setString(17, r.getApprovedBy());
                ps.setString(18, r.getPostedBy());
                ps.setString(19, dateTime(r.getPreparedAt()));
                ps.setString(20, dateTime(r.getApprovedAt()));
                ps.setString(21, dateTime(r.getPostedAt()));
                ps.setString(22, r.getJournalId());
                ps.setString(23, r.getReversalOfId());
                ps.setString(24, r.getNotes());
                ps.setString(25, dateTime(r.getCreatedAt()));
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private void loadPayrollRuns(Connection conn) throws SQLException {
        PayrollStore store = PayrollStore.getInstance();
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM payroll_runs ORDER BY year DESC, month DESC")) {
            while (rs.next()) {
                PayrollRun r = PayrollRun.withId(rs.getString("id"));
                r.setRunNumber(rs.getString("run_number"));
                r.setMonth(rs.getInt("month"));
                r.setYear(rs.getInt("year"));
                r.setPeriodStart(parseDate(rs.getString("period_start")));
                r.setPeriodEnd(parseDate(rs.getString("period_end")));
                String status = rs.getString("status");
                if (status != null) r.setStatus(PayrollRun.PayrollStatus.valueOf(status));
                r.setTotalGrossPay(parseMoney(rs.getString("total_gross_pay")));
                r.setTotalDeductions(parseMoney(rs.getString("total_deductions")));
                r.setTotalNetPay(parseMoney(rs.getString("total_net_pay")));
                r.setTotalPAYE(parseMoney(rs.getString("total_paye")));
                r.setTotalNSSF(parseMoney(rs.getString("total_nssf")));
                r.setTotalSHIF(parseMoney(rs.getString("total_shif")));
                r.setTotalPension(parseMoney(rs.getString("total_pension")));
                r.setEmployeeCount(rs.getInt("employee_count"));
                r.setPreparedBy(rs.getString("prepared_by"));
                r.setApprovedBy(rs.getString("approved_by"));
                r.setPostedBy(rs.getString("posted_by"));
                r.setPreparedAt(parseDateTime(rs.getString("prepared_at")));
                r.setApprovedAt(parseDateTime(rs.getString("approved_at")));
                r.setPostedAt(parseDateTime(rs.getString("posted_at")));
                r.setJournalId(rs.getString("journal_id"));
                r.setReversalOfId(rs.getString("reversal_of_id"));
                r.setNotes(rs.getString("notes"));
                r.setCreatedAt(parseDateTime(rs.getString("created_at")));
                store.getPayrollRuns().add(r);
            }
        }
    }

    private void savePayrollItems(Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO payroll_items (id, payroll_run_id, employee_id, employee_number,
                    employee_name, department, basic_salary, house_allowance, responsibility_allowance,
                    transport_allowance, overtime, bonus, other_earnings, gross_pay,
                    paye, nssf, shif, pension, staff_loan_repayment, salary_advance_recovery,
                    welfare_contribution, custom_deductions, custom_deduction_name,
                    total_deductions, net_pay, employer_nssf, employer_pension)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                ON CONFLICT(id) DO UPDATE SET payroll_run_id=excluded.payroll_run_id,
                    employee_id=excluded.employee_id, employee_number=excluded.employee_number,
                    employee_name=excluded.employee_name, department=excluded.department,
                    basic_salary=excluded.basic_salary, house_allowance=excluded.house_allowance,
                    responsibility_allowance=excluded.responsibility_allowance,
                    transport_allowance=excluded.transport_allowance, overtime=excluded.overtime,
                    bonus=excluded.bonus, other_earnings=excluded.other_earnings,
                    gross_pay=excluded.gross_pay, paye=excluded.paye, nssf=excluded.nssf,
                    shif=excluded.shif, pension=excluded.pension,
                    staff_loan_repayment=excluded.staff_loan_repayment,
                    salary_advance_recovery=excluded.salary_advance_recovery,
                    welfare_contribution=excluded.welfare_contribution,
                    custom_deductions=excluded.custom_deductions,
                    custom_deduction_name=excluded.custom_deduction_name,
                    total_deductions=excluded.total_deductions, net_pay=excluded.net_pay,
                    employer_nssf=excluded.employer_nssf, employer_pension=excluded.employer_pension
                """)) {
            for (PayrollItem item : PayrollStore.getInstance().getPayrollItems()) {
                ps.setString(1, item.getId());
                ps.setString(2, item.getPayrollRunId());
                ps.setString(3, item.getEmployeeId());
                ps.setString(4, item.getEmployeeNumber());
                ps.setString(5, item.getEmployeeName());
                ps.setString(6, item.getDepartment());
                ps.setString(7, money(item.getBasicSalary()));
                ps.setString(8, money(item.getHouseAllowance()));
                ps.setString(9, money(item.getResponsibilityAllowance()));
                ps.setString(10, money(item.getTransportAllowance()));
                ps.setString(11, money(item.getOvertime()));
                ps.setString(12, money(item.getBonus()));
                ps.setString(13, money(item.getOtherEarnings()));
                ps.setString(14, money(item.getGrossPay()));
                ps.setString(15, money(item.getPaye()));
                ps.setString(16, money(item.getNssf()));
                ps.setString(17, money(item.getShif()));
                ps.setString(18, money(item.getPension()));
                ps.setString(19, money(item.getStaffLoanRepayment()));
                ps.setString(20, money(item.getSalaryAdvanceRecovery()));
                ps.setString(21, money(item.getWelfareContribution()));
                ps.setString(22, money(item.getCustomDeductions()));
                ps.setString(23, item.getCustomDeductionName());
                ps.setString(24, money(item.getTotalDeductions()));
                ps.setString(25, money(item.getNetPay()));
                ps.setString(26, money(item.getEmployerNssf()));
                ps.setString(27, money(item.getEmployerPension()));
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private void loadPayrollItems(Connection conn) throws SQLException {
        PayrollStore store = PayrollStore.getInstance();
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM payroll_items")) {
            while (rs.next()) {
                PayrollItem item = PayrollItem.withId(rs.getString("id"));
                item.setPayrollRunId(rs.getString("payroll_run_id"));
                item.setEmployeeId(rs.getString("employee_id"));
                item.setEmployeeNumber(rs.getString("employee_number"));
                item.setEmployeeName(rs.getString("employee_name"));
                item.setDepartment(rs.getString("department"));
                item.setBasicSalary(parseMoney(rs.getString("basic_salary")));
                item.setHouseAllowance(parseMoney(rs.getString("house_allowance")));
                item.setResponsibilityAllowance(parseMoney(rs.getString("responsibility_allowance")));
                item.setTransportAllowance(parseMoney(rs.getString("transport_allowance")));
                item.setOvertime(parseMoney(rs.getString("overtime")));
                item.setBonus(parseMoney(rs.getString("bonus")));
                item.setOtherEarnings(parseMoney(rs.getString("other_earnings")));
                item.setGrossPay(parseMoney(rs.getString("gross_pay")));
                item.setPaye(parseMoney(rs.getString("paye")));
                item.setNssf(parseMoney(rs.getString("nssf")));
                item.setShif(parseMoney(rs.getString("shif")));
                item.setPension(parseMoney(rs.getString("pension")));
                item.setStaffLoanRepayment(parseMoney(rs.getString("staff_loan_repayment")));
                item.setSalaryAdvanceRecovery(parseMoney(rs.getString("salary_advance_recovery")));
                item.setWelfareContribution(parseMoney(rs.getString("welfare_contribution")));
                item.setCustomDeductions(parseMoney(rs.getString("custom_deductions")));
                item.setCustomDeductionName(rs.getString("custom_deduction_name"));
                item.setTotalDeductions(parseMoney(rs.getString("total_deductions")));
                item.setNetPay(parseMoney(rs.getString("net_pay")));
                item.setEmployerNssf(parseMoney(rs.getString("employer_nssf")));
                item.setEmployerPension(parseMoney(rs.getString("employer_pension")));
                store.getPayrollItems().add(item);
            }
        }
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

    private static int boolInt(boolean b) {
        return b ? 1 : 0;
    }

    private static boolean parseBool(int i) {
        return i == 1;
    }

    // ==================== Procurement ====================

    private void saveSuppliers(Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO suppliers (id, supplier_number, business_name, contact_person, email, phone,
                    kra_pin, registration_number, address, category, active, blacklisted,
                    blacklist_reason, notes, created_at)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                ON CONFLICT(id) DO UPDATE SET supplier_number=excluded.supplier_number,
                    business_name=excluded.business_name, contact_person=excluded.contact_person,
                    email=excluded.email, phone=excluded.phone, kra_pin=excluded.kra_pin,
                    registration_number=excluded.registration_number, address=excluded.address,
                    category=excluded.category, active=excluded.active, blacklisted=excluded.blacklisted,
                    blacklist_reason=excluded.blacklist_reason, notes=excluded.notes,
                    created_at=excluded.created_at
                """)) {
            for (Supplier s : ProcurementStore.getInstance().getSuppliers()) {
                ps.setString(1, s.getId());
                ps.setString(2, s.getSupplierNumber());
                ps.setString(3, s.getBusinessName());
                ps.setString(4, s.getContactPerson());
                ps.setString(5, s.getEmail());
                ps.setString(6, s.getPhone());
                ps.setString(7, s.getKraPin());
                ps.setString(8, s.getRegistrationNumber());
                ps.setString(9, s.getAddress());
                ps.setString(10, s.getCategory());
                ps.setInt(11, boolInt(s.isActive()));
                ps.setInt(12, boolInt(s.isBlacklisted()));
                ps.setString(13, s.getBlacklistReason());
                ps.setString(14, s.getNotes());
                ps.setString(15, dateTime(s.getCreatedAt()));
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private void loadSuppliers(Connection conn) throws SQLException {
        ProcurementStore store = ProcurementStore.getInstance();
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM suppliers ORDER BY supplier_number")) {
            while (rs.next()) {
                Supplier s = Supplier.withId(rs.getString("id"));
                s.setSupplierNumber(rs.getString("supplier_number"));
                s.setBusinessName(rs.getString("business_name"));
                s.setContactPerson(rs.getString("contact_person"));
                s.setEmail(rs.getString("email"));
                s.setPhone(rs.getString("phone"));
                s.setKraPin(rs.getString("kra_pin"));
                s.setRegistrationNumber(rs.getString("registration_number"));
                s.setAddress(rs.getString("address"));
                s.setCategory(rs.getString("category"));
                s.setActive(parseBool(rs.getInt("active")));
                s.setBlacklisted(parseBool(rs.getInt("blacklisted")));
                s.setBlacklistReason(rs.getString("blacklist_reason"));
                s.setNotes(rs.getString("notes"));
                s.setCreatedAt(parseDateTime(rs.getString("created_at")));
                store.getSuppliers().add(s);
            }
        }
    }

    private void saveProcurementRequests(Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO procurement_requests (id, request_number, request_date, department, requested_by,
                    item_description, quantity, estimated_cost, justification, required_date, budget_account,
                    status, tender_id, created_at)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                ON CONFLICT(id) DO UPDATE SET request_number=excluded.request_number,
                    request_date=excluded.request_date, department=excluded.department,
                    requested_by=excluded.requested_by, item_description=excluded.item_description,
                    quantity=excluded.quantity, estimated_cost=excluded.estimated_cost,
                    justification=excluded.justification, required_date=excluded.required_date,
                    budget_account=excluded.budget_account, status=excluded.status,
                    tender_id=excluded.tender_id, created_at=excluded.created_at
                """)) {
            for (ProcurementRequest r : ProcurementStore.getInstance().getProcurementRequests()) {
                ps.setString(1, r.getId());
                ps.setString(2, r.getRequestNumber());
                ps.setString(3, date(r.getRequestDate()));
                ps.setString(4, r.getDepartment());
                ps.setString(5, r.getRequestedBy());
                ps.setString(6, r.getItemDescription());
                ps.setInt(7, r.getQuantity());
                ps.setString(8, money(r.getEstimatedCost()));
                ps.setString(9, r.getJustification());
                ps.setString(10, date(r.getRequiredDate()));
                ps.setString(11, r.getBudgetAccount());
                ps.setString(12, enumName(r.getStatus()));
                ps.setString(13, r.getTenderId());
                ps.setString(14, dateTime(r.getCreatedAt()));
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private void loadProcurementRequests(Connection conn) throws SQLException {
        ProcurementStore store = ProcurementStore.getInstance();
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM procurement_requests ORDER BY request_number")) {
            while (rs.next()) {
                ProcurementRequest r = ProcurementRequest.withId(rs.getString("id"));
                r.setRequestNumber(rs.getString("request_number"));
                r.setRequestDate(parseDate(rs.getString("request_date")));
                r.setDepartment(rs.getString("department"));
                r.setRequestedBy(rs.getString("requested_by"));
                r.setItemDescription(rs.getString("item_description"));
                r.setQuantity(rs.getInt("quantity"));
                r.setEstimatedCost(parseMoney(rs.getString("estimated_cost")));
                r.setJustification(rs.getString("justification"));
                r.setRequiredDate(parseDate(rs.getString("required_date")));
                r.setBudgetAccount(rs.getString("budget_account"));
                String status = rs.getString("status");
                if (status != null) {
                    r.setStatus(ProcurementRequestStatus.valueOf(status));
                }
                r.setTenderId(rs.getString("tender_id"));
                r.setCreatedAt(parseDateTime(rs.getString("created_at")));
                store.getProcurementRequests().add(r);
            }
        }
    }

    private void saveTenders(Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO tenders (id, tender_number, title, description, opening_date, closing_date,
                    tender_type, category, estimated_budget, evaluation_criteria, status,
                    procurement_request_id, awarded_supplier_id, awarded_amount, award_date,
                    award_reason, created_at)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                ON CONFLICT(id) DO UPDATE SET tender_number=excluded.tender_number, title=excluded.title,
                    description=excluded.description, opening_date=excluded.opening_date,
                    closing_date=excluded.closing_date, tender_type=excluded.tender_type,
                    category=excluded.category, estimated_budget=excluded.estimated_budget,
                    evaluation_criteria=excluded.evaluation_criteria, status=excluded.status,
                    procurement_request_id=excluded.procurement_request_id,
                    awarded_supplier_id=excluded.awarded_supplier_id,
                    awarded_amount=excluded.awarded_amount, award_date=excluded.award_date,
                    award_reason=excluded.award_reason, created_at=excluded.created_at
                """)) {
            for (Tender t : ProcurementStore.getInstance().getTenders()) {
                ps.setString(1, t.getId());
                ps.setString(2, t.getTenderNumber());
                ps.setString(3, t.getTitle());
                ps.setString(4, t.getDescription());
                ps.setString(5, date(t.getOpeningDate()));
                ps.setString(6, date(t.getClosingDate()));
                ps.setString(7, enumName(t.getTenderType()));
                ps.setString(8, enumName(t.getCategory()));
                ps.setString(9, money(t.getEstimatedBudget()));
                ps.setString(10, t.getEvaluationCriteria());
                ps.setString(11, enumName(t.getStatus()));
                ps.setString(12, t.getProcurementRequestId());
                ps.setString(13, t.getAwardedSupplierId());
                ps.setString(14, money(t.getAwardedAmount()));
                ps.setString(15, date(t.getAwardDate()));
                ps.setString(16, t.getAwardReason());
                ps.setString(17, dateTime(t.getCreatedAt()));
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private void loadTenders(Connection conn) throws SQLException {
        ProcurementStore store = ProcurementStore.getInstance();
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM tenders ORDER BY tender_number")) {
            while (rs.next()) {
                Tender t = Tender.withId(rs.getString("id"));
                t.setTenderNumber(rs.getString("tender_number"));
                t.setTitle(rs.getString("title"));
                t.setDescription(rs.getString("description"));
                t.setOpeningDate(parseDate(rs.getString("opening_date")));
                t.setClosingDate(parseDate(rs.getString("closing_date")));
                String tt = rs.getString("tender_type");
                if (tt != null) {
                    t.setTenderType(TenderType.valueOf(tt));
                }
                String cat = rs.getString("category");
                if (cat != null) {
                    t.setCategory(ProcurementCategory.valueOf(cat));
                }
                t.setEstimatedBudget(parseMoney(rs.getString("estimated_budget")));
                t.setEvaluationCriteria(rs.getString("evaluation_criteria"));
                String status = rs.getString("status");
                if (status != null) {
                    t.setStatus(TenderStatus.valueOf(status));
                }
                t.setProcurementRequestId(rs.getString("procurement_request_id"));
                t.setAwardedSupplierId(rs.getString("awarded_supplier_id"));
                t.setAwardedAmount(parseMoney(rs.getString("awarded_amount")));
                t.setAwardDate(parseDate(rs.getString("award_date")));
                t.setAwardReason(rs.getString("award_reason"));
                t.setCreatedAt(parseDateTime(rs.getString("created_at")));
                store.getTenders().add(t);
            }
        }
    }

    private void saveTenderBids(Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO tender_bids (id, tender_id, supplier_id, submission_date, bid_amount,
                    technical_score, financial_score, weighted_score, documents, remarks, status,
                    rank, created_at)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)
                ON CONFLICT(id) DO UPDATE SET tender_id=excluded.tender_id, supplier_id=excluded.supplier_id,
                    submission_date=excluded.submission_date, bid_amount=excluded.bid_amount,
                    technical_score=excluded.technical_score, financial_score=excluded.financial_score,
                    weighted_score=excluded.weighted_score, documents=excluded.documents,
                    remarks=excluded.remarks, status=excluded.status, rank=excluded.rank,
                    created_at=excluded.created_at
                """)) {
            for (TenderBid b : ProcurementStore.getInstance().getBids()) {
                ps.setString(1, b.getId());
                ps.setString(2, b.getTenderId());
                ps.setString(3, b.getSupplierId());
                ps.setString(4, date(b.getSubmissionDate()));
                ps.setString(5, money(b.getBidAmount()));
                ps.setString(6, money(b.getTechnicalScore()));
                ps.setString(7, money(b.getFinancialScore()));
                ps.setString(8, money(b.getWeightedScore()));
                ps.setString(9, b.getDocuments());
                ps.setString(10, b.getRemarks());
                ps.setString(11, enumName(b.getStatus()));
                ps.setInt(12, b.getRank());
                ps.setString(13, dateTime(b.getCreatedAt()));
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private void loadTenderBids(Connection conn) throws SQLException {
        ProcurementStore store = ProcurementStore.getInstance();
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM tender_bids ORDER BY submission_date")) {
            while (rs.next()) {
                TenderBid b = TenderBid.withId(rs.getString("id"));
                b.setTenderId(rs.getString("tender_id"));
                b.setSupplierId(rs.getString("supplier_id"));
                b.setSubmissionDate(parseDate(rs.getString("submission_date")));
                b.setBidAmount(parseMoney(rs.getString("bid_amount")));
                b.setTechnicalScore(parseMoney(rs.getString("technical_score")));
                b.setFinancialScore(parseMoney(rs.getString("financial_score")));
                b.setWeightedScore(parseMoney(rs.getString("weighted_score")));
                b.setDocuments(rs.getString("documents"));
                b.setRemarks(rs.getString("remarks"));
                String status = rs.getString("status");
                if (status != null) {
                    b.setStatus(BidStatus.valueOf(status));
                }
                b.setRank(rs.getInt("rank"));
                b.setCreatedAt(parseDateTime(rs.getString("created_at")));
                store.getBids().add(b);
            }
        }
    }

    private void saveTenderEvaluations(Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO tender_evaluations (id, tender_id, bid_id, evaluator_name, evaluation_type,
                    score, max_score, comments, evaluated_date, created_at)
                VALUES (?,?,?,?,?,?,?,?,?,?)
                ON CONFLICT(id) DO UPDATE SET tender_id=excluded.tender_id, bid_id=excluded.bid_id,
                    evaluator_name=excluded.evaluator_name, evaluation_type=excluded.evaluation_type,
                    score=excluded.score, max_score=excluded.max_score, comments=excluded.comments,
                    evaluated_date=excluded.evaluated_date, created_at=excluded.created_at
                """)) {
            for (TenderEvaluation e : ProcurementStore.getInstance().getEvaluations()) {
                ps.setString(1, e.getId());
                ps.setString(2, e.getTenderId());
                ps.setString(3, e.getBidId());
                ps.setString(4, e.getEvaluatorName());
                ps.setString(5, e.getEvaluationType());
                ps.setString(6, money(e.getScore()));
                ps.setString(7, money(e.getMaxScore()));
                ps.setString(8, e.getComments());
                ps.setString(9, date(e.getEvaluatedDate()));
                ps.setString(10, dateTime(e.getCreatedAt()));
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private void loadTenderEvaluations(Connection conn) throws SQLException {
        ProcurementStore store = ProcurementStore.getInstance();
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM tender_evaluations ORDER BY evaluated_date")) {
            while (rs.next()) {
                TenderEvaluation e = TenderEvaluation.withId(rs.getString("id"));
                e.setTenderId(rs.getString("tender_id"));
                e.setBidId(rs.getString("bid_id"));
                e.setEvaluatorName(rs.getString("evaluator_name"));
                e.setEvaluationType(rs.getString("evaluation_type"));
                e.setScore(parseMoney(rs.getString("score")));
                e.setMaxScore(parseMoney(rs.getString("max_score")));
                e.setComments(rs.getString("comments"));
                e.setEvaluatedDate(parseDate(rs.getString("evaluated_date")));
                e.setCreatedAt(parseDateTime(rs.getString("created_at")));
                store.getEvaluations().add(e);
            }
        }
    }

    private void saveTenderAwards(Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO tender_awards (id, tender_id, supplier_id, award_date, award_amount,
                    award_reason, contract_duration_months, approval_reference, approved_by, created_at)
                VALUES (?,?,?,?,?,?,?,?,?,?)
                ON CONFLICT(id) DO UPDATE SET tender_id=excluded.tender_id, supplier_id=excluded.supplier_id,
                    award_date=excluded.award_date, award_amount=excluded.award_amount,
                    award_reason=excluded.award_reason, contract_duration_months=excluded.contract_duration_months,
                    approval_reference=excluded.approval_reference, approved_by=excluded.approved_by,
                    created_at=excluded.created_at
                """)) {
            for (TenderAward a : ProcurementStore.getInstance().getAwards()) {
                ps.setString(1, a.getId());
                ps.setString(2, a.getTenderId());
                ps.setString(3, a.getSupplierId());
                ps.setString(4, date(a.getAwardDate()));
                ps.setString(5, money(a.getAwardAmount()));
                ps.setString(6, a.getAwardReason());
                ps.setInt(7, a.getContractDurationMonths());
                ps.setString(8, a.getApprovalReference());
                ps.setString(9, a.getApprovedBy());
                ps.setString(10, dateTime(a.getCreatedAt()));
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private void loadTenderAwards(Connection conn) throws SQLException {
        ProcurementStore store = ProcurementStore.getInstance();
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM tender_awards ORDER BY award_date")) {
            while (rs.next()) {
                TenderAward a = TenderAward.withId(rs.getString("id"));
                a.setTenderId(rs.getString("tender_id"));
                a.setSupplierId(rs.getString("supplier_id"));
                a.setAwardDate(parseDate(rs.getString("award_date")));
                a.setAwardAmount(parseMoney(rs.getString("award_amount")));
                a.setAwardReason(rs.getString("award_reason"));
                a.setContractDurationMonths(rs.getInt("contract_duration_months"));
                a.setApprovalReference(rs.getString("approval_reference"));
                a.setApprovedBy(rs.getString("approved_by"));
                a.setCreatedAt(parseDateTime(rs.getString("created_at")));
                store.getAwards().add(a);
            }
        }
    }

    private void saveContracts(Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO contracts (id, contract_number, tender_id, supplier_id, start_date, end_date,
                    contract_value, deliverables, status, notes, created_at)
                VALUES (?,?,?,?,?,?,?,?,?,?,?)
                ON CONFLICT(id) DO UPDATE SET contract_number=excluded.contract_number,
                    tender_id=excluded.tender_id, supplier_id=excluded.supplier_id,
                    start_date=excluded.start_date, end_date=excluded.end_date,
                    contract_value=excluded.contract_value, deliverables=excluded.deliverables,
                    status=excluded.status, notes=excluded.notes, created_at=excluded.created_at
                """)) {
            for (Contract c : ProcurementStore.getInstance().getContracts()) {
                ps.setString(1, c.getId());
                ps.setString(2, c.getContractNumber());
                ps.setString(3, c.getTenderId());
                ps.setString(4, c.getSupplierId());
                ps.setString(5, date(c.getStartDate()));
                ps.setString(6, date(c.getEndDate()));
                ps.setString(7, money(c.getContractValue()));
                ps.setString(8, c.getDeliverables());
                ps.setString(9, enumName(c.getStatus()));
                ps.setString(10, c.getNotes());
                ps.setString(11, dateTime(c.getCreatedAt()));
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private void loadContracts(Connection conn) throws SQLException {
        ProcurementStore store = ProcurementStore.getInstance();
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM contracts ORDER BY contract_number")) {
            while (rs.next()) {
                Contract c = Contract.withId(rs.getString("id"));
                c.setContractNumber(rs.getString("contract_number"));
                c.setTenderId(rs.getString("tender_id"));
                c.setSupplierId(rs.getString("supplier_id"));
                c.setStartDate(parseDate(rs.getString("start_date")));
                c.setEndDate(parseDate(rs.getString("end_date")));
                c.setContractValue(parseMoney(rs.getString("contract_value")));
                c.setDeliverables(rs.getString("deliverables"));
                String status = rs.getString("status");
                if (status != null) {
                    c.setStatus(ContractStatus.valueOf(status));
                }
                c.setNotes(rs.getString("notes"));
                c.setCreatedAt(parseDateTime(rs.getString("created_at")));
                store.getContracts().add(c);
            }
        }
    }

    private void saveContractMilestones(Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO contract_milestones (id, contract_id, title, description, due_date,
                    completed_date, completed, amount, created_at)
                VALUES (?,?,?,?,?,?,?,?,?)
                ON CONFLICT(id) DO UPDATE SET contract_id=excluded.contract_id, title=excluded.title,
                    description=excluded.description, due_date=excluded.due_date,
                    completed_date=excluded.completed_date, completed=excluded.completed,
                    amount=excluded.amount, created_at=excluded.created_at
                """)) {
            for (ContractMilestone m : ProcurementStore.getInstance().getMilestones()) {
                ps.setString(1, m.getId());
                ps.setString(2, m.getContractId());
                ps.setString(3, m.getTitle());
                ps.setString(4, m.getDescription());
                ps.setString(5, date(m.getDueDate()));
                ps.setString(6, date(m.getCompletedDate()));
                ps.setInt(7, boolInt(m.isCompleted()));
                ps.setString(8, money(m.getAmount()));
                ps.setString(9, dateTime(m.getCreatedAt()));
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private void loadContractMilestones(Connection conn) throws SQLException {
        ProcurementStore store = ProcurementStore.getInstance();
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM contract_milestones ORDER BY due_date")) {
            while (rs.next()) {
                ContractMilestone m = ContractMilestone.withId(rs.getString("id"));
                m.setContractId(rs.getString("contract_id"));
                m.setTitle(rs.getString("title"));
                m.setDescription(rs.getString("description"));
                m.setDueDate(parseDate(rs.getString("due_date")));
                m.setCompletedDate(parseDate(rs.getString("completed_date")));
                m.setCompleted(parseBool(rs.getInt("completed")));
                m.setAmount(parseMoney(rs.getString("amount")));
                m.setCreatedAt(parseDateTime(rs.getString("created_at")));
                store.getMilestones().add(m);
            }
        }
    }

    private void saveProcurementApprovals(Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO procurement_approvals (id, entity_type, entity_id, action, performed_by,
                    role, comments, timestamp)
                VALUES (?,?,?,?,?,?,?,?)
                ON CONFLICT(id) DO UPDATE SET entity_type=excluded.entity_type, entity_id=excluded.entity_id,
                    action=excluded.action, performed_by=excluded.performed_by, role=excluded.role,
                    comments=excluded.comments, timestamp=excluded.timestamp
                """)) {
            for (ProcurementApproval a : ProcurementStore.getInstance().getApprovals()) {
                ps.setString(1, a.getId());
                ps.setString(2, a.getEntityType());
                ps.setString(3, a.getEntityId());
                ps.setString(4, enumName(a.getAction()));
                ps.setString(5, a.getPerformedBy());
                ps.setString(6, a.getRole());
                ps.setString(7, a.getComments());
                ps.setString(8, dateTime(a.getTimestamp()));
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private void loadProcurementApprovals(Connection conn) throws SQLException {
        ProcurementStore store = ProcurementStore.getInstance();
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM procurement_approvals ORDER BY timestamp")) {
            while (rs.next()) {
                ProcurementApproval a = ProcurementApproval.withId(rs.getString("id"));
                a.setEntityType(rs.getString("entity_type"));
                a.setEntityId(rs.getString("entity_id"));
                String action = rs.getString("action");
                if (action != null) {
                    a.setAction(ApprovalAction.valueOf(action));
                }
                a.setPerformedBy(rs.getString("performed_by"));
                a.setRole(rs.getString("role"));
                a.setComments(rs.getString("comments"));
                a.setTimestamp(parseDateTime(rs.getString("timestamp")));
                store.getApprovals().add(a);
            }
        }
    }
}
