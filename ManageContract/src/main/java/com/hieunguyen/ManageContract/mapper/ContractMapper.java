package com.hieunguyen.ManageContract.mapper;

import com.hieunguyen.ManageContract.dto.contract.ContractResponse;
import com.hieunguyen.ManageContract.entity.Contract;
import com.hieunguyen.ManageContract.entity.ContractVariableValue;

import java.util.stream.Collectors;

public class ContractMapper {

    public static ContractResponse toResponse(Contract c) {
        if (c == null) return null;
        ContractResponse dto = new ContractResponse();
        dto.setId(c.getId());
        dto.setContractNumber(c.getContractNumber());
        dto.setTitle(c.getTitle());
        dto.setStatus(c.getStatus() != null ? c.getStatus().name() : null);
        dto.setFilePath(c.getFilePath());
        dto.setTemplateName(c.getTemplate() != null ? c.getTemplate().getName() : null);

        if (c.getVariableValues() != null) {
            dto.setVariables(
                    c.getVariableValues().stream().map(v -> {
                        ContractResponse.VariableValueResponse vv = new ContractResponse.VariableValueResponse();
                        vv.setVarName(v.getVarName());
                        vv.setVarValue(v.getVarValue());
                        return vv;
                    }).toList()
            );
        }
        // currentStep* sẽ được service set tuỳ ngữ cảnh
        return dto;
    }


    private static ContractResponse.VariableValueResponse mapVariable(ContractVariableValue v) {
        ContractResponse.VariableValueResponse dto = new ContractResponse.VariableValueResponse();
        dto.setVarName(v.getVarName());
        dto.setVarValue(v.getVarValue());
        return dto;
    }
}
