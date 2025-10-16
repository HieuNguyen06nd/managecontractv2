package com.hieunguyen.ManageContract.dto.contract;

import com.hieunguyen.ManageContract.dto.approval.ApprovalStepResponse;
import lombok.Data;

import java.util.List;

@Data
public class PlannedFlowResponse {
    private boolean exists;          // true nếu đang chạy (snapshot runtime)
    private String source;           // RUNTIME | CONTRACT | TEMPLATE_DEFAULT
    private Long flowId;
    private String flowName;
    private List<ApprovalStepResponse> steps;
}
