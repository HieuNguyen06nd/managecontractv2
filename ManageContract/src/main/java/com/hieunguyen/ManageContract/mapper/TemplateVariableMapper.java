package com.hieunguyen.ManageContract.mapper;

import com.hieunguyen.ManageContract.dto.templateVariable.TemplateVariableRequest;
import com.hieunguyen.ManageContract.entity.TemplateVariable;
import com.hieunguyen.ManageContract.entity.ContractTemplate;

public class TemplateVariableMapper {

    public static TemplateVariable toEntity(TemplateVariableRequest request, ContractTemplate template) {
        TemplateVariable variable = new TemplateVariable();
        variable.setVarName(request.getVarName());
        variable.setVarType(request.getVarType());
        variable.setRequired(request.getRequired());
        variable.setName(request.getName());
        variable.setOrderIndex(request.getOrderIndex());
        variable.setTemplate(template);

        // THÊM: Xử lý config và allowedValues
        if (request.getConfig() != null) {
            // Config sẽ được xử lý trong service để chuyển thành JSON string
            variable.setConfig(null); // Service sẽ set sau
        }

        if (request.getAllowedValues() != null) {
            variable.setAllowedValues(request.getAllowedValues());
        }

        return variable;
    }
}