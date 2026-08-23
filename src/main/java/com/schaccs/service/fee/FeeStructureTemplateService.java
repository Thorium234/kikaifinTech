package com.schaccs.service.fee;

import com.schaccs.config.CurrencyConfig;
import com.schaccs.enums.AcademicTerm;
import com.schaccs.enums.BoardingStatus;
import com.schaccs.model.fee.FeeStructure;
import com.schaccs.model.fee.FeeStructureItem;
import com.schaccs.model.fee.FeeStructureTemplate;
import com.schaccs.model.fee.FeeStructureTemplateItem;
import com.schaccs.repository.PersistenceService;
import com.schaccs.store.FeeStructureStore;
import javafx.collections.ObservableList;

import java.math.BigDecimal;

/**
 * Saves an existing fee structure as a reusable internal template and re-creates
 * structures from those templates later (year / class / boarding are chosen at
 * import time).
 */
public class FeeStructureTemplateService {

    private final FeeStructureStore store;

    public FeeStructureTemplateService() {
        this(FeeStructureStore.getInstance());
    }

    public FeeStructureTemplateService(FeeStructureStore store) {
        this.store = store;
    }

    public void saveAsTemplate(FeeStructure structure) {
        FeeStructureTemplate template = new FeeStructureTemplate(structure.getName());
        for (FeeStructureItem item : structure.getItems()) {
            for (AcademicTerm term : AcademicTerm.values()) {
                BigDecimal amt = item.amountForTerm(term);
                if (amt.compareTo(BigDecimal.ZERO) > 0) {
                    template.addItem(new FeeStructureTemplateItem(
                            item.getVoteheadCode(), item.getVoteheadName(), term, amt));
                }
            }
        }
        store.addTemplate(template);
        PersistenceService.getInstance().saveAll();
    }

    public ObservableList<FeeStructureTemplate> getTemplates() {
        return store.getTemplates();
    }

    public void deleteTemplate(FeeStructureTemplate template) {
        store.removeTemplate(template);
        PersistenceService.getInstance().saveAll();
    }

    public FeeStructure buildStructure(FeeStructureTemplate template, int academicYear,
                                       String formClass, BoardingStatus boardingStatus, String name) {
        FeeStructure structure = new FeeStructure(academicYear, formClass, boardingStatus, name);
        java.util.Map<String, FeeStructureItem> itemMap = new java.util.LinkedHashMap<>();
        for (FeeStructureTemplateItem ti : template.getItems()) {
            FeeStructureItem item = itemMap.computeIfAbsent(ti.getVoteheadCode(),
                    k -> new FeeStructureItem(ti.getVoteheadCode(), ti.getVoteheadName(), boardingStatus,
                            CurrencyConfig.zero(), CurrencyConfig.zero(), CurrencyConfig.zero()));
            item.setAmountForTerm(ti.getTerm(), ti.getAmount());
        }
        for (FeeStructureItem item : itemMap.values()) {
            structure.addItem(item);
        }
        return structure;
    }
}
