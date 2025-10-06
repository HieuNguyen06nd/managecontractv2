package com.hieunguyen.ManageContract.dto.approval;

import com.hieunguyen.ManageContract.common.constants.ApprovalAction;
import com.hieunguyen.ManageContract.common.constants.ApprovalStatus;
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

    // -------- bổ sung cho hiển thị/preview ----------
    private ApprovalAction action;          // SIGN_ONLY / APPROVE_ONLY / SIGN_THEN_APPROVE
    private String signaturePlaceholder;    // placeholder khi là bước ký

    // -------- chỉ có khi hợp đồng đã submit (runtime) ----------
    private ApprovalStatus status;          // PENDING / APPROVED / REJECTED
    private Boolean isCurrent;              // bước hiện tại?
    private String decidedBy;               // người đã duyệt/ký
    private String decidedAt;               // thời điểm duyệt/ký (ISO string)
}
