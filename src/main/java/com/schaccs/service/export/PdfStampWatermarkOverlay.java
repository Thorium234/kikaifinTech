package com.schaccs.service.export;

import com.schaccs.config.AppConfig;
import com.schaccs.config.SchoolProfile;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState;
import org.apache.pdfbox.util.Matrix;

import java.awt.Color;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class PdfStampWatermarkOverlay {

    private static final Color STAMP_COLOR = new Color(26, 35, 126);
    private static final float STAMP_OPACITY = 0.75f;
    private static final float STAMP_WIDTH = 220f;
    private static final float STAMP_HEIGHT = 100f;
    private static final float ROTATION_DEGREES = -35f;
    private static final float CORNER_RADIUS = 8f;

    private PdfStampWatermarkOverlay() {}

    public static boolean isEnabled() {
        return AppConfig.getInstance().getSchoolProfile().isPdfStampEnabled();
    }

    public static void drawReceiptStamp(PDDocument document, PDPage page, PDPageContentStream content,
                                         String receiptNumber, String studentName) throws IOException {
        if (!isEnabled()) return;
        drawStamp(document, page, content, "PAID - OFFICIAL RECEIPT", receiptNumber, studentName);
    }

    public static void drawVerificationStamp(PDDocument document, PDPage page, PDPageContentStream content,
                                              String transactionRef, String context) throws IOException {
        if (!isEnabled()) return;
        drawStamp(document, page, content, "APPROVED / VERIFIED", transactionRef, context);
    }

    private static String currentTermLabel = null;

    public static void setCurrentTermLabel(String label) {
        currentTermLabel = label;
    }

    private static void drawStamp(PDDocument document, PDPage page, PDPageContentStream content,
                                   String headerText, String ref, String context) throws IOException {
        SchoolProfile school = AppConfig.getInstance().getSchoolProfile();
        PDRectangle box = page.getMediaBox();
        float cx = box.getWidth() / 2f;
        float cy = box.getHeight() / 2f;

        PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
        PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

        String dateStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("d'%s' MMM yyyy HH:mm"));
        String daySuffix = daySuffix(LocalDateTime.now().getDayOfMonth());
        dateStr = dateStr.replace("%s", daySuffix);
        String approvedLine = "Approved: " + dateStr;
        String schoolLine = school.getSchoolName();
        String termLabel = currentTermLabel != null ? currentTermLabel : com.schaccs.enums.AcademicTerm.TERM_1.getDisplayName();
        String yearTerm = AppConfig.getInstance().getAcademicYear() + " - " + termLabel;
        String refLine = "Ref: " + ref;

        content.saveGraphicsState();

        PDExtendedGraphicsState gs = new PDExtendedGraphicsState();
        gs.setNonStrokingAlphaConstant(STAMP_OPACITY);
        gs.setStrokingAlphaConstant(STAMP_OPACITY);
        gs.setAlphaSourceFlag(true);
        content.setGraphicsStateParameters(gs);

        content.setNonStrokingColor(STAMP_COLOR);
        content.setStrokingColor(STAMP_COLOR);
        content.setLineWidth(1.5f);

        float hw = STAMP_WIDTH / 2f;
        float hh = STAMP_HEIGHT / 2f;

        double rad = Math.toRadians(ROTATION_DEGREES);
        float cos = (float) Math.cos(rad);
        float sin = (float) Math.sin(rad);
        float tx = cx - cx * cos + cy * sin;
        float ty = cy - cx * sin - cy * cos;
        content.saveGraphicsState();
        content.beginText();
        content.setTextMatrix(new Matrix(cos, sin, -sin, cos, tx, ty));
        content.setFont(bold, 4f);
        content.newLineAtOffset(0, 0);
        content.endText();
        content.restoreGraphicsState();

        // Outer rounded rectangle (double border)
        drawRoundedRect(content, cx - hw - 4, cy - hh - 4, STAMP_WIDTH + 8, STAMP_HEIGHT + 8, CORNER_RADIUS);
        content.stroke();

        // Inner rounded rectangle
        drawRoundedRect(content, cx - hw, cy - hh, STAMP_WIDTH, STAMP_HEIGHT, CORNER_RADIUS);
        content.stroke();

        content.setLineWidth(0.5f);
        float textY = cy + hh - 14f;
        content.beginText();
        content.setFont(bold, 7f);
        content.newLineAtOffset(cx - hw + 10, textY);
        content.showText(truncate(headerText, STAMP_WIDTH - 20, bold, 7f));
        content.endText();
        textY -= 11f;

        content.beginText();
        content.setFont(regular, 6f);
        content.newLineAtOffset(cx - hw + 10, textY);
        content.showText(truncate(approvedLine, STAMP_WIDTH - 20, regular, 6f));
        content.endText();
        textY -= 10f;

        content.beginText();
        content.setFont(regular, 5f);
        content.newLineAtOffset(cx - hw + 10, textY);
        content.showText(truncate(schoolLine, STAMP_WIDTH - 20, regular, 5f));
        content.endText();
        textY -= 9f;

        content.beginText();
        content.setFont(regular, 5f);
        content.newLineAtOffset(cx - hw + 10, textY);
        content.showText(truncate(yearTerm, STAMP_WIDTH - 20, regular, 5f));
        content.endText();
        textY -= 9f;

        content.beginText();
        content.setFont(regular, 5f);
        content.newLineAtOffset(cx - hw + 10, textY);
        content.showText(truncate(refLine, STAMP_WIDTH - 20, regular, 5f));
        content.endText();

        content.restoreGraphicsState();
        content.setNonStrokingColor(Color.BLACK);
        content.setStrokingColor(Color.BLACK);
        content.setLineWidth(1f);
    }

    private static void drawRoundedRect(PDPageContentStream content,
                                         float x, float y, float w, float h,
                                         float r) throws IOException {
        r = Math.min(r, Math.min(w / 2f, h / 2f));
        content.moveTo(x + r, y);
        content.lineTo(x + w - r, y);
        content.curveTo(x + w, y, x + w, y + r, x + w, y + r);
        content.lineTo(x + w, y + h - r);
        content.curveTo(x + w, y + h, x + w - r, y + h, x + w - r, y + h);
        content.lineTo(x + r, y + h);
        content.curveTo(x, y + h, x, y + h - r, x, y + h - r);
        content.lineTo(x, y + r);
        content.curveTo(x, y, x + r, y, x + r, y);
        content.closePath();
    }

    private static String truncate(String text, float width, PDType1Font font, float fontSize) throws IOException {
        if (text == null) return "";
        while (!text.isEmpty() && font.getStringWidth(text) / 1000 * fontSize > width) {
            text = text.substring(0, text.length() - 1);
        }
        return text;
    }

    private static String daySuffix(int day) {
        if (day >= 11 && day <= 13) return "th";
        return switch (day % 10) {
            case 1 -> "st";
            case 2 -> "nd";
            case 3 -> "rd";
            default -> "th";
        };
    }
}
