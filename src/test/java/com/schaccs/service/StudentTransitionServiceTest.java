package com.schaccs.service;

import com.schaccs.config.AppConfig;
import com.schaccs.config.CurrencyConfig;
import com.schaccs.enums.AcademicTerm;
import com.schaccs.enums.AccountType;
import com.schaccs.enums.BoardingStatus;
import com.schaccs.enums.StudentStatus;
import com.schaccs.model.fee.FeeStructure;
import com.schaccs.model.fee.FeeStructureItem;
import com.schaccs.model.finance.Votehead;
import com.schaccs.model.student.Student;
import com.schaccs.model.student.StudentFeeLedger;
import com.schaccs.repository.PersistenceService;
import com.schaccs.service.fee.FeeCalculationService;
import com.schaccs.service.student.StudentTransitionService;
import com.schaccs.store.FeeStructureStore;
import com.schaccs.store.StudentStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class StudentTransitionServiceTest {

    @BeforeEach
    void setUp() {
        PersistenceService.getInstance().clearAll();
        AppConfig.getInstance().getSchoolProfile().setAcademicYear(2026);
    }

    @AfterEach
    void tearDown() {
        PersistenceService.getInstance().clearAll();
    }

    private void setupStructure(BoardingStatus status, AcademicTerm term,
                                String code, String name, BigDecimal amount) {
        FeeStructureStore store = FeeStructureStore.getInstance();
        if (store.findVoteheadByCode(code).isEmpty()) {
            store.addVotehead(new Votehead(code, name, AccountType.SCHOOL_FUND, 1));
        }
        FeeStructure structure = store.findStructure(2026, status).orElse(null);
        if (structure == null) {
            structure = new FeeStructure(2026, "ALL", status, status + " Structure 2026");
            store.addStructure(structure);
        }
        structure.addItem(new FeeStructureItem(code, name, term, status, amount));
    }

    private Student createStudent(String adm, BoardingStatus boarding) {
        Student s = new Student();
        s.setAdmissionNumber(adm);
        s.setName("Test Student " + adm);
        s.setFormClass("Form 2");
        s.setStream("A");
        s.setBoardingStatus(boarding);
        s.setGender("M");
        s.setPhone("0700000000");
        s.setStatus(StudentStatus.ACTIVE);
        s.setAcademicYear(2026);
        StudentStore.getInstance().add(s);
        return s;
    }

    private FeeCalculationService feeCalc() {
        return new FeeCalculationService(FeeStructureStore.getInstance(), StudentStore.getInstance());
    }

    private StudentTransitionService transition() {
        return new StudentTransitionService(feeCalc(), StudentStore.getInstance(),
                FeeStructureStore.getInstance(), new com.schaccs.service.audit.AuditService());
    }

    private void dayBoardStructures() {
        setupStructure(BoardingStatus.DAY, AcademicTerm.TERM_1, "TUITION", "Tuition", CurrencyConfig.money("1000"));
        setupStructure(BoardingStatus.BOARDING, AcademicTerm.TERM_1, "TUITION", "Tuition", CurrencyConfig.money("1500"));
        setupStructure(BoardingStatus.BOARDING, AcademicTerm.TERM_1, "BOARD", "Boarding", CurrencyConfig.money("5000"));
        setupStructure(BoardingStatus.BOARDING, AcademicTerm.TERM_2, "BOARD", "Boarding", CurrencyConfig.money("5000"));
    }

    @Test
    void dayToBoardingChargesOnlyCurrentTermDeltaNotPastTerms() {
        dayBoardStructures();
        Student student = createStudent("ADM-DB", BoardingStatus.DAY);
        feeCalc().chargeTermFees(student, AcademicTerm.TERM_1);

        StudentFeeLedger before = StudentStore.getInstance().getLedger(student.getId());
        assertEquals(0, before.getCharged("TUITION").compareTo(CurrencyConfig.money("1000")),
                "Day student should be charged day TUITION 1000 for term 1 only");
        assertEquals(0, before.getCharged("BOARD").compareTo(BigDecimal.ZERO));

        StudentTransitionService.TransitionResult result = transition().apply(student, BoardingStatus.BOARDING, AcademicTerm.TERM_1);

        assertTrue(result.success(), "Transition should succeed: " + result.errors());
        assertEquals(BoardingStatus.BOARDING, student.getBoardingStatus());

        StudentFeeLedger after = StudentStore.getInstance().getLedger(student.getId());
        assertEquals(0, after.getCharged("TUITION").compareTo(CurrencyConfig.money("1500")),
                "TUITION should rise to the boarding amount (delta +500)");
        assertEquals(0, after.getCharged("BOARD").compareTo(CurrencyConfig.money("5000")),
                "Boarding fee for the current term must now be billed");
        assertEquals(0, after.getTotalCharged().compareTo(CurrencyConfig.money("6500")),
                "Total = TUITION 1500 + BOARD 5000 for term 1 only, no past terms");
    }

    @Test
    void dayToBoardingDoesNotBillFutureTermsYet() {
        dayBoardStructures();
        Student student = createStudent("ADM-FUTURE", BoardingStatus.DAY);
        feeCalc().chargeTermFees(student, AcademicTerm.TERM_1);

        transition().apply(student, BoardingStatus.BOARDING, AcademicTerm.TERM_1);

        StudentFeeLedger ledger = StudentStore.getInstance().getLedger(student.getId());
        assertEquals(0, ledger.getTotalCharged().compareTo(CurrencyConfig.money("6500")),
                "Term 2 boarding must NOT be charged until that term is billed");

        feeCalc().chargeTermFees(student, AcademicTerm.TERM_2);
        assertEquals(0, ledger.getCharged("BOARD").compareTo(CurrencyConfig.money("10000")),
                "Future term is billed under the new boarding status automatically");
    }

    @Test
    void boardingToDayReducesBoardingChargeForCurrentTermOnly() {
        dayBoardStructures();
        Student student = createStudent("ADM-BD", BoardingStatus.BOARDING);
        feeCalc().chargeTermFees(student, AcademicTerm.TERM_1);

        StudentFeeLedger before = StudentStore.getInstance().getLedger(student.getId());
        assertEquals(0, before.getTotalCharged().compareTo(CurrencyConfig.money("6500")));

        StudentTransitionService.TransitionResult result = transition().apply(student, BoardingStatus.DAY, AcademicTerm.TERM_1);

        assertTrue(result.success(), "Transition should succeed: " + result.errors());
        assertEquals(BoardingStatus.DAY, student.getBoardingStatus());

        StudentFeeLedger after = StudentStore.getInstance().getLedger(student.getId());
        assertEquals(0, after.getCharged("TUITION").compareTo(CurrencyConfig.money("1000")));
        assertEquals(0, after.getCharged("BOARD").compareTo(BigDecimal.ZERO),
                "Boarding charge for the current term must be removed");
        assertEquals(0, after.getTotalCharged().compareTo(CurrencyConfig.money("1000")),
                "Only day amount remains for the transitioned term");
    }

    @Test
    void sameStatusTransitionIsRejected() {
        dayBoardStructures();
        Student student = createStudent("ADM-SAME", BoardingStatus.DAY);
        StudentTransitionService.TransitionResult result =
                transition().apply(student, BoardingStatus.DAY, AcademicTerm.TERM_1);
        assertFalse(result.success());
        assertEquals(BoardingStatus.DAY, student.getBoardingStatus());
    }

    @Test
    void previewReportsPerVoteheadDeltas() {
        dayBoardStructures();
        Student student = createStudent("ADM-PREV", BoardingStatus.DAY);
        List<StudentTransitionService.TransitionDelta> deltas =
                transition().preview(student, BoardingStatus.BOARDING, AcademicTerm.TERM_1);

        Map<String, StudentTransitionService.TransitionDelta> byCode =
                new java.util.HashMap<>();
        for (StudentTransitionService.TransitionDelta d : deltas) {
            byCode.put(d.code(), d);
        }
        assertTrue(byCode.containsKey("BOARD"));
        assertTrue(byCode.containsKey("TUITION"));
        assertEquals(0, byCode.get("BOARD").delta().compareTo(CurrencyConfig.money("5000")));
        assertEquals(0, byCode.get("TUITION").delta().compareTo(CurrencyConfig.money("500")));
        assertEquals(0, transition().previewNetDelta(student, BoardingStatus.BOARDING, AcademicTerm.TERM_1)
                .compareTo(CurrencyConfig.money("5500")));
    }

    @Test
    void transitionSetsStudentStatusAndLedgerTogether() {
        dayBoardStructures();
        Student student = createStudent("ADM-BOUND", BoardingStatus.DAY);
        feeCalc().chargeTermFees(student, AcademicTerm.TERM_1);

        transition().apply(student, BoardingStatus.BOARDING, AcademicTerm.TERM_1);

        assertEquals(BoardingStatus.BOARDING, student.getBoardingStatus());
        StudentFeeLedger ledger = StudentStore.getInstance().getLedger(student.getId());
        assertEquals(0, ledger.getBalance().compareTo(CurrencyConfig.money("6500")),
                "Unpaid charges now include the boarding fee for the current term");
    }

    @Test
    void prorationScalesDeltaForTransitionTerm() {
        dayBoardStructures();
        Student student = createStudent("ADM-PRORATE", BoardingStatus.DAY);
        feeCalc().chargeTermFees(student, AcademicTerm.TERM_1);

        // Half term remaining -> boarding charges pro-rated to 50%.
        StudentTransitionService.TransitionResult result =
                transition().apply(student, BoardingStatus.BOARDING, AcademicTerm.TERM_1,
                        CurrencyConfig.money("0.5"));

        assertTrue(result.success(), "Prorated transition should succeed: " + result.errors());
        StudentFeeLedger ledger = StudentStore.getInstance().getLedger(student.getId());
        assertEquals(0, ledger.getCharged("BOARD").compareTo(CurrencyConfig.money("2500")),
                "Boarding 5000 pro-rated by 50% = 2500");
        assertEquals(0, ledger.getCharged("TUITION").compareTo(CurrencyConfig.money("1250")),
                "Tuition delta +500 pro-rated by 50% = 1250 on top of 1000 already billed");
        assertEquals(0, ledger.getTotalCharged().compareTo(CurrencyConfig.money("3750")),
                "Total = 2500 boarding + 1250 tuition (no future terms)");
    }

    @Test
    void fullRatioKeepsFullDelta() {
        dayBoardStructures();
        Student student = createStudent("ADM-FULLRATIO", BoardingStatus.DAY);
        feeCalc().chargeTermFees(student, AcademicTerm.TERM_1);
        transition().apply(student, BoardingStatus.BOARDING, AcademicTerm.TERM_1, BigDecimal.ONE);
        StudentFeeLedger ledger = StudentStore.getInstance().getLedger(student.getId());
        assertEquals(0, ledger.getTotalCharged().compareTo(CurrencyConfig.money("6500")),
                "Ratio 1.0 must match the unp-rorated behaviour");
    }

    @Test
    void prepaidLunchConvertsToCreditOnDayToBoarding() {
        dayBoardStructures();
        // Add LUNCH to the day structure so a deltas is produced when moving to boarding.
        setupStructure(BoardingStatus.DAY, AcademicTerm.TERM_1, "LUNCH", "Lunch", CurrencyConfig.money("2000"));
        Student student = createStudent("ADM-PREPAID", BoardingStatus.DAY);
        feeCalc().chargeTermFees(student, AcademicTerm.TERM_1);

        StudentFeeLedger ledger = StudentStore.getInstance().getLedger(student.getId());
        assertTrue(ledger.getCharged("LUNCH").compareTo(CurrencyConfig.money("2000")) == 0,
                "Day student must be charged lunch");
        ledger.pay("LUNCH", CurrencyConfig.money("2000"));
        assertEquals(0, ledger.getAdvance().compareTo(BigDecimal.ZERO), "No advance before transition");
        assertEquals(0, ledger.getPaid("LUNCH").compareTo(CurrencyConfig.money("2000")),
                "Lunch prepaid before the transition");

        // Boarding has no LUNCH -> moving drops it -> prepaid converts to advance credit.
        StudentTransitionService.TransitionResult result =
                transition().apply(student, BoardingStatus.BOARDING, AcademicTerm.TERM_1);

        assertTrue(result.success(), "Transition should succeed: " + result.errors());
        assertEquals(0, ledger.getPaid("LUNCH").compareTo(BigDecimal.ZERO),
                "Prepaid lunch must be released from the dropped votehead");
        assertEquals(0, ledger.getAdvance().compareTo(CurrencyConfig.money("2000")),
                "Prepaid lunch must become a carry-forward credit");
        assertFalse(result.conversions().isEmpty());
        assertEquals(0, result.conversions().get(0).amount().compareTo(CurrencyConfig.money("2000")));
    }

    @Test
    void prepaidIsProRatedAgainstReduction() {
        dayBoardStructures();
        // Day: SPORT 2000. Boarding: SPORT 800 (partial reduction; prepaid released beyond new charge).
        setupStructure(BoardingStatus.DAY, AcademicTerm.TERM_1, "SPORT", "Uniform/Sport", CurrencyConfig.money("2000"));
        setupStructure(BoardingStatus.BOARDING, AcademicTerm.TERM_1, "SPORT", "Uniform/Sport", CurrencyConfig.money("800"));
        Student student = createStudent("ADM-PROPCONV", BoardingStatus.DAY);
        feeCalc().chargeTermFees(student, AcademicTerm.TERM_1);
        StudentFeeLedger ledger = StudentStore.getInstance().getLedger(student.getId());
        assertEquals(0, ledger.getCharged("SPORT").compareTo(CurrencyConfig.money("2000")));
        ledger.pay("SPORT", CurrencyConfig.money("2000"));

        transition().apply(student, BoardingStatus.BOARDING, AcademicTerm.TERM_1);

        // Reduction is 2000 - 800 = 1200; prepaid 2000 -> convert only 1200, keep 800 paid.
        assertEquals(0, ledger.getAdvance().compareTo(CurrencyConfig.money("1200")),
                "Only the reduced portion (1200) converts to credit");
        assertEquals(0, ledger.getPaid("SPORT").compareTo(CurrencyConfig.money("800")),
                "Remaining prepaid held against the new sport charge");
        assertEquals(0, ledger.getCharged("SPORT").compareTo(CurrencyConfig.money("800")),
                "Sport charge reduced to the boarding amount");
    }

    @Test
    void invalidProrationRatioIsRejected() {
        dayBoardStructures();
        Student student = createStudent("ADM-BADRATIO", BoardingStatus.DAY);
        StudentTransitionService.TransitionResult result =
                transition().apply(student, BoardingStatus.BOARDING, AcademicTerm.TERM_1, BigDecimal.ZERO);
        assertFalse(result.success());
        assertEquals(BoardingStatus.DAY, student.getBoardingStatus(), "No change on invalid ratio");
    }
}
