package com.hieunguyen.ManageContract.mapper;

import com.hieunguyen.ManageContract.dto.authAccount.AuthAccountResponse;
import com.hieunguyen.ManageContract.dto.contractTemplate.ContractTemplateResponse;
import com.hieunguyen.ManageContract.dto.templateVariable.TemplateVariableResponse;
import com.hieunguyen.ManageContract.entity.AuthAccount;
import com.hieunguyen.ManageContract.entity.ContractTemplate;
import com.hieunguyen.ManageContract.entity.TemplateVariable;
import com.hieunguyen.ManageContract.entity.User;

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

        // Map createdBy từ User
        if (template.getCreatedBy() != null) {
            User user = template.getCreatedBy();
            AuthAccount account = user.getAccount();
            if (account != null) {
                AuthAccountResponse dtoAccount = new AuthAccountResponse();
                dtoAccount.setId(account.getId());
                dtoAccount.setEmail(account.getEmail());
                dtoAccount.setPhone(account.getPhone());
                dtoAccount.setEmailVerified(account.isEmailVerified());
                dto.setCreatedBy(dtoAccount);
            }
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
        dto.setAllowedValues(variable.getAllowedValues()); // nếu muốn trả về list dropdown
        dto.setOrderIndex(variable.getOrderIndex()); // thứ tự biến
        return dto;
    }

}