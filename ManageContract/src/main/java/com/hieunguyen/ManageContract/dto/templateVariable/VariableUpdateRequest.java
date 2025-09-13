package com.hieunguyen.ManageContract.dto.templateVariable;

import com.hieunguyen.ManageContract.common.constants.VariableType;
import lombok.Data;

@Data
public class VariableUpdateRequest {
    private String varName;
    private VariableType varType;
}

