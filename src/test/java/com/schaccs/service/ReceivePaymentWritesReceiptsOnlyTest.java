package com.schaccs.service;

import com.schaccs.config.AppConfig;
import com.schaccs.config.CurrencyConfig;
import com.schaccs.enums.AccountType;
import com.schaccs.enums.BoardingStatus;
import com.schaccs.enums.PaymentMode;
import com.schaccs.model.finance.Votehead;
import com.schaccs.model.receipt.Receipt;
import com.schaccs.model.student.Student;
import com.schaccs.model.student.StudentFeeLedger;
import com.schaccs.repository.Database;
import com.schaccs.repository.PersistenceService;
import com.schaccs.service.receipt.ReceiptService;
import com.schaccs.store.FeeStructureStore;
import com.schaccs.store.LedgerStore;
import com.schaccs.store.ReceiptStore;
import com.schaccs.store.StudentStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Guards the receive-payment persistence contract: posting a payment must be an
 * INSERT INTO receipts (plus its ledger projections) and must NEVER rewrite the
 * students table or its unique columns. Student balances stay dynamic —
 * (Fee Structure + Arrears) − Payments — across receipting, imports, and
 * save/load round trips, with no snapshot constraint conflicts.
 */
class ReceivePaymentWritesReceiptsOnlyTest {

    private String studentId;

    @BeforeEach
    void setUp() {
        PersistenceService.getInstance().clearAll();
        FeeStructureStore.getInstance().addVotehead(new Votehead("BOARD", "Boarding", AccountType.SCHOOL_FUND, 1));
        FeeStructureStore.getInstance().addVotehead(new Votehead("TUITION", "Tuition", AccountType.TUITION_FEES, 2));
        AppConfig.getInstance().getSchoolProfile().setNextReceiptNumber(50000);
    }

    @AfterEach
    void tearDown() {
        PersistenceService.getInstance().clearAll();
        Database.getInstance().close();
    }

    private Student createStudent(String admission, String name) {
        Student student = new Student();
        student.setAdmissionNumber(admission);
        student.setName(name);
        student.setFormClass("Form 2");
        student.setBoardingStatus(BoardingStatus.BOARDING);
        StudentStore.getInstance().add(student);
        studentId = student.getId();
        return student;
    }

    private void charge(Student student, String code, String amount) {
        StudentFeeLedger ledger = StudentStore.getInstance().getLedger(student.getId());
        ledger.charge(code, CurrencyConfig.money(amount));
    }

    /** Default {@code ReceiptService} uses the receipts-only persistence action. */
    private ReceiptService service() {
        return new ReceiptService();
    }

    /** Full snapshot of the students table: id + admission_number + name, ordered. */
    private List<String[]> studentsTableRows() throws Exception {
        List<String[]> rows = new ArrayList<>();
        try (Connection conn = Database.getInstance().getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT id, admission_number, name FROM students ORDER BY id")) {
            while (rs.next()) {
                rows.add(new String[]{rs.getString("id"), rs.getString("admission_number"), rs.getString("name")});
            }
        }
        return rows;
    }

    private int countRows(String table) throws Exception {
        try (Connection conn = Database.getInstance().getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM " + table)) {
            return rs.next() ? rs.getInt(1) : -1;
        }
    }

    @Test
    @DisplayName("Receiving a payment is an INSERT INTO receipts and leaves the students table untouched")
    void receivingPaymentIsInsertIntoReceiptsNotStudentsUpsert() throws Exception {
        Student student = createStudent("ADM-ONLY-" + UUID.randomUUID(), "Payment Test One");
        charge(student, "BOARD", "20000");
        PersistenceService.getInstance().saveAll();

        List<String[]> studentsBefore = studentsTableRows();
        int receiptsBefore = countRows("receipts");

        ReceiptService.Result result = service().receivePayment(
                student, CurrencyConfig.money("5000"), PaymentMode.MPESA, "PAY-REF", LocalDate.now(), null);
        assertTrue(result.isSuccess(), () -> String.join(", ", result.getErrors()));

        // The payment wrote a new receipt row.
        assertEquals(receiptsBefore + 1, countRows("receipts"), "Payment must INSERT one receipt row");

        // The students table is byte-for-byte identical: no rewrite, no unique-column update.
        assertEquals(studentsBefore.size(), studentsTableRows().size(),
                "Payment must not add or remove students rows");
        assertArrayEquals(studentsBefore.stream().flatMap(r -> Arrays.stream(r)).toArray(),
                studentsTableRows().stream().flatMap(r -> Arrays.stream(r)).toArray(),
                "Payment must not rewrite any students row (incl. admission_number)");
    }

