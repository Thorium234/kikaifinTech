package com.schaccs.service;

import com.schaccs.config.AppConfig;
import com.schaccs.model.receipt.Receipt;
import com.schaccs.model.receipt.ReceiptLine;
import com.schaccs.service.export.PdfExportService;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PdfExportCoreTest {

    @Test
    void exportReceiptWorksWithBrandingFieldsConfigured() throws Exception {
        AppConfig.getInstance().getSchoolProfile().setLogoPath("/tmp/nonexistent-logo.png");
        AppConfig.getInstance().getSchoolProfile().setStampPath("/tmp/nonexistent-stamp.png");
        AppConfig.getInstance().getSchoolProfile().setSignaturePath("/tmp/nonexistent-signature.png");

        Receipt receipt = new Receipt();
        receipt.setReceiptNumber(12345);
        receipt.setDate(LocalDate.now());
        receipt.setStudentName("Test Student");
        receipt.setAdmissionNumber("ADM-1");
        receipt.setClassLabel("Form 2");
        receipt.setBankReference("BANK-REF");
        receipt.setReceivedBy("Tester");
        receipt.setAmount(new java.math.BigDecimal("1500.00"));
        ReceiptLine line = new ReceiptLine();
        line.setVoteheadCode("BOARD");
        line.setVoteheadName("Boarding");
        line.setAmount(new java.math.BigDecimal("1500.00"));
        receipt.addLine(line);

        Path file = Files.createTempFile("receipt-branding", ".pdf");
        new PdfExportService().exportReceipt(file, receipt);

        assertTrue(Files.exists(file));
        assertTrue(Files.size(file) > 0);
    }

    @Test
    void exportReceiptWorksWithoutBrandingFieldsConfigured() throws Exception {
        AppConfig.getInstance().getSchoolProfile().setLogoPath(null);
        AppConfig.getInstance().getSchoolProfile().setStampPath(null);
        AppConfig.getInstance().getSchoolProfile().setSignaturePath(null);

        Receipt receipt = new Receipt();
        receipt.setReceiptNumber(54321);
        receipt.setDate(LocalDate.now());
        receipt.setStudentName("Another Student");
        receipt.setAdmissionNumber("ADM-2");
        receipt.setClassLabel("Form 1");
        receipt.setReceivedBy("Tester");
        receipt.setAmount(new java.math.BigDecimal("800.00"));
        ReceiptLine line = new ReceiptLine();
        line.setVoteheadCode("ACT");
        line.setVoteheadName("Activity");
        line.setAmount(new java.math.BigDecimal("800.00"));
        receipt.addLine(line);

        Path file = Files.createTempFile("receipt-no-branding", ".pdf");
        new PdfExportService().exportReceipt(file, receipt);

        assertTrue(Files.exists(file));
        assertTrue(Files.size(file) > 0);
    }
}
