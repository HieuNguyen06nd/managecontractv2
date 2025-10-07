package com.hieunguyen.ManageContract.dto.contract;

import com.hieunguyen.ManageContract.common.constants.ApprovalAction;
import com.hieunguyen.ManageContract.common.constants.ApproverType;
import com.hieunguyen.ManageContract.common.constants.SignatureType;
import lombok.Data;

import java.util.List;

@Data
public class CreateContractRequest {
    private Long templateId;
    private String title;
    private List<VariableValueRequest> variables;
    private Long flowId; // ID flow được chọn (nullable)
    private Boolean allowChangeFlow; // nếu muốn cho phép override flow
    private String flowName; // Nếu tạo luồng ký mới
    private String flowDescription; // Mô tả luồng ký mới
    private Long existingFlowId;
    private Long createdBy;
    private String flowOption;
    private List<SignStepRequest> signSteps; // Các bước ký

    @Data
    public static class VariableValueRequest {
        private String varName;
        private String varValue;
    }

    @Data
    public static class SignStepRequest {
        private Boolean required;
        private ApproverType approverType; // USER hoặc POSITION
        private Long employeeId; // Id của người ký, nếu là USER
        private Long departmentId; // Id của phòng ban, nếu là POSITION
        private Long positionId; // Id của vị trí, nếu là POSITION
        private ApprovalAction action; // APPROVE_ONLY, SIGN_ONLY, SIGN_THEN_APPROVE
        private String signaturePlaceholder;
        private Boolean isFinalStep;
    }
}
