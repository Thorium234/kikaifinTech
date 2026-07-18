package com.schaccs.util;

import com.schaccs.config.AppConfig;
import com.schaccs.config.CurrencyConfig;
import com.schaccs.model.receipt.Receipt;
import com.schaccs.model.student.Student;
import com.schaccs.model.student.StudentBalance;
import com.schaccs.model.student.StudentFeeLedger;
import com.schaccs.service.Services;
import com.schaccs.service.report.ReportService;
import com.schaccs.store.ReceiptStore;
import com.schaccs.store.StudentStore;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class DisasterRecoveryEngine {

    private static final DateTimeFormatter DIR_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final int RECEIPT_TRIGGER = 20;
    private static final int RETENTION_DAYS = 30;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private int receiptCountSinceLastBackup = 0;
    private final ReportService reportService;

    public DisasterRecoveryEngine() {
        this.reportService = Services.getInstance().report();
    }

    public void start() {
        Runtime.getRuntime().addShutdownHook(new Thread(this::backupNow));
        scheduler.scheduleAtFixedRate(this::backupNow, 1, 1, TimeUnit.HOURS);
    }

    public void onReceiptPosted() {
        receiptCountSinceLastBackup++;
        if (receiptCountSinceLastBackup >= RECEIPT_TRIGGER) {
            backupNow();
            receiptCountSinceLastBackup = 0;
        }
    }

    public void backupNow() {
        try {
            Path backupDir = getBackupDir();
            Files.createDirectories(backupDir);
            Path file = backupDir.resolve("schaccs-backup-" + LocalDate.now() + ".xlsx");
            try (XSSFWorkbook workbook = new XSSFWorkbook()) {
                writeStudents(workbook);
                writeLedgerTransactions(workbook);
                writeFeeBalances(workbook);
                writeDailyCollections(workbook);
                try (var out = Files.newOutputStream(file)) {
                    workbook.write(out);
                }
            }
            enforceRetention(backupDir);
        } catch (Exception ignored) {
        }
    }

    public void onAppShutdown() {
        backupNow();
        scheduler.shutdown();
    }

    private Path getBackupDir() {
        return Path.of(System.getProperty("user.home"), "Documents", "SCHACCS_Backups",
                LocalDate.now().format(DIR_FMT));
    }

    private void writeStudents(XSSFWorkbook workbook) {
        Sheet sheet = workbook.createSheet("Students");
        Row header = sheet.createRow(0);
        header.createCell(0).setCellValue("Admission Number");
        header.createCell(1).setCellValue("Name");
        header.createCell(2).setCellValue("Class");
        header.createCell(3).setCellValue("Status");
        List<Student> students = StudentStore.getInstance().getStudents();
        for (int i = 0; i < students.size(); i++) {
            Student s = students.get(i);
            Row row = sheet.createRow(i + 1);
            row.createCell(0).setCellValue(safe(s.getAdmissionNumber()));
            row.createCell(1).setCellValue(safe(s.getName()));
            row.createCell(2).setCellValue(safe(s.getClassLabel()));
            row.createCell(3).setCellValue(s.getStatus() != null ? s.getStatus().name() : "");
        }
        autoSize(sheet, 4);
    }

    private void writeLedgerTransactions(XSSFWorkbook workbook) {
        Sheet sheet = workbook.createSheet("Ledger Transactions");
        Row header = sheet.createRow(0);
        header.createCell(0).setCellValue("Date");
        header.createCell(1).setCellValue("Reference");
        header.createCell(2).setCellValue("Description");
        header.createCell(3).setCellValue("Debit");
        header.createCell(4).setCellValue("Credit");
        header.createCell(5).setCellValue("Account");
        var txs = com.schaccs.store.LedgerStore.getInstance().getTransactions();
        for (int i = 0; i < txs.size(); i++) {
            var tx = txs.get(i);
            Row row = sheet.createRow(i + 1);
            row.createCell(0).setCellValue(DateUtil.format(tx.getDate()));
            row.createCell(1).setCellValue(safe(tx.getReference()));
            row.createCell(2).setCellValue(safe(tx.getDescription()));
            row.createCell(3).setCellValue(CurrencyConfig.formatPlain(tx.getDebit()));
            row.createCell(4).setCellValue(CurrencyConfig.formatPlain(tx.getCredit()));
            row.createCell(5).setCellValue(tx.getAccountType() != null ? tx.getAccountType().getDisplayName() : "");
        }
        autoSize(sheet, 6);
    }

    private void writeFeeBalances(XSSFWorkbook workbook) {
        Sheet sheet = workbook.createSheet("Fee Balances");
        Row header = sheet.createRow(0);
        header.createCell(0).setCellValue("Admission Number");
        header.createCell(1).setCellValue("Name");
        header.createCell(2).setCellValue("Class");
        header.createCell(3).setCellValue("Charged");
        header.createCell(4).setCellValue("Paid");
        header.createCell(5).setCellValue("Balance");
        List<StudentBalance> balances = reportService.feeBalances();
        for (int i = 0; i < balances.size(); i++) {
            StudentBalance b = balances.get(i);
            Row row = sheet.createRow(i + 1);
            row.createCell(0).setCellValue(safe(b.getAdmissionNumber()));
            row.createCell(1).setCellValue(safe(b.getStudentName()));
            row.createCell(2).setCellValue(safe(b.getClassLabel()));
            row.createCell(3).setCellValue(CurrencyConfig.formatPlain(b.getTotalCharged()));
            row.createCell(4).setCellValue(CurrencyConfig.formatPlain(b.getTotalPaid()));
            row.createCell(5).setCellValue(CurrencyConfig.formatPlain(b.getBalance()));
        }
        autoSize(sheet, 6);
    }

    private void writeDailyCollections(XSSFWorkbook workbook) {
        Sheet sheet = workbook.createSheet("Daily Collections");
        Row header = sheet.createRow(0);
        header.createCell(0).setCellValue("Date");
        header.createCell(1).setCellValue("Receipt #");
        header.createCell(2).setCellValue("Student");
        header.createCell(3).setCellValue("Amount");
        header.createCell(4).setCellValue("Mode");
        List<Receipt> receipts = ReceiptStore.getInstance().getReceipts();
        for (int i = 0; i < receipts.size(); i++) {
            Receipt r = receipts.get(i);
            Row row = sheet.createRow(i + 1);
            row.createCell(0).setCellValue(DateUtil.format(r.getDate()));
            row.createCell(1).setCellValue(r.getReceiptNumberDisplay());
            row.createCell(2).setCellValue(safe(r.getStudentName()));
            row.createCell(3).setCellValue(CurrencyConfig.formatPlain(r.getAmount()));
            row.createCell(4).setCellValue(r.getPaymentMode() != null ? r.getPaymentMode().getDisplayName() : "");
        }
        autoSize(sheet, 5);
    }

    private void enforceRetention(Path backupDir) {
        try {
            Path parent = backupDir.getParent();
            if (parent == null || !Files.exists(parent)) return;
            LocalDate cutoff = LocalDate.now().minusDays(RETENTION_DAYS);
            try (var dirs = Files.list(parent)) {
                dirs.filter(Files::isDirectory)
                        .filter(d -> d.getFileName().toString().matches("\\d{4}-\\d{2}-\\d{2}"))
                        .filter(d -> {
                            try {
                                return LocalDate.parse(d.getFileName().toString(), DIR_FMT).isBefore(cutoff);
                            } catch (Exception e) {
                                return false;
                            }
                        })
                        .forEach(d -> {
                            try (var files = Files.list(d)) {
                                files.forEach(f -> {
                                    try { Files.deleteIfExists(f); } catch (Exception ignored) {}
                                });
                                Files.deleteIfExists(d);
                            } catch (Exception ignored) {}
                        });
            }
        } catch (Exception ignored) {}
    }

    private void autoSize(Sheet sheet, int cols) {
        for (int i = 0; i < cols; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private String safe(String v) { return v == null ? "" : v; }
}
