package com.hieunguyen.ManageContract.service.impl;

import com.hieunguyen.ManageContract.common.constants.*;
import com.hieunguyen.ManageContract.dto.approval.StepApprovalRequest;
import com.hieunguyen.ManageContract.dto.contract.ContractResponse;
import com.hieunguyen.ManageContract.dto.contractSign.SignStepRequest;
import com.hieunguyen.ManageContract.entity.*;
import com.hieunguyen.ManageContract.mapper.ContractMapper;
import com.hieunguyen.ManageContract.repository.*;
import com.hieunguyen.ManageContract.security.jwt.SecurityUtil;
import com.hieunguyen.ManageContract.service.ContractApprovalService;
import com.hieunguyen.ManageContract.service.ContractFileService;
import com.hieunguyen.ManageContract.service.SignatureStorageService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ContractApprovalServiceImpl implements ContractApprovalService {

    private final ContractRepository contractRepository;
    private final UserRepository userRepository; // repo của Employee
    private final ApprovalFlowRepository flowRepository;
    private final ContractApprovalRepository contractApprovalRepository;
    private final ContractSignatureRepository contractSignatureRepository;
    private final ContractFileService contractFileService;
    private final SignatureStorageService signatureStorage;
    private final SecurityUtil securityUtils;

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

        // Chọn flow: ưu tiên tham số flowId, nếu không có thì lấy theo template
        ApprovalFlow flow;
        if (flowId != null) {
            flow = flowRepository.findById(flowId)
                    .orElseThrow(() -> new RuntimeException("Flow not found"));
        } else {
            // Lấy flow mặc định của template
            flow = Optional.ofNullable(contract.getTemplate().getDefaultFlow())
                    .orElseThrow(() -> new RuntimeException("No default approval flow set for this template"));
        }

        if (flow.getSteps() == null || flow.getSteps().isEmpty()) {
            throw new RuntimeException("Selected flow has no steps");
        }

        // Snapshot step sang ContractApproval
        List<ContractApproval> approvals = flow.getSteps().stream()
                .map(step -> ContractApproval.builder()
                        .contract(contract)
                        .step(step)
                        .stepOrder(step.getStepOrder())
                        .required(step.getRequired())
                        .isFinalStep(step.getIsFinalStep())
                        // chỉ snapshot department/position nếu step là POSITION
                        .department(step.getApproverType() == ApproverType.POSITION ? step.getDepartment() : null)
                        .position(step.getApproverType() == ApproverType.POSITION ? step.getPosition() : null)
                        .isCurrent(step.getStepOrder() == 1)
                        .status(ApprovalStatus.PENDING)
                        .build())
                .toList();

        contractApprovalRepository.saveAll(approvals);

        contract.setFilePath(contractFileService.generateContractFile(contract));
        contract.setStatus(ContractStatus.PENDING_APPROVAL);

        return ContractMapper.toResponse(contractRepository.save(contract));
    }
    @Transactional
    @Override
    public ContractResponse signStep(Long contractId, Long stepId, SignStepRequest req) {
        ContractApproval approval = contractApprovalRepository.findById(stepId)
                .orElseThrow(() -> new RuntimeException("Approval step not found"));

        Contract contract = approval.getContract();
        if (!contract.getId().equals(contractId)) {
            throw new RuntimeException("Step không thuộc hợp đồng này");
        }
        if (!Boolean.TRUE.equals(approval.getIsCurrent())) {
            throw new RuntimeException("Step chưa đến lượt ký");
        }

        // Chỉ cho phép ký khi step có yêu cầu ký
        ApprovalAction action = approval.getStep().getAction();
        if (action == ApprovalAction.APPROVE_ONLY) {
            throw new RuntimeException("Bước này chỉ phê duyệt, không yêu cầu ký.");
        }

        // 1) Lấy nhân sự hiện tại
        String email = securityUtils.getCurrentUserEmail();
        Employee me = userRepository.findByAccount_Email(email)
                .orElseThrow(() -> new RuntimeException("Employee not found: " + email));

        // 2) Kiểm tra quyền theo approverType (giữ nguyên như bạn đã làm)
        switch (approval.getStep().getApproverType()) {
            case USER -> {
                if (approval.getStep().getEmployee() == null
                        || !approval.getStep().getEmployee().getId().equals(me.getId())) {
                    throw new RuntimeException("Bạn không phải người được chỉ định ký bước này");
                }
            }
            case POSITION -> {
                var s = approval.getStep();
                if (s.getDepartment() == null || s.getPosition() == null
                        || me.getDepartment() == null || me.getPosition() == null
                        || !s.getDepartment().getId().equals(me.getDepartment().getId())
                        || !s.getPosition().getId().equals(me.getPosition().getId())) {
                    throw new RuntimeException("Bạn không đúng vị trí/phòng ban để ký bước này");
                }
            }
        }

        // 3) Lưu ảnh chữ ký → URL
        String signatureUrl = signatureStorage.saveBase64Png(contract.getId(), me.getId(), req.getImageBase64());

        // 4) Lưu snapshot chữ ký
        ContractSignature signature = new ContractSignature();
        signature.setContract(contract);
        signature.setSigner(me);
        signature.setApprovalStep(approval);
        signature.setSignedAt(LocalDateTime.now());
        signature.setSignatureImage(signatureUrl);
        signature.setPlaceholderKey(req.getPlaceholder());
        signature.setPage(req.getPage());
        signature.setX(req.getX());
        signature.setY(req.getY());
        signature.setWidth(req.getWidth());
        signature.setHeight(req.getHeight());
        signature.setType(SignatureType.DRAWN); // hoặc IMAGE
        contractSignatureRepository.save(signature);

        // 5) Chèn chữ ký vào file
        String updatedPath = contractFileService.embedSignatureFromUrl(
                contract.getFilePath(),
                signatureUrl,
                req.getPage(), req.getX(), req.getY(), req.getWidth(), req.getHeight(),
                req.getPlaceholder() // có thể null
        );
        if (updatedPath != null) {
            contract.setFilePath(updatedPath);
            contractRepository.save(contract);
        }

        // 6) Hoàn tất bước tuỳ theo action
        if (action == ApprovalAction.SIGN_ONLY) {
            // ký xong là xong bước → auto-approve
            approval.setApprover(me);
            approval.setApprovedAt(LocalDateTime.now());
            approval.setComment(req.getComment());
            approval.setIsCurrent(false);
            approval.setStatus(ApprovalStatus.APPROVED);
            contractApprovalRepository.save(approval);

            if (Boolean.TRUE.equals(approval.getIsFinalStep())) {
                contract.setStatus(ContractStatus.APPROVED);
                contractRepository.save(contract);
                return ContractMapper.toResponse(contract);
            }

            contractApprovalRepository.findByContractIdAndStepOrder(
                    contract.getId(), approval.getStepOrder() + 1
            ).ifPresentOrElse(next -> {
                next.setIsCurrent(true);
                contractApprovalRepository.save(next);
                contract.setStatus(ContractStatus.PENDING_APPROVAL);
                contractRepository.save(contract);
            }, () -> { throw new RuntimeException("Next step not found"); });

            return ContractMapper.toResponse(contract);
        }

        // SIGN_THEN_APPROVE → chưa duyệt, vẫn giữ isCurrent=true
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
                    dto.setCurrentStepId(a.getId()); // đây chính là stepId
                    dto.setCurrentStepName(buildCurrentStepName(a.getStep()));
                    dto.setCurrentStepAction(a.getStep().getAction().name());
                    dto.setCurrentStepSignaturePlaceholder(a.getStep().getSignaturePlaceholder());
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



    private ContractResponse processStep(Long stepId, StepApprovalRequest request, boolean approved) {
        ContractApproval approval = contractApprovalRepository.findById(stepId)
                .orElseThrow(() -> new RuntimeException("Approval step not found"));

        if (!Boolean.TRUE.equals(approval.getIsCurrent())) {
            throw new RuntimeException("This step is not active for approval");
        }

        // Lấy Employee hiện tại theo email
        String email = securityUtils.getCurrentUserEmail();
        Employee me = userRepository.findByAccount_Email(email)
                .orElseThrow(() -> new RuntimeException("Employee not found for email: " + email));

        // ---- KIỂM TRA QUYỀN THEO LOẠI APPROVER ----
        ApprovalStep step = approval.getStep();
        if (step.getApproverType() == null) {
            throw new RuntimeException("Step approverType is not set");
        }
        switch (step.getApproverType()) {
            case USER -> {
                if (step.getEmployee() == null || !step.getEmployee().getId().equals(me.getId())) {
                    throw new RuntimeException("Bạn không phải người được chỉ định duyệt bước này");
                }
            }
            case POSITION -> {
                if (step.getDepartment() == null || step.getPosition() == null) {
                    throw new RuntimeException("Step thiếu department/position yêu cầu");
                }
                if (me.getDepartment() == null || me.getPosition() == null
                        || !step.getDepartment().getId().equals(me.getDepartment().getId())
                        || !step.getPosition().getId().equals(me.getPosition().getId())) {
                    throw new RuntimeException("Bạn không đúng vị trí/phòng ban yêu cầu để duyệt bước này");
                }
            }
            default -> throw new RuntimeException("approverType không hỗ trợ");
        }

        // ---- CẬP NHẬT KẾT QUẢ DUYỆT ----
        approval.setApprover(me); // ai thực sự đã duyệt
        approval.setApprovedAt(LocalDateTime.now());
        approval.setComment(request.getComment());
        approval.setIsCurrent(false);
        approval.setStatus(approved ? ApprovalStatus.APPROVED : ApprovalStatus.REJECTED);
        contractApprovalRepository.save(approval);

        Contract contract = approval.getContract();

        if (!approved) {
            contract.setStatus(ContractStatus.REJECTED);
            contractRepository.save(contract);
            return ContractMapper.toResponse(contract);
        }
        if (step.getAction() == ApprovalAction.APPROVE_ONLY) {
            String stampText = String.format(
                    "APPROVED by %s (%s) - %s",
                    me.getFullName() != null ? me.getFullName() : me.getAccount().getEmail(),
                    me.getPosition() != null ? me.getPosition().getName() : "N/A",
                    java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").format(LocalDateTime.now())
            );

            // Ưu tiên placeholder nếu có, ví dụ {{APPROVAL_STAMP}} hoặc {{SIGN:APPROVAL_STAMP}}
            String ph = step.getSignaturePlaceholder() != null && !step.getSignaturePlaceholder().isBlank()
                    ? step.getSignaturePlaceholder()
                    : "APPROVAL_STAMP";

            // Không cần toạ độ. Nếu không có placeholder trong file, method sẽ append xuống cuối.
            contractFileService.embedSignatureText(
                    contract.getFilePath(),
                    stampText,
                    null, null, null,   // page/x/y: không dùng
                    10, false,          // font size, bold?
                    ph
            );
        }

        if (Boolean.TRUE.equals(approval.getIsFinalStep())) {
            contract.setStatus(ContractStatus.APPROVED);
            contractRepository.save(contract);
            return ContractMapper.toResponse(contract);
        }

        contractApprovalRepository.findByContractIdAndStepOrder(
                contract.getId(), approval.getStepOrder() + 1
        ).ifPresentOrElse(next -> {
            next.setIsCurrent(true);
            contractApprovalRepository.save(next);
            contract.setStatus(ContractStatus.PENDING_APPROVAL);
            contractRepository.save(contract);
        }, () -> { throw new RuntimeException("Next step not found"); });

        return ContractMapper.toResponse(contract);
    }

    @Override
    public ContractResponse getApprovalProgress(Long contractId) {
        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new RuntimeException("Contract not found"));

        ContractResponse dto = ContractMapper.toResponse(contract);

        // tìm step hiện tại (nếu có) để set hiển thị
        contractApprovalRepository.findByContractIdAndIsCurrentTrue(contractId)
                .ifPresent(a -> {
                    dto.setCurrentStepId(a.getId());
                    dto.setCurrentStepName(buildCurrentStepName(a.getStep()));
                });

        return dto;
    }


}
