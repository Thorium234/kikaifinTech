package com.schaccs.service.export;

import com.schaccs.model.report.AgeingBucket;
import com.schaccs.model.report.CollectionSummary;
import com.schaccs.model.report.TrialBalanceRow;
import com.schaccs.model.report.VoteheadSummary;
import com.schaccs.model.student.StudentBalance;
import com.schaccs.service.report.ReportService;
import com.schaccs.util.CurrencyUtil;
import com.schaccs.util.DateUtil;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class ReportPackExportService {

    private final SpreadsheetExportService exportService;
    private final ReportService reportService;

    public ReportPackExportService() {
        this(new SpreadsheetExportService(), new ReportService());
    }

    public ReportPackExportService(SpreadsheetExportService exportService, ReportService reportService) {
        this.exportService = exportService;
        this.reportService = reportService;
    }

    public void exportFullReportPack(Path path, LocalDate dailyDate) throws IOException {
        List<SpreadsheetExportService.SheetData> sheets = new ArrayList<>();
        sheets.add(new SpreadsheetExportService.SheetData("Fee Balances",
                List.of("Admission Number", "Name", "Class", "Charged", "Paid", "Arrears", "Balance"),
                reportService.feeBalances().stream().map(this::studentBalanceRow).toList()));
        sheets.add(new SpreadsheetExportService.SheetData("Defaulters",
                List.of("Admission Number", "Name", "Class", "Charged", "Paid", "Arrears", "Balance"),
                reportService.defaulters(null).stream().map(this::studentBalanceRow).toList()));
        sheets.add(new SpreadsheetExportService.SheetData("Daily Collection",
                List.of("Date", "Payment Mode", "Receipts", "Total Amount"),
                reportService.dailyCollection(dailyDate).stream().map(this::dailyRow).toList()));
        sheets.add(new SpreadsheetExportService.SheetData("Votehead Summary",
                List.of("Code", "Vote Head", "Charged", "Collected", "Outstanding"),
                reportService.voteheadSummaries().stream().map(this::voteheadRow).toList()));
        sheets.add(new SpreadsheetExportService.SheetData("Ageing",
                List.of("Ageing Bucket", "Outstanding", "Students"),
                reportService.ageing().stream().map(this::ageingRow).toList()));
        sheets.add(new SpreadsheetExportService.SheetData("Trial Balance",
                List.of("Account", "Debit", "Credit"),
                reportService.trialBalance().stream().map(this::trialRow).toList()));
        exportService.exportWorkbook(path, sheets);
    }

    private List<String> studentBalanceRow(StudentBalance b) {
        return List.of(
                safe(b.getAdmissionNumber()),
                safe(b.getStudentName()),
                safe(b.getClassLabel()),
                CurrencyUtil.formatPlain(b.getTotalCharged()),
                CurrencyUtil.formatPlain(b.getTotalPaid()),
                CurrencyUtil.formatPlain(b.getArrears()),
                CurrencyUtil.formatPlain(b.getBalance())
        );
    }

    private List<String> dailyRow(CollectionSummary d) {
        return List.of(
                DateUtil.format(d.getDate()),
                d.getPaymentMode().getDisplayName(),
                String.valueOf(d.getReceiptCount()),
                CurrencyUtil.formatPlain(d.getTotalAmount())
        );
    }

    private List<String> voteheadRow(VoteheadSummary v) {
        return List.of(
                safe(v.getVoteheadCode()),
                safe(v.getVoteheadName()),
                CurrencyUtil.formatPlain(v.getCharged()),
                CurrencyUtil.formatPlain(v.getCollected()),
                CurrencyUtil.formatPlain(v.getOutstanding())
        );
    }

    private List<String> ageingRow(AgeingBucket a) {
        return List.of(
                safe(a.getLabel()),
                CurrencyUtil.formatPlain(a.getAmount()),
                String.valueOf(a.getStudents())
        );
    }

    private List<String> trialRow(TrialBalanceRow t) {
        return List.of(
                safe(t.getAccountName()),
                CurrencyUtil.formatPlain(t.getDebit()),
                CurrencyUtil.formatPlain(t.getCredit())
        );
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
