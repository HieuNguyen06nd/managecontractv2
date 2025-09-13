package com.hieunguyen.ManageContract.service.impl;

import com.hieunguyen.ManageContract.common.constants.ApprovalStatus;
import com.hieunguyen.ManageContract.common.constants.ContractStatus;
import com.hieunguyen.ManageContract.dto.contract.ContractResponse;
import com.hieunguyen.ManageContract.dto.contract.CreateContractRequest;
import com.hieunguyen.ManageContract.entity.*;
import com.hieunguyen.ManageContract.mapper.ContractMapper;
import com.hieunguyen.ManageContract.repository.*;
import com.hieunguyen.ManageContract.service.ContractFileService;
import com.hieunguyen.ManageContract.service.ContractService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ContractServiceImpl implements ContractService {
    private final ContractRepository contractRepository;
    private final ContractVariableValueRepository variableValueRepository;
    private final ContractTemplateRepository templateRepository;
    private final ContractFileService contractFileService;
    private final ApprovalFlowRepository flowRepository;
    private final ContractApprovalRepository contractApprovalRepository;

    @Transactional
    @Override
    public ContractResponse createContract(CreateContractRequest request, User createdBy) {
        ContractTemplate template = templateRepository.findById(request.getTemplateId())
                .orElseThrow(() -> new RuntimeException("Template not found"));

        Contract contract = new Contract();
        contract.setTitle(request.getTitle());
        contract.setContractNumber("HD-" + System.currentTimeMillis());
        contract.setTemplate(template);
        contract.setCreatedBy(createdBy);
        contract.setStatus(ContractStatus.DRAFT);

        Contract saved = contractRepository.save(contract);

        // Lưu biến hợp đồng
        List<ContractVariableValue> values = request.getVariables().stream()
                .map(v -> {
                    ContractVariableValue cv = new ContractVariableValue();
                    cv.setContract(saved);
                    cv.setVarName(v.getVarName());
                    cv.setVarValue(v.getVarValue());
                    return cv;
                })
                .toList();

        variableValueRepository.saveAll(values);

        // Gắn lại list values để mapper convert sang DTO đầy đủ
        saved.setVariableValues(values);

        // Gắn flow (mặc định hoặc override)
        ApprovalFlow flowToUse;
        if (template.getAllowOverrideFlow() && request.getFlowId() != null) {
            flowToUse = flowRepository.findById(request.getFlowId())
                    .orElseThrow(() -> new RuntimeException("Flow không tồn tại"));
        } else if (template.getDefaultFlow() != null) {
            flowToUse = template.getDefaultFlow();
        } else {
            flowToUse = null; // không có flow áp dụng
        }

        if (flowToUse != null) {
            // Copy steps sang ContractApproval
            List<ContractApproval> approvals = flowToUse.getSteps().stream()
                    .map(step -> ContractApproval.builder()
                            .contract(saved)
                            .step(step)
                            .stepOrder(step.getStepOrder())
                            .required(step.getRequired())
                            .isFinalStep(step.getIsFinalStep())
                            .department(step.getDepartment())
                            .position(step.getPosition())
                            .isCurrent(step.getStepOrder() == 1)
                            .status(step.getStepOrder() == 1 ? ApprovalStatus.PENDING : ApprovalStatus.PENDING)
                            .build())
                    .toList();

            contractApprovalRepository.saveAll(approvals);
        }

        // Dùng mapper để trả ra DTO
        return ContractMapper.toResponse(saved);
    }

}
