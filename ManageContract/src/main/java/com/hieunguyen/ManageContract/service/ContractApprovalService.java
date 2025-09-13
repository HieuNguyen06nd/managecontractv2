package com.hieunguyen.ManageContract.service;

import com.hieunguyen.ManageContract.dto.approval.StepApprovalRequest;
import com.hieunguyen.ManageContract.dto.contract.ContractResponse;

public interface ContractApprovalService {
    // Trình ký hợp đồng
    ContractResponse submitForApproval(Long contractId, Long flowId);

    // Approve step chỉ cần stepId
    ContractResponse approveStep(Long stepId, StepApprovalRequest request);

    // Reject step chỉ cần stepId
    ContractResponse rejectStep(Long stepId, StepApprovalRequest request);

    // Xem tiến trình
    ContractResponse getApprovalProgress(Long contractId);
}

