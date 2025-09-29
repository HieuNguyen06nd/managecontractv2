package com.hieunguyen.ManageContract.dto.approval;

import com.hieunguyen.ManageContract.common.constants.ApproverType;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ApprovalStepResponse {
    private Long id;
    private Integer stepOrder;
    private Boolean required;
    private ApproverType approverType;
    private Boolean isFinalStep;

    private Long employeeId;
    private String employeeName;

    private Long positionId;
    private String positionName;

    private Long departmentId;
    private String departmentName;
}
