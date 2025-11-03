package com.hieunguyen.ManageContract.controller;

import com.hieunguyen.ManageContract.common.constants.ContractStatus;
import com.hieunguyen.ManageContract.dto.ResponseData;
import com.hieunguyen.ManageContract.dto.approval.StepApprovalRequest;
import com.hieunguyen.ManageContract.dto.contract.ContractResponse;
import com.hieunguyen.ManageContract.dto.contractSign.SignStepRequest;
import com.hieunguyen.ManageContract.service.ContractApprovalService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/contracts/approvals")
@RequiredArgsConstructor
public class ContractApprovalController {

    private final ContractApprovalService contractApprovalService;

    // Submit hợp đồng vào flow
    @PostMapping("/{contractId}/submit")
    @PreAuthorize("hasAuthority('contract.approval.submit') or hasRole('ADMIN')")
    public ResponseData<ContractResponse> submitForApproval(
            @PathVariable("contractId") Long contractId,
            @RequestParam(required = false) Long flowId
    ) {
        ContractResponse response = contractApprovalService.submitForApproval(contractId, flowId);
        return new ResponseData<>(HttpStatus.OK.value(), "Submit contract for approval successfully", response);
    }

    // Phê duyệt bước
    @PostMapping("/{contractId}/steps/{stepId}/approve")
    @PreAuthorize("hasAuthority('contract.approval.approve') or hasRole('ADMIN')")
    public ResponseData<ContractResponse> approveStep(
            @PathVariable("contractId") Long contractId,
            @PathVariable("stepId") Long stepId,
            @RequestBody StepApprovalRequest request
    ) {
        ContractResponse response = contractApprovalService.approveStep(stepId, request);
        return new ResponseData<>(200, "Step approved successfully", response);
    }

    // Từ chối bước
    @PostMapping("/{contractId}/steps/{stepId}/reject")
    @PreAuthorize("hasAuthority('contract.approval.reject') or hasRole('ADMIN')")
    public ResponseData<ContractResponse> rejectStep(
            @PathVariable("contractId") Long contractId,
            @PathVariable("stepId") Long stepId,
            @RequestBody StepApprovalRequest request
    ) {
        ContractResponse response = contractApprovalService.rejectStep(stepId, request);
        return new ResponseData<>(200, "Step reject successfully", response);
    }

    // Xem tiến độ phê duyệt của 1 hợp đồng
    @GetMapping("/{contractId}/progress")
    @PreAuthorize("hasAuthority('contract.approval.read') or hasRole('ADMIN')")
    public ResponseEntity<ResponseData<ContractResponse>> getApprovalProgress(
            @PathVariable("contractId") Long contractId
    ) {
        ContractResponse response = contractApprovalService.getApprovalProgress(contractId);
        return ResponseEntity.ok(
                new ResponseData<>(HttpStatus.OK.value(), "Get approval progress successfully", response)
        );
    }

    // Hợp đồng tôi đã xử lý (lọc theo status)
    @GetMapping("/my-handled")
    @PreAuthorize("hasAuthority('contract.approval.read') or hasRole('ADMIN')")
    public ResponseData<List<ContractResponse>> getMyHandledContracts(
            @RequestParam ContractStatus status
    ) {
        List<ContractResponse> response = contractApprovalService.getMyHandledContracts(status);
        return new ResponseData<>(200, "Get handled contracts successfully", response);
    }

    // Hợp đồng đang chờ tôi xử lý
    @GetMapping("/my-pending")
    @PreAuthorize("hasAuthority('contract.approval.read') or hasRole('ADMIN')")
    public ResponseData<List<ContractResponse>> getMyPendingContracts() {
        List<ContractResponse> response = contractApprovalService.getMyPendingContracts();
        return new ResponseData<>(200, "Get pending contracts successfully", response);
    }

    // Xem preview flow hoặc tiến độ
    @GetMapping("/{contractId}/preview")
    @PreAuthorize("hasAuthority('contract.approval.read') or hasRole('ADMIN')")
    public ResponseData<ContractResponse> preview(
            @PathVariable Long contractId,
            @RequestParam(required = false) Long flowId
    ) {
        return new ResponseData<>(200, "Lấy step thành công",
                contractApprovalService.getApprovalProgressOrPreview(contractId, flowId)
        );
    }

    // Ký bước (ký ảnh)
    @PostMapping("/{contractId}/steps/{stepId}/sign")
    @PreAuthorize("hasAuthority('contract.approval.sign') or hasRole('ADMIN')")
    public ResponseData<ContractResponse> signStep(
            @PathVariable("contractId") Long contractId,
            @PathVariable("stepId") Long stepId,
            @RequestBody SignStepRequest request
    ) {
        ContractResponse response = contractApprovalService.signStep(contractId, stepId, request);
        return new ResponseData<>(200, "Sign step successfully", response);
    }
}
