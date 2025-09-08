package com.hieunguyen.ManageContract.controller;

import com.hieunguyen.ManageContract.dto.ResponseData;
import com.hieunguyen.ManageContract.dto.approval.*;
import com.hieunguyen.ManageContract.service.ApprovalFlowService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/flows")
@RequiredArgsConstructor
public class ApprovalFlowController {

    private final ApprovalFlowService approvalFlowService;

    @PostMapping
    public ResponseData<ApprovalFlowResponse> createFlow(@RequestBody ApprovalFlowRequest request) {
        return new ResponseData<>(200, "Tạo flow thành công", approvalFlowService.createFlow(request));
    }

    @PostMapping("/{flowId}/steps")
    public ResponseData<ApprovalStepResponse> addStep(
            @PathVariable Long flowId,
            @RequestBody ApprovalStepRequest request) {
        return new ResponseData<>(200, "Thêm step thành công", approvalFlowService.addStep(flowId, request));
    }

    @GetMapping("/{flowId}")
    public ResponseData<ApprovalFlowResponse> getFlow(@PathVariable Long flowId) {
        return new ResponseData<>(200, "Lấy flow thành công", approvalFlowService.getFlow(flowId));
    }
}
