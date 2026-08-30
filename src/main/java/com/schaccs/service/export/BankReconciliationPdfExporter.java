package com.schaccs.service.export;

import com.schaccs.config.AppConfig;
import com.schaccs.config.CurrencyConfig;
import com.schaccs.config.SchoolProfile;
import com.schaccs.model.finance.BankReconciliation;
import com.schaccs.util.DateUtil;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

import java.awt.Color;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Produces the non-editable classic-layout Bank Reconciliation Statement PDF
 * (V8). The layout follows standard accounting convention:
 *
 * <pre>
 *   Balance as per Bank Statement          KSh X
 *   Add:  Receipts not yet in Bank         KSh X   (Deposits in Transit)
 *   Less: Cheques not yet presented       (KSh X)  (Unpresented Cheques)
 *   ----------------------------------------------
 *   Balance as per Cashbook                KSh X
 * </pre>
 *
 * It is derived only from the already-finalized reconciliation snapshot, so it
 * is immutable once the period is locked.
 */
public class BankReconciliationPdfExporter {

    private static final float MARGIN = 40f;
    private static final float ROW_HEIGHT = 18f;
    private static final Color BRAND = new Color(26, 71, 42);
    private static final Color BORDER = new Color(130, 130, 130);

    private final PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
    private final PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

    public void export(Path path, BankReconciliation rec, String bankName,
                       java.math.BigDecimal depositsInTransit,
                       java.math.BigDecimal unpresentedCheques) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            PDRectangle box = page.getMediaBox();
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                float width = box.getWidth() - (2 * MARGIN);
                SchoolProfile school = AppConfig.getInstance().getSchoolProfile();
                float y = box.getHeight() - MARGIN;

                y = drawSchoolHeader(content, school, width, y);
                y = drawCentered(content, bold, "Bank Reconciliation Statement", 13f, y);
                y = drawCentered(content, regular, "As Of Date: " + DateUtil.format(rec.getStatementDate())
                        + (bankName != null ? "  \u2014  " + bankName : ""), 9f, y);
                y -= 14f;

                y = drawRule(content, MARGIN, y, width);

                y = drawValueRow(content, "Balance as per Bank Statement", rec.getStatementBalance(), false, y);
                y = drawValueRow(content, "Add: Receipts in Cashbook not yet in Bank (Deposits in Transit)",
                        depositsInTransit, false, y);
                y = drawValueRow(content, "Less: Cheques issued not yet presented to Bank (Unpresented Cheques)",
                        unpresentedCheques.negate(), true, y);
                y = drawRule(content, MARGIN, y, width);
                y = drawValueRowBold(content, "Balance as per Cashbook", rec.getBookBalance(), y);
                y = drawRule(content, MARGIN, y, width);

                y -= 10f;
                y = drawCentered(content, bold, "Status: " + rec.getStatus(), 10f, y);
                if (rec.getReconciledAt() != null) {
                    y -= 16f;
                    y = drawCentered(content, regular, "Reconciled: " + rec.getReconciledAt(), 8f, y);
                }
                y -= 20f;

