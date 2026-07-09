package com.schaccs.model.student;

import com.schaccs.config.CurrencyConfig;
import com.schaccs.enums.AcademicTerm;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Per-student votehead ledger: charged vs paid amounts by votehead code.
 */
public class StudentFeeLedger {

    private final String studentId;
    private final Map<String, BigDecimal> chargedByVotehead = new LinkedHashMap<>();
    private final Map<String, BigDecimal> paidByVotehead = new LinkedHashMap<>();
    private BigDecimal arrears = CurrencyConfig.zero();
    private AcademicTerm currentTerm = AcademicTerm.TERM_1;

    public StudentFeeLedger(String studentId) {
        this.studentId = studentId;
    }

    public String getStudentId() {
        return studentId;
    }

    public AcademicTerm getCurrentTerm() {
        return currentTerm;
    }

    public void setCurrentTerm(AcademicTerm currentTerm) {
        this.currentTerm = currentTerm;
    }

    public BigDecimal getArrears() {
        return arrears;
    }

    public void setArrears(BigDecimal arrears) {
        this.arrears = CurrencyConfig.money(arrears);
    }

    public void charge(String voteheadCode, BigDecimal amount) {
        chargedByVotehead.merge(voteheadCode, CurrencyConfig.money(amount), BigDecimal::add);
    }

    public void pay(String voteheadCode, BigDecimal amount) {
        paidByVotehead.merge(voteheadCode, CurrencyConfig.money(amount), BigDecimal::add);
    }

    public BigDecimal getCharged(String voteheadCode) {
        return chargedByVotehead.getOrDefault(voteheadCode, CurrencyConfig.zero());
    }

    public BigDecimal getPaid(String voteheadCode) {
        return paidByVotehead.getOrDefault(voteheadCode, CurrencyConfig.zero());
    }

    public BigDecimal getOutstanding(String voteheadCode) {
        BigDecimal outstanding = getCharged(voteheadCode).subtract(getPaid(voteheadCode));
        return outstanding.max(CurrencyConfig.zero());
    }

    public Map<String, BigDecimal> getOutstandingByVotehead() {
        Map<String, BigDecimal> result = new LinkedHashMap<>();
        for (String code : chargedByVotehead.keySet()) {
            BigDecimal out = getOutstanding(code);
            if (out.compareTo(BigDecimal.ZERO) > 0) {
                result.put(code, out);
            }
        }
        // include paid-only keys with zero charge if needed — skip
        return result;
    }

    public BigDecimal getTotalCharged() {
        return chargedByVotehead.values().stream()
                .reduce(CurrencyConfig.zero(), BigDecimal::add);
    }

    public BigDecimal getTotalPaid() {
        return paidByVotehead.values().stream()
                .reduce(CurrencyConfig.zero(), BigDecimal::add);
    }

    public BigDecimal getBalance() {
        return CurrencyConfig.money(getTotalCharged().add(arrears).subtract(getTotalPaid()));
    }

    public Map<String, BigDecimal> getChargedByVotehead() {
        return chargedByVotehead;
    }

    public Map<String, BigDecimal> getPaidByVotehead() {
        return paidByVotehead;
    }
}
