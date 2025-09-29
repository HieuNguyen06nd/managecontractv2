package com.hieunguyen.ManageContract.service;

import com.hieunguyen.ManageContract.dto.approval.*;

import java.util.List;

public interface ApprovalFlowService {
    ApprovalFlowResponse createFlow(ApprovalFlowRequest request);
    ApprovalFlowResponse updateFlow(Long flowId, ApprovalFlowRequest request);
    ApprovalFlowResponse getFlow(Long flowId);
    List<ApprovalFlowResponse> listFlowsByTemplate(Long templateId);
    void setDefaultFlow(Long templateId, Long flowId);
    void deleteFlow(Long flowId);
}
