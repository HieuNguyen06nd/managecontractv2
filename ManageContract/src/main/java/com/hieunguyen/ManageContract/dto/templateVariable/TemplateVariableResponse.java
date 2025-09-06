package com.hieunguyen.ManageContract.dto.templateVariable;

import lombok.Data;

@Data
public class TemplateVariableResponse {
    private Long id;
    private String varName;
    private String varType;
    private Boolean required;
    private String defaultValue;
}
