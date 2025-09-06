package com.hieunguyen.ManageContract.service.impl;

import com.hieunguyen.ManageContract.common.constants.ContractStatus;
import com.hieunguyen.ManageContract.entity.AuthAccount;
import com.hieunguyen.ManageContract.entity.Contract;
import com.hieunguyen.ManageContract.entity.ContractTemplate;
import com.hieunguyen.ManageContract.entity.ContractVariableValue;
import com.hieunguyen.ManageContract.repository.ContractRepository;
import com.hieunguyen.ManageContract.repository.ContractTemplateRepository;
import com.hieunguyen.ManageContract.repository.ContractVariableValueRepository;
import com.hieunguyen.ManageContract.service.ContractService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class ContractServiceImpl implements ContractService {
    private final ContractRepository contractRepository;
    private final ContractVariableValueRepository variableValueRepository;
    private final ContractTemplateRepository templateRepository;

    public Contract createContract(Long templateId, String title, Map<String, String> variableValues, AuthAccount createdBy) {
        ContractTemplate template = templateRepository.findById(templateId)
                .orElseThrow(() -> new RuntimeException("Template not found"));

        Contract contract = new Contract();
        contract.setTitle(title);
        contract.setTemplate(template);
        contract.setCreatedBy(createdBy);
        contract.setStatus(ContractStatus.DRAFT);
        contract = contractRepository.save(contract);

        for (Map.Entry<String, String> entry : variableValues.entrySet()) {
            ContractVariableValue varValue = new ContractVariableValue();
            varValue.setContract(contract);
            varValue.setVarName(entry.getKey());
            varValue.setVarValue(entry.getValue());
            variableValueRepository.save(varValue);
        }

        return contract;
    }
}
