package com.schaccs.service.export;

import com.schaccs.config.AppConfig;
import com.schaccs.config.SchoolProfile;
import com.schaccs.model.receipt.Receipt;
import com.schaccs.model.receipt.ReceiptLine;
import com.schaccs.model.student.Student;
import com.schaccs.util.CurrencyUtil;
import com.schaccs.util.DateUtil;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

import com.schaccs.model.report.IncomeExpenditureRow;
import com.schaccs.model.report.TrialBalanceRow;
import com.schaccs.model.student.StudentBalance;
import com.schaccs.service.report.ReportService;
import com.schaccs.store.StudentStore;

import java.awt.Color;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class PdfExportService {

    private static final float MARGIN = 40f;
    private static final float TITLE_SIZE = 14f;
    private static final float HEADER_SIZE = 10f;
    private static final float BODY_SIZE = 9f;
    private static final float SMALL_SIZE = 8f;
    private static final float ROW_HEIGHT = 18f;
    private static final Color BRAND = new Color(26, 71, 42);
    private static final Color BRAND_LIGHT = new Color(225, 239, 229);
    private static final Color TOTAL_FILL = new Color(247, 231, 206);
    private static final Color BORDER = new Color(130, 130, 130);

    public void exportText(Path path, String title, List<String> lines) throws IOException {
        exportTable(path, title, List.of("Content"), lines.stream().map(line -> List.of(line)).toList());
    }

    public void exportTable(Path path, String title, List<String> headers, List<List<String>> rows) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            PDPage page = new PDPage(new PDRectangle(PDRectangle.A4.getHeight(), PDRectangle.A4.getWidth()));
            document.addPage(page);
            PDPageContentStream content = new PDPageContentStream(document, page);
            PDRectangle box = page.getMediaBox();
            float usableWidth = box.getWidth() - (2 * MARGIN);
            float y = box.getHeight() - MARGIN;

            y = drawTitle(content, bold, title, y);
            float[] colWidths = evenWidths(headers.size(), usableWidth);
            y = drawHeader(content, bold, headers, colWidths, y);
            for (List<String> row : rows) {
                if (y - ROW_HEIGHT < MARGIN) {
                    content.close();
                    page = new PDPage(new PDRectangle(PDRectangle.A4.getHeight(), PDRectangle.A4.getWidth()));
                    document.addPage(page);
                    content = new PDPageContentStream(document, page);
                    box = page.getMediaBox();
                    usableWidth = box.getWidth() - (2 * MARGIN);
                    colWidths = evenWidths(headers.size(), usableWidth);
                    y = box.getHeight() - MARGIN;
                    y = drawTitle(content, bold, title, y);
                    y = drawHeader(content, bold, headers, colWidths, y);
                }
                y = drawRow(content, regular, row, colWidths, y);
            }
            content.close();
            document.save(path.toFile());
        }
    }

    public void exportReceipt(Path path, Receipt receipt) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            PDRectangle box = page.getMediaBox();
            float width = box.getWidth() - (2 * MARGIN);
            float y = box.getHeight() - MARGIN;

            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                SchoolProfile school = AppConfig.getInstance().getSchoolProfile();
                y = drawLogo(document, content, school, box, y);
                y = drawCentered(content, bold, safe(school.getMinistry()), 11f, box.getWidth() / 2, y, BRAND);
                y = drawCentered(content, bold, safe(school.getSchoolName()), 14f, box.getWidth() / 2, y - 2, BRAND);
                y = drawCentered(content, regular, safe(school.getLocation()), 9f, box.getWidth() / 2, y - 2, Color.DARK_GRAY);
                y = drawReceiptBanner(content, box, y - 8, "OFFICIAL FEE RECEIPT");

                y -= 12f;
                content.setStrokingColor(BORDER);
                content.addRect(MARGIN, y - 54f, width, 54f);
                content.stroke();
                drawLabelValue(content, bold, regular, "Receipt No", receipt.getReceiptNumberDisplay(), MARGIN + 8, y - 16);
                drawLabelValue(content, bold, regular, "Date", DateUtil.format(receipt.getDate()), MARGIN + width / 2, y - 16);
                drawLabelValue(content, bold, regular, "Student", safe(receipt.getStudentName()), MARGIN + 8, y - 32);
                drawLabelValue(content, bold, regular, "Adm No", safe(receipt.getAdmissionNumber()), MARGIN + width / 2, y - 32);
                drawLabelValue(content, bold, regular, "Class", safe(receipt.getClassLabel()), MARGIN + 8, y - 48);
                drawLabelValue(content, bold, regular, "Mode", receipt.getPaymentMode() != null ? receipt.getPaymentMode().getDisplayName() : "", MARGIN + width / 2, y - 48);
                y -= 72f;

                if (receipt.getBankReference() != null && !receipt.getBankReference().isBlank()) {
                    drawLabelValue(content, bold, regular, "Reference", receipt.getBankReference(), MARGIN, y);
                    y -= 18f;
                }

                float[] colWidths = new float[]{width * 0.68f, width * 0.32f};
                y = drawHeader(content, bold, List.of("Vote Head", "Amount (KSh)"), colWidths, y);
                for (ReceiptLine line : receipt.getLines()) {
                    y = drawRow(content, regular, List.of(safe(line.getVoteheadName()), CurrencyUtil.formatPlain(line.getAmount())), colWidths, y);
                }

                y -= 8f;
                content.setNonStrokingColor(TOTAL_FILL);
                content.addRect(MARGIN + width * 0.45f, y - 34f, width * 0.55f, 34f);
                content.fill();
                content.setStrokingColor(BORDER);
                content.addRect(MARGIN + width * 0.45f, y - 34f, width * 0.55f, 34f);
                content.stroke();
                drawLabelValue(content, bold, regular, "TOTAL PAID", CurrencyUtil.formatPlain(receipt.getAmount()), MARGIN + width * 0.47f, y - 14);
                drawLabelValue(content, bold, regular, "Status", receipt.isReversed() ? "REVERSED" : "POSTED", MARGIN + width * 0.47f, y - 28);
                y -= 48f;

                drawParagraph(content, bold, regular, "Amount in words", CurrencyUtil.toWords(receipt.getAmount()), y, width);
                y -= 34f;
                drawParagraph(content, bold, regular, "Received by", safe(receipt.getReceivedBy()), y, width);
                y -= 30f;
                y = drawApprovalImages(document, content, school, y, width);
                drawParagraph(content, bold, regular, "Principal", safe(school.getPrincipal()), y, width);
                y -= 30f;
                drawParagraph(content, bold, regular, "Banking details", bankDetails(school), y, width);
                y -= 42f;
                drawParagraph(content, bold, regular, "Policy", safe(school.getCashPolicy()), y, width);
            }
            document.save(path.toFile());
        }
    }

    private float drawTitle(PDPageContentStream content, PDType1Font font, String title, float y) throws IOException {
        content.beginText();
        content.setNonStrokingColor(BRAND);
        content.setFont(font, TITLE_SIZE);
        content.newLineAtOffset(MARGIN, y);
        content.showText(sanitize(title == null ? "Export" : title));
        content.endText();
        content.setNonStrokingColor(Color.BLACK);
        return y - 24f;
    }

    private float drawHeader(PDPageContentStream content, PDType1Font font, List<String> headers, float[] widths, float y) throws IOException {
        float x = MARGIN;
        for (int i = 0; i < headers.size(); i++) {
            drawCell(content, font, HEADER_SIZE, sanitize(headers.get(i)), x, y, widths[i], ROW_HEIGHT, true);
            x += widths[i];
        }
        return y - ROW_HEIGHT;
    }

    private float drawRow(PDPageContentStream content, PDType1Font font, List<String> row, float[] widths, float y) throws IOException {
        float x = MARGIN;
        for (int i = 0; i < widths.length; i++) {
            String value = i < row.size() ? row.get(i) : "";
            drawCell(content, font, BODY_SIZE, sanitize(value), x, y, widths[i], ROW_HEIGHT, false);
            x += widths[i];
        }
        return y - ROW_HEIGHT;
    }

    private void drawCell(PDPageContentStream content, PDType1Font font, float fontSize, String text,
                          float x, float y, float width, float height, boolean header) throws IOException {
        if (header) {
            content.setNonStrokingColor(BRAND_LIGHT);
            content.addRect(x, y - height, width, height);
            content.fill();
        }
        content.setStrokingColor(BORDER);
        content.addRect(x, y - height, width, height);
        content.stroke();
        content.beginText();
        content.setNonStrokingColor(header ? BRAND : Color.BLACK);
        content.setFont(font, fontSize);
        content.newLineAtOffset(x + 4, y - 12);
        content.showText(truncate(text, width, font, fontSize));
        content.endText();
        content.setNonStrokingColor(Color.BLACK);
    }

    private float[] evenWidths(int count, float totalWidth) {
        float[] widths = new float[Math.max(count, 1)];
        float each = totalWidth / widths.length;
        for (int i = 0; i < widths.length; i++) {
            widths[i] = each;
        }
        return widths;
    }

    private String truncate(String text, float width, PDType1Font font, float fontSize) throws IOException {
        if (text == null) {
            return "";
        }
        String clean = text;
        while (!clean.isEmpty() && font.getStringWidth(clean) / 1000 * fontSize > width - 8) {
            clean = clean.substring(0, clean.length() - 1);
        }
        if (!clean.equals(text) && clean.length() > 3) {
            clean = clean.substring(0, clean.length() - 3) + "...";
        }
        return clean;
    }

    private float drawLogo(PDDocument document, PDPageContentStream content, SchoolProfile school, PDRectangle box, float y) throws IOException {
        String logoPath = school.getLogoPath();
        if (logoPath == null || logoPath.isBlank()) {
            return y;
        }
        Path path = Path.of(logoPath);
        if (!Files.exists(path)) {
            return y;
        }
        PDImageXObject image = PDImageXObject.createFromFileByContent(path.toFile(), document);
        float maxHeight = 52f;
        float scale = Math.min(1f, maxHeight / image.getHeight());
        float drawWidth = image.getWidth() * scale;
        float drawHeight = image.getHeight() * scale;
        float x = (box.getWidth() - drawWidth) / 2f;
        content.drawImage(image, x, y - drawHeight, drawWidth, drawHeight);
        return y - drawHeight - 8f;
    }

    private float drawReceiptBanner(PDPageContentStream content, PDRectangle box, float y, String text) throws IOException {
        float bannerWidth = box.getWidth() - (2 * MARGIN);
        content.setNonStrokingColor(BRAND);
        content.addRect(MARGIN, y - 18f, bannerWidth, 20f);
        content.fill();
        content.beginText();
        content.setNonStrokingColor(Color.WHITE);
        content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 11f);
        float textWidth = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD).getStringWidth(text) / 1000 * 11f;
        content.newLineAtOffset((box.getWidth() - textWidth) / 2f, y - 13f);
        content.showText(text);
        content.endText();
        content.setNonStrokingColor(Color.BLACK);
        return y - 24f;
    }

    private float drawCentered(PDPageContentStream content, PDType1Font font, String text, float fontSize, float centerX, float y, Color color) throws IOException {
        String value = sanitize(text);
        float textWidth = font.getStringWidth(value) / 1000 * fontSize;
        content.beginText();
        content.setNonStrokingColor(color);
        content.setFont(font, fontSize);
        content.newLineAtOffset(centerX - (textWidth / 2), y);
        content.showText(value);
        content.endText();
        content.setNonStrokingColor(Color.BLACK);
        return y - (fontSize + 4f);
    }

    private void drawLabelValue(PDPageContentStream content, PDType1Font bold, PDType1Font regular,
                                String label, String value, float x, float y) throws IOException {
        content.beginText();
        content.setFont(bold, BODY_SIZE);
        content.newLineAtOffset(x, y);
        content.showText(sanitize(label + ": "));
        content.endText();

        content.beginText();
        content.setFont(regular, BODY_SIZE);
        content.newLineAtOffset(x + 52, y);
        content.showText(sanitize(value));
        content.endText();
    }

    private void drawParagraph(PDPageContentStream content, PDType1Font bold, PDType1Font regular,
                               String label, String value, float y, float width) throws IOException {
        content.beginText();
        content.setNonStrokingColor(BRAND);
        content.setFont(bold, BODY_SIZE);
        content.newLineAtOffset(MARGIN, y);
        content.showText(sanitize(label));
        content.endText();

        content.setNonStrokingColor(BRAND_LIGHT);
        content.addRect(MARGIN, y - 20f, width, 18f);
        content.fill();
        content.setStrokingColor(BORDER);
        content.addRect(MARGIN, y - 20f, width, 18f);
        content.stroke();
        content.beginText();
        content.setNonStrokingColor(Color.BLACK);
        content.setFont(regular, SMALL_SIZE);
        content.newLineAtOffset(MARGIN + 4, y - 14f);
        content.showText(truncate(sanitize(value), width, regular, SMALL_SIZE));
        content.endText();
    }

    private float drawApprovalImages(PDDocument document, PDPageContentStream content, SchoolProfile school, float y, float width) throws IOException {
        float leftX = MARGIN;
        float rightX = MARGIN + width - 110f;
        float imageY = y - 52f;
        if (school.getStampPath() != null && !school.getStampPath().isBlank()) {
            drawOptionalImage(document, content, school.getStampPath(), leftX, imageY, 90f, 45f);
        }
        if (school.getSignaturePath() != null && !school.getSignaturePath().isBlank()) {
            drawOptionalImage(document, content, school.getSignaturePath(), rightX, imageY, 100f, 35f);
        }
        return y - 58f;
    }

    private void drawOptionalImage(PDDocument document, PDPageContentStream content, String pathValue,
                                   float x, float y, float maxWidth, float maxHeight) throws IOException {
        Path path = Path.of(pathValue);
        if (!Files.exists(path)) {
            return;
        }
        PDImageXObject image = PDImageXObject.createFromFileByContent(path.toFile(), document);
        float scale = Math.min(maxWidth / image.getWidth(), maxHeight / image.getHeight());
        float drawWidth = image.getWidth() * scale;
        float drawHeight = image.getHeight() * scale;
        content.drawImage(image, x, y, drawWidth, drawHeight);
    }

    private String bankDetails(SchoolProfile school) {
        return "Bank: " + safe(school.getBankName())
                + " | A/C: " + safe(school.getBankAccount())
                + " | PayBill: " + safe(school.getPayBill())
                + " | Account: " + safe(school.getPayBillAccount());
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String sanitize(String line) {
        return (line == null ? "" : line)
                .replace('\t', ' ')
                .replace('\r', ' ')
                .replace('\n', ' ');
    }

    public void exportDefaultersPdf(Path path, List<StudentBalance> defaulters) throws IOException {
        List<String> headers = List.of("#", "Admission", "Student Name", "Class", "Charged", "Paid", "Arrears", "Balance");
        List<List<String>> rows = new ArrayList<>();
        for (int i = 0; i < defaulters.size(); i++) {
            StudentBalance b = defaulters.get(i);
            rows.add(List.of(
                    String.valueOf(i + 1),
                    safe(b.getAdmissionNumber()),
                    safe(b.getStudentName()),
                    safe(b.getClassLabel()),
                    CurrencyUtil.formatPlain(b.getTotalCharged()),
                    CurrencyUtil.formatPlain(b.getTotalPaid()),
                    CurrencyUtil.formatPlain(b.getArrears()),
                    CurrencyUtil.formatPlain(b.getBalance())
            ));
        }
        exportTable(path, "Defaulters / Arrears Report", headers, rows);
    }

    public void exportTrialBalancePdf(Path path, List<TrialBalanceRow> rows) throws IOException {
        List<String> headers = List.of("Account", "Debit (KSh)", "Credit (KSh)");
        List<List<String>> data = new ArrayList<>();
        java.math.BigDecimal totalDebit = java.math.BigDecimal.ZERO;
        java.math.BigDecimal totalCredit = java.math.BigDecimal.ZERO;
        for (TrialBalanceRow row : rows) {
            data.add(List.of(row.getAccountName(), CurrencyUtil.formatPlain(row.getDebit()), CurrencyUtil.formatPlain(row.getCredit())));
            totalDebit = totalDebit.add(row.getDebit());
            totalCredit = totalCredit.add(row.getCredit());
        }
        data.add(List.of("TOTAL", CurrencyUtil.formatPlain(totalDebit), CurrencyUtil.formatPlain(totalCredit)));
        exportTable(path, "Trial Balance", headers, data);
    }

    public void exportIncomeExpenditurePdf(Path path, List<IncomeExpenditureRow> rows) throws IOException {
        List<String> headers = List.of("Category", "Item", "Amount (KSh)");
        List<List<String>> data = new ArrayList<>();
        java.math.BigDecimal totalIncome = java.math.BigDecimal.ZERO;
        java.math.BigDecimal totalExpense = java.math.BigDecimal.ZERO;
        for (IncomeExpenditureRow row : rows) {
            data.add(List.of(row.getCategory(), row.getItem(), CurrencyUtil.formatPlain(row.getAmount())));
            if ("Income".equals(row.getCategory())) {
                totalIncome = totalIncome.add(row.getAmount());
            } else {
                totalExpense = totalExpense.add(row.getAmount());
            }
        }
        data.add(List.of("", "TOTAL INCOME", CurrencyUtil.formatPlain(totalIncome)));
        data.add(List.of("", "TOTAL EXPENDITURE", CurrencyUtil.formatPlain(totalExpense)));
        data.add(List.of("", "NET SURPLUS / (DEFICIT)", CurrencyUtil.formatPlain(totalIncome.subtract(totalExpense))));
        exportTable(path, "Income & Expenditure Statement", headers, data);
    }

    public void exportFeeRemindersPdf(Path path, List<StudentBalance> defaulters, StudentStore studentStore, ReportService reportService) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            SchoolProfile school = AppConfig.getInstance().getSchoolProfile();

            for (StudentBalance def : defaulters) {
                PDPage page = new PDPage(PDRectangle.A4);
                document.addPage(page);
                float width = page.getMediaBox().getWidth() - (2 * MARGIN);

                try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                    float y = page.getMediaBox().getHeight() - MARGIN;

                    y = drawCentered(content, bold, safe(school.getSchoolName()), 14f, page.getMediaBox().getWidth() / 2, y, BRAND);
                    y = drawCentered(content, regular, safe(school.getLocation()), 9f, page.getMediaBox().getWidth() / 2, y - 2, Color.DARK_GRAY);
                    y -= 12f;
                    content.setStrokingColor(BORDER);
                    content.addRect(MARGIN, y - 2f, width, 2f);
                    content.stroke();
                    y -= 20f;

                    String parentName = "";
                    Student student = studentStore.findByAdmissionNumber(def.getAdmissionNumber()).orElse(null);
                    if (student != null && student.getParentName() != null) {
                        parentName = student.getParentName();
                    }
                    String salutation = parentName.isBlank() ? "Dear Parent/Guardian" : "Dear " + parentName;
                    y = drawCentered(content, bold, "FEE REMINDER", 13f, page.getMediaBox().getWidth() / 2, y, Color.RED);
                    y -= 20f;

                    content.beginText();
                    content.setFont(regular, 10f);
                    content.newLineAtOffset(MARGIN, y);
                    content.showText(salutation + ",");
                    content.endText();
                    y -= 18f;

                    String studentLine = "RE: Outstanding School Fees for " + safe(def.getStudentName()) + " (" + safe(def.getAdmissionNumber()) + ") - " + safe(def.getClassLabel());
                    content.beginText();
                    content.setFont(regular, 10f);
                    content.newLineAtOffset(MARGIN, y);
                    content.showText(sanitize(studentLine));
                    content.endText();
                    y -= 22f;

                    String body = "We kindly remind you that the following fee balance remains outstanding. "
                            + "We request you to clear the balance at your earliest convenience to ensure uninterrupted learning for your child.";
                    String[] words = body.split(" ");
                    StringBuilder line = new StringBuilder();
                    for (String word : words) {
                        String test = line + " " + word;
                        if (regular.getStringWidth(sanitize(test.trim())) / 1000 * 10f > width) {
                            content.beginText();
                            content.setFont(regular, 10f);
                            content.newLineAtOffset(MARGIN, y);
                            content.showText(sanitize(line.toString().trim()));
                            content.endText();
                            y -= 16f;
                            line = new StringBuilder(word);
                        } else {
                            if (line.length() > 0) line.append(" ");
                            line.append(word);
                        }
                    }
                    if (line.length() > 0) {
                        content.beginText();
                        content.setFont(regular, 10f);
                        content.newLineAtOffset(MARGIN, y);
                        content.showText(sanitize(line.toString().trim()));
                        content.endText();
                        y -= 18f;
                    }

                    y -= 8f;
                    float[] colWidths = new float[]{width * 0.5f, width * 0.25f, width * 0.25f};
                    y = drawHeader(content, bold, List.of("Description", "Amount (KSh)", "Notes"), colWidths, y);
                    y = drawRow(content, regular, List.of("Term Fee Charged", CurrencyUtil.formatPlain(def.getTotalCharged()), ""), colWidths, y);
                    y = drawRow(content, regular, List.of("Amount Paid", CurrencyUtil.formatPlain(def.getTotalPaid()), ""), colWidths, y);
                    y = drawRow(content, regular, List.of("Arrears B/F", CurrencyUtil.formatPlain(def.getArrears()), ""), colWidths, y);
                    content.setNonStrokingColor(TOTAL_FILL);
                    content.addRect(MARGIN + width * 0.5f, y - 18f, width * 0.5f, 18f);
                    content.fill();
                    y = drawRow(content, bold, List.of("BALANCE DUE", CurrencyUtil.formatPlain(def.getBalance()), "URGENT"), colWidths, y);

                    y -= 20f;
                    content.beginText();
                    content.setFont(regular, 10f);
                    content.newLineAtOffset(MARGIN, y);
                    content.showText("Payment can be made via:");
                    content.endText();
                    y -= 16f;
                    content.beginText();
                    content.setFont(regular, 10f);
                    content.newLineAtOffset(MARGIN, y);
                    content.showText(sanitize("  - Bank: " + safe(school.getBankName()) + " | A/C: " + safe(school.getBankAccount())));
                    content.endText();
                    y -= 14f;
                    content.beginText();
                    content.setFont(regular, 10f);
                    content.newLineAtOffset(MARGIN, y);
                    content.showText(sanitize("  - M-Pesa PayBill: " + safe(school.getPayBill()) + " | Account: " + safe(school.getPayBillAccount())));
                    content.endText();
                    y -= 24f;

                    content.beginText();
                    content.setFont(regular, 10f);
                    content.newLineAtOffset(MARGIN, y);
                    content.showText("Thank you for your cooperation.");
                    content.endText();
                    y -= 16f;
                    content.beginText();
                    content.setFont(regular, 10f);
                    content.newLineAtOffset(MARGIN, y);
                    content.showText("Yours faithfully,");
                    content.endText();
                    y -= 30f;
                    content.beginText();
                    content.setFont(bold, 10f);
                    content.newLineAtOffset(MARGIN, y);
                    content.showText(sanitize(safe(school.getPrincipal())));
                    content.endText();
                    y -= 14f;
                    content.beginText();
                    content.setFont(regular, 10f);
                    content.newLineAtOffset(MARGIN, y);
                    content.showText("Principal, " + safe(school.getSchoolName()));
                    content.endText();
                }
            }
            document.save(path.toFile());
        }
    }
}
