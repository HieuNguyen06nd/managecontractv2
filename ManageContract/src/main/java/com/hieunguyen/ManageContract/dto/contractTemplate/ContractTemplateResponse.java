package com.hieunguyen.ManageContract.dto.contractTemplate;

import com.hieunguyen.ManageContract.common.constants.Status;
import com.hieunguyen.ManageContract.dto.authAccount.AuthAccountResponse;
import com.hieunguyen.ManageContract.dto.templateVariable.TemplateVariableResponse;
import lombok.Data;

import java.util.List;

@Data
public class ContractTemplateResponse {
    private Long id;
    private String name;
    private String description;
    private String filePath;

    private AuthAccountResponse createdBy;
    private List<TemplateVariableResponse> variables;

    // --- flow info ---
    private Long defaultFlowId;          // Id của flow mặc định (nếu có)
    private String defaultFlowName;      // Tên flow mặc định (tùy chọn)
    private Boolean allowOverrideFlow;   // Có cho phép thay đổi flow không

    // --- category info (NEW) ---
    private Long categoryId;             // id danh mục
    private String categoryCode;         // ví dụ: LABOR, BUSINESS, SERVICE...
    private String categoryName;         // ví dụ: "Hợp đồng lao động"
    private Status categoryStatus; // ACTIVE/INACTIVE
}
