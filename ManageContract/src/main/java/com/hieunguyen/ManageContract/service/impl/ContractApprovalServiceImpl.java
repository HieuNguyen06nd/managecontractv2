package com.hieunguyen.ManageContract.service.impl;

import com.hieunguyen.ManageContract.common.constants.ApprovalStatus;
import com.hieunguyen.ManageContract.common.constants.ContractStatus;
import com.hieunguyen.ManageContract.dto.contract.ContractResponse;
import com.hieunguyen.ManageContract.entity.ApprovalFlow;
import com.hieunguyen.ManageContract.entity.Contract;
import com.hieunguyen.ManageContract.entity.ContractApproval;
import com.hieunguyen.ManageContract.entity.User;
import com.hieunguyen.ManageContract.mapper.ContractMapper;
import com.hieunguyen.ManageContract.repository.ApprovalFlowRepository;
import com.hieunguyen.ManageContract.repository.ContractApprovalRepository;
import com.hieunguyen.ManageContract.repository.ContractRepository;
import com.hieunguyen.ManageContract.repository.UserRepository;
import com.hieunguyen.ManageContract.service.ContractApprovalService;
import com.hieunguyen.ManageContract.service.ContractFileService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ContractApprovalServiceImpl implements ContractApprovalService {
    private final ContractRepository contractRepository;
    private final UserRepository userRepository;
    private final ApprovalFlowRepository flowRepository;
    private final ContractApprovalRepository contractApprovalRepository;
    private final ContractFileService contractFileService;

    @Transactional
    @Override
    public ContractResponse submitForApproval(Long contractId, Long flowId) {
        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new RuntimeException("Contract not found"));

        if (contract.getStatus() != ContractStatus.DRAFT) {
            throw new RuntimeException("Only draft contracts can be submitted");
        }


        ApprovalFlow flow = (flowId != null)
                ? flowRepository.findById(flowId).orElseThrow(() -> new RuntimeException("Flow not found"))
                : flowRepository.findByTemplateId(contract.getTemplate().getId())
                .orElseThrow(() -> new RuntimeException("No approval flow defined"));

        // Copy steps từ flow sang contract_approvals
        List<ContractApproval> approvals = flow.getSteps().stream().map(step -> ContractApproval.builder()
                        .contract(contract)
                        .step(step)
                        .stepOrder(step.getStepOrder())
                        .required(step.getRequired())
                        .isFinalStep(step.getIsFinalStep())
                        .role(step.getRole())
                        .department(step.getDepartment())
                        .isCurrent(step.getStepOrder() == 1) // step đầu tiên active
                        .status(ApprovalStatus.PENDING)
                        .build())
                .toList();

        contractApprovalRepository.saveAll(approvals);

        // Generate file Word đã thay biến
        String filePath = contractFileService.generateContractFile(contract);
        contract.setFilePath(filePath);

        // Update trạng thái
        contract.setStatus(ContractStatus.PENDING_APPROVAL);
        Contract updated = contractRepository.save(contract);

        return ContractMapper.toResponse(updated);
    }


    @Transactional
    @Override
    public ContractResponse approveStep(Long contractId, Long stepId, Long approverId, boolean approved, String comment) {
        ContractApproval approval = contractApprovalRepository.findByContractIdAndStepId(contractId, stepId)
                .orElseThrow(() -> new RuntimeException("Step not found for contract"));

        if (!Boolean.TRUE.equals(approval.getIsCurrent())) {
            throw new RuntimeException("This step is not active");
        }

        User approver = userRepository.findById(approverId)
                .orElseThrow(() -> new RuntimeException("Approver not found"));

        approval.setStatus(approved ? ApprovalStatus.APPROVED : ApprovalStatus.REJECTED);
        approval.setApprovedAt(LocalDateTime.now());
        approval.setApprover(approver);
        approval.setComment(comment);
        approval.setIsCurrent(false);

        contractApprovalRepository.save(approval);

        Contract contract = approval.getContract();

        if (!approved) {
            contract.setStatus(ContractStatus.REJECTED);
            contractRepository.save(contract);
            return ContractMapper.toResponse(contract);
        }

        // Nếu là step cuối cùng
        if (Boolean.TRUE.equals(approval.getIsFinalStep())) {
            contract.setStatus(ContractStatus.APPROVED);
            contractRepository.save(contract);
        } else {
            // Kích hoạt step kế tiếp
            ContractApproval nextStep = contractApprovalRepository.findByContractIdAndStepOrder(contractId, approval.getStepOrder() + 1)
                    .orElseThrow(() -> new RuntimeException("Next step not found"));
            nextStep.setIsCurrent(true);
            contractApprovalRepository.save(nextStep);

            contract.setStatus(ContractStatus.PENDING_APPROVAL);
            contractRepository.save(contract);
        }

        return ContractMapper.toResponse(contract);
    }


    @Override
    public ContractResponse getApprovalProgress(Long contractId) {
        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new RuntimeException("Contract not found"));
        return ContractMapper.toResponse(contract);
    }
}
