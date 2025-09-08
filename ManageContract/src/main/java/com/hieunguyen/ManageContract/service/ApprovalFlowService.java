package com.hieunguyen.ManageContract.service;

import com.hieunguyen.ManageContract.dto.approval.*;

public interface ApprovalFlowService {
    ApprovalFlowResponse createFlow(ApprovalFlowRequest request);
    ApprovalStepResponse addStep(Long flowId, ApprovalStepRequest request);
    ApprovalFlowResponse getFlow(Long flowId);
}
