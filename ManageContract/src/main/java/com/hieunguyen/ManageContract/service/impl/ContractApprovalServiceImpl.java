package com.hieunguyen.ManageContract.service.impl;

import com.hieunguyen.ManageContract.common.constants.*;
import com.hieunguyen.ManageContract.dto.approval.ApprovalStepResponse;
import com.hieunguyen.ManageContract.dto.approval.StepApprovalRequest;
import com.hieunguyen.ManageContract.dto.contract.ContractResponse;
import com.hieunguyen.ManageContract.dto.contractSign.SignStepRequest;
import com.hieunguyen.ManageContract.entity.*;
import com.hieunguyen.ManageContract.event.ContractApprovalEvent;
import com.hieunguyen.ManageContract.mapper.ContractMapper;
import com.hieunguyen.ManageContract.repository.*;
import com.hieunguyen.ManageContract.security.jwt.SecurityUtil;
import com.hieunguyen.ManageContract.service.ContractApprovalService;
import com.hieunguyen.ManageContract.service.ContractFileService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class ContractApprovalServiceImpl implements ContractApprovalService {

    private final ContractRepository contractRepository;
    private final UserRepository userRepository;
    private final ApprovalFlowRepository flowRepository;
    private final ContractApprovalRepository contractApprovalRepository;
    private final ContractSignatureRepository contractSignatureRepository;
    private final ContractFileService contractFileService;
    private final SecurityUtil securityUtils;

    // Thêm publisher để bắn domain event (listener gửi mail sẽ nghe AFTER_COMMIT)
    private final ApplicationEventPublisher events;

    // ---------------------------------------------------------------------
    // Submit for approval
    // ---------------------------------------------------------------------
    @Transactional
    @Override
    public ContractResponse submitForApproval(Long contractId, Long flowId) {
        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new RuntimeException("Contract not found"));

        if (contract.getStatus() != ContractStatus.DRAFT) {
            throw new RuntimeException("Only draft contracts can be submitted for approval");
        }

        if (contractApprovalRepository.existsByContractId(contractId)) {
            throw new RuntimeException("Contract already has an approval flow");
        }

        // Chọn flow
        ApprovalFlow flow = determineApprovalFlow(contract, flowId);
        if (flow.getSteps() == null || flow.getSteps().isEmpty()) {
            throw new RuntimeException("Selected flow has no steps");
        }

        // Snapshot step sang ContractApproval
        List<ContractApproval> approvals = copyFlowToContractApproval(contract, flow);
        contractApprovalRepository.saveAll(approvals);

        // Đảm bảo file tồn tại trước khi validate
        ensureContractFileExists(contract);

        // Validate placeholder (mềm mại)
        try {
            boolean isValid = contractFileService.validatePlaceholdersInContract(contractId);
            if (!isValid) {
                log.warn("Some signature placeholders not found in contract file for contract {}.", contractId);
            }
        } catch (Exception e) {
            log.warn("Placeholder validation failed for contract {}: {}. Continuing anyway.", contractId, e.getMessage());
        }

        contract.setStatus(ContractStatus.PENDING_APPROVAL);
        contract.setFlow(flow);
        return ContractMapper.toResponse(contractRepository.save(contract));
    }

    private ApprovalFlow determineApprovalFlow(Contract contract, Long flowId) {
        if (flowId != null) {
            return flowRepository.findById(flowId)
                    .orElseThrow(() -> new RuntimeException("Flow not found"));
        }
        if (contract.getFlow() != null) {
            return contract.getFlow();
        }
        return Optional.ofNullable(contract.getTemplate().getDefaultFlow())
                .orElseThrow(() -> new RuntimeException("No approval flow available"));
    }

    private List<ContractApproval> copyFlowToContractApproval(Contract contract, ApprovalFlow flow) {
        return flow.getSteps().stream()
                .map(step -> ContractApproval.builder()
                        .contract(contract)
                        .step(step)
                        .stepOrder(step.getStepOrder())
                        .required(step.getRequired())
                        .isFinalStep(step.getIsFinalStep())
                        .department(step.getApproverType() == ApproverType.POSITION ? step.getDepartment() : null)
                        .position(step.getApproverType() == ApproverType.POSITION ? step.getPosition() : null)
                        .isCurrent(step.getStepOrder() == 1)
                        .status(ApprovalStatus.PENDING)
                        .signaturePlaceholder(step.getSignaturePlaceholder())
                        .build())
                .toList();
    }

    private void ensureContractFileExists(Contract contract) {
        try {
            boolean fileExists = contract.getFilePath() != null &&
                    Files.exists(Path.of(contract.getFilePath()));

            if (!fileExists) {
                File pdfFile = contractFileService.getPdfOrConvert(contract.getId());
                contract.setFilePath(pdfFile.getAbsolutePath());
                contractRepository.save(contract);
                log.info("Generated/ensured contract PDF: {}", contract.getFilePath());
            } else {
                log.info("Contract file already exists: {}", contract.getFilePath());
            }
        } catch (Exception e) {
            log.error("CRITICAL: Could not create/ensure contract file for contract {}: {}",
                    contract.getId(), e.getMessage());
            throw new RuntimeException("Could not create contract file: " + e.getMessage(), e);
        }
    }

    // ---------------------------------------------------------------------
    // Signing & approval steps
    // ---------------------------------------------------------------------
    @Transactional
    @Override
    public ContractResponse signStep(Long contractId, Long stepId, SignStepRequest req) {
        ContractApproval approval = contractApprovalRepository.findById(stepId)
                .orElseThrow(() -> new RuntimeException("Approval step not found"));

        Contract contract = approval.getContract();
        if (!contract.getId().equals(contractId)) throw new RuntimeException("Step không thuộc hợp đồng này");
        if (!Boolean.TRUE.equals(approval.getIsCurrent())) throw new RuntimeException("Step chưa đến lượt ký");

        ApprovalAction action = approval.getStep().getAction();
        if (action == ApprovalAction.APPROVE_ONLY)
            throw new RuntimeException("Bước này chỉ phê duyệt, không yêu cầu ký.");

        // Người thực hiện + kiểm tra quyền
        String email = securityUtils.getCurrentUserEmail();
        Employee me = userRepository.findByAccount_Email(email)
                .orElseThrow(() -> new RuntimeException("Employee not found: " + email));
        validateApprovalPermission(approval.getStep(), me);

        // Ảnh chữ ký & tên in
        String signatureUrl = Optional.ofNullable(req.getSignatureImage())
                .filter(s -> !s.isBlank())
                .orElse(me.getSignatureImage());
        if (signatureUrl == null || signatureUrl.isBlank())
            throw new RuntimeException("Bạn chưa có chữ ký số. Vui lòng upload chữ ký trước.");

        String printedName = Optional.ofNullable(me.getFullName())
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .orElse(me.getAccount().getEmail());

        // ========= 1) CẬP NHẬT DB TRƯỚC KHI RENDER =========
        LocalDateTime now = LocalDateTime.now();
        approval.setApprover(me);
        if (approval.getDepartment() == null && me.getDepartment() != null) {
            approval.setDepartment(me.getDepartment());
        }
        approval.setUpdatedAt(now);

        boolean justApprovedBySignOnly = false;
        if (action == ApprovalAction.SIGN_ONLY) {
            // Hoàn tất step ngay ở bước ký
            approval.setStatus(ApprovalStatus.APPROVED);
            approval.setApprovedAt(now);
            approval.setIsCurrent(false);
            approval.setComment(req.getComment());
            justApprovedBySignOnly = true;
        }
        // GHI & FLUSH để trang nhật ký đọc thấy dữ liệu mới
        contractApprovalRepository.saveAndFlush(approval);

        // ========= 2) CHÈN CHỮ KÝ & CẬP NHẬT TRANG NHẬT KÝ =========
        String updatedPath;
        String snapshotKey;
        try {
            // ưu tiên ký theo tên in
            updatedPath = contractFileService.embedSignatureByName(contract.getId(), signatureUrl, printedName);
            snapshotKey = "PRINTED_NAME:" + printedName;
        } catch (RuntimeException ex) {
            // fallback: ký theo placeholder của step
            String ph = approval.getSignaturePlaceholder();
            if (ph == null || ph.isBlank())
                throw new RuntimeException("Không tìm thấy vị trí ký: không khớp tên in và step không có placeholder.");
            updatedPath = contractFileService.embedSignatureForApproval(contract.getId(), signatureUrl, approval.getId());
            snapshotKey = ph;
        }

        if (updatedPath != null) {
            contract.setFilePath(updatedPath);
            contractRepository.save(contract);
        }

        // ========= 3) LƯU SNAPSHOT CHỮ KÝ =========
        ContractSignature sig = new ContractSignature();
        sig.setContract(contract);
        sig.setSigner(me);
        sig.setApprovalStep(approval);
        sig.setSignedAt(now);
        sig.setSignatureImage(signatureUrl);
        sig.setPlaceholderKey(snapshotKey);
        sig.setType(SignatureType.EMPLOYEE);
        contractSignatureRepository.save(sig);

        // ========= 4) CHUYỂN BƯỚC & BẮN EVENT (chỉ với SIGN_ONLY) =========
        if (action == ApprovalAction.SIGN_ONLY) {
            if (Boolean.TRUE.equals(approval.getIsFinalStep())) {
                contract.setStatus(ContractStatus.APPROVED);
                contractRepository.save(contract);
                //  Bắn event APPROVED (listener sẽ gửi mail sau commit)
                events.publishEvent(new ContractApprovalEvent(approval.getId(), contract.getId(), ApprovalStatus.APPROVED));
                return ContractMapper.toResponse(contract);
            }
            moveToNextStep(contract, approval.getStepOrder());
            if (justApprovedBySignOnly) {
                //  Bắn event APPROVED cho bước ký vừa hoàn tất
                events.publishEvent(new ContractApprovalEvent(approval.getId(), contract.getId(), ApprovalStatus.APPROVED));
            }
        }

        return ContractMapper.toResponse(contract);
    }

    @Transactional
    @Override
    public ContractResponse approveStep(Long stepId, StepApprovalRequest request) {
        return processStep(stepId, request, true);
    }

    @Transactional
    @Override
    public ContractResponse rejectStep(Long stepId, StepApprovalRequest request) {
        return processStep(stepId, request, false);
    }

    private ContractResponse processStep(Long stepId, StepApprovalRequest request, boolean approved) {
        ContractApproval approval = contractApprovalRepository.findById(stepId)
                .orElseThrow(() -> new RuntimeException("Approval step not found"));

        if (!Boolean.TRUE.equals(approval.getIsCurrent())) {
            throw new RuntimeException("This step is not active for approval");
        }

        String email = securityUtils.getCurrentUserEmail();
        Employee me = userRepository.findByAccount_Email(email)
                .orElseThrow(() -> new RuntimeException("Employee not found for email: " + email));

        validateApprovalPermission(approval.getStep(), me);

        approval.setApprover(me);
        approval.setApprovedAt(LocalDateTime.now());
        approval.setComment(request.getComment());
        approval.setIsCurrent(false);
        approval.setStatus(approved ? ApprovalStatus.APPROVED : ApprovalStatus.REJECTED);
        contractApprovalRepository.save(approval);

        Contract contract = approval.getContract();

        if (!approved) {
            contract.setStatus(ContractStatus.REJECTED);
            contractRepository.save(contract);
            // ✅ Bắn event REJECTED
            events.publishEvent(new ContractApprovalEvent(approval.getId(), contract.getId(), ApprovalStatus.REJECTED));
            return ContractMapper.toResponse(contract);
        }

        // APPROVED nhánh dưới:
        ApprovalAction action = approval.getStep().getAction();
        if (action == ApprovalAction.APPROVE_ONLY || action == ApprovalAction.SIGN_THEN_APPROVE) {
            String approveText = String.format(
                    "Đã phê duyệt bởi: %s - %s - %s",
                    me.getFullName() != null ? me.getFullName() : me.getAccount().getEmail(),
                    me.getPhone() != null ? me.getPhone() : "Chưa cập nhật SĐT",
                    LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
            );
            contractFileService.addApprovalText(contract.getFilePath(), approveText);
        }

        if (Boolean.TRUE.equals(approval.getIsFinalStep())) {
            contract.setStatus(ContractStatus.APPROVED);
            contractRepository.save(contract);
            // ✅ Bắn event APPROVED (hợp đồng hoàn tất)
            events.publishEvent(new ContractApprovalEvent(approval.getId(), contract.getId(), ApprovalStatus.APPROVED));
            return ContractMapper.toResponse(contract);
        }

        // ✅ Bắn event APPROVED cho bước vừa duyệt xong (không phải final)
        events.publishEvent(new ContractApprovalEvent(approval.getId(), contract.getId(), ApprovalStatus.APPROVED));

        moveToNextStep(contract, approval.getStepOrder());
        return ContractMapper.toResponse(contract);
    }

    private void validateApprovalPermission(ApprovalStep step, Employee employee) {
        if (step.getApproverType() == null) {
            throw new RuntimeException("Step approverType is not set");
        }
        switch (step.getApproverType()) {
            case USER -> {
                if (step.getEmployee() == null || !step.getEmployee().getId().equals(employee.getId())) {
                    throw new RuntimeException("Bạn không phải người được chỉ định duyệt/ký bước này");
                }
            }
            case POSITION -> {
                if (step.getDepartment() == null || step.getPosition() == null) {
                    throw new RuntimeException("Step thiếu department/position yêu cầu");
                }
                if (employee.getDepartment() == null || employee.getPosition() == null
                        || !step.getDepartment().getId().equals(employee.getDepartment().getId())
                        || !step.getPosition().getId().equals(employee.getPosition().getId())) {
                    throw new RuntimeException("Bạn không đúng vị trí/phòng ban yêu cầu để duyệt/ký bước này");
                }
            }
            default -> throw new RuntimeException("approverType không hỗ trợ");
        }
    }

    private void completeApprovalStep(ContractApproval approval, Employee approver, String comment) {
        approval.setApprover(approver);
        approval.setApprovedAt(LocalDateTime.now());
        approval.setComment(comment);
        approval.setIsCurrent(false);
        approval.setStatus(ApprovalStatus.APPROVED);
        contractApprovalRepository.save(approval);
    }

    private void moveToNextStep(Contract contract, Integer currentStepOrder) {
        contractApprovalRepository.findByContractIdAndStepOrder(
                contract.getId(), currentStepOrder + 1
        ).ifPresentOrElse(next -> {
            next.setIsCurrent(true);
            contractApprovalRepository.save(next);
            contract.setStatus(ContractStatus.PENDING_APPROVAL);
            contractRepository.save(contract);
        }, () -> {
            throw new RuntimeException("Next step not found");
        });
    }

    // ---------------------------------------------------------------------
    // Queries
    // ---------------------------------------------------------------------
    @Override
    public List<ContractResponse> getMyHandledContracts(ContractStatus status) {
        String email = securityUtils.getCurrentUserEmail();
        Employee me = userRepository.findByAccount_Email(email)
                .orElseThrow(() -> new RuntimeException("Employee not found"));

        List<Contract> contracts = contractApprovalRepository
                .findAllByApproverIdAndContract_Status(me.getId(), status);

        return contracts.stream()
                .map(ContractMapper::toResponse)
                .toList();
    }

    @Override
    public List<ContractResponse> getMyPendingContracts() {
        String email = securityUtils.getCurrentUserEmail();
        Employee me = userRepository.findByAccount_Email(email)
                .orElseThrow(() -> new RuntimeException("Employee not found"));

        List<ContractApproval> approvals = contractApprovalRepository
                .findAllByIsCurrentTrueAndStatusAndContract_Status(
                        ApprovalStatus.PENDING,
                        ContractStatus.PENDING_APPROVAL
                );

        return approvals.stream()
                .filter(a -> {
                    var step = a.getStep();
                    return switch (step.getApproverType()) {
                        case USER -> step.getEmployee() != null && step.getEmployee().getId().equals(me.getId());
                        case POSITION -> step.getDepartment() != null && step.getPosition() != null
                                && me.getDepartment() != null && me.getPosition() != null
                                && step.getDepartment().getId().equals(me.getDepartment().getId())
                                && step.getPosition().getId().equals(me.getPosition().getId());
                    };
                })
                .map(a -> {
                    ContractResponse dto = ContractMapper.toResponse(a.getContract());
                    dto.setCurrentStepId(a.getId());
                    dto.setCurrentStepName(buildCurrentStepName(a.getStep()));
                    dto.setCurrentStepAction(a.getStep().getAction().name());
                    dto.setCurrentStepSignaturePlaceholder(a.getSignaturePlaceholder());
                    return dto;
                })
                .toList();
    }

    private String buildCurrentStepName(ApprovalStep step) {
        if (step == null || step.getApproverType() == null) return "Bước hiện tại";
        return switch (step.getApproverType()) {
            case USER -> {
                var emp = step.getEmployee();
                yield emp != null
                        ? ("Người duyệt: " + (emp.getFullName() != null ? emp.getFullName() : emp.getAccount().getEmail()))
                        : "Người duyệt (chưa gán)";
            }
            case POSITION -> {
                String dept = step.getDepartment() != null ? step.getDepartment().getName() : "Phòng/ban?";
                String pos  = step.getPosition()   != null ? step.getPosition().getName()   : "Chức vụ?";
                yield "Vị trí: " + dept + " - " + pos;
            }
        };
    }

    @Override
    public ContractResponse getApprovalProgress(Long contractId) {
        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new RuntimeException("Contract not found"));

        ContractResponse dto = ContractMapper.toResponse(contract);

        contractApprovalRepository.findByContractIdAndIsCurrentTrue(contractId)
                .ifPresent(a -> {
                    dto.setCurrentStepId(a.getId());
                    dto.setCurrentStepName(buildCurrentStepName(a.getStep()));
                    dto.setCurrentStepSignaturePlaceholder(a.getSignaturePlaceholder());
                });

        return dto;
    }

    @Override
    public ContractResponse getApprovalProgressOrPreview(Long contractId, Long flowId) {
        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new RuntimeException("Contract not found"));

        List<ContractApproval> approvals =
                contractApprovalRepository.findAllByContractIdOrderByStepOrderAsc(contractId);

        ContractResponse dto = ContractMapper.toResponse(contract);

        if (!approvals.isEmpty()) {
            dto.setHasFlow(true);
            dto.setFlowSource("CONTRACT");
            dto.setFlowId(approvals.get(0).getStep().getFlow().getId());
            dto.setFlowName(approvals.get(0).getStep().getFlow().getName());

            List<ApprovalStepResponse> steps = approvals.stream().map(a -> {
                ApprovalStep s = a.getStep();
                ApprovalStepResponse r = ApprovalStepResponse.builder()
                        .id(a.getId())
                        .stepOrder(a.getStepOrder())
                        .required(a.getRequired())
                        .approverType(s.getApproverType())
                        .isFinalStep(a.getIsFinalStep())
                        .employeeId(s.getEmployee() != null ? s.getEmployee().getId() : null)
                        .employeeName(s.getEmployee() != null
                                ? (s.getEmployee().getFullName() != null ? s.getEmployee().getFullName()
                                : s.getEmployee().getAccount().getEmail())
                                : null)
                        .positionId(s.getPosition() != null ? s.getPosition().getId() : null)
                        .positionName(s.getPosition() != null ? s.getPosition().getName() : null)
                        .departmentId(s.getDepartment() != null ? s.getDepartment().getId() : null)
                        .departmentName(s.getDepartment() != null ? s.getDepartment().getName() : null)
                        .action(s.getAction())
                        .signaturePlaceholder(a.getSignaturePlaceholder())
                        .status(a.getStatus())
                        .isCurrent(a.getIsCurrent())
                        .decidedBy(a.getApprover() != null
                                ? (a.getApprover().getFullName() != null ? a.getApprover().getFullName()
                                : a.getApprover().getAccount().getEmail())
                                : null)
                        .decidedAt(a.getApprovedAt() != null ? a.getApprovedAt().toString() : null)
                        .build();
                return r;
            }).toList();

            dto.setSteps(steps);

            contractApprovalRepository.findByContractIdAndIsCurrentTrue(contractId)
                    .ifPresent(a -> {
                        dto.setCurrentStepId(a.getId());
                        dto.setCurrentStepName(buildCurrentStepName(a.getStep()));
                        dto.setCurrentStepAction(a.getStep().getAction().name());
                        dto.setCurrentStepSignaturePlaceholder(a.getSignaturePlaceholder());
                    });
            return dto;
        }

        // Preview flow
        ApprovalFlow flow;
        if (flowId != null) {
            flow = flowRepository.findById(flowId)
                    .orElseThrow(() -> new RuntimeException("Flow not found"));
            if (!flow.getTemplate().getId().equals(contract.getTemplate().getId())) {
                throw new RuntimeException("Flow không thuộc template của hợp đồng");
            }
            dto.setFlowSource("SELECTED");
        } else {
            flow = Optional.ofNullable(contract.getTemplate().getDefaultFlow())
                    .orElseThrow(() -> new RuntimeException("Template chưa có flow mặc định"));
            dto.setFlowSource("TEMPLATE_DEFAULT");
        }

        dto.setHasFlow(false);
        dto.setFlowId(flow.getId());
        dto.setFlowName(flow.getName());

        List<ApprovalStepResponse> previewSteps = flow.getSteps().stream()
                .sorted(Comparator.comparingInt(ApprovalStep::getStepOrder))
                .map(s -> ApprovalStepResponse.builder()
                        .id(s.getId())
                        .stepOrder(s.getStepOrder())
                        .required(s.getRequired())
                        .approverType(s.getApproverType())
                        .isFinalStep(s.getIsFinalStep())
                        .employeeId(s.getEmployee() != null ? s.getEmployee().getId() : null)
                        .employeeName(s.getEmployee() != null
                                ? (s.getEmployee().getFullName() != null ? s.getEmployee().getFullName()
                                : s.getEmployee().getAccount().getEmail())
                                : null)
                        .positionId(s.getPosition() != null ? s.getPosition().getId() : null)
                        .positionName(s.getPosition() != null ? s.getPosition().getName() : null)
                        .departmentId(s.getDepartment() != null ? s.getDepartment().getId() : null)
                        .departmentName(s.getDepartment() != null ? s.getDepartment().getName() : null)
                        .action(s.getAction())
                        .signaturePlaceholder(s.getSignaturePlaceholder())
                        .build()
                ).toList();

        dto.setSteps(previewSteps);
        return dto;
    }

    @Override
    public String getEmployeeSignature(Long employeeId) {
        Employee employee = userRepository.findById(employeeId)
                .orElseThrow(() -> new RuntimeException("Employee not found"));

        if (employee.getSignatureImage() == null || employee.getSignatureImage().isEmpty()) {
            throw new RuntimeException("Employee does not have a signature");
        }
        return employee.getSignatureImage();
    }
}
