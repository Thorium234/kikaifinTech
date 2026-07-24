package com.schaccs.util;

import com.schaccs.config.CurrencyConfig;
import com.schaccs.enums.AcademicTerm;
import com.schaccs.enums.AccountType;
import com.schaccs.enums.BoardingStatus;
import com.schaccs.enums.PaymentMode;
import com.schaccs.enums.StudentStatus;
import com.schaccs.model.fee.FeeStructure;
import com.schaccs.model.fee.FeeStructureItem;
import com.schaccs.model.finance.Votehead;
import com.schaccs.model.payroll.Employee;
import com.schaccs.model.payroll.SalaryStructure;
import com.schaccs.model.student.Student;
import com.schaccs.model.voucher.Creditor;
import com.schaccs.service.fee.FeeCalculationService;
import com.schaccs.service.receipt.ReceiptService;
import com.schaccs.store.EmployeeStore;
import com.schaccs.store.FeeStructureStore;
import com.schaccs.store.StudentStore;
import com.schaccs.store.VoucherStore;

import java.time.LocalDate;

/**
 * Seeds Friends School Kikai Boys 2026 fee structure and sample students.
 */
public final class MockData {

    private MockData() {
    }

    public static void load() {
        loadVoteheads();
        loadFeeStructures();
        loadStudents();
        chargeFees();
        sampleReceipts();
        sampleCreditors();
        sampleEmployees();
    }

    private static void sampleCreditors() {
        VoucherStore vs = VoucherStore.getInstance();
        Creditor c1 = new Creditor("Kikai Hardware Suppliers", "0700111222");
        c1.setDescription("RMI materials");
        vs.addCreditor(c1);
        Creditor c2 = new Creditor("Chwele Bookshop", "0700333444");
        c2.setDescription("LT&T / stationery");
        vs.addCreditor(c2);
    }

    private static void loadVoteheads() {
        FeeStructureStore store = FeeStructureStore.getInstance();
        // Priority: lower number = paid first (Boarding first per school practice)
        store.addVotehead(new Votehead("BOARDING", "Boarding", AccountType.SCHOOL_FUND, 10));
        store.addVotehead(new Votehead("EWC", "EWC", AccountType.FSE_OPERATIONS, 20));
        store.addVotehead(new Votehead("PE", "Personal Emolument", AccountType.SCHOOL_FUND, 30));
        store.addVotehead(new Votehead("RMI", "RMI", AccountType.FSE_OPERATIONS, 40));
        store.addVotehead(new Votehead("ADMIN", "Administration Costs", AccountType.SCHOOL_FUND, 50));
        store.addVotehead(new Votehead("LTT", "L.T & T", AccountType.FSE_OPERATIONS, 60));
        store.addVotehead(new Votehead("ACTIVITY", "Activity", AccountType.FSE_OPERATIONS, 70));
        store.addVotehead(new Votehead("LUNCH", "Lunch (Day Scholars)", AccountType.SCHOOL_FUND, 15));
    }

    private static void loadFeeStructures() {
        FeeStructureStore store = FeeStructureStore.getInstance();

        FeeStructure boarding = new FeeStructure(2026, "ALL", BoardingStatus.BOARDING,
                "2026 Boarding — Parents Fees");
        // Term 1
        add(boarding, "BOARDING", "Boarding", AcademicTerm.TERM_1, 14000);
        add(boarding, "EWC", "EWC", AcademicTerm.TERM_1, 1000);
        add(boarding, "PE", "Personal Emolument", AcademicTerm.TERM_1, 2000);
        add(boarding, "RMI", "RMI", AcademicTerm.TERM_1, 1000);
        add(boarding, "ADMIN", "Administration Costs", AcademicTerm.TERM_1, 1000);
        add(boarding, "LTT", "L.T & T", AcademicTerm.TERM_1, 1000);
        add(boarding, "ACTIVITY", "Activity", AcademicTerm.TERM_1, 1000);
        // Term 2
        add(boarding, "BOARDING", "Boarding", AcademicTerm.TERM_2, 6500);
        add(boarding, "EWC", "EWC", AcademicTerm.TERM_2, 1000);
        add(boarding, "PE", "Personal Emolument", AcademicTerm.TERM_2, 1000);
        add(boarding, "RMI", "RMI", AcademicTerm.TERM_2, 1000);
        add(boarding, "ADMIN", "Administration Costs", AcademicTerm.TERM_2, 1000);
        add(boarding, "LTT", "L.T & T", AcademicTerm.TERM_2, 1000);
        add(boarding, "ACTIVITY", "Activity", AcademicTerm.TERM_2, 1000);
        // Term 3
        add(boarding, "BOARDING", "Boarding", AcademicTerm.TERM_3, 4000);
        add(boarding, "EWC", "EWC", AcademicTerm.TERM_3, 500);
        add(boarding, "PE", "Personal Emolument", AcademicTerm.TERM_3, 500);
        add(boarding, "RMI", "RMI", AcademicTerm.TERM_3, 500);
        add(boarding, "ADMIN", "Administration Costs", AcademicTerm.TERM_3, 500);
        add(boarding, "LTT", "L.T & T", AcademicTerm.TERM_3, 500);
        add(boarding, "ACTIVITY", "Activity", AcademicTerm.TERM_3, 500);
        store.addStructure(boarding);

        FeeStructure day = new FeeStructure(2026, "ALL", BoardingStatus.DAY,
                "2026 Day Scholars — Lunch Term 1");
        addDay(day, "LUNCH", "Lunch (Day Scholars)", AcademicTerm.TERM_1, 5500);
        // Day scholars may still have shared operational voteheads — keep lunch primary for V1
        addDay(day, "EWC", "EWC", AcademicTerm.TERM_1, 1000);
        addDay(day, "ACTIVITY", "Activity", AcademicTerm.TERM_1, 1000);
        addDay(day, "ADMIN", "Administration Costs", AcademicTerm.TERM_1, 1000);
        store.addStructure(day);
    }

