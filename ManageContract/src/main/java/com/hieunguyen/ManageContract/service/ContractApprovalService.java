package com.hieunguyen.ManageContract.service;

import com.hieunguyen.ManageContract.common.constants.ContractStatus;
import com.hieunguyen.ManageContract.dto.approval.StepApprovalRequest;
import com.hieunguyen.ManageContract.dto.contract.ContractResponse;
import com.hieunguyen.ManageContract.dto.contractSign.SignStepRequest;

import java.util.List;

public interface ContractApprovalService {
    // Trình ký hợp đồng
    ContractResponse submitForApproval(Long contractId, Long flowId);

    // Approve step chỉ cần stepId
    ContractResponse approveStep(Long stepId, StepApprovalRequest request);

    // Reject step chỉ cần stepId
    ContractResponse rejectStep(Long stepId, StepApprovalRequest request);

    // Xem tiến trình
    ContractResponse getApprovalProgress(Long contractId);

    ContractResponse signStep(Long contractId, Long stepId, SignStepRequest request);

    List<ContractResponse> getMyHandledContracts(ContractStatus status);
    List<ContractResponse> getMyPendingContracts();

    ContractResponse getApprovalProgressOrPreview(Long contractId, Long flowId);

}

