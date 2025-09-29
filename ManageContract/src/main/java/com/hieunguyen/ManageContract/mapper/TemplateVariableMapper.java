package com.hieunguyen.ManageContract.mapper;

import com.hieunguyen.ManageContract.dto.templateVariable.TemplateVariableRequest;
import com.hieunguyen.ManageContract.dto.templateVariable.TemplateVariableResponse;
import com.hieunguyen.ManageContract.entity.ContractTemplate;
import com.hieunguyen.ManageContract.entity.TemplateVariable;

import java.util.List;
import java.util.stream.Collectors;

public class TemplateVariableMapper {

    public static TemplateVariable toEntity(TemplateVariableRequest req, ContractTemplate template) {
        TemplateVariable v = new TemplateVariable();
        v.setVarName(req.getVarName());
        v.setName(req.getName());
        v.setVarType(req.getVarType());
        v.setRequired(Boolean.TRUE.equals(req.getRequired())); // tránh null
        v.setDefaultValue(req.getDefaultValue());
        v.setAllowedValues(req.getAllowedValues());
        v.setOrderIndex(req.getOrderIndex());
        v.setTemplate(template);
        return v;
    }

    public static TemplateVariableResponse toResponse(TemplateVariable entity) {
        TemplateVariableResponse res = new TemplateVariableResponse();
        res.setId(entity.getId());
        res.setVarName(entity.getVarName());
        res.setName(entity.getName());
        res.setVarType(entity.getVarType());
        res.setRequired(entity.getRequired());
        res.setDefaultValue(entity.getDefaultValue());
        res.setAllowedValues(entity.getAllowedValues());
        res.setOrderIndex(entity.getOrderIndex());
        return res;
    }

    public static List<TemplateVariableResponse> toResponseList(List<TemplateVariable> entities) {
        return entities.stream()
                .map(TemplateVariableMapper::toResponse)
                .collect(Collectors.toList());
    }
}
