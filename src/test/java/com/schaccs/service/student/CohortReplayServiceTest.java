package com.schaccs.service.student;

import com.schaccs.config.AppConfig;
import com.schaccs.config.CurrencyConfig;
import com.schaccs.enums.AcademicTerm;
import com.schaccs.enums.BoardingStatus;
import com.schaccs.enums.StudentStatus;
import com.schaccs.model.fee.FeeStructure;
import com.schaccs.model.fee.FeeStructureItem;
import com.schaccs.model.student.Student;
import com.schaccs.model.student.StudentTermBalance;
import com.schaccs.repository.PersistenceService;
import com.schaccs.service.school.AcademicCalendarService;
import com.schaccs.store.FeeStructureStore;
import com.schaccs.store.StudentStore;
import com.schaccs.store.StudentTermBalanceStore;
import com.schaccs.service.student.CohortReplayService.ReplayResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CohortReplayServiceTest {

    private final FeeStructureStore feeStore = FeeStructureStore.getInstance();
    private final StudentTermBalanceStore balanceStore = StudentTermBalanceStore.getInstance();
    private final CohortReplayService replayService = new CohortReplayService();

    @BeforeEach
    void setUp() {
        PersistenceService.getInstance().clearAll();
        AppConfig.getInstance().setCurrentUserRole("PRINCIPAL");
    }

    @AfterEach
    void tearDown() {
        PersistenceService.getInstance().clearAll();
    }

    private void addFeeStructure(int year, BigDecimal t1, BigDecimal t2, BigDecimal t3) {
        FeeStructure fs = new FeeStructure(year, "ALL", BoardingStatus.BOARDING,
                "Boarding " + year);
        fs.addItem(new FeeStructureItem("TUITION", "Tuition", BoardingStatus.BOARDING, t1, t2, t3));
        feeStore.addStructure(fs);
    }

    private Student createStudent(String adm, int admissionYear, int duration, int arrivalYear) {
        Student s = new Student();
        s.setAdmissionNumber(adm);
        s.setName("Student " + adm);
        s.setFormClass("Form 1");
        s.setStream("A");
        s.setBoardingStatus(BoardingStatus.BOARDING);
        s.setStatus(StudentStatus.ACTIVE);
        s.setAcademicYear(arrivalYear);
        s.setYearOfAdmission(admissionYear);
        s.setCourseDurationYears(duration);
        s.setLifecycleStatus("ACTIVE");
        StudentStore.getInstance().add(s);
        return s;
    }

    @Test
    @DisplayName("Replays each historical year with that year's fee structure and lands opening arrears")
    void replaysTimelineAndLandsArrears() {
        addFeeStructure(2024, CurrencyConfig.money("1000"), CurrencyConfig.money("1000"),
                CurrencyConfig.money("1000"));
        addFeeStructure(2025, CurrencyConfig.money("2000"), CurrencyConfig.money("2000"),
                CurrencyConfig.money("2000"));
        Student s = createStudent("ADM-R1", 2024, 4, 2026);

        ReplayResult result = replayService.replay(s);

        assertTrue(result.replayed());
        assertEquals(6, result.termsSnapshotted());
        assertEquals(0, CurrencyConfig.money("9000").compareTo(result.openingArrears()));
        assertEquals(0, CurrencyConfig.money("9000").compareTo(
                StudentStore.getInstance().getLedger(s.getId()).getArrears()));

        var snapshots = balanceStore.findByStudent(s.getId());
        assertEquals(6, snapshots.size());
        var first = balanceStore.find(s.getId(), 2024, AcademicTerm.TERM_1).orElseThrow();
        assertEquals(0, CurrencyConfig.money("1000").compareTo(first.getFeeBilled()));
        assertEquals(0, CurrencyConfig.zero().compareTo(first.getArrearsBroughtForward()));
        // Waterfall: 2024 closes at 3000, becomes 2025 T1 brought-forward
        assertEquals(0, CurrencyConfig.money("3000").compareTo(
                balanceStore.find(s.getId(), 2024, AcademicTerm.TERM_3).orElseThrow().getClosingBalance()));
        assertEquals(0, CurrencyConfig.money("3000").compareTo(
                balanceStore.find(s.getId(), 2025, AcademicTerm.TERM_1).orElseThrow().getArrearsBroughtForward()));
        assertEquals(0, CurrencyConfig.money("9000").compareTo(
                balanceStore.find(s.getId(), 2025, AcademicTerm.TERM_3).orElseThrow().getClosingBalance()));
    }

    @Test
    @DisplayName("Historical payments waterfall across terms earliest first")
    void paymentsWaterfall() {
        addFeeStructure(2024, CurrencyConfig.money("1000"), CurrencyConfig.money("1000"),
                CurrencyConfig.money("1000"));
        Student s = createStudent("ADM-R2", 2024, 4, 2025);

        ReplayResult result = replayService.replay(s, CurrencyConfig.money("2500"));

        assertTrue(result.replayed());
        var t1 = balanceStore.find(s.getId(), 2024, AcademicTerm.TERM_1).orElseThrow();
        var t2 = balanceStore.find(s.getId(), 2024, AcademicTerm.TERM_2).orElseThrow();
        var t3 = balanceStore.find(s.getId(), 2024, AcademicTerm.TERM_3).orElseThrow();
        assertEquals(0, CurrencyConfig.money("1000").compareTo(t1.getAmountPaid()));
        assertEquals(0, CurrencyConfig.money("1000").compareTo(t2.getAmountPaid()));
        assertEquals(0, CurrencyConfig.money("500").compareTo(t3.getAmountPaid()));
        assertEquals(0, CurrencyConfig.zero().compareTo(t2.getClosingBalance()));
        assertEquals(0, CurrencyConfig.money("500").compareTo(result.openingArrears()));
    }

    @Test
    @DisplayName("Overpayment becomes advance credit, not negative arrears")
    void overpaymentBecomesAdvance() {
        addFeeStructure(2024, CurrencyConfig.money("1000"), CurrencyConfig.money("1000"),
                CurrencyConfig.money("1000"));
        Student s = createStudent("ADM-R3", 2024, 4, 2025);

        ReplayResult result = replayService.replay(s, CurrencyConfig.money("5000"));

        assertTrue(result.replayed());
        assertEquals(0, CurrencyConfig.zero().compareTo(result.openingArrears()));
        assertEquals(0, CurrencyConfig.money("2000").compareTo(result.advanceCredit()));
        assertEquals(0, CurrencyConfig.money("2000").compareTo(
                StudentStore.getInstance().getLedger(s.getId()).getAdvance()));
        assertEquals(0, CurrencyConfig.zero().compareTo(
                balanceStore.find(s.getId(), 2024, AcademicTerm.TERM_3)
                        .orElseThrow().getClosingBalance()));
    }

    @Test
    @DisplayName("Missing structure warns but keeps the arrears chain continuous")
    void missingStructureCarriesForward() {
        addFeeStructure(2025, CurrencyConfig.money("2000"), CurrencyConfig.money("2000"),
                CurrencyConfig.money("2000"));
        Student s = createStudent("ADM-R4", 2024, 4, 2026);

        ReplayResult result = replayService.replay(s);

        assertTrue(result.replayed());
        assertTrue(result.warnings().stream().anyMatch(w -> w.contains("2024")));
        // 2024 unbilled (zeros), 2025 bills 6000 → closing chain intact
        assertEquals(0, CurrencyConfig.money("6000").compareTo(result.openingArrears()));
        assertEquals(0, CurrencyConfig.money("6000").compareTo(
                balanceStore.find(s.getId(), 2025, AcademicTerm.TERM_3)
                        .orElseThrow().getArrearsBroughtForward()
                        .add(balanceStore.find(s.getId(), 2025, AcademicTerm.TERM_3)
                                .orElseThrow().getFeeBilled())));
    }

    @Test
    @DisplayName("Replay is idempotent — second run is skipped and does not double arrears")
    void replayIsIdempotent() {
        addFeeStructure(2024, CurrencyConfig.money("1000"), CurrencyConfig.money("1000"),
                CurrencyConfig.money("1000"));
        Student s = createStudent("ADM-R5", 2024, 4, 2025);

        replayService.replay(s);
        ReplayResult second = replayService.replay(s);

        assertFalse(second.replayed());
        assertNotNull(second.skipReason());
        assertEquals(3, balanceStore.findByStudent(s.getId()).size());
        assertEquals(0, CurrencyConfig.money("3000").compareTo(
                StudentStore.getInstance().getLedger(s.getId()).getArrears()));
    }

    @Test
    @DisplayName("Cohort starting in the arrival year has nothing to replay")
    void noHistoryWhenAdmissionEqualsArrival() {
        int currentYear = LocalDate.now().getYear();
        Student s = createStudent("ADM-R6", currentYear, 4, currentYear);

        ReplayResult result = replayService.replay(s);

        assertFalse(result.replayed());
        assertEquals(0, balanceStore.findByStudent(s.getId()).size());
        assertEquals(0, CurrencyConfig.zero().compareTo(
                StudentStore.getInstance().getLedger(s.getId()).getArrears()));
    }

    @Test
    @DisplayName("Auto-stop: timeline never extends past Y_admit + D - 1")
    void timelineStopsAtCompletionYear() {
        addFeeStructure(2020, CurrencyConfig.money("100"), CurrencyConfig.money("100"),
                CurrencyConfig.money("100"));
        addFeeStructure(2021, CurrencyConfig.money("100"), CurrencyConfig.money("100"),
                CurrencyConfig.money("100"));
        addFeeStructure(2022, CurrencyConfig.money("100"), CurrencyConfig.money("100"),
                CurrencyConfig.money("100"));
        addFeeStructure(2023, CurrencyConfig.money("100"), CurrencyConfig.money("100"),
                CurrencyConfig.money("100"));
        Student s = createStudent("ADM-R7", 2020, 4, LocalDate.now().getYear());

        ReplayResult result = replayService.replay(s);

        assertTrue(result.replayed());
        assertEquals(12, result.termsSnapshotted());
        List<StudentTermBalance> snapshots = balanceStore.findByStudent(s.getId());
        assertTrue(snapshots.stream().allMatch(b -> b.getAcademicYear() <= 2023),
                "No snapshot may exist beyond the expected completion year");
        assertEquals(0, CurrencyConfig.money("1200").compareTo(result.openingArrears()));
    }

    @Test
    @DisplayName("End-of-term rollover writes its own term snapshot (live-cycle continuity)")
    void rolloverWritesTermSnapshot() {
        addFeeStructure(2024, CurrencyConfig.money("1000"), CurrencyConfig.money("1000"),
                CurrencyConfig.money("1000"));
        new AcademicCalendarService().ensureYearCalendar(2024);
        Student s = createStudent("ADM-R8", 2024, 4, 2024);
        StudentStore.getInstance().getLedger(s.getId()).charge("TUITION",
                CurrencyConfig.money("1000"));

        new AcademicCalendarService().rolloverIfDue(LocalDate.now());

        // The catch-up loop bills every ended term of 2024 in sequence:
        // T1 closes at 1000 and becomes T2's brought-forward, etc.
        var t1 = balanceStore.find(s.getId(), 2024, AcademicTerm.TERM_1).orElseThrow();
        var t2 = balanceStore.find(s.getId(), 2024, AcademicTerm.TERM_2).orElseThrow();
        assertEquals(0, CurrencyConfig.money("1000").compareTo(t1.getFeeBilled()));
        assertEquals(0, CurrencyConfig.money("1000").compareTo(t1.getClosingBalance()));
        assertEquals(0, CurrencyConfig.money("1000").compareTo(t2.getArrearsBroughtForward()));
        assertEquals(3, balanceStore.findByStudent(s.getId()).size());
        assertEquals(0, CurrencyConfig.money("3000").compareTo(
                StudentStore.getInstance().getLedger(s.getId()).getArrears()));
    }
}
