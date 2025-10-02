package com.hieunguyen.ManageContract.controller;

import com.hieunguyen.ManageContract.common.constants.ContractStatus;
import com.hieunguyen.ManageContract.dto.ResponseData;
import com.hieunguyen.ManageContract.dto.approval.StepApprovalRequest;
import com.hieunguyen.ManageContract.dto.contract.ContractResponse;
import com.hieunguyen.ManageContract.service.ContractApprovalService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/contracts/approvals")
@RequiredArgsConstructor
public class ContractApprovalController {

    private final ContractApprovalService contractApprovalService;

    @PostMapping("/{contractId}/submit")
    public ResponseData<ContractResponse> submitForApproval(
            @PathVariable("contractId") Long contractId,
            @RequestParam(required = false) Long flowId
    ) {
        ContractResponse response = contractApprovalService.submitForApproval(contractId, flowId);
        return new ResponseData<>(HttpStatus.OK.value(), "Submit contract for approval successfully", response);
    }

    @PostMapping("/{contractId}/steps/{stepId}/approve")
    public ResponseData<ContractResponse> approveStep(
            @PathVariable("contractId") Long contractId,
            @PathVariable("stepId") Long stepId,
            @RequestBody StepApprovalRequest request
    ) {
        // contractId không dùng ở service, nhưng cần trong chữ ký để khớp path
        ContractResponse response = contractApprovalService.approveStep(stepId, request);
        return new ResponseData<>(200, "Step approved successfully", response);
    }

    @PostMapping("/{contractId}/steps/{stepId}/reject")
    public ResponseData<ContractResponse> rejectStep(
            @PathVariable("contractId") Long contractId,
            @PathVariable("stepId") Long stepId,
            @RequestBody StepApprovalRequest request
    ) {
        ContractResponse response = contractApprovalService.rejectStep(stepId, request);
        return new ResponseData<>(200, "Step reject successfully", response);
    }

    @GetMapping("/{contractId}/progress")
    public ResponseEntity<ResponseData<ContractResponse>> getApprovalProgress(
            @PathVariable("contractId") Long contractId
    ) {
        ContractResponse response = contractApprovalService.getApprovalProgress(contractId);
        return ResponseEntity.ok(
                new ResponseData<>(HttpStatus.OK.value(), "Get approval progress successfully", response)
        );
    }

    @GetMapping("/my-handled")
    public ResponseData<List<ContractResponse>> getMyHandledContracts(
            @RequestParam ContractStatus status
    ) {
        List<ContractResponse> response = contractApprovalService.getMyHandledContracts(status);
        return new ResponseData<>(200, "Get handled contracts successfully", response);
    }

    @GetMapping("/my-pending")
    public ResponseData<List<ContractResponse>> getMyPendingContracts() {
        List<ContractResponse> response = contractApprovalService.getMyPendingContracts();
        return new ResponseData<>(200, "Get pending contracts successfully", response);
    }
}
