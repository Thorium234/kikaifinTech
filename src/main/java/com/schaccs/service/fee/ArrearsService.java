package com.schaccs.service.fee;

import com.schaccs.config.CurrencyConfig;
import com.schaccs.model.student.Student;
import com.schaccs.model.student.StudentFeeLedger;
import com.schaccs.store.StudentStore;

import java.math.BigDecimal;

public class ArrearsService {

    private final StudentStore studentStore;

    public ArrearsService() {
        this(StudentStore.getInstance());
    }

    public ArrearsService(StudentStore studentStore) {
        this.studentStore = studentStore;
    }

    public void setArrears(Student student, BigDecimal amount) {
        StudentFeeLedger ledger = studentStore.getLedger(student.getId());
        ledger.setArrears(CurrencyConfig.money(amount));
    }

    public BigDecimal getArrears(Student student) {
        return studentStore.getLedger(student.getId()).getArrears();
    }
}
