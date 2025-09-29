package com.hieunguyen.ManageContract.dto.templateVariable;

import lombok.Data;

import java.util.List;

@Data
public class TemplatePreviewResponse {
    private String tempFileName; // dùng cho bước finalize
    private List<TemplateVariablePreview> variables;
}
