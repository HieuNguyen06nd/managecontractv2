package com.hieunguyen.ManageContract.dto.approval;

import com.hieunguyen.ManageContract.common.constants.ApprovalAction;
import com.hieunguyen.ManageContract.common.constants.ApproverType;
import lombok.Data;

@Data
public class ApprovalStepRequest {
    private Integer stepOrder;
    private Boolean required;
    private ApproverType approverType;
    private Long departmentId; // optional
    private Long positionId;
    private Long employeeId;
    private Boolean isFinalStep;
    private ApprovalAction action;     // REQUIRED
    private String signaturePlaceholder;
}
