package com.hieunguyen.ManageContract.dto.templateVariable;

import com.hieunguyen.ManageContract.common.constants.VariableType;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class TemplateVariableResponse {
    private Long id;
    private String varName;
    private VariableType varType;
    private String name;
    private Boolean required;
    private String defaultValue;
    private Integer orderIndex;
    private List<String> allowedValues;
    private Map<String, Object> config;
}