                y = drawUnclearedSchedule(content, rec, y, box);
            }
            document.save(path.toFile());
        }
    }

    private float drawUnclearedSchedule(PDPageContentStream content, BankReconciliation rec,
                                        float y, PDRectangle box) throws IOException {
        List<BankReconciliation.ReconciliationItem> uncleared = rec.getItems().stream()
                .filter(i -> !i.isCleared()).toList();
        if (uncleared.isEmpty()) return y;
        if (y < 120f) return y;

        float width = box.getWidth() - (2 * MARGIN);
        y = drawSection(content, "Uncleared Items Schedule", y);
        float[] widths = {90f, width - 90f};
        String[] headers = {"Reference", "Description"};
        y = drawTableHeader(content, headers, widths, y);
        for (BankReconciliation.ReconciliationItem item : uncleared) {
            y = drawTableRow(content, new String[]{nvl(item.getReference()), nvl(item.getDescription())}, widths, y);
        }
        return y;
    }

    // ---- drawing helpers (self-contained) ----

    private float drawSchoolHeader(PDPageContentStream content, SchoolProfile school, float width, float y) throws IOException {
        String name = school != null ? school.getSchoolName() : "Friends School Kikai Boys Secondary School";
        y = drawCentered(content, bold, nvl(name), 14f, y);
        y -= 16f;
        return y;
    }

    private float drawCentered(PDPageContentStream c, PDType1Font f, String text, float size, float y) throws IOException {
        c.beginText();
        c.setFont(f, size);
        c.setNonStrokingColor(BRAND);
        float textWidth = f.getStringWidth(text) / 1000f * size;
        c.newLineAtOffset((PDRectangle.A4.getWidth() - textWidth) / 2f, y);
        c.showText(text);
        c.endText();
        return y - 20f;
    }

    private float drawSection(PDPageContentStream c, String text, float y) throws IOException {
        c.setNonStrokingColor(BRAND);
        c.setStrokingColor(BRAND);
        c.beginText();
        c.setFont(bold, 10f);
        c.newLineAtOffset(MARGIN, y);
        c.showText(text);
        c.endText();
        y -= 16f;
        c.moveTo(MARGIN, y + 2);
        c.lineTo(PDRectangle.A4.getWidth() - MARGIN, y + 2);
        c.stroke();
        return y - 8f;
    }

    private float drawTableHeader(PDPageContentStream c, String[] headers, float[] widths, float y) throws IOException {
        c.setStrokingColor(BORDER);
        float x = MARGIN;
        for (int i = 0; i < headers.length; i++) {
            c.setNonStrokingColor(BRAND);
            c.beginText();
            c.setFont(bold, 9f);
            c.newLineAtOffset(x, y - 4);
            c.showText(headers[i]);
            c.endText();
            x += widths[i];
        }
        y -= ROW_HEIGHT;
        c.setStrokingColor(BORDER);
        c.moveTo(MARGIN, y);
        c.lineTo(MARGIN + (widths[0] + widths[1]), y);
        c.stroke();
        return y;
    }

    private float drawTableRow(PDPageContentStream c, String[] cells, float[] widths, float y) throws IOException {
        float x = MARGIN;
        c.setNonStrokingColor(Color.BLACK);
        c.beginText();
        c.setFont(regular, 8f);
        c.newLineAtOffset(x, y - 4);
        c.showText(cells[0]);
        c.endText();
        x += widths[0];
        c.beginText();
        c.newLineAtOffset(x, y - 4);
        c.showText(cells[1]);
        c.endText();
        return y - ROW_HEIGHT;
    }

    private float drawValueRow(PDPageContentStream c, String label, java.math.BigDecimal amt,
                               boolean negative, float y) throws IOException {
        String formatted = CurrencyConfig.format(amt);
        String shown = negative ? "(" + formatted + ")" : formatted;
        float width = PDRectangle.A4.getWidth() - (2 * MARGIN);
        c.setNonStrokingColor(Color.BLACK);
        c.beginText();
        c.setFont(regular, 10f);
        // wrap long labels onto a base line
        c.newLineAtOffset(MARGIN + 8, y - 4);
        c.showText(label);
        c.endText();
        // right-align the amount
        float shownWidth = regular.getStringWidth(shown) / 1000f * 10f;
        float rightX = PDRectangle.A4.getWidth() - MARGIN - shownWidth;
        c.setNonStrokingColor(Color.BLACK);
        c.beginText();
        c.setFont(regular, 10f);
        c.newLineAtOffset(rightX, y - 4);
        c.showText(shown);
        c.endText();
        return y - 24f;
    }

    private float drawValueRowBold(PDPageContentStream c, String label, java.math.BigDecimal amt, float y) throws IOException {
        String formatted = CurrencyConfig.format(amt);
        float width = PDRectangle.A4.getWidth() - (2 * MARGIN);
        c.setNonStrokingColor(BRAND);
        c.beginText();
        c.setFont(bold, 11f);
        c.newLineAtOffset(MARGIN + 8, y - 4);
        c.showText(label);
        c.endText();
        float labelWidth = bold.getStringWidth(label) / 1000f * 11f;
        float shownWidth = bold.getStringWidth(formatted) / 1000f * 11f;
        float rightX = PDRectangle.A4.getWidth() - MARGIN - shownWidth;
        c.beginText();
        c.setFont(bold, 11f);
        c.newLineAtOffset(rightX, y - 4);
        c.showText(formatted);
        c.endText();
        return y - 26f;
    }

    private float drawRule(PDPageContentStream c, float x, float y, float width) throws IOException {
        c.setStrokingColor(BORDER);
        c.moveTo(x, y);
        c.lineTo(x + width, y);
        c.stroke();
        return y - 10f;
    }

    private static String nvl(String s) {
        return s != null ? s : "";
    }
}
