package com.hieunguyen.ManageContract.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hieunguyen.ManageContract.dto.templateVariable.TemplateVariableRequest;
import com.hieunguyen.ManageContract.dto.templateVariable.TemplateVariableResponse;
import com.hieunguyen.ManageContract.entity.TemplateVariable;
import com.hieunguyen.ManageContract.entity.ContractTemplate;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

@Slf4j
public class TemplateVariableMapper {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static TemplateVariable toEntity(TemplateVariableRequest request, ContractTemplate template) {
        TemplateVariable variable = new TemplateVariable();
        variable.setVarName(request.getVarName());
        variable.setVarType(request.getVarType());
        variable.setRequired(request.getRequired());
        variable.setName(request.getName());
        variable.setOrderIndex(request.getOrderIndex());
        variable.setTemplate(template);

        // Xử lý config - chuyển Map thành JSON string
        if (request.getConfig() != null && !request.getConfig().isEmpty()) {
            try {
                String configJson = objectMapper.writeValueAsString(request.getConfig());
                variable.setConfig(configJson);
            } catch (JsonProcessingException e) {
                log.error("Error serializing config to JSON for variable: {}", request.getVarName(), e);
                variable.setConfig("{}");
            }
        } else {
            variable.setConfig("{}");
        }

        // Xử lý allowedValues
        if (request.getAllowedValues() != null) {
            variable.setAllowedValues(request.getAllowedValues());
        }

        // Xử lý defaultValue
        if (request.getDefaultValue() != null) {
            variable.setDefaultValue(request.getDefaultValue());
        }

        return variable;
    }

    public static TemplateVariableResponse toResponse(TemplateVariable variable) {
        if (variable == null) return null;

        TemplateVariableResponse response = new TemplateVariableResponse();
        response.setId(variable.getId());
        response.setVarName(variable.getVarName());
        response.setVarType(variable.getVarType());
        response.setName(variable.getName());
        response.setRequired(variable.getRequired());
        response.setDefaultValue(variable.getDefaultValue());
        response.setOrderIndex(variable.getOrderIndex());
        response.setAllowedValues(variable.getAllowedValues());

        // QUAN TRỌNG: Parse config từ JSON string sang Map
        Map<String, Object> configMap = new HashMap<>();
        if (variable.getConfig() != null && !variable.getConfig().trim().isEmpty()) {
            try {
                configMap = objectMapper.readValue(variable.getConfig(),
                        new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
            } catch (Exception e) {
                log.error("Error parsing config JSON for variable {}: {}", variable.getVarName(), variable.getConfig(), e);
                configMap = new HashMap<>();
            }
        }
        response.setConfig(configMap);

        return response;
    }
}