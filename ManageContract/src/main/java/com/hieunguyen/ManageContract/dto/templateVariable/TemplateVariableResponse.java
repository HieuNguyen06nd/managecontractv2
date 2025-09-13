package com.hieunguyen.ManageContract.dto.templateVariable;

import lombok.Data;

import java.util.List;

@Data
public class TemplateVariableResponse {
    private Long id;
    private String varName;
    private String varType;
    private Boolean required;
    private String defaultValue;
    private Integer orderIndex;
    private List<String> allowedValues;
}
