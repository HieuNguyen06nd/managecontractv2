package com.hieunguyen.ManageContract.dto.templateVariable;

import com.hieunguyen.ManageContract.common.constants.VariableType;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
public class TemplateVariablePreview {
    private String varName;
    private Integer orderIndex;
    private VariableType varType;
    private Map<String, Object> config;
    private List<String> allowedValues;
}
