package com.schaccs.service.finance;

import com.schaccs.accounting.DoubleEntryEngine;
import com.schaccs.config.CurrencyConfig;
import com.schaccs.enums.AccountType;
import com.schaccs.enums.StudentStatus;
import com.schaccs.enums.TransactionType;
import com.schaccs.model.finance.JournalEntry;
import com.schaccs.model.student.Student;
import com.schaccs.model.student.StudentFeeLedger;
import com.schaccs.repository.PersistenceService;
import com.schaccs.service.audit.AuditService;
import com.schaccs.store.FeeStructureStore;
import com.schaccs.store.StudentStore;
import com.schaccs.util.NumberGenerator;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Writes off an uncollectible student's outstanding fees (dropout / transfer /
 * confirmed bad debt) without deleting any historical registry or ledger data.
 * A balanced double-entry journal is posted per votehead — Debit Bad Debts
 * Expense, Credit Accounts Receivable — and the student's live ledger balance
 * is cleared. The journal and student record remain fully intact for audit.
 */
public class BadDebtWriteOffService {

    private final DoubleEntryEngine engine;
    private final StudentStore studentStore;
    private final FeeStructureStore feeStore;
    private final AuditService auditService;

    public BadDebtWriteOffService() {
        this(new DoubleEntryEngine(), StudentStore.getInstance(),
                FeeStructureStore.getInstance(), new AuditService());
    }

    public BadDebtWriteOffService(DoubleEntryEngine engine, StudentStore studentStore,
                                  FeeStructureStore feeStore, AuditService auditService) {
        this.engine = engine;
        this.studentStore = studentStore;
        this.feeStore = feeStore;
        this.auditService = auditService;
    }

    /** Per-votehead outstanding balance that would be written off for a student. */
    public Map<String, BigDecimal> outstandingByVotehead(String studentId) {
        Optional<Student> student = studentStore.findById(studentId);
        Map<String, BigDecimal> result = new LinkedHashMap<>();
        if (student.isEmpty()) {
            return result;
        }
        StudentFeeLedger ledger = studentStore.getLedger(studentId);
        for (Map.Entry<String, BigDecimal> e : ledger.getChargedByVotehead().entrySet()) {
            BigDecimal outstanding = ledger.getOutstanding(e.getKey());
            if (outstanding.compareTo(BigDecimal.ZERO) > 0) {
                result.put(e.getKey(), outstanding);
            }
        }
        return result;
    }

    /**
     * Writes off the student's outstanding AR and clears their ledger balance.
     * Returns the total amount written off. The student's student record and the
     * full journal history are preserved (nothing is deleted).
     */
    public WriteOffResult writeOff(Student student, String reason, String createdBy, LocalDate date) {
        if (student == null) {
            return WriteOffResult.failure(List.of("A student must be selected for write-off."));
        }
        if (reason == null || reason.isBlank()) {
            return WriteOffResult.failure(List.of("A reason is required (e.g. dropout, transfer)."));
        }
        if (createdBy == null || createdBy.isBlank()) {
            createdBy = "system";
        }
        if (date == null) {
            date = LocalDate.now();
        }

        String studentId = student.getId();
        Map<String, BigDecimal> outstanding = outstandingByVotehead(studentId);
        List<WriteOffLine> lines = new ArrayList<>();
        BigDecimal total = CurrencyConfig.zero();
        for (Map.Entry<String, BigDecimal> e : outstanding.entrySet()) {
            BigDecimal amount = e.getValue();
            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            total = total.add(amount);
            lines.add(new WriteOffLine(e.getKey(), feeStore.voteheadName(e.getKey()), amount));
        }

        if (total.compareTo(BigDecimal.ZERO) <= 0) {
            return WriteOffResult.failure(List.of(
                    student.getName() + " has no outstanding balance to write off."));
        }

        long woNumber = NumberGenerator.nextWriteOffNumber();
        String reference = "WO-" + woNumber + "-" + safe(student.getAdmissionNumber());
        String narration = "Bad-debt write-off: " + reason;

        for (WriteOffLine line : lines) {
            JournalEntry journal = new JournalEntry();
            journal.setDate(date);
            journal.setReference(reference);
            journal.setNarration(narration);
            journal.addLine(AccountType.BAD_DEBTS_EXPENSE, line.code(),
                    line.amount(), CurrencyConfig.zero(),
                    "Bad debt — " + line.name());
            journal.addLine(AccountType.ACCOUNTS_RECEIVABLE, line.code(),
                    CurrencyConfig.zero(), line.amount(),
                    "AR write-off — " + student.getName() + " (" + reason + ")");
            engine.postJournal(journal, createdBy, studentId, null, reference,
                    TransactionType.WRITE_OFF);
        }

        // Clear the live ledger charges so the student no longer shows as owing.
        StudentFeeLedger ledger = studentStore.getLedger(studentId);
        for (WriteOffLine line : lines) {
            ledger.reduceCharge(line.code(), line.amount());
        }

        if (reason.toLowerCase().contains("drop") || reason.toLowerCase().contains("transfer")) {
            student.setStatus(StudentStatus.DROPPED);
        }
        PersistenceService.getInstance().saveAll();

        auditService.log("BAD_DEBT_WRITE_OFF", "Student", studentId,
                "{\"admissionNumber\":\"" + safe(student.getAdmissionNumber())
                        + "\",\"reference\":\"" + reference
                        + "\",\"reason\":\"" + safe(reason)
                        + "\",\"total\":" + total + ",\"voteheads\":["
                        + lines.stream().map(l -> "{\"code\":\"" + safe(l.code())
                        + "\",\"amount\":" + l.amount() + "}")
                        .collect(java.util.stream.Collectors.joining(",")) + "]}");

        return WriteOffResult.success(reference, total, lines, student.getStatus());
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    public record WriteOffLine(String code, String name, BigDecimal amount) {
    }

    public record WriteOffResult(boolean success, String reference, BigDecimal total,
                                 List<WriteOffLine> lines, StudentStatus studentStatus,
                                 List<String> errors) {

        public static WriteOffResult success(String reference, BigDecimal total,
                                             List<WriteOffLine> lines, StudentStatus status) {
            return new WriteOffResult(true, reference, total, lines, status, List.of());
        }

        public static WriteOffResult failure(List<String> errors) {
            return new WriteOffResult(false, null, CurrencyConfig.zero(), List.of(), null, errors);
        }
    }
}
