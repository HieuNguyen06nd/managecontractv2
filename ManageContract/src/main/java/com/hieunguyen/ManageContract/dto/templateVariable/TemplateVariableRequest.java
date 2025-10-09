package com.hieunguyen.ManageContract.dto.templateVariable;


import com.hieunguyen.ManageContract.common.constants.VariableType;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class TemplateVariableRequest {
    private String varName;       // tên biến trong file ${...}
    private String name;          // tên hiển thị (user nhập)
    private VariableType varType; // kiểu dữ liệu
    private Boolean required;
    private String defaultValue;
    private List<String> allowedValues;
    private Integer orderIndex;
    private Map<String, Object> config;
}