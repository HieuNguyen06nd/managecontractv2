package com.hieunguyen.ManageContract.dto.templateVariable;

import com.hieunguyen.ManageContract.common.constants.VariableType;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class VariableUpdateRequest {
    private String varName;
    private VariableType varType;
    private Boolean required;
    private String name;
    private Integer orderIndex;

    // THÊM: Config cho các kiểu biến đặc biệt
    private Map<String, Object> config;

    // THÊM: Danh sách giá trị cho DROPDOWN/LIST
    private List<String> allowedValues;

    // getters và setters
}

