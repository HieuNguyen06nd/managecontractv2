package com.hieunguyen.ManageContract.service;

import com.hieunguyen.ManageContract.dto.contract.ContractResponse;

public interface ContractApprovalService {
    // Trình ký hợp đồng (sử dụng flow mặc định hoặc override bằng flowId)
    ContractResponse submitForApproval(Long contractId, Long flowId);

    // Approve / Reject một step
    ContractResponse approveStep(Long contractId, Long stepId, Long approverId, boolean approved, String comment);

    // Xem tiến trình phê duyệt
    ContractResponse getApprovalProgress(Long contractId);
}