    private static void add(FeeStructure structure, String code, String name, AcademicTerm term, double amount) {
        structure.addItem(new FeeStructureItem(code, name, term, BoardingStatus.BOARDING, CurrencyConfig.money(amount)));
    }

    private static void addDay(FeeStructure structure, String code, String name, AcademicTerm term, double amount) {
        structure.addItem(new FeeStructureItem(code, name, term, BoardingStatus.DAY, CurrencyConfig.money(amount)));
    }

    private static void loadStudents() {
        StudentStore store = StudentStore.getInstance();

        Student s1 = new Student("2026/001", "Elias Wanjala", "Form 3", "W", BoardingStatus.BOARDING, "0712345678");
        s1.setGender("Male");
        s1.setParentName("Mr. Wanjala");
        s1.setYearOfAdmission(2023);
        s1.setAcademicYear(2026);
        s1.setStatus(StudentStatus.ACTIVE);
        store.add(s1);
        store.getLedger(s1.getId()).setArrears(CurrencyConfig.money(2500));

        Student s2 = new Student("2026/002", "Brian Simiyu", "Form 1", "A", BoardingStatus.BOARDING, "0722001122");
        s2.setGender("Male");
        s2.setParentName("Mrs. Simiyu");
        s2.setYearOfAdmission(2026);
        store.add(s2);

        Student s3 = new Student("2026/003", "David Barasa", "Form 2", "B", BoardingStatus.BOARDING, "0733112233");
        s3.setGender("Male");
        s3.setParentName("Mr. Barasa");
        s3.setYearOfAdmission(2025);
        store.add(s3);

        Student s4 = new Student("2026/004", "Peter Wafula", "Form 4", "C", BoardingStatus.BOARDING, "0744223344");
        s4.setGender("Male");
        s4.setParentName("Mrs. Wafula");
        s4.setYearOfAdmission(2022);
        store.add(s4);

        Student s5 = new Student("2026/005", "James Masinde", "Form 3", "W", BoardingStatus.DAY, "0755334455");
        s5.setGender("Male");
        s5.setParentName("Mr. Masinde");
        s5.setYearOfAdmission(2023);
        store.add(s5);

        Student s6 = new Student("2026/006", "Samuel Nakitare", "Form 1", "B", BoardingStatus.BOARDING, "0766445566");
        s6.setGender("Male");
        s6.setParentName("Mrs. Nakitare");
        s6.setYearOfAdmission(2026);
        store.add(s6);

        Student s7 = new Student("2026/007", "Kevin Wekesa", "Form 2", "A", BoardingStatus.BOARDING, "0777556677");
        s7.setGender("Male");
        s7.setParentName("Mr. Wekesa");
        s7.setYearOfAdmission(2025);
        store.add(s7);

        Student s8 = new Student("2026/008", "George Mukhwana", "Form 4", "W", BoardingStatus.DAY, "0788667788");
        s8.setGender("Male");
        s8.setParentName("Mrs. Mukhwana");
        s8.setYearOfAdmission(2022);
        store.add(s8);
    }

    private static void chargeFees() {
        FeeCalculationService feeService = new FeeCalculationService();
        for (Student s : StudentStore.getInstance().getStudents()) {
            feeService.chargeAnnualFees(s);
        }
    }

