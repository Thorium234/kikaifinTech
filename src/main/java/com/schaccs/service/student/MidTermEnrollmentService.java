package com.schaccs.service.student;

import com.schaccs.config.CurrencyConfig;
import com.schaccs.enums.AcademicTerm;
import com.schaccs.model.student.MidTermStudent;
import com.schaccs.model.student.Student;
import com.schaccs.model.student.StudentFeeLedger;
import com.schaccs.repository.PersistenceService;
import com.schaccs.service.Services;
import com.schaccs.service.fee.FeeCalculationService;
import com.schaccs.store.MidTermEnrollmentStore;
import com.schaccs.store.StudentStore;
import javafx.collections.ObservableList;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Mid-term enrollments: records a student admitted part-way through the current
 * term. When "charge for current mid-term" is on, the custom fee is posted to
 * the student's fee ledger under the MIDTERM votehead; otherwise the current
 * term is left unbilled. Full standard tuition is charged automatically from
 * the next term onward — the end-of-term transition calls
 * {@link #chargeFullFeesForCurrentTerm(Student)} for enrolled students.
 */
public class MidTermEnrollmentService {

    private final MidTermEnrollmentStore store;
    private final StudentStore studentStore;
    private final FeeCalculationService feeCalculationService;

    public MidTermEnrollmentService() {
        this(MidTermEnrollmentStore.getInstance(), StudentStore.getInstance(), new FeeCalculationService());
    }

    public MidTermEnrollmentService(MidTermEnrollmentStore store, StudentStore studentStore,
                                    FeeCalculationService feeCalculationService) {
        this.store = store;
        this.studentStore = studentStore;
        this.feeCalculationService = feeCalculationService;
    }

    public ObservableList<MidTermStudent> getEnrollments() {
        return store.getEnrollments();
    }

    public Optional<MidTermStudent> findByStudentId(String studentId) {
        return store.findByStudentId(studentId);
    }

    public boolean isMidTermEnrolled(String studentId) {
        return store.findByStudentId(studentId).isPresent();
    }

    public List<String> enrollStudent(Student student, LocalDate dateJoined,
                                      boolean chargeCurrentTerm, BigDecimal customFee) {
        List<String> errors = validate(student, dateJoined, chargeCurrentTerm, customFee, null);
        if (!errors.isEmpty()) {
            return errors;
        }
        StudentFeeLedger ledger = studentStore.getLedger(student.getId());
        currentSchoolTerm().ifPresent(ledger::setCurrentTerm);
        BigDecimal fee = chargeCurrentTerm ? CurrencyConfig.money(customFee) : CurrencyConfig.zero();
        if (chargeCurrentTerm) {
            ledger.charge(MidTermStudent.MIDTERM_CODE, fee);
        }
        store.add(MidTermStudent.forStudent(student, dateJoined, chargeCurrentTerm, fee));
        PersistenceService.getInstance().saveAll();
        return errors;
    }

    public List<String> updateEnrollment(MidTermStudent enrollment, LocalDate dateJoined,
                                         boolean chargeCurrentTerm, BigDecimal customFee) {
        List<String> errors = validate(null, dateJoined, chargeCurrentTerm, customFee, enrollment);
        if (!errors.isEmpty()) {
            return errors;
        }
        StudentFeeLedger ledger = studentStore.getLedger(enrollment.getStudentId());
        if (enrollment.isChargeCurrentTerm()) {
            ledger.reduceCharge(MidTermStudent.MIDTERM_CODE, enrollment.getMidTermFee());
        }
        BigDecimal fee = chargeCurrentTerm ? CurrencyConfig.money(customFee) : CurrencyConfig.zero();
        if (chargeCurrentTerm) {
            ledger.charge(MidTermStudent.MIDTERM_CODE, fee);
        }
        enrollment.setDateJoined(dateJoined);
        enrollment.setChargeCurrentTerm(chargeCurrentTerm);
        enrollment.setMidTermFee(fee);
        PersistenceService.getInstance().saveAll();
        return errors;
    }

    public void deleteEnrollment(MidTermStudent enrollment) {
        store.remove(enrollment);
        PersistenceService.getInstance().saveAll();
    }

    /**
     * Charges the full standard tuition for the student's current term cycle.
     * Called by the end-of-term transition once an enrolled student advances to
     * the next term, which is what makes full fees apply from the next term
     * onward. Idempotent for a term that has already been billed.
     */
    public void chargeFullFeesForCurrentTerm(Student student) {
        StudentFeeLedger ledger = studentStore.getLedger(student.getId());
        feeCalculationService.chargeTermFees(student, ledger.getCurrentTerm());
    }

    private List<String> validate(Student student, LocalDate dateJoined,
                                  boolean chargeCurrentTerm, BigDecimal customFee,
                                  MidTermStudent editing) {
        List<String> errors = new ArrayList<>();
        if (editing == null) {
            if (student == null) {
                errors.add("Select a student.");
            } else if (studentStore.findById(student.getId()).isEmpty()) {
                errors.add("Student not found.");
            } else if (store.findByStudentId(student.getId()).isPresent()) {
                errors.add("Student already has a mid-term enrollment.");
            }
        }
        if (dateJoined == null) {
            errors.add("Pick the date the student joined.");
        }
        if (chargeCurrentTerm && (customFee == null
                || CurrencyConfig.money(customFee).signum() <= 0)) {
            errors.add("Mid-term fee must be greater than zero when charging the current term.");
        }
        return errors;
    }

    private Optional<AcademicTerm> currentSchoolTerm() {
        return Services.getInstance().academicCalendar().currentTerm(LocalDate.now());
    }
}
