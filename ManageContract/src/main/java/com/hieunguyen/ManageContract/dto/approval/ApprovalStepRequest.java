package com.hieunguyen.ManageContract.dto.approval;

import lombok.Data;

@Data
public class ApprovalStepRequest {
    private Integer stepOrder;
    private Boolean required;
    private Long roleId;       // ai sẽ ký
    private Long departmentId; // optional
    private Long positionId;
    private Boolean isFinalStep;
}
