package com.hieunguyen.ManageContract.controller;

import com.hieunguyen.ManageContract.dto.ResponseData;
import com.hieunguyen.ManageContract.dto.contract.ContractResponse;
import com.hieunguyen.ManageContract.service.ContractApprovalService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/approvals")
@RequiredArgsConstructor
public class ContractApprovalController {

    private final ContractApprovalService approvalService;

    @PostMapping("/submit/{contractId}")
    public ResponseData<ContractResponse> submitContract(
            @PathVariable Long contractId,
            @RequestParam(required = false) Long flowId) {
        ContractResponse response = approvalService.submitForApproval(contractId, flowId);
        return new ResponseData<>(200, "Trình ký hợp đồng thành công", response);
    }

    @PostMapping("/contracts/{contractId}/steps/{stepId}/approve")
    public ResponseData<ContractResponse> approveStep(
            @PathVariable Long contractId,
            @PathVariable Long stepId,
            @RequestParam Long approverId,
            @RequestParam boolean approved,
            @RequestParam(required = false) String comment
    ) {
        ContractResponse response = approvalService.approveStep(contractId, stepId, approverId, approved, comment);
        String msg = approved ? "Phê duyệt thành công" : "Từ chối hợp đồng thành công";
        return new ResponseData<>(200, msg, response);
    }

    // Lấy tiến trình phê duyệt
    @GetMapping("/{contractId}/progress")
    public ResponseData<ContractResponse> getApprovalProgress(@PathVariable Long contractId) {
        ContractResponse response = approvalService.getApprovalProgress(contractId);
        return new ResponseData<>(200, "Lấy tiến trình phê duyệt thành công", response);
    }
}
