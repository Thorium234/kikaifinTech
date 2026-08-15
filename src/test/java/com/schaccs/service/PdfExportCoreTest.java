package com.schaccs.service;

import com.schaccs.config.AppConfig;
import com.schaccs.model.receipt.Receipt;
import com.schaccs.model.receipt.ReceiptLine;
import com.schaccs.service.export.PdfExportService;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PdfExportCoreTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("export receipt embeds uploaded logo, stamp and signature images")
    void exportReceiptEmbedsUploadedBrandingImages() throws Exception {
        File logo = writePng(tempDir.resolve("logo.png"));
        File stamp = writePng(tempDir.resolve("stamp.png"));
        File signature = writePng(tempDir.resolve("signature.png"));
        AppConfig.getInstance().getSchoolProfile().setLogoPath(logo.getAbsolutePath());
        AppConfig.getInstance().getSchoolProfile().setStampPath(stamp.getAbsolutePath());
        AppConfig.getInstance().getSchoolProfile().setSignaturePath(signature.getAbsolutePath());

        Receipt receipt = new Receipt();
        receipt.setReceiptNumber(77777);
        receipt.setDate(LocalDate.now());
        receipt.setStudentName("Image Student");
        receipt.setAdmissionNumber("ADM-IMG");
        receipt.setClassLabel("Form 3");
        receipt.setBankReference("BANK-IMG");
        receipt.setReceivedBy("Tester");
        receipt.setAmount(new java.math.BigDecimal("2500.00"));
        ReceiptLine line = new ReceiptLine();
        line.setVoteheadCode("BOARD");
        line.setVoteheadName("Boarding");
        line.setAmount(new java.math.BigDecimal("2500.00"));
        receipt.addLine(line);

        Path file = tempDir.resolve("receipt-images.pdf");
        new PdfExportService().exportReceipt(file, receipt);

        assertTrue(Files.exists(file));
        assertTrue(Files.size(file) > 0);
        try (PDDocument document = Loader.loadPDF(file.toFile())) {
            assertTrue(document.getNumberOfPages() > 0);
            boolean hasImage = false;
            for (PDPage page : document.getPages()) {
                if (page.getResources() != null) {
                    for (org.apache.pdfbox.cos.COSName name : page.getResources().getXObjectNames()) {
                        if (page.getResources().getXObject(name) instanceof PDImageXObject) {
                            hasImage = true;
                        }
                    }
                }
            }
            assertTrue(hasImage, "Expected at least one embedded image in the exported receipt PDF");
        }
    }

    private File writePng(Path path) throws Exception {
        BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_RGB);
        ImageIO.write(image, "png", path.toFile());
        return path.toFile();
    }

    @Test
    @DisplayName("export receipt works with branding fields configured")
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

        Path file = tempDir.resolve("receipt-branding.pdf");
        new PdfExportService().exportReceipt(file, receipt);

        assertTrue(Files.exists(file));
        assertTrue(Files.size(file) > 0);
    }

    @Test
    @DisplayName("export receipt works without branding fields configured")
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

        Path file = tempDir.resolve("receipt-no-branding.pdf");
        new PdfExportService().exportReceipt(file, receipt);

        assertTrue(Files.exists(file));
        assertTrue(Files.size(file) > 0);
    }
}
