package com.schaccs.service.procurement;

import com.schaccs.config.AppConfig;
import com.schaccs.config.CurrencyConfig;
import com.schaccs.enums.ApprovalAction;
import com.schaccs.enums.ContractStatus;
import com.schaccs.model.procurement.Contract;
import com.schaccs.model.procurement.ContractMilestone;
import com.schaccs.model.procurement.ProcurementApproval;
import com.schaccs.repository.PersistenceService;
import com.schaccs.service.audit.AuditService;
import com.schaccs.store.ProcurementStore;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ContractService {

    private final ProcurementStore store;
    private final AuditService audit;

    public ContractService(ProcurementStore store, AuditService audit) {
        this.store = store;
        this.audit = audit;
    }

    public ContractService() {
        this(ProcurementStore.getInstance(), new AuditService());
    }

    // ---- Contract Lifecycle ----

    public List<String> createContract(Contract contract) {
        List<String> errors = new ArrayList<>();
        if (contract.getSupplierId() == null || contract.getSupplierId().isBlank()) {
            errors.add("Supplier is required.");
        }
        if (contract.getStartDate() == null) {
            errors.add("Start date is required.");
        }
        if (contract.getEndDate() == null) {
            errors.add("End date is required.");
        }
        if (contract.getStartDate() != null && contract.getEndDate() != null
                && !contract.getEndDate().isAfter(contract.getStartDate())) {
            errors.add("End date must be after start date.");
        }
        if (contract.getContractValue() == null || contract.getContractValue().compareTo(BigDecimal.ZERO) <= 0) {
            errors.add("Contract value must be greater than zero.");
        }
        if (!errors.isEmpty()) {
            return errors;
        }

        int year = AppConfig.getInstance().getAcademicYear();
        long number = AppConfig.getInstance().getSchoolProfile().allocateContractNumber();
        contract.setContractNumber(String.format("CTR-%d-%04d", year, number));
        contract.setStatus(ContractStatus.DRAFT);

        store.addContract(contract);
        persist();
        audit.log("CREATE", "Contract", contract.getId(),
                "{\"contractNumber\":\"" + contract.getContractNumber() + "\"}");
        return errors;
    }

    public List<String> activateContract(Contract contract) {
        List<String> errors = new ArrayList<>();
        if (contract == null) {
            errors.add("Select a contract.");
            return errors;
        }
        if (contract.getStatus() != ContractStatus.DRAFT) {
            errors.add("Only draft contracts can be activated.");
            return errors;
        }

        contract.setStatus(ContractStatus.ACTIVE);
        recordApproval("Contract", contract.getId(),
                ApprovalAction.APPROVE,
                AppConfig.getInstance().getCurrentUser(),
                AppConfig.getInstance().getCurrentUserRole(),
                "Contract activated");
        persist();
        audit.log("ACTIVATE", "Contract", contract.getId(),
                "{\"contractNumber\":\"" + contract.getContractNumber() + "\"}");
        return errors;
    }

    public List<String> completeContract(Contract contract) {
        List<String> errors = new ArrayList<>();
        if (contract == null) {
            errors.add("Select a contract.");
            return errors;
        }
        if (contract.getStatus() != ContractStatus.ACTIVE && contract.getStatus() != ContractStatus.EXTENDED) {
            errors.add("Only active or extended contracts can be completed.");
            return errors;
        }

        contract.setStatus(ContractStatus.COMPLETED);
        persist();
        audit.log("COMPLETE", "Contract", contract.getId(),
                "{\"contractNumber\":\"" + contract.getContractNumber() + "\"}");
        return errors;
    }

    public List<String> extendContract(Contract contract, LocalDate newEndDate) {
        List<String> errors = new ArrayList<>();
        if (contract == null) {
            errors.add("Select a contract.");
            return errors;
        }
        if (contract.getStatus() != ContractStatus.ACTIVE && contract.getStatus() != ContractStatus.EXTENDED) {
            errors.add("Only active or extended contracts can be extended.");
            return errors;
        }
        if (newEndDate == null) {
            errors.add("New end date is required.");
        }
        if (newEndDate != null && contract.getEndDate() != null && !newEndDate.isAfter(contract.getEndDate())) {
            errors.add("New end date must be after the current end date.");
        }
        if (!errors.isEmpty()) {
            return errors;
        }

        contract.setEndDate(newEndDate);
        contract.setStatus(ContractStatus.EXTENDED);
        recordApproval("Contract", contract.getId(),
                ApprovalAction.APPROVE,
                AppConfig.getInstance().getCurrentUser(),
                AppConfig.getInstance().getCurrentUserRole(),
                "Contract extended to " + newEndDate);
        persist();
        audit.log("EXTEND", "Contract", contract.getId(),
                "{\"contractNumber\":\"" + contract.getContractNumber()
                        + "\",\"newEndDate\":\"" + newEndDate + "\"}");
        return errors;
    }

    public List<String> terminateContract(Contract contract, String reason) {
        List<String> errors = new ArrayList<>();
        if (contract == null) {
            errors.add("Select a contract.");
            return errors;
        }
        if (contract.getStatus() != ContractStatus.ACTIVE && contract.getStatus() != ContractStatus.EXTENDED) {
            errors.add("Only active or extended contracts can be terminated.");
            return errors;
        }
        if (reason == null || reason.isBlank()) {
            errors.add("Termination reason is required.");
            return errors;
        }

        contract.setStatus(ContractStatus.TERMINATED);
        contract.setNotes(reason);
        recordApproval("Contract", contract.getId(),
                ApprovalAction.REJECT,
                AppConfig.getInstance().getCurrentUser(),
                AppConfig.getInstance().getCurrentUserRole(),
                "Contract terminated: " + reason);
        persist();
        audit.log("TERMINATE", "Contract", contract.getId(),
                "{\"contractNumber\":\"" + contract.getContractNumber()
                        + "\",\"reason\":\"" + reason + "\"}");
        return errors;
    }

    // ---- Milestones ----

    public List<String> addMilestone(ContractMilestone milestone) {
        List<String> errors = new ArrayList<>();
        if (milestone.getContractId() == null || milestone.getContractId().isBlank()) {
            errors.add("Contract is required.");
        } else if (store.findContractById(milestone.getContractId()).isEmpty()) {
            errors.add("Contract not found.");
        }
        if (milestone.getTitle() == null || milestone.getTitle().isBlank()) {
            errors.add("Milestone title is required.");
        }
        if (milestone.getDueDate() == null) {
            errors.add("Due date is required.");
        }
        if (milestone.getAmount() != null) {
            milestone.setAmount(CurrencyConfig.money(milestone.getAmount()));
        }
        if (!errors.isEmpty()) {
            return errors;
        }

        store.addMilestone(milestone);
        persist();
        audit.log("CREATE", "ContractMilestone", milestone.getId(),
                "{\"contractId\":\"" + milestone.getContractId()
                        + "\",\"title\":\"" + milestone.getTitle() + "\"}");
        return errors;
    }

    public List<String> completeMilestone(ContractMilestone milestone) {
        List<String> errors = new ArrayList<>();
        if (milestone == null) {
            errors.add("Select a milestone.");
            return errors;
        }
        if (milestone.isCompleted()) {
            errors.add("Milestone is already completed.");
            return errors;
        }

        milestone.setCompleted(true);
        milestone.setCompletedDate(LocalDate.now());
        persist();
        audit.log("COMPLETE", "ContractMilestone", milestone.getId(),
                "{\"contractId\":\"" + milestone.getContractId()
                        + "\",\"title\":\"" + milestone.getTitle() + "\"}");
        return errors;
    }

    // ---- Queries ----

    public List<Contract> activeContracts() {
        return store.getContracts().stream()
                .filter(c -> c.getStatus() == ContractStatus.ACTIVE)
                .toList();
    }

    public List<Contract> allContracts() {
        return new ArrayList<>(store.getContracts());
    }

    public Optional<Contract> findById(String id) {
        return store.findContractById(id);
    }

    public Optional<Contract> findByNumber(String number) {
        return store.findContractByNumber(number);
    }

    public List<ContractMilestone> milestonesForContract(String contractId) {
        return new ArrayList<>(store.milestonesForContract(contractId));
    }

    public BigDecimal totalContractValue(String supplierId) {
        BigDecimal total = CurrencyConfig.zero();
        for (Contract c : store.getContracts()) {
            if (c.getSupplierId() != null && c.getSupplierId().equals(supplierId)
                    && (c.getStatus() == ContractStatus.ACTIVE || c.getStatus() == ContractStatus.EXTENDED)) {
                total = total.add(CurrencyConfig.money(c.getContractValue()));
            }
        }
        return total;
    }

    // ---- Private Helpers ----

    private void persist() {
        PersistenceService.getInstance().saveAll();
    }

    private void recordApproval(String entityType, String entityId,
                                ApprovalAction action, String performer,
                                String role, String comments) {
        ProcurementApproval approval = new ProcurementApproval();
        approval.setEntityType(entityType);
        approval.setEntityId(entityId);
        approval.setAction(action);
        approval.setPerformedBy(performer);
        approval.setRole(role);
        approval.setComments(comments);
        store.addApproval(approval);
    }
}