    private static void sampleEmployees() {
        EmployeeStore store = EmployeeStore.getInstance();

        Employee e1 = new Employee();
        e1.setEmployeeNumber("EMP001");
        e1.setFirstName("John");
        e1.setLastName("Odhiambo");
        e1.setNationalId("12345678");
        e1.setDepartment("Teaching");
        e1.setPosition("Senior Teacher");
        e1.setEmploymentDate(LocalDate.of(2018, 1, 15));
        e1.setPhone("0722100001");
        e1.setKraPin("A001234567B");
        e1.setNssfNumber("NSSF001");
        e1.setShifNumber("SHIF001");
        e1.setBankName("KCB");
        e1.setBankBranch("Kikai");
        e1.setBankAccountNumber("1234567890");
        store.getEmployees().add(e1);

        SalaryStructure ss1 = new SalaryStructure();
        ss1.setEmployeeId(e1.getId());
        ss1.setBasicSalary(CurrencyConfig.money(85000));
        ss1.setHouseAllowance(CurrencyConfig.money(15000));
        ss1.setResponsibilityAllowance(CurrencyConfig.money(10000));
        ss1.setTransportAllowance(CurrencyConfig.money(5000));
        ss1.setEffectiveDate(LocalDate.of(2024, 1, 1));
        store.getSalaryStructures().add(ss1);

        Employee e2 = new Employee();
        e2.setEmployeeNumber("EMP002");
        e2.setFirstName("Mary");
        e2.setLastName("Wanjiku");
        e2.setNationalId("23456789");
        e2.setDepartment("Teaching");
        e2.setPosition("Teacher");
        e2.setEmploymentDate(LocalDate.of(2020, 9, 1));
        e2.setPhone("0722100002");
        e2.setKraPin("B002345678C");
        e2.setNssfNumber("NSSF002");
        e2.setShifNumber("SHIF002");
        e2.setBankName("Equity");
        e2.setBankBranch("Chwele");
        e2.setBankAccountNumber("2345678901");
        store.getEmployees().add(e2);

        SalaryStructure ss2 = new SalaryStructure();
        ss2.setEmployeeId(e2.getId());
        ss2.setBasicSalary(CurrencyConfig.money(65000));
        ss2.setHouseAllowance(CurrencyConfig.money(12000));
        ss2.setTransportAllowance(CurrencyConfig.money(4000));
        ss2.setEffectiveDate(LocalDate.of(2024, 1, 1));
        store.getSalaryStructures().add(ss2);

        Employee e3 = new Employee();
        e3.setEmployeeNumber("EMP003");
        e3.setFirstName("Peter");
        e3.setLastName("Kiprop");
        e3.setNationalId("34567890");
        e3.setDepartment("Administration");
        e3.setPosition("Bursar");
        e3.setEmploymentDate(LocalDate.of(2015, 3, 10));
        e3.setPhone("0722100003");
        e3.setKraPin("C003456789D");
        e3.setNssfNumber("NSSF003");
        e3.setShifNumber("SHIF003");
        e3.setBankName("KCB");
        e3.setBankBranch("Kikai");
        e3.setBankAccountNumber("3456789012");
        store.getEmployees().add(e3);

        SalaryStructure ss3 = new SalaryStructure();
        ss3.setEmployeeId(e3.getId());
        ss3.setBasicSalary(CurrencyConfig.money(75000));
        ss3.setHouseAllowance(CurrencyConfig.money(15000));
        ss3.setResponsibilityAllowance(CurrencyConfig.money(8000));
        ss3.setTransportAllowance(CurrencyConfig.money(5000));
        ss3.setStaffLoanRepayment(CurrencyConfig.money(3000));
        ss3.setEffectiveDate(LocalDate.of(2024, 1, 1));
        store.getSalaryStructures().add(ss3);
    }

    private static void sampleReceipts() {
        ReceiptService receipts = new ReceiptService();
        StudentStore students = StudentStore.getInstance();

        students.findByAdmissionNumber("2026/001").ifPresent(s ->
                receipts.receivePayment(s, CurrencyConfig.money(15000), PaymentMode.BANK_SLIP,
                        "NBK-20260319-001", LocalDate.of(2026, 3, 19), "Sample receipt matching school slip"));

        students.findByAdmissionNumber("2026/002").ifPresent(s ->
                receipts.receivePayment(s, CurrencyConfig.money(21000), PaymentMode.BANK_SLIP,
                        "NBK-20260115-014", LocalDate.of(2026, 1, 15), "Term 1 full payment"));

        students.findByAdmissionNumber("2026/003").ifPresent(s ->
                receipts.receivePayment(s, CurrencyConfig.money(5000), PaymentMode.MPESA,
                        "QK7H2X9LM1", LocalDate.of(2026, 2, 10), "Partial payment"));
    }
}
