package com.hieunguyen.ManageContract.mapper;

import com.hieunguyen.ManageContract.dto.contract.ContractResponse;
import com.hieunguyen.ManageContract.entity.Contract;
import com.hieunguyen.ManageContract.entity.ContractVariableValue;

import java.util.stream.Collectors;

public class ContractMapper {

    public static ContractResponse toResponse(Contract contract) {
        if (contract == null) {
            return null;
        }

        ContractResponse dto = new ContractResponse();
        dto.setId(contract.getId());
        dto.setContractNumber(contract.getContractNumber());
        dto.setTitle(contract.getTitle());
        dto.setStatus(contract.getStatus().name());
        dto.setFilePath(contract.getFilePath());

        if (contract.getTemplate() != null) {
            dto.setTemplateName(contract.getTemplate().getName());
        }

        if (contract.getVariableValues() != null) {
            dto.setVariables(contract.getVariableValues().stream()
                    .map(ContractMapper::mapVariable)
                    .collect(Collectors.toList()));
        }

        return dto;
    }

    private static ContractResponse.VariableValueResponse mapVariable(ContractVariableValue v) {
        ContractResponse.VariableValueResponse dto = new ContractResponse.VariableValueResponse();
        dto.setVarName(v.getVarName());
        dto.setVarValue(v.getVarValue());
        return dto;
    }
}
