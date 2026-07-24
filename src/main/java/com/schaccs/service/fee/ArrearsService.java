package com.schaccs.service.fee;

import com.schaccs.config.CurrencyConfig;
import com.schaccs.model.student.Student;
import com.schaccs.model.student.StudentFeeLedger;
import com.schaccs.service.audit.AuditService;
import com.schaccs.store.StudentStore;

import java.math.BigDecimal;

public class ArrearsService {

    private final StudentStore studentStore;
    private final AuditService auditService;

    public ArrearsService() {
        this(StudentStore.getInstance(), new AuditService());
    }

    public ArrearsService(StudentStore studentStore) {
        this(studentStore, new AuditService());
    }

    public ArrearsService(StudentStore studentStore, AuditService auditService) {
        this.studentStore = studentStore;
        this.auditService = auditService;
    }

    public void setArrears(Student student, BigDecimal amount) {
        StudentFeeLedger ledger = studentStore.getLedger(student.getId());
        ledger.setArrears(CurrencyConfig.money(amount));
    }

    public BigDecimal getArrears(Student student) {
        return studentStore.getLedger(student.getId()).getArrears();
    }

    /**
     * Move each active student's current outstanding balance into arrears
     * (e.g. at term close). Already-reversed/paid amounts are untouched.
     * Advance credits are preserved and netted against outstanding.
     */
    public void rolloverAll() {
        for (Student s : studentStore.getStudents()) {
            if (s.getStatus() != com.schaccs.enums.StudentStatus.ACTIVE) {
                continue;
            }
            StudentFeeLedger ledger = studentStore.getLedger(s.getId());
            BigDecimal totalOutstanding = ledger.getTotalCharged().subtract(ledger.getTotalPaid());
            BigDecimal advanceConsumed = totalOutstanding.min(ledger.getAdvance()).max(CurrencyConfig.zero());
            ledger.setAdvance(CurrencyConfig.money(ledger.getAdvance().subtract(advanceConsumed)));
            BigDecimal netOutstanding = totalOutstanding.subtract(advanceConsumed).max(CurrencyConfig.zero());
            if (netOutstanding.compareTo(CurrencyConfig.zero()) > 0) {
                ledger.setArrears(CurrencyConfig.money(ledger.getArrears().add(netOutstanding)));
            }
            ledger.clearCurrentCycle();
        }
        auditService.log("ARREARS_ROLLOVER", "System", "ALL",
                "{\"activeStudents\":" + studentStore.getStudents().stream()
                        .filter(s -> s.getStatus() == com.schaccs.enums.StudentStatus.ACTIVE).count() + "}");
    }

}
