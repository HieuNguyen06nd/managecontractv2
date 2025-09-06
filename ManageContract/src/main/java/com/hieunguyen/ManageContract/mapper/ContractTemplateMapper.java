package com.hieunguyen.ManageContract.mapper;

import com.hieunguyen.ManageContract.dto.authAccount.AuthAccountResponse;
import com.hieunguyen.ManageContract.dto.contractTemplate.ContractTemplateResponse;
import com.hieunguyen.ManageContract.dto.templateVariable.TemplateVariableResponse;
import com.hieunguyen.ManageContract.entity.ContractTemplate;
import com.hieunguyen.ManageContract.entity.TemplateVariable;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class ContractTemplateMapper {

    public static ContractTemplateResponse toResponse(ContractTemplate template) {
        if (template == null) return null;

        ContractTemplateResponse dto = new ContractTemplateResponse();
        dto.setId(template.getId());
        dto.setName(template.getName());
        dto.setDescription(template.getDescription());
        dto.setFilePath(template.getFilePath());

        // Map createdBy
        if (template.getCreatedBy() != null) {
            AuthAccountResponse account = new AuthAccountResponse();
            account.setId(template.getCreatedBy().getId());
            account.setEmail(template.getCreatedBy().getEmail());
            account.setPhone(template.getCreatedBy().getPhone());
            account.setEmailVerified(template.getCreatedBy().isEmailVerified());
            dto.setCreatedBy(account);
        }

        // Map variables
        if (template.getVariables() != null) {
            List<TemplateVariableResponse> vars = template.getVariables().stream()
                    .map(ContractTemplateMapper::toVariableResponse)
                    .collect(Collectors.toList());
            dto.setVariables(vars);
        } else {
            dto.setVariables(Collections.emptyList());
        }

        return dto;
    }

    private static TemplateVariableResponse toVariableResponse(TemplateVariable variable) {
        TemplateVariableResponse dto = new TemplateVariableResponse();
        dto.setId(variable.getId());
        dto.setVarName(variable.getVarName());
        dto.setVarType(variable.getVarType() != null ? variable.getVarType().name() : null);
        dto.setRequired(variable.getRequired());
        dto.setDefaultValue(variable.getDefaultValue());
        return dto;
    }
}