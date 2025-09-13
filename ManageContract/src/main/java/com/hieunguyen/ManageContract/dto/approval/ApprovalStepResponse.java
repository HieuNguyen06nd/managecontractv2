package com.hieunguyen.ManageContract.dto.approval;

import lombok.Data;

@Data
public class ApprovalStepResponse {
    private Long id;
    private Integer stepOrder;
    private Boolean required;
    private Long roleId;
    private Long departmentId;
    private Long positionId;
    private Boolean isFinalStep;
}
