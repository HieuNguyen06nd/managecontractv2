package com.hieunguyen.ManageContract.controller;

import com.hieunguyen.ManageContract.dto.ResponseData;
import com.hieunguyen.ManageContract.dto.approval.StepApprovalRequest;
import com.hieunguyen.ManageContract.dto.contract.ContractResponse;
import com.hieunguyen.ManageContract.service.ContractApprovalService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/contracts/approvals")
@RequiredArgsConstructor
public class ContractApprovalController {

    private final ContractApprovalService contractApprovalService;

    /**
     * Trình ký hợp đồng (chạy theo flow mặc định hoặc flow override nếu có)
     */
    @PostMapping("/{contractId}/submit")
    public ResponseData<ContractResponse> submitForApproval(
            @PathVariable Long contractId,
            @RequestParam(required = false) Long flowId
    ) {
        ContractResponse response = contractApprovalService.submitForApproval(contractId, flowId);
        return new ResponseData<>(HttpStatus.OK.value(), "Submit contract for approval successfully", response);
    }

    /**
     * Approve step
     */
    @PostMapping("/{contractId}/steps/{stepId}/approve")
    public ResponseData<ContractResponse> approveStep(
            @PathVariable Long stepId,
            @RequestBody StepApprovalRequest request
    ) {
        ContractResponse response = contractApprovalService.approveStep( stepId, request);
        return new ResponseData<>(200, "Step approved successfully", response);
    }

    /**
     * Reject step
     */
    @PostMapping("/{contractId}/steps/{stepId}/reject")
    public ResponseData<ContractResponse> rejectStep(
            @PathVariable Long stepId,
            @RequestBody StepApprovalRequest request
    ) {
        ContractResponse response = contractApprovalService.rejectStep( stepId, request);
        return new ResponseData<>(200, "Step reject successfully", response);
    }

    /**
     * Xem tiến trình phê duyệt
     */
    @GetMapping("/{contractId}/progress")
    public ResponseEntity<ResponseData<ContractResponse>> getApprovalProgress(
            @PathVariable Long contractId
    ) {
        ContractResponse response = contractApprovalService.getApprovalProgress(contractId);
        return ResponseEntity.ok(
                new ResponseData<>(HttpStatus.OK.value(), "Get approval progress successfully", response)
        );
    }
}