    @Test
    @DisplayName("First payment for a brand-new student posts and survives a reload")
    void firstPaymentForNewStudentPostsAndSurvivesReload() {
        Student student = createStudent("ADM-NEW-" + UUID.randomUUID(), "Newly Created");
        charge(student, "TUITION", "15000");

        ReceiptService.Result result = service().receivePayment(
                student, CurrencyConfig.money("6000"), PaymentMode.BANK_SLIP, "NEW-REF", LocalDate.now(), null);
        assertTrue(result.isSuccess(), () -> String.join(", ", result.getErrors()));
        String receiptId = result.getReceipt().getId();

        StudentStore.getInstance().clear();
        ReceiptStore.getInstance().clear();
        FeeStructureStore.getInstance().clear();
        LedgerStore.getInstance().clear();
        PersistenceService.getInstance().loadAll();

        Student reloaded = StudentStore.getInstance().findByAdmissionNumber(student.getAdmissionNumber()).orElse(null);
        assertNotNull(reloaded, "Student must exist after reload");
        Receipt reloadedReceipt = ReceiptStore.getInstance().getReceipts().stream()
                .filter(r -> r.getId().equals(receiptId))
                .findFirst().orElse(null);
        assertNotNull(reloadedReceipt, "Receipt must exist after reload");
        assertTrue(reloadedReceipt.isVerified(), "Receipt must remain verified");

        StudentFeeLedger ledger = StudentStore.getInstance().getLedger(reloaded.getId());
        assertEquals(0, ledger.getPaid("TUITION").compareTo(CurrencyConfig.money("6000")),
                "Paid must be restored from the receipt after reload");
        assertEquals(0, ledger.getBalance().compareTo(CurrencyConfig.money("9000")),
                "Balance must recompute as (charged + arrears) - payments");
    }

    @Test
    @DisplayName("Balance is always dynamic: (Fee Structure + Arrears) − Payments, stable across round trips")
    void balanceIsDynamicAcrossImportsReceiptingAndRoundTrips() {
        Student student = createStudent("ADM-DYN-" + UUID.randomUUID(), "Dynamic Balance");
        StudentFeeLedger ledger = StudentStore.getInstance().getLedger(student.getId());

        // Fee structure charge (term billing) + imported opening arrears.
        charge(student, "BOARD", "20000");
        charge(student, "TUITION", "10000");
        ledger.setArrears(CurrencyConfig.money("5000"));

        // Manual receipting pays the balance: allocation clears imported arrears
        // first (5000), the remaining 3000 goes against term voteheads.
        ReceiptService.Result result = service().receivePayment(
                student, CurrencyConfig.money("8000"), PaymentMode.MPESA, "DYN-REF", LocalDate.now(), null);
        assertTrue(result.isSuccess(), () -> String.join(", ", result.getErrors()));

        assertEquals(0, ledger.getArrears().compareTo(CurrencyConfig.zero()),
                "Imported arrears are consumed by the payment in real time");
        assertEquals(0, ledger.getTotalPaid().compareTo(CurrencyConfig.money("3000")),
                "Remaining 3000 is booked as paid against voteheads");

        BigDecimal expected = CurrencyConfig.money("30000").add(CurrencyConfig.money("5000"))
                .subtract(CurrencyConfig.money("8000"));
        assertEquals(0, ledger.getBalance().compareTo(expected),
                "Live balance = (Fee Structure 30000 + Arrears 5000) - Payments 8000 = 27000");

        // Persist and reload: the same dynamic result must come back, no drift.
        PersistenceService.getInstance().saveAll();
        StudentStore.getInstance().clear();
        FeeStructureStore.getInstance().clear();
        PersistenceService.getInstance().loadAll();

        StudentFeeLedger reloaded = StudentStore.getInstance().getLedger(
                StudentStore.getInstance().findByAdmissionNumber(student.getAdmissionNumber()).orElseThrow().getId());
        assertEquals(0, reloaded.getTotalCharged().compareTo(CurrencyConfig.money("30000")),
                "Charges survive round trip");
        assertEquals(0, reloaded.getTotalPaid().compareTo(CurrencyConfig.money("3000")),
                "Payments survive round trip");
        assertEquals(0, reloaded.getArrears().compareTo(CurrencyConfig.zero()),
                "Consumed arrears stay consumed after round trip");
        assertEquals(0, reloaded.getBalance().compareTo(expected),
                "Dynamic balance stays identical after save/load round trip");
    }

    @Test
    @DisplayName("Receipt-only save keeps the receipt counter from regressing after a payment")
    void receiptCounterSurvivesPaymentWithoutFullSave() {
        Student student = createStudent("ADM-NUM-" + UUID.randomUUID(), "Counter Check");
        charge(student, "TUITION", "10000");

        service().receivePayment(student, CurrencyConfig.money("4000"), PaymentMode.MPESA, "NUM-REF",
                LocalDate.now(), null);
        long afterPost = AppConfig.getInstance().getSchoolProfile().getNextReceiptNumber();

        ReceiptStore.getInstance().clear();
        StudentStore.getInstance().clear();
        PersistenceService.getInstance().loadAll();

        assertTrue(AppConfig.getInstance().getSchoolProfile().getNextReceiptNumber() >= afterPost,
                "Receipt counter must not regress after reload");
    }

    @Test
    @DisplayName("Concurrent posting persists both receipts as separate rows")
    void concurrentPostingWritesSeparateReceiptRows() throws Exception {
        Student student = createStudent("ADM-TWO-" + UUID.randomUUID(), "Two Receipts");
        charge(student, "TUITION", "20000");

        ReceiptService.Result r1 = service().receivePayment(
                student, CurrencyConfig.money("3000"), PaymentMode.MPESA, "TWO-1", LocalDate.now(), null);
        ReceiptService.Result r2 = service().receivePayment(
                student, CurrencyConfig.money("4000"), PaymentMode.MPESA, "TWO-2", LocalDate.now(), null);
        assertTrue(r1.isSuccess());
        assertTrue(r2.isSuccess());
        assertNotEquals(r1.getReceipt().getId(), r2.getReceipt().getId());
        assertNotEquals(r1.getReceipt().getReceiptNumber(), r2.getReceipt().getReceiptNumber());

        assertEquals(2, countRows("receipts"), "Each payment is its own receipts row");
    }
}
