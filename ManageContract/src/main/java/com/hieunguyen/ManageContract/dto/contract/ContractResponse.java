package com.hieunguyen.ManageContract.dto.contract;

import com.hieunguyen.ManageContract.dto.approval.ApprovalStepResponse;
import lombok.Data;

import java.util.List;

@Data
public class ContractResponse {
    private Long id;
    private String contractNumber;
    private String title;
    private String status;
    private String filePath;
    private String templateName;
    private Long templateId;
    private List<VariableValueResponse> variables;

    private Long currentStepId;
    private String currentStepName;

    private String currentStepAction;
    private String currentStepSignaturePlaceholder;

    // ---------- bổ sung để hiển thị flow/steps ----------
    private Boolean hasFlow;          // true: đang chạy (đã snapshot ContractApproval); false: chỉ preview
    private String flowSource;        // "CONTRACT" | "TEMPLATE_DEFAULT" | "SELECTED"
    private Long flowId;
    private String flowName;
    private List<ApprovalStepResponse> steps; // danh sách bước để render pipeline

    @Data
    public static class VariableValueResponse {
        private String varName;
        private String varValue;
    }

}
