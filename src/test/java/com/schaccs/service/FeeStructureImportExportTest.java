package com.schaccs.service;

import com.schaccs.config.AppConfig;
import com.schaccs.config.CurrencyConfig;
import com.schaccs.enums.AcademicTerm;
import com.schaccs.enums.AccountType;
import com.schaccs.enums.BoardingStatus;
import com.schaccs.model.fee.FeeStructure;
import com.schaccs.model.fee.FeeStructureItem;
import com.schaccs.model.fee.FeeStructureTemplate;
import com.schaccs.model.fee.FeeStructureTemplateItem;
import com.schaccs.model.finance.Votehead;
import com.schaccs.repository.PersistenceService;
import com.schaccs.service.export.FeeStructureExportService;
import com.schaccs.service.export.PdfExportService;
import com.schaccs.service.fee.FeeStructureTemplateService;
import com.schaccs.service.importer.FeeStructureImportService;
import com.schaccs.store.FeeStructureStore;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FeeStructureImportExportTest {

    private FeeStructureStore store;

    @BeforeEach
    void setUp() {
        PersistenceService.getInstance().clearAll();
        store = FeeStructureStore.getInstance();
        store.addVotehead(new Votehead("BOARD", "Boarding", AccountType.SCHOOL_FUND, 1));
        store.addVotehead(new Votehead("TUITION", "Tuition", AccountType.SCHOOL_FUND, 2));
    }

    @AfterEach
    void tearDown() {
        PersistenceService.getInstance().clearAll();
    }

    private FeeStructure sampleStructure() {
        FeeStructure structure = new FeeStructure(AppConfig.getInstance().getAcademicYear(),
                "Form 2", BoardingStatus.BOARDING, "Sample Structure");
        structure.addItem(new FeeStructureItem("BOARD", "Boarding", AcademicTerm.TERM_1,
                BoardingStatus.BOARDING, CurrencyConfig.money("5000")));
        structure.addItem(new FeeStructureItem("BOARD", "Boarding", AcademicTerm.TERM_2,
                BoardingStatus.BOARDING, CurrencyConfig.money("5000")));
        structure.addItem(new FeeStructureItem("BOARD", "Boarding", AcademicTerm.TERM_3,
                BoardingStatus.BOARDING, CurrencyConfig.money("4500")));
        structure.addItem(new FeeStructureItem("TUITION", "Tuition", AcademicTerm.TERM_1,
                BoardingStatus.BOARDING, CurrencyConfig.money("1000")));
        return structure;
    }

    @Test
    void excelExportThenImportRoundTripsItems() throws Exception {
        FeeStructure structure = sampleStructure();
        store.addStructure(structure);

        FeeStructureExportService exportService = new FeeStructureExportService();
        Path xlsx = Files.createTempFile("fee-structure", ".xlsx");
        exportService.exportStructure(xlsx, structure);
        assertTrue(Files.size(xlsx) > 0);

        FeeStructureImportService importService = new FeeStructureImportService();
        List<Map<String, String>> rows = importService.parseFile(xlsx);
        FeeStructureImportService.ImportResult result =
                importService.parseItems(rows, BoardingStatus.BOARDING);

        assertEquals(4, rows.size(), "All 4 items must round-trip");
        assertEquals(4, result.items().size(), "All rows valid");
        assertEquals(0, result.warnings().size());
        assertEquals(CurrencyConfig.money("5000"), result.items().get(0).getAmount());
        assertEquals(AcademicTerm.TERM_1, result.items().get(0).getTerm());
        assertEquals("BOARD", result.items().get(0).getVoteheadCode());
    }

    @Test
    void csvImportSkipsBadRowsWithWarnings() throws Exception {
        FeeStructureImportService importService = new FeeStructureImportService();
        Path csv = Files.createTempFile("fee-import", ".csv");
        Files.writeString(csv, String.join("\n",
                "Term,Code,Vote Head,Amount",
                "Term 1,BOARD,Boarding,5000",
                "Term 9,BOARD,Boarding,5000",
                "Term 2,,Missing Code,4000",
                "Term 3,TUITION,Tuition,not-a-number",
                "Term 1,TUITION,Tuition,1000"));

        List<Map<String, String>> rows = importService.parseFile(csv);
        FeeStructureImportService.ImportResult result =
                importService.parseItems(rows, BoardingStatus.BOARDING);

        assertEquals(5, rows.size());
        assertEquals(2, result.items().size());
        assertEquals(3, result.warnings().size());
    }

    @Test
    void templateSaveAndBuildRoundTripsAmounts() {
        FeeStructure structure = sampleStructure();
        FeeStructureTemplateService templateService = new FeeStructureTemplateService(store);

        templateService.saveAsTemplate(structure);
        assertEquals(1, store.getTemplates().size());

        FeeStructureTemplate template = store.getTemplates().get(0);
        assertEquals(4, template.getItems().size());

        FeeStructure rebuilt = templateService.buildStructure(template,
                AppConfig.getInstance().getAcademicYear(), "Form 3", BoardingStatus.DAY, "Rebuilt");
        assertEquals(4, rebuilt.getItems().size());
        assertEquals("Form 3", rebuilt.getFormClass());
        assertEquals(BoardingStatus.DAY, rebuilt.getBoardingStatus());
        assertTrue(rebuilt.getItems().stream()
                .allMatch(i -> i.getBoardingStatus() == BoardingStatus.DAY),
                "Rebuilt items must carry the new boarding status");
        assertEquals(structure.totalForTerm(AcademicTerm.TERM_1), rebuilt.totalForTerm(AcademicTerm.TERM_1));
    }

    @Test
    void templateDeleteRemovesFromStore() {
        FeeStructureTemplateService templateService = new FeeStructureTemplateService(store);
        templateService.saveAsTemplate(sampleStructure());

        FeeStructureTemplate template = store.getTemplates().get(0);
        templateService.deleteTemplate(template);
        assertEquals(0, store.getTemplates().size());
    }

    @Test
    void feeStructurePdfWritesValidDocument() throws Exception {
        FeeStructure structure = sampleStructure();
        PdfExportService pdfService = new PdfExportService();

        Path combined = Files.createTempFile("fee-combined", ".pdf");
        pdfService.exportFeeStructurePdf(combined, structure, null);
        assertTrue(Files.size(combined) > 0);
        try (PDDocument doc = Loader.loadPDF(combined.toFile())) {
            assertTrue(doc.getNumberOfPages() >= 1);
        }

        Path termOnly = Files.createTempFile("fee-term", ".pdf");
        pdfService.exportFeeStructurePdf(termOnly, structure, AcademicTerm.TERM_1);
        assertTrue(Files.size(termOnly) > 0);
        try (PDDocument doc = Loader.loadPDF(termOnly.toFile())) {
            assertTrue(doc.getNumberOfPages() >= 1);
        }
    }

    @Test
    void templateModelCarriesPerTermAmounts() {
        FeeStructureTemplate template = new FeeStructureTemplate("T");
        template.addItem(new FeeStructureTemplateItem("BOARD", "Boarding",
                AcademicTerm.TERM_1, CurrencyConfig.money("2500")));
        assertEquals(1, template.getItems().size());
        assertEquals(AcademicTerm.TERM_1, template.getItems().get(0).getTerm());
        assertEquals(CurrencyConfig.money("2500"), template.getItems().get(0).getAmount());
    }
}
