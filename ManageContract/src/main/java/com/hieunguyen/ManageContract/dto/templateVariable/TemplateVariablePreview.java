package com.hieunguyen.ManageContract.dto.templateVariable;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TemplateVariablePreview {
    private String varName;
    private Integer orderIndex;
}
