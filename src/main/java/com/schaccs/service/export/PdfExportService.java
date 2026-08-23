package com.schaccs.service.export;

import com.schaccs.config.AppConfig;
import com.schaccs.config.CurrencyConfig;
import com.schaccs.config.SchoolProfile;
import com.schaccs.service.export.PdfStampWatermarkOverlay;
import com.schaccs.enums.AcademicTerm;
import com.schaccs.enums.BoardingStatus;
import com.schaccs.model.fee.FeeStructure;
import com.schaccs.model.fee.FeeStructureItem;
import com.schaccs.model.receipt.Receipt;
import com.schaccs.model.receipt.ReceiptLine;
import com.schaccs.model.report.IncomeExpenditureRow;
import com.schaccs.model.report.TrialBalanceRow;
import com.schaccs.model.student.Student;
import com.schaccs.model.student.StudentBalance;
import com.schaccs.model.student.StudentFeeLedger;
import com.schaccs.service.report.ReportService;
import com.schaccs.store.FeeStructureStore;
import com.schaccs.store.StudentStore;
import com.schaccs.util.CurrencyUtil;
import com.schaccs.util.DateUtil;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

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
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    public void exportText(Path path, String title, List<String> lines) throws IOException {
        exportTable(path, title, List.of("Content"), lines.stream().map(line -> List.of(line)).toList());
    }

    public void exportTable(Path path, String title, List<String> headers, List<List<String>> rows) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            SchoolProfile school = AppConfig.getInstance().getSchoolProfile();
            PDPage page = new PDPage(new PDRectangle(PDRectangle.A4.getHeight(), PDRectangle.A4.getWidth()));
            document.addPage(page);
            PDPageContentStream content = new PDPageContentStream(document, page);
            PDRectangle box = page.getMediaBox();
            float usableWidth = box.getWidth() - (2 * MARGIN);
            float y = box.getHeight() - MARGIN;

            y = drawSchoolHeader(document, content, school, box, y);
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
                    y = drawSchoolHeader(document, content, school, box, y);
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

                // ── School header ──
                y = drawSchoolHeader(document, content, school, box, y);
                y = drawReceiptBanner(content, box, y, "OFFICIAL FEE RECEIPT");
                y -= 8f;

                // ── Resolve student + ledger first (needed in metadata and breakdown) ──
                Student student = null;
                StudentFeeLedger ledger = null;
                if (receipt.getStudentId() != null) {
                    student = StudentStore.getInstance().findById(receipt.getStudentId()).orElse(null);
                    if (student == null && receipt.getAdmissionNumber() != null) {
                        student = StudentStore.getInstance().findByAdmissionNumber(receipt.getAdmissionNumber()).orElse(null);
                    }
                    if (student != null) {
                        ledger = StudentStore.getInstance().getLedger(student.getId());
                    }
                }
                String ref = safe(receipt.getBankReference());

                // ── Receipt metadata box ──
                float infoBoxH = 90f;
                content.setStrokingColor(BORDER);
                content.addRect(MARGIN, y - infoBoxH, width, infoBoxH);
                content.stroke();
                String ts = receipt.getCreatedAt() != null
                        ? receipt.getCreatedAt().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"))
                        : "";
                drawInfoRowY(content, bold, regular, "Receipt No", receipt.getReceiptNumberDisplay(),
                        "Date", DateUtil.format(receipt.getDate()), width, y - 16);
                drawInfoRowY(content, bold, regular, "Timestamp", ts,
                        "Academic Year", String.valueOf(AppConfig.getInstance().getAcademicYear()), width, y - 34);
                drawInfoRowY(content, bold, regular, "Student", safe(receipt.getStudentName()),
                        "Adm No", safe(receipt.getAdmissionNumber()), width, y - 52);
                String guardianName = student != null ? safe(student.getParentName()) : "";
                String phone = student != null ? safe(student.getPhone()) : "";
                drawInfoRowY(content, bold, regular, "Guardian", guardianName,
                        "Phone", phone, width, y - 70);
                drawInfoRowY(content, bold, regular, "Class", safe(receipt.getClassLabel()),
                        "Mode", receipt.getPaymentMode() != null ? receipt.getPaymentMode().getDisplayName() : "", width, y - 88);
                y -= infoBoxH + 12f;

                // ── Compute dual-balance breakdown ──

                // Arrears cleared in this receipt
                BigDecimal arrearsCleared = ZERO;
                for (ReceiptLine rl : receipt.getLines()) {
                    if ("ARREARS".equals(rl.getVoteheadCode())) {
                        arrearsCleared = arrearsCleared.add(rl.getAmount()).setScale(2, RoundingMode.HALF_UP);
                    }
                }
                BigDecimal arrearsBf = ZERO;
                BigDecimal remainingArrears = ZERO;
                if (ledger != null) {
                    remainingArrears = ledger.getArrears() != null
                            ? ledger.getArrears().setScale(2, RoundingMode.HALF_UP) : ZERO;
                    arrearsBf = remainingArrears.add(arrearsCleared).setScale(2, RoundingMode.HALF_UP);
                }

                // Current term fee
                AcademicTerm currentTerm = ledger != null ? ledger.getCurrentTerm() : AcademicTerm.TERM_1;
                BigDecimal expectedTermFee = ZERO;
                if (student != null) {
                    int year = student.getAcademicYear() != null ? student.getAcademicYear()
                            : AppConfig.getInstance().getAcademicYear();
                    BoardingStatus bs = student.getBoardingStatus();
                    FeeStructure fs = FeeStructureStore.getInstance().findStructure(year, bs).orElse(null);
                    if (fs != null) {
                        expectedTermFee = fs.totalForTerm(currentTerm).setScale(2, RoundingMode.HALF_UP);
                    }
                }
                // Current term paid = total paid - arrears cleared - advance
                BigDecimal totalPmVh = ZERO;
                BigDecimal advanceAllocated = ZERO;
                for (ReceiptLine rl : receipt.getLines()) {
                    if ("ARREARS".equals(rl.getVoteheadCode()) || "ADVANCE".equals(rl.getVoteheadCode())) {
                        if ("ADVANCE".equals(rl.getVoteheadCode())) {
                            advanceAllocated = advanceAllocated.add(rl.getAmount()).setScale(2, RoundingMode.HALF_UP);
                        }
                        continue;
                    }
                    totalPmVh = totalPmVh.add(rl.getAmount()).setScale(2, RoundingMode.HALF_UP);
                }
                BigDecimal termPaidTotal = totalPmVh;
                if (ledger != null) {
                    termPaidTotal = ZERO;
                    for (String code : ledger.getPaidByVotehead().keySet()) {
                        if ("ARREARS".equals(code) || "ADVANCE".equals(code)) continue;
                        termPaidTotal = termPaidTotal.add(ledger.getPaid(code)).setScale(2, RoundingMode.HALF_UP);
                    }
                }
                BigDecimal termOutstanding = expectedTermFee.subtract(termPaidTotal).max(ZERO).setScale(2, RoundingMode.HALF_UP);

                // ── ARREARS section ──
                float[] threeCol = new float[]{width * 0.45f, width * 0.27f, width * 0.28f};
                y = drawSectionHeader(content, bold, "ARREARS CLEARANCE", threeCol, y);
                y = drawRow3(content, regular, "Arrears Brought Forward", CurrencyUtil.formatPlain(arrearsBf), "", threeCol, y);
                if (arrearsCleared.compareTo(ZERO) > 0) {
                    y = drawRow3(content, regular, "Cleared in this Receipt", CurrencyUtil.formatPlain(arrearsCleared.negate()), "(" + CurrencyUtil.formatPlain(arrearsCleared) + ")", threeCol, y);
                }
                y = drawRow3(content, bold, "Remaining Arrears", CurrencyUtil.formatPlain(remainingArrears), "", threeCol, y);
                y -= 6f;

                // ── CURRENT TERM section ──
                y = drawSectionHeader(content, bold, "CURRENT TERM — " + currentTerm.getDisplayName().toUpperCase(), threeCol, y);
                y = drawRow3(content, regular, "Billed Fee for " + currentTerm.getDisplayName(), CurrencyUtil.formatPlain(expectedTermFee), "", threeCol, y);
                y = drawRow3(content, regular, "Paid to Date (Current Term)", CurrencyUtil.formatPlain(termPaidTotal), "", threeCol, y);
                y = drawRow3(content, bold, "Outstanding Balance (Term)", CurrencyUtil.formatPlain(termOutstanding), "", threeCol, y);
                y -= 6f;

                // ── VOTEHEAD breakdown ──
                y = drawSectionHeader(content, bold, "VOTEHEAD ALLOCATION", threeCol, y);
                for (ReceiptLine line : receipt.getLines()) {
                    String label = safe(line.getVoteheadName());
                    String amt = CurrencyUtil.formatPlain(line.getAmount());
                    y = drawRow3(content, regular, label, amt, "", threeCol, y);
                }
                y -= 6f;

                // ── SUMMARY box ──
                BigDecimal netBalance = arrearsBf.add(expectedTermFee).subtract(receipt.getAmount()).subtract(termPaidTotal.subtract(totalPmVh)).max(ZERO).setScale(2, RoundingMode.HALF_UP);
                if (remainingArrears.compareTo(ZERO) > 0) {
                    netBalance = remainingArrears.add(termOutstanding).setScale(2, RoundingMode.HALF_UP);
                } else {
                    netBalance = termOutstanding.setScale(2, RoundingMode.HALF_UP);
                }

                content.setNonStrokingColor(TOTAL_FILL);
                content.addRect(MARGIN, y - 52f, width, 52f);
                content.fill();
                content.setStrokingColor(BORDER);
                content.addRect(MARGIN, y - 52f, width, 52f);
                content.stroke();
                float sumX = MARGIN + 8;
                drawLabelLine(content, bold, regular, "Total Received", CurrencyUtil.formatPlain(receipt.getAmount()), sumX, y - 16);
                drawLabelLine(content, bold, regular, "Cumulative Outstanding", CurrencyUtil.formatPlain(netBalance), sumX, y - 34);
                drawLabelLine(content, bold, regular, "Status", receipt.isReversed() ? "REVERSED" : "POSTED", sumX, y - 52);
                y -= 66f;

                // ── Amount in words ──
                drawParagraph(content, bold, regular, "Amount in words", CurrencyUtil.toWords(receipt.getAmount()), y, width);
                y -= 26f;

                // ── Reference ──
                if (!ref.isEmpty()) {
                    drawParagraph(content, bold, regular, "Reference", ref, y, width);
                    y -= 26f;
                }

                // ── Received by ──
                drawParagraph(content, bold, regular, "Received by", safe(receipt.getReceivedBy()), y, width);
                y -= 26f;

                // ── Principal ──
                drawParagraph(content, bold, regular, "Principal", safe(school.getPrincipal()), y, width);
                y -= 26f;

                // ── Signature image (uploaded from Settings) ──
                PDImageXObject signature = loadImage(document, school.getSignaturePath());
                if (signature != null) {
                    float[] dims = fitImage(signature, 150f, 24f);
                    if (dims[0] > 0f && dims[1] > 0f) {
                        content.drawImage(signature, MARGIN + width - dims[0], y - dims[1] - 2f, dims[0], dims[1]);
                        content.beginText();
                        content.setFont(regular, SMALL_SIZE);
                        content.setNonStrokingColor(Color.DARK_GRAY);
                        content.newLineAtOffset(MARGIN + width - dims[0], y - dims[1] - 14f);
                        content.showText("Signature");
                        content.endText();
                        content.setNonStrokingColor(Color.BLACK);
                        y -= dims[1] + 18f;
                    }
                }

                // ── Banking details ──
                drawParagraph(content, bold, regular, "Banking details", bankDetails(school), y, width);
                y -= 26f;

                // ── Cash policy ──
                drawParagraph(content, bold, regular, "Policy", safe(school.getCashPolicy()), y, width);

                com.schaccs.model.student.StudentFeeLedger stampLedger =
                        com.schaccs.store.StudentStore.getInstance().getLedger(receipt.getStudentId());
                PdfStampWatermarkOverlay.setCurrentTermLabel(
                        stampLedger != null && stampLedger.getCurrentTerm() != null
                                ? stampLedger.getCurrentTerm().getDisplayName() : null);
                PdfStampWatermarkOverlay.drawReceiptStamp(document, page, content,
                        receipt.getReceiptNumberDisplay(), receipt.getStudentName());
            }
            document.save(path.toFile());
        }
    }

    /**
     * Rich fee-structure PDF: school header, structure info, a vote-head x term
     * grid with totals, and the school's payment details at the bottom.
     *
     * @param onlyTerm when non-null, only that term's rows are rendered;
     *                 otherwise all terms are shown side by side.
     */
    public void exportFeeStructurePdf(Path path, FeeStructure structure, AcademicTerm onlyTerm) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            PDRectangle box = page.getMediaBox();
            float width = box.getWidth() - (2 * MARGIN);

            PDPageContentStream content = new PDPageContentStream(document, page);
            try {
                SchoolProfile school = AppConfig.getInstance().getSchoolProfile();
                float y = box.getHeight() - MARGIN;

                y = drawSchoolHeader(document, content, school, box, y);
                y = drawReceiptBanner(content, box, y, "FEE STRUCTURE");
                y -= 8f;

                String mode = onlyTerm != null ? onlyTerm.getDisplayName().toUpperCase() : "COMBINED";
                drawInfoRowY(content, bold, regular, "Academic Year", String.valueOf(structure.getAcademicYear()),
                        "Structure", safe(structure.getName()), width, y - 16);
                drawInfoRowY(content, bold, regular, "Class", safe(structure.getFormClass()),
                        "Boarding", structure.getBoardingStatus() != null ? structure.getBoardingStatus().getDisplayName() : "", width, y - 34);
                drawInfoRowY(content, bold, regular, "Mode", mode,
                        "Vote Heads", String.valueOf(structure.getItems().size()), width, y - 52);
                y -= 64f;

                List<FeeStructureItem> items = new ArrayList<>(structure.getItems());
                if (onlyTerm != null) {
                    AcademicTerm filterTerm = onlyTerm;
                    items.removeIf(i -> i.amountForTerm(filterTerm).compareTo(BigDecimal.ZERO) <= 0);
                }
                if (items.isEmpty()) {
                    items = new ArrayList<>(structure.getItems());
                    onlyTerm = null;
                }

                // Group amounts by vote-head code, keyed by term
                java.util.LinkedHashMap<String, Map<AcademicTerm, BigDecimal>> grouped = new java.util.LinkedHashMap<>();
                java.util.LinkedHashMap<String, String> names = new java.util.LinkedHashMap<>();
                List<AcademicTerm> terms = new ArrayList<>();
                for (FeeStructureItem item : items) {
                    String code = safe(item.getVoteheadCode());
                    Map<AcademicTerm, BigDecimal> termMap = grouped.computeIfAbsent(code, k -> new java.util.LinkedHashMap<>());
                    for (AcademicTerm t : AcademicTerm.values()) {
                        BigDecimal amt = item.amountForTerm(t);
                        if (amt.compareTo(BigDecimal.ZERO) > 0) {
                            termMap.put(t, amt);
                            if (!terms.contains(t)) {
                                terms.add(t);
                            }
                        }
                    }
                    names.putIfAbsent(code, safe(item.getVoteheadName()));
                }
                terms.sort(Comparator.naturalOrder());

                int columns = 2 + terms.size() + 1;
                float[] colWidths = new float[columns];
                colWidths[0] = width * 0.13f;
                float each = (width - colWidths[0]) / (terms.size() + 2);
                for (int i = 1; i < columns; i++) {
                    colWidths[i] = each;
                }

                List<String> headers = new ArrayList<>();
                headers.add("Code");
                headers.add("Vote Head");
                for (AcademicTerm term : terms) {
                    headers.add(term.getDisplayName());
                }
                headers.add("Total");

                y = drawHeader(content, bold, headers, colWidths, y);

                BigDecimal grandTotal = ZERO;
                Map<AcademicTerm, BigDecimal> termTotals = new java.util.HashMap<>();
                for (AcademicTerm term : terms) {
                    termTotals.put(term, ZERO);
                }
                for (Map.Entry<String, Map<AcademicTerm, BigDecimal>> entry : grouped.entrySet()) {
                    BigDecimal rowTotal = ZERO;
                    List<String> row = new ArrayList<>();
                    row.add(entry.getKey());
                    row.add(names.get(entry.getKey()));
                    for (AcademicTerm term : terms) {
                        BigDecimal amount = entry.getValue().get(term);
                        BigDecimal value = amount == null ? ZERO : amount.setScale(2, RoundingMode.HALF_UP);
                        row.add(value.compareTo(ZERO) == 0 ? "—" : CurrencyUtil.formatPlain(value));
                        rowTotal = rowTotal.add(value).setScale(2, RoundingMode.HALF_UP);
                        termTotals.put(term, termTotals.get(term).add(value).setScale(2, RoundingMode.HALF_UP));
                    }
                    row.add(CurrencyUtil.formatPlain(rowTotal));
                    grandTotal = grandTotal.add(rowTotal).setScale(2, RoundingMode.HALF_UP);
                    if (y - ROW_HEIGHT < MARGIN + 120) {
                        content.close();
                        page = new PDPage(PDRectangle.A4);
                        document.addPage(page);
                        box = page.getMediaBox();
                        width = box.getWidth() - (2 * MARGIN);
                        content = new PDPageContentStream(document, page);
                        y = box.getHeight() - MARGIN;
                        y = drawSchoolHeader(document, content, school, box, y);
                        y = drawHeader(content, bold, headers, colWidths, y);
                    }
                    y = drawRow(content, regular, row, colWidths, y);
                }

                List<String> totalRow = new ArrayList<>();
                totalRow.add("");
                totalRow.add("TOTAL");
                for (AcademicTerm term : terms) {
                    totalRow.add(CurrencyUtil.formatPlain(termTotals.get(term)));
                }
                totalRow.add(CurrencyUtil.formatPlain(grandTotal));
                content.setNonStrokingColor(TOTAL_FILL);
                content.addRect(MARGIN, y - ROW_HEIGHT, width, ROW_HEIGHT);
                content.fill();
                y = drawRow(content, bold, totalRow, colWidths, y);
                y -= 16f;

                drawParagraph(content, bold, regular, "Payment Details", bankDetails(school), y, width);
                y -= 26f;
                drawParagraph(content, bold, regular, "Total Fee in Words", CurrencyUtil.toWords(grandTotal), y, width);
                y -= 26f;
                drawParagraph(content, bold, regular, "Prepared by", safe(school.getPrincipal()) + " — " + safe(school.getSchoolName()), y, width);
            } finally {
                content.close();
            }
            document.save(path.toFile());
        }
    }

    private float drawSchoolHeader(PDDocument document, PDPageContentStream content, SchoolProfile school, PDRectangle box, float y) throws IOException {
        PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
        PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

        float logoW = 0f;
        float logoH = 0f;
        PDImageXObject logo = loadImage(document, school.getLogoPath());
        if (logo != null) {
            float[] dims = fitImage(logo, 52f, 52f);
            logoW = dims[0];
            logoH = dims[1];
            if (logoW > 0f && logoH > 0f) {
                content.drawImage(logo, MARGIN, y - logoH, logoW, logoH);
            }
        }

        float centerX = box.getWidth() / 2f;
        if (logoW > 0f) {
            float usable = box.getWidth() - (2 * MARGIN);
            centerX = MARGIN + logoW + (usable - logoW) / 2f;
        }

        y = drawCentered(content, bold, safe(school.getMinistry()), 10f, centerX, y, BRAND);
        y = drawCentered(content, bold, safe(school.getSchoolName()), 16f, centerX, y - 1, BRAND);
        y = drawCentered(content, regular, safe(school.getLocation()), 9f, centerX, y - 1, Color.DARK_GRAY);
        y -= 10f;
        content.setStrokingColor(BRAND);
        float width = box.getWidth() - (2 * MARGIN);
        content.setLineWidth(1.5f);
        content.addRect(MARGIN, y - 2f, width, 2f);
        content.stroke();
        content.setLineWidth(1f);
        return y - 22f;
    }

    private float drawReceiptBanner(PDPageContentStream content, PDRectangle box, float y, String text) throws IOException {
        float bannerWidth = box.getWidth() - (2 * MARGIN);
        content.setNonStrokingColor(BRAND);
        content.addRect(MARGIN, y - 18f, bannerWidth, 20f);
        content.fill();
        content.beginText();
        content.setNonStrokingColor(Color.WHITE);
        content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 12f);
        float textWidth = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD).getStringWidth(text) / 1000 * 12f;
        content.newLineAtOffset((box.getWidth() - textWidth) / 2f, y - 13f);
        content.showText(text);
        content.endText();
        content.setNonStrokingColor(Color.BLACK);
        return y - 24f;
    }

    private float drawSectionHeader(PDPageContentStream content, PDType1Font font, String title, float[] colWidths, float y) throws IOException {
        content.setNonStrokingColor(BRAND_LIGHT);
        float x = MARGIN;
        for (float w : colWidths) {
            content.addRect(x, y - ROW_HEIGHT, w, ROW_HEIGHT);
            x += w;
        }
        content.fill();
        content.setStrokingColor(BORDER);
        x = MARGIN;
        for (float w : colWidths) {
            content.addRect(x, y - ROW_HEIGHT, w, ROW_HEIGHT);
            x += w;
        }
        content.stroke();
        content.beginText();
        content.setNonStrokingColor(BRAND);
        content.setFont(font, BODY_SIZE);
        content.newLineAtOffset(MARGIN + 4, y - 12);
        content.showText(sanitize(title));
        content.endText();
        content.setNonStrokingColor(Color.BLACK);
        return y - ROW_HEIGHT;
    }

    private float drawRow3(PDPageContentStream content, PDType1Font font, String label, String value, String extra, float[] colWidths, float y) throws IOException {
        float x = MARGIN;
        String[] parts = {label, value, extra};
        for (int i = 0; i < colWidths.length; i++) {
            String text = i < parts.length ? safe(parts[i]) : "";
            drawCell(content, font, BODY_SIZE, text, x, y, colWidths[i], ROW_HEIGHT, false);
            x += colWidths[i];
        }
        return y - ROW_HEIGHT;
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
        if (text == null) return "";
        String clean = text;
        while (!clean.isEmpty() && font.getStringWidth(clean) / 1000 * fontSize > width - 8) {
            clean = clean.substring(0, clean.length() - 1);
        }
        if (!clean.equals(text) && clean.length() > 3) {
            clean = clean.substring(0, clean.length() - 3) + "...";
        }
        return clean;
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

    private void drawInfoRowY(PDPageContentStream content, PDType1Font bold, PDType1Font regular,
                              String label1, String value1, String label2, String value2, float totalWidth, float y) throws IOException {
        float midX = MARGIN + totalWidth / 2f;
        content.beginText();
        content.setFont(bold, BODY_SIZE);
        content.newLineAtOffset(MARGIN + 8, y);
        content.showText(sanitize(label1 + ": "));
        content.endText();
        content.beginText();
        content.setFont(regular, BODY_SIZE);
        content.newLineAtOffset(MARGIN + 100, y);
        content.showText(sanitize(value1));
        content.endText();
        content.beginText();
        content.setFont(bold, BODY_SIZE);
        content.newLineAtOffset(midX, y);
        content.showText(sanitize(label2 + ": "));
        content.endText();
        content.beginText();
        content.setFont(regular, BODY_SIZE);
        content.newLineAtOffset(midX + 60, y);
        content.showText(sanitize(value2));
        content.endText();
    }

    private void drawLabelLine(PDPageContentStream content, PDType1Font bold, PDType1Font regular,
                               String label, String value, float x, float y) throws IOException {
        content.beginText();
        content.setFont(bold, BODY_SIZE);
        content.newLineAtOffset(x, y);
        content.showText(sanitize(label + ":  "));
        content.endText();
        content.beginText();
        content.setFont(regular, BODY_SIZE);
        content.newLineAtOffset(x + 120, y);
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

    private String bankDetails(SchoolProfile school) {
        return "Bank: " + safe(school.getBankName())
                + " | A/C: " + safe(school.getBankAccount())
                + " | PayBill: " + safe(school.getPayBill())
                + " | Account: " + safe(school.getPayBillAccount());
    }

    private PDImageXObject loadImage(PDDocument document, String path) {
        if (path == null || path.isBlank()) return null;
        File file = new File(path);
        if (!file.isFile()) return null;
        try {
            return PDImageXObject.createFromFile(file.getAbsolutePath(), document);
        } catch (IOException e) {
            return null;
        }
    }

    private float[] fitImage(PDImageXObject image, float maxW, float maxH) {
        float iw = image.getWidth();
        float ih = image.getHeight();
        if (iw <= 0f || ih <= 0f) return new float[]{0f, 0f};
        float scale = Math.min(maxW / iw, maxH / ih);
        return new float[]{iw * scale, ih * scale};
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
        BigDecimal totalDebit = ZERO;
        BigDecimal totalCredit = ZERO;
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
        BigDecimal totalIncome = ZERO;
        BigDecimal totalExpense = ZERO;
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
                    PDRectangle box = page.getMediaBox();
                    float y = box.getHeight() - MARGIN;

                    y = drawSchoolHeader(document, content, school, box, y);

                    String parentName = "";
                    Student student = studentStore.findByAdmissionNumber(def.getAdmissionNumber()).orElse(null);
                    if (student != null && student.getParentName() != null) parentName = student.getParentName();
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

                    com.schaccs.model.student.StudentFeeLedger stampLedger2 =
                            com.schaccs.store.StudentStore.getInstance().getLedger(def.getStudentId());
                    PdfStampWatermarkOverlay.setCurrentTermLabel(
                            stampLedger2 != null && stampLedger2.getCurrentTerm() != null
                                    ? stampLedger2.getCurrentTerm().getDisplayName() : null);
                    PdfStampWatermarkOverlay.drawVerificationStamp(document, page, content,
                            def.getAdmissionNumber(), def.getStudentName());
                }
            }
            document.save(path.toFile());
        }
    }
}
