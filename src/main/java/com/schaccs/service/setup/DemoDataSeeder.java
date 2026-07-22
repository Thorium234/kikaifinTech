package com.schaccs.service.setup;

import com.schaccs.accounting.AccountingEngine;
import com.schaccs.config.AppConfig;
import com.schaccs.config.CurrencyConfig;
import com.schaccs.enums.AcademicTerm;
import com.schaccs.enums.AccountType;
import com.schaccs.enums.BoardingStatus;
import com.schaccs.enums.PaymentMode;
import com.schaccs.enums.VoucherStatus;
import com.schaccs.model.fee.FeeStructure;
import com.schaccs.model.fee.FeeStructureItem;
import com.schaccs.model.finance.Votehead;
import com.schaccs.model.receipt.Receipt;
import com.schaccs.model.receipt.ReceiptLine;
import com.schaccs.model.school.SchoolFormClass;
import com.schaccs.model.school.SchoolStream;
import com.schaccs.model.student.Student;
import com.schaccs.model.student.StudentFeeLedger;
import com.schaccs.model.voucher.Creditor;
import com.schaccs.model.voucher.PaymentVoucher;
import com.schaccs.repository.PersistenceService;
import com.schaccs.service.fee.FeeCalculationService;
import com.schaccs.store.AuditStore;
import com.schaccs.store.BankReconciliationStore;
import com.schaccs.store.FeeStructureStore;
import com.schaccs.store.LedgerStore;
import com.schaccs.store.ReceiptStore;
import com.schaccs.store.SchoolCustomStore;
import com.schaccs.store.StudentStore;
import com.schaccs.store.VoucherStore;
import com.schaccs.util.NumberGenerator;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class DemoDataSeeder {

    private static final Random RANDOM = new Random(42);

    private DemoDataSeeder() {}

    public static void seed() {
        PersistenceService psvc = PersistenceService.getInstance();
        StudentStore studentStore = StudentStore.getInstance();
        FeeStructureStore feeStore = FeeStructureStore.getInstance();
        ReceiptStore receiptStore = ReceiptStore.getInstance();
        LedgerStore ledgerStore = LedgerStore.getInstance();
        VoucherStore voucherStore = VoucherStore.getInstance();
        AuditStore auditStore = AuditStore.getInstance();
        BankReconciliationStore bankRecStore = BankReconciliationStore.getInstance();
        SchoolCustomStore schoolCustomStore = SchoolCustomStore.getInstance();
        FeeCalculationService feeCalc = new FeeCalculationService(feeStore, studentStore);
        AccountingEngine accounting = new AccountingEngine();

        String originalUser = AppConfig.getInstance().getCurrentUser();
        AppConfig.getInstance().setCurrentUser("Demo Seeder");

        try {
            studentStore.clear();
            feeStore.clear();
            receiptStore.clear();
            ledgerStore.clear();
            voucherStore.clear();
            auditStore.clear();
            bankRecStore.clear();
            schoolCustomStore.clear();

            psvc.transactional(conn -> {
                try (Statement st = conn.createStatement()) {
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
                }
            });

            List<Votehead> voteheads = createVoteheads();
            for (Votehead v : voteheads) feeStore.addVotehead(v);

            int year = AppConfig.getInstance().getAcademicYear();
            FeeStructure boardingStructure = createBoardingFeeStructure(year, feeStore);
            FeeStructure dayStructure = createDayFeeStructure(year, feeStore);
            feeStore.addStructure(boardingStructure);
            feeStore.addStructure(dayStructure);

            schoolCustomStore.addFormClass(new SchoolFormClass("Form 1"));
            schoolCustomStore.addFormClass(new SchoolFormClass("Form 2"));
            schoolCustomStore.addFormClass(new SchoolFormClass("Form 3"));
            schoolCustomStore.addFormClass(new SchoolFormClass("Form 4"));
            schoolCustomStore.addFormClass(new SchoolFormClass("G10"));
            schoolCustomStore.addFormClass(new SchoolFormClass("G11"));
            schoolCustomStore.addFormClass(new SchoolFormClass("G12"));
            schoolCustomStore.addFormClass(new SchoolFormClass("G13"));
            schoolCustomStore.addStream(new SchoolStream("A"));
            schoolCustomStore.addStream(new SchoolStream("W"));
            schoolCustomStore.addStream(new SchoolStream("E"));
            schoolCustomStore.addStream(new SchoolStream("S"));
            schoolCustomStore.addStream(new SchoolStream("N"));

            List<Student> students = createStudents();
            for (Student s : students) studentStore.add(s);

            List<String[]> studentPlan = buildStudentPaymentPlan();

            for (int i = 0; i < students.size(); i++) {
                Student s = students.get(i);
                String[] plan = studentPlan.get(i);
                boolean hasArrears = "YES".equals(plan[0]);
                double payRatioT1 = Double.parseDouble(plan[1]);
                double payRatioT2 = Double.parseDouble(plan[2]);
                double payRatioT3 = Double.parseDouble(plan[3]);

                StudentFeeLedger ledger = studentStore.getLedger(s.getId());

                if (hasArrears) {
                    ledger.setArrears(CurrencyConfig.money("18500.00"));
                }

                feeCalc.chargeTermFees(s, AcademicTerm.TERM_1);
                createReceipt(s, ledger, payRatioT1, AcademicTerm.TERM_1, accounting, receiptStore);
                feeCalc.chargeTermFees(s, AcademicTerm.TERM_2);
                createReceipt(s, ledger, payRatioT2, AcademicTerm.TERM_2, accounting, receiptStore);
                feeCalc.chargeTermFees(s, AcademicTerm.TERM_3);
                createReceipt(s, ledger, payRatioT3, AcademicTerm.TERM_3, accounting, receiptStore);
            }

            createSampleVouchers(voucherStore, accounting);

            psvc.saveAll();

        } finally {
            AppConfig.getInstance().setCurrentUser(originalUser);
        }
    }

    private static List<Votehead> createVoteheads() {
        return List.of(
                new Votehead("TUIT", "Tuition", AccountType.SCHOOL_FUND, 1),
                new Votehead("BORD", "Boarding", AccountType.SCHOOL_FUND, 2),
                new Votehead("LUNCH", "Lunch Fee", AccountType.SCHOOL_FUND, 3),
                new Votehead("ACTIV", "Activities", AccountType.SCHOOL_FUND, 4),
                new Votehead("COMP", "Computer", AccountType.SCHOOL_FUND, 5),
                new Votehead("DEV", "Development", AccountType.FSE_OPERATIONS, 6),
                new Votehead("MED", "Medical", AccountType.SCHOOL_FUND, 7),
                new Votehead("TRANSP", "Transport", AccountType.SCHOOL_FUND, 8)
        );
    }

    private static FeeStructure createBoardingFeeStructure(int year, FeeStructureStore feeStore) {
        FeeStructure fs = new FeeStructure(year, "ALL", BoardingStatus.BOARDING, "Boarding Fee Structure " + year);
        for (AcademicTerm term : AcademicTerm.values()) {
            fs.addItem(new FeeStructureItem("TUIT", "Tuition", term, BoardingStatus.BOARDING, CurrencyConfig.money("10000.00")));
            fs.addItem(new FeeStructureItem("BORD", "Boarding", term, BoardingStatus.BOARDING, CurrencyConfig.money("15000.00")));
            fs.addItem(new FeeStructureItem("LUNCH", "Lunch Fee", term, BoardingStatus.BOARDING, CurrencyConfig.money("3000.00")));
            fs.addItem(new FeeStructureItem("ACTIV", "Activities", term, BoardingStatus.BOARDING, CurrencyConfig.money("2000.00")));
            fs.addItem(new FeeStructureItem("COMP", "Computer", term, BoardingStatus.BOARDING, CurrencyConfig.money("1500.00")));
            fs.addItem(new FeeStructureItem("DEV", "Development", term, BoardingStatus.BOARDING, CurrencyConfig.money("3000.00")));
            fs.addItem(new FeeStructureItem("MED", "Medical", term, BoardingStatus.BOARDING, CurrencyConfig.money("1000.00")));
            fs.addItem(new FeeStructureItem("TRANSP", "Transport", term, BoardingStatus.BOARDING, CurrencyConfig.money("1500.00")));
        }
        return fs;
    }

    private static FeeStructure createDayFeeStructure(int year, FeeStructureStore feeStore) {
        FeeStructure fs = new FeeStructure(year, "ALL", BoardingStatus.DAY, "Day Fee Structure " + year);
        for (AcademicTerm term : AcademicTerm.values()) {
            fs.addItem(new FeeStructureItem("TUIT", "Tuition", term, BoardingStatus.DAY, CurrencyConfig.money("10000.00")));
            fs.addItem(new FeeStructureItem("LUNCH", "Lunch Fee", term, BoardingStatus.DAY, CurrencyConfig.money("3000.00")));
            fs.addItem(new FeeStructureItem("ACTIV", "Activities", term, BoardingStatus.DAY, CurrencyConfig.money("2000.00")));
            fs.addItem(new FeeStructureItem("COMP", "Computer", term, BoardingStatus.DAY, CurrencyConfig.money("1500.00")));
            fs.addItem(new FeeStructureItem("DEV", "Development", term, BoardingStatus.DAY, CurrencyConfig.money("3000.00")));
            fs.addItem(new FeeStructureItem("MED", "Medical", term, BoardingStatus.DAY, CurrencyConfig.money("1000.00")));
        }
        return fs;
    }

    private static List<Student> createStudents() {
        List<Student> students = new ArrayList<>();

        String[][] data = {
                {"2025/001", "Otieno Okoth",   "M", "Form 1", "A", "BOARDING", "0721-100-001", "Mary Okoth", "0721-100-901", "ID123401"},
                {"2025/002", "Wanjiku Njoroge","F", "Form 1", "A", "DAY",      "0721-100-002", "John Njoroge", "0721-100-902", "ID123402"},
                {"2025/003", "Kimutai Kiprop", "M", "Form 1", "A", "BOARDING", "0721-100-003", "Sarah Kiprop", "0721-100-903", "ID123403"},
                {"2025/004", "Akinyi Omondi",  "F", "Form 1", "A", "DAY",      "0721-100-004", "Peter Omondi", "0721-100-904", "ID123404"},
                {"2025/005", "Muthoni Kariuki","F", "Form 1", "A", "BOARDING", "0721-100-005", "David Kariuki", "0721-100-905", "ID123405"},
                {"2025/006", "Kamau Wachira",  "M", "Form 1", "A", "BOARDING", "0721-100-006", "Grace Wachira", "0721-100-906", "ID123406"},
                {"2024/001", "Kipchumba Rotich","M", "Form 2", "A", "BOARDING", "0722-200-001", "Jane Rotich", "0722-200-901", "ID123407"},
                {"2024/002", "Chebet Kiplagat","F", "Form 2", "A", "DAY",      "0722-200-002", "Paul Kiplagat", "0722-200-902", "ID123408"},
                {"2024/003", "Mutua Mwende",   "M", "Form 2", "A", "BOARDING", "0722-200-003", "Agnes Mwende", "0722-200-903", "ID123409"},
                {"2024/004", "Nyambura Wainaina","F","Form 2", "A", "DAY",      "0722-200-004", "Samuel Wainaina", "0722-200-904", "ID123410"},
                {"2024/005", "Ochieng Onyango","M", "Form 2", "A", "BOARDING", "0722-200-005", "Eunice Onyango", "0722-200-905", "ID123411"},
                {"2024/006", "Jepkosgei Biwott","F", "Form 2", "A", "BOARDING", "0722-200-006", "William Biwott", "0722-200-906", "ID123412"},
                {"2023/001", "Njenga Mbugua",  "M", "Form 3", "A", "BOARDING", "0723-300-001", "Ruth Mbugua", "0723-300-901", "ID123413"},
                {"2023/002", "Achieng Otieno", "F", "Form 3", "A", "DAY",      "0723-300-002", "Tom Otieno", "0723-300-902", "ID123414"},
                {"2023/003", "Kipkorir Langat","M", "Form 3", "A", "BOARDING", "0723-300-003", "Nancy Langat", "0723-300-903", "ID123415"},
                {"2023/004", "Wambui Gichuru", "F", "Form 3", "A", "DAY",      "0723-300-004", "Francis Gichuru", "0723-300-904", "ID123416"},
                {"2023/005", "Wafula Simiyu",  "M", "Form 3", "A", "BOARDING", "0723-300-005", "Catherine Simiyu", "0723-300-905", "ID123417"},
                {"2023/006", "Chepkoech Rono", "F", "Form 3", "A", "BOARDING", "0723-300-006", "Joseph Rono", "0723-300-906", "ID123418"},
                {"2022/001", "Barasa Wekesa",  "M", "Form 4", "A", "BOARDING", "0724-400-001", "Sarah Wekesa", "0724-400-901", "ID123419"},
                {"2022/002", "Moraa Nyang'au", "F", "Form 4", "A", "DAY",      "0724-400-002", "Daniel Nyang'au", "0724-400-902", "ID123420"},
                {"2022/003", "Kiprono Sawe",   "M", "Form 4", "A", "BOARDING", "0724-400-003", "Margaret Sawe", "0724-400-903", "ID123421"},
                {"2022/004", "Njoki Thiong'o", "F", "Form 4", "A", "DAY",      "0724-400-004", "James Thiong'o", "0724-400-904", "ID123422"},
                {"2022/005", "Mwendwa Kilonzo","M", "Form 4", "A", "BOARDING", "0724-400-005", "Elizabeth Kilonzo", "0724-400-905", "ID123423"},
                {"2022/006", "Jerotich Mutai", "F", "Form 4", "A", "BOARDING", "0724-400-006", "Simon Mutai", "0724-400-906", "ID123424"},
                {"2026/001", "Nyongesa Wanjala","M", "G10",    "W", "BOARDING", "0730-100-001", "Esther Wanjala", "0730-100-901", "ID123425"},
                {"2026/002", "Akinyi Odhiambo", "F", "G10",    "E", "DAY",      "0730-100-002", "George Odhiambo", "0730-100-902", "ID123426"},
                {"2026/003", "Chepkemoi Kiprono","F","G11",    "S", "BOARDING", "0730-200-001", "David Kiprono", "0730-200-901", "ID123427"},
                {"2026/004", "Omondi Otieno",    "M", "G11",   "N", "DAY",      "0730-200-002", "Susan Otieno", "0730-200-902", "ID123428"},
                {"2026/005", "Wanjiku Maina",    "F", "G12",   "W", "BOARDING", "0730-300-001", "Patrick Maina", "0730-300-901", "ID123429"},
                {"2026/006", "Kiprop Cheruiyot", "M", "G12",   "E", "BOARDING", "0730-300-002", "Monica Cheruiyot", "0730-300-902", "ID123430"},
                {"2026/007", "Mueni Mutua",      "F", "G13",   "S", "DAY",      "0730-400-001", "Bernard Mutua", "0730-400-901", "ID123431"},
                {"2026/008", "Kamau Njoroge",    "M", "G13",   "N", "BOARDING", "0730-400-002", "Phoebe Njoroge", "0730-400-902", "ID123432"},
        };

        for (String[] row : data) {
            Student s = new Student(
                    row[0], row[1], row[3], row[4],
                    BoardingStatus.valueOf(row[5]), row[6]
            );
            s.setGender(row[2]);
            s.setAcademicYear(AppConfig.getInstance().getAcademicYear());
            s.setParentName(row[7]);
            s.setGuardianPhone(row[8]);
            s.setGuardianId(row[9]);
            students.add(s);
        }

        return students;
    }

    private static List<String[]> buildStudentPaymentPlan() {
        List<String[]> plans = new ArrayList<>();
        // Form 1 (6)
        plans.add(new String[]{"NO",  "1.0", "1.0", "1.0"});
        plans.add(new String[]{"NO",  "1.0", "1.0", "0.85"});
        plans.add(new String[]{"YES", "0.70", "0.60", "0.20"});
        plans.add(new String[]{"YES", "0.80", "0.50", "0.15"});
        plans.add(new String[]{"NO",  "1.0", "0.90", "0.75"});
        plans.add(new String[]{"YES", "0.65", "0.40", "0.10"});
        // Form 2 (6)
        plans.add(new String[]{"NO",  "1.0", "1.0", "1.0"});
        plans.add(new String[]{"NO",  "1.0", "1.0", "0.80"});
        plans.add(new String[]{"YES", "0.75", "0.55", "0.25"});
        plans.add(new String[]{"YES", "0.85", "0.60", "0.20"});
        plans.add(new String[]{"NO",  "1.0", "0.95", "0.70"});
        plans.add(new String[]{"YES", "0.60", "0.35", "0.10"});
        // Form 3 (6)
        plans.add(new String[]{"NO",  "1.0", "1.0", "1.0"});
        plans.add(new String[]{"NO",  "1.0", "1.0", "0.75"});
        plans.add(new String[]{"YES", "0.70", "0.50", "0.20"});
        plans.add(new String[]{"YES", "0.80", "0.55", "0.15"});
        plans.add(new String[]{"NO",  "1.0", "0.85", "0.65"});
        plans.add(new String[]{"YES", "0.55", "0.30", "0.10"});
        // Form 4 (6)
        plans.add(new String[]{"NO",  "1.0", "1.0", "1.0"});
        plans.add(new String[]{"NO",  "1.0", "1.0", "0.85"});
        plans.add(new String[]{"YES", "0.75", "0.50", "0.20"});
        plans.add(new String[]{"YES", "0.80", "0.45", "0.15"});
        plans.add(new String[]{"NO",  "1.0", "0.90", "0.70"});
        plans.add(new String[]{"YES", "0.65", "0.40", "0.10"});
        // G10 (2)
        plans.add(new String[]{"YES", "0.60", "0.50", "0.15"});
        plans.add(new String[]{"NO",  "1.0", "0.85", "0.70"});
        // G11 (2)
        plans.add(new String[]{"YES", "0.70", "0.45", "0.20"});
        plans.add(new String[]{"NO",  "1.0", "0.90", "0.75"});
        // G12 (2)
        plans.add(new String[]{"NO",  "1.0", "1.0", "0.80"});
        plans.add(new String[]{"YES", "0.65", "0.50", "0.10"});
        // G13 (2)
        plans.add(new String[]{"YES", "0.75", "0.55", "0.25"});
        plans.add(new String[]{"NO",  "1.0", "1.0", "0.90"});

        return plans;
    }

    private static void createReceipt(Student student, StudentFeeLedger ledger,
                                       double payRatio, AcademicTerm term,
                                       AccountingEngine accounting,
                                       ReceiptStore receiptStore) {
        BigDecimal termFee = FeeStructureStore.getInstance()
                .findStructure(AppConfig.getInstance().getAcademicYear(), student.getBoardingStatus())
                .map(fs -> fs.totalForTerm(term))
                .orElse(BigDecimal.ZERO);
        BigDecimal payAmount = CurrencyConfig.money(termFee.multiply(CurrencyConfig.money(String.valueOf(payRatio))));
        if (payAmount.compareTo(BigDecimal.ZERO) <= 0) return;

        PaymentMode mode = pickPaymentMode();
        String ref = mode == PaymentMode.CASH ? null : "TXN-" + term.getNumber() + "-" + student.getAdmissionNumber().replace("/", "");

        LocalDate date = termDate(term);

        Receipt receipt = new Receipt();
        receipt.setReceiptNumber(NumberGenerator.nextReceiptNumber());
        receipt.setDate(date);
        receipt.setStudentId(student.getId());
        receipt.setAdmissionNumber(student.getAdmissionNumber());
        receipt.setStudentName(student.getName());
        receipt.setClassLabel(student.getClassLabel());
        receipt.setAmount(payAmount);
        receipt.setPaymentMode(mode);
        receipt.setBankReference(ref);
        receipt.setReceivedBy("Demo Seeder");
        receipt.setNotes("Demo " + term.getDisplayName() + " payment");

        String reportRef = "RCPT-" + receipt.getReceiptNumber();
        BigDecimal totalAllocated = CurrencyConfig.zero();

        java.util.Map<String, BigDecimal> outstanding = ledger.getOutstandingByVotehead();
        List<String> orderedCodes = new ArrayList<>(outstanding.keySet());
        orderedCodes.sort(java.util.Comparator.comparingInt(code ->
                FeeStructureStore.getInstance().findVoteheadByCode(code)
                        .map(Votehead::getPriority).orElse(999)));

        BigDecimal remaining = payAmount;

        if (ledger.getArrears().compareTo(BigDecimal.ZERO) > 0 && remaining.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal arrearsTake = ledger.getArrears().min(remaining);
            remaining = remaining.subtract(arrearsTake);
            ledger.setArrears(ledger.getArrears().subtract(arrearsTake));
        }

        for (String code : orderedCodes) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) break;
            BigDecimal due = outstanding.get(code);
            if (due == null || due.compareTo(BigDecimal.ZERO) <= 0) continue;
            BigDecimal take = due.min(remaining);
            String vhName = FeeStructureStore.getInstance().voteheadName(code);
            ReceiptLine line = new ReceiptLine(code, vhName, take);
            receipt.addLine(line);
            ledger.pay(code, take);
            totalAllocated = totalAllocated.add(take);
            remaining = remaining.subtract(take);

            Votehead vh = FeeStructureStore.getInstance().findVoteheadByCode(code).orElse(null);
            AccountType acct = vh != null ? vh.getAccountType() : AccountType.SCHOOL_FUND;
            accounting.postFeeReceiptLine(reportRef,
                    "Fee receipt " + receipt.getReceiptNumber() + " \u2014 " + vhName
                            + " (" + student.getAdmissionNumber() + ")",
                    acct, code, take, student.getId(), receipt.getId(), null, date);
        }

        if (remaining.compareTo(BigDecimal.ZERO) > 0) {
            ReceiptLine line = new ReceiptLine("ADVANCE", "Advance / Credit", remaining);
            receipt.addLine(line);
            ledger.addAdvance(remaining);
            totalAllocated = totalAllocated.add(remaining);
        }

        if (totalAllocated.compareTo(BigDecimal.ZERO) > 0) {
            receiptStore.add(receipt);
        }
    }

    private static void createSampleVouchers(VoucherStore voucherStore, AccountingEngine accounting) {
        Creditor cred1 = new Creditor("Demo Supplies Ltd", "0725-000-111");
        cred1.setDescription("Stationery supplier");
        Creditor cred2 = new Creditor("EcoSan Services", "0725-000-222");
        cred2.setDescription("Sanitation contractor");
        voucherStore.addCreditor(cred1);
        voucherStore.addCreditor(cred2);

        PaymentVoucher v1 = new PaymentVoucher();
        v1.setVoucherNumber(AppConfig.getInstance().getSchoolProfile().allocateVoucherNumber());
        v1.setDate(LocalDate.of(2026, 3, 15));
        v1.setCreditorId(cred1.getId());
        v1.setCreditorName(cred1.getName());
        v1.setVoteheadCode("ACTIV");
        v1.setVoteheadName("Activities");
        v1.setAccountType(AccountType.SCHOOL_FUND);
        v1.setAmount(CurrencyConfig.money("45000.00"));
        v1.setDescription("Sports equipment for Form 1-4 games");
        v1.setStatus(VoucherStatus.PAID);
        v1.setPaymentMode(PaymentMode.BANK_SLIP);
        v1.setBankReference("BVN-2026-001");
        v1.setPreparedBy("Demo Seeder");
        v1.setApprovedBy("Mr. Kituyi S.A.");
        v1.setNotes("Demo expenditure");
        v1.setCreatedAt(LocalDateTime.of(2026, 3, 15, 10, 0));
        voucherStore.addVoucher(v1);

        accounting.postFeeReceiptLine("VCH-1001",
                "Payment to " + cred1.getName() + " for sports equipment",
                AccountType.SCHOOL_FUND, "ACTIV",
                CurrencyConfig.money("45000.00").negate(),
                null, null, v1.getId(), LocalDate.of(2026, 3, 15));

        PaymentVoucher v2 = new PaymentVoucher();
        v2.setVoucherNumber(AppConfig.getInstance().getSchoolProfile().allocateVoucherNumber());
        v2.setDate(LocalDate.of(2026, 6, 20));
        v2.setCreditorId(cred2.getId());
        v2.setCreditorName(cred2.getName());
        v2.setVoteheadCode("DEV");
        v2.setVoteheadName("Development");
        v2.setAccountType(AccountType.FSE_OPERATIONS);
        v2.setAmount(CurrencyConfig.money("120000.00"));
        v2.setDescription("Classroom renovation - painting and flooring");
        v2.setStatus(VoucherStatus.PAID);
        v2.setPaymentMode(PaymentMode.BANK_SLIP);
        v2.setBankReference("BVN-2026-002");
        v2.setPreparedBy("Demo Seeder");
        v2.setApprovedBy("Mr. Kituyi S.A.");
        v2.setNotes("Demo expenditure");
        v2.setCreatedAt(LocalDateTime.of(2026, 6, 20, 14, 30));
        voucherStore.addVoucher(v2);

        accounting.postFeeReceiptLine("VCH-1002",
                "Payment to " + cred2.getName() + " for classroom renovation",
                AccountType.FSE_OPERATIONS, "DEV",
                CurrencyConfig.money("120000.00").negate(),
                null, null, v2.getId(), LocalDate.of(2026, 6, 20));
    }

    private static PaymentMode pickPaymentMode() {
        PaymentMode[] modes = {PaymentMode.BANK_SLIP, PaymentMode.MPESA, PaymentMode.CHEQUE};
        return modes[RANDOM.nextInt(modes.length)];
    }

    private static LocalDate termDate(AcademicTerm term) {
        return switch (term) {
            case TERM_1 -> LocalDate.of(2026, 1, 15);
            case TERM_2 -> LocalDate.of(2026, 5, 10);
            case TERM_3 -> LocalDate.of(2026, 9, 1);
        };
    }
}
