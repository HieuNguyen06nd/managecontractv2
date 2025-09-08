package com.hieunguyen.ManageContract.service.impl;

import com.hieunguyen.ManageContract.common.constants.ContractStatus;
import com.hieunguyen.ManageContract.dto.contract.ContractResponse;
import com.hieunguyen.ManageContract.dto.contract.CreateContractRequest;
import com.hieunguyen.ManageContract.entity.AuthAccount;
import com.hieunguyen.ManageContract.entity.Contract;
import com.hieunguyen.ManageContract.entity.ContractTemplate;
import com.hieunguyen.ManageContract.entity.ContractVariableValue;
import com.hieunguyen.ManageContract.mapper.ContractMapper;
import com.hieunguyen.ManageContract.repository.ContractRepository;
import com.hieunguyen.ManageContract.repository.ContractTemplateRepository;
import com.hieunguyen.ManageContract.repository.ContractVariableValueRepository;
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

    @Transactional
    @Override
    public ContractResponse createContract(CreateContractRequest request, AuthAccount createdBy) {
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

        // Dùng mapper để trả ra DTO
        return ContractMapper.toResponse(saved);
    }

    @Transactional
    @Override
    public ContractResponse submitForApproval(Long contractId) {
        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new RuntimeException("Contract not found"));

        if (contract.getStatus() != ContractStatus.DRAFT) {
            throw new RuntimeException("Only draft contracts can be submitted");
        }

        // Generate file Word đã thay biến
        String filePath = contractFileService.generateContractFile(contract);
        contract.setFilePath(filePath);

        // Update trạng thái
        contract.setStatus(ContractStatus.PENDING_APPROVAL);
        Contract updated = contractRepository.save(contract);

        return ContractMapper.toResponse(updated);
    }



}
