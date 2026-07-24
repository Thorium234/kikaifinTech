package com.schaccs.service.procurement;

import com.schaccs.config.AppConfig;
import com.schaccs.enums.ApprovalAction;
import com.schaccs.enums.ProcurementRequestStatus;
import com.schaccs.model.procurement.ProcurementApproval;
import com.schaccs.model.procurement.ProcurementRequest;
import com.schaccs.repository.PersistenceService;
import com.schaccs.service.Services;
import com.schaccs.service.audit.AuditService;
import com.schaccs.store.ProcurementStore;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class ProcurementService {

    private final ProcurementStore store;
    private final AuditService audit;

    public ProcurementService(ProcurementStore store, AuditService audit) {
        this.store = store;
        this.audit = audit;
    }

    public ProcurementService() {
        this(ProcurementStore.getInstance(), Services.getInstance().audit());
    }

    public List<String> createRequest(ProcurementRequest request) {
        List<String> errors = new ArrayList<>();
        if (request.getItemDescription() == null || request.getItemDescription().isBlank()) {
            errors.add("Item description is required.");
        }
        if (request.getEstimatedCost() == null || request.getEstimatedCost().signum() <= 0) {
            errors.add("Estimated cost must be greater than zero.");
        }
        if (request.getRequestedBy() == null || request.getRequestedBy().isBlank()) {
            errors.add("Requested by is required.");
        }
        if (!errors.isEmpty()) {
            return errors;
        }

        int year = AppConfig.getInstance().getAcademicYear();
        long number = AppConfig.getInstance().getSchoolProfile().allocateProcurementRequestNumber();
        String requestNumber = String.format("PR-%d-%04d", year, number);
        request.setRequestNumber(requestNumber);
        request.setRequestDate(java.time.LocalDate.now());
        request.setStatus(ProcurementRequestStatus.DRAFT);

        store.addProcurementRequest(request);
        persist();
        audit.log("CREATE", "ProcurementRequest", request.getId(),
                "{\"requestNumber\":\"" + requestNumber + "\"}");
        return errors;
    }

    public List<String> submitRequest(ProcurementRequest request) {
        List<String> errors = new ArrayList<>();
        if (request == null) {
            errors.add("Select a procurement request.");
            return errors;
        }
        if (request.getStatus() != ProcurementRequestStatus.DRAFT) {
            errors.add("Only draft requests can be submitted.");
            return errors;
        }

        request.setStatus(ProcurementRequestStatus.SUBMITTED);
        recordApproval("ProcurementRequest", request.getId(),
                ApprovalAction.SUBMIT,
                AppConfig.getInstance().getCurrentUser(),
                AppConfig.getInstance().getCurrentUserRole(),
                null);
        persist();
        audit.log("SUBMIT", "ProcurementRequest", request.getId(),
                "{\"requestNumber\":\"" + request.getRequestNumber() + "\"}");
        return errors;
    }

    public List<String> approveRequest(ProcurementRequest request, String approverName,
                                       String role, String comments) {
        List<String> errors = new ArrayList<>();
        if (request == null) {
            errors.add("Select a procurement request.");
            return errors;
        }
        if (request.getStatus() != ProcurementRequestStatus.SUBMITTED) {
            errors.add("Only submitted requests can be approved.");
            return errors;
        }

        request.setStatus(ProcurementRequestStatus.APPROVED);
        recordApproval("ProcurementRequest", request.getId(),
                ApprovalAction.APPROVE,
                approverName, role, comments);
        persist();
        audit.log("APPROVE", "ProcurementRequest", request.getId(),
                "{\"requestNumber\":\"" + request.getRequestNumber()
                        + "\",\"approver\":\"" + approverName + "\"}");
        return errors;
    }

    public List<String> rejectRequest(ProcurementRequest request, String approverName,
                                      String role, String comments) {
        List<String> errors = new ArrayList<>();
        if (request == null) {
            errors.add("Select a procurement request.");
            return errors;
        }
        if (request.getStatus() != ProcurementRequestStatus.SUBMITTED) {
            errors.add("Only submitted requests can be rejected.");
            return errors;
        }

        request.setStatus(ProcurementRequestStatus.REJECTED);
        recordApproval("ProcurementRequest", request.getId(),
                ApprovalAction.REJECT,
                approverName, role, comments);
        persist();
        audit.log("REJECT", "ProcurementRequest", request.getId(),
                "{\"requestNumber\":\"" + request.getRequestNumber()
                        + "\",\"approver\":\"" + approverName + "\"}");
        return errors;
    }

    public List<String> returnRequest(ProcurementRequest request, String approverName,
                                      String role, String comments) {
        List<String> errors = new ArrayList<>();
        if (request == null) {
            errors.add("Select a procurement request.");
            return errors;
        }
        if (request.getStatus() != ProcurementRequestStatus.SUBMITTED) {
            errors.add("Only submitted requests can be returned for revision.");
            return errors;
        }

        request.setStatus(ProcurementRequestStatus.DRAFT);
        recordApproval("ProcurementRequest", request.getId(),
                ApprovalAction.RETURN,
                approverName, role, comments);
        persist();
        audit.log("RETURN", "ProcurementRequest", request.getId(),
                "{\"requestNumber\":\"" + request.getRequestNumber()
                        + "\",\"approver\":\"" + approverName + "\"}");
        return errors;
    }

    public void markTenderCreated(ProcurementRequest request, String tenderId) {
        if (request == null) {
            return;
        }
        request.setStatus(ProcurementRequestStatus.TENDER_CREATED);
        request.setTenderId(tenderId);
        persist();
        audit.log("TENDER_CREATED", "ProcurementRequest", request.getId(),
                "{\"requestNumber\":\"" + request.getRequestNumber()
                        + "\",\"tenderId\":\"" + tenderId + "\"}");
    }

    public List<ProcurementRequest> requestsByStatus(ProcurementRequestStatus status) {
        return store.getProcurementRequests().stream()
                .filter(r -> r.getStatus() == status)
                .collect(Collectors.toList());
    }

    public List<ProcurementRequest> allRequests() {
        return new ArrayList<>(store.getProcurementRequests());
    }

    public Optional<ProcurementRequest> findById(String id) {
        return store.findProcurementRequestById(id);
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

    private void persist() {
        PersistenceService.getInstance().saveAll();
    }
}
