package com.hieunguyen.ManageContract.controller;

import com.hieunguyen.ManageContract.dto.ResponseData;
import com.hieunguyen.ManageContract.dto.approval.ApprovalFlowRequest;
import com.hieunguyen.ManageContract.dto.approval.ApprovalFlowResponse;
import com.hieunguyen.ManageContract.service.ApprovalFlowService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/flows")
@RequiredArgsConstructor
public class ApprovalFlowController {

    private final ApprovalFlowService approvalFlowService;

    // Tạo flow
    @PreAuthorize("hasAuthority('flow.create')")
    @PostMapping
    public ResponseData<ApprovalFlowResponse> createFlow(@Valid @RequestBody ApprovalFlowRequest request) {
        var res = approvalFlowService.createFlow(request);
        return new ResponseData<>(200, "Tạo flow thành công", res);
    }

    // Cập nhật flow
    @PreAuthorize("hasAuthority('flow.update')")
    @PutMapping("/{flowId}")
    public ResponseData<ApprovalFlowResponse> updateFlow(
            @PathVariable Long flowId,
            @Valid @RequestBody ApprovalFlowRequest request
    ) {
        var res = approvalFlowService.updateFlow(flowId, request);
        return new ResponseData<>(200, "Cập nhật flow thành công", res);
    }

    // Lấy chi tiết flow
    @PreAuthorize("hasAuthority('flow.read')")
    @GetMapping("/{flowId}")
    public ResponseData<ApprovalFlowResponse> getFlow(@PathVariable Long flowId) {
        var res = approvalFlowService.getFlow(flowId);
        return new ResponseData<>(200, "Lấy flow thành công", res);
    }

    // Liệt kê các flow của 1 template (dùng cho bước 3 để chọn flow)
    @PreAuthorize("hasAuthority('flow.read')")
    @GetMapping("/by-template/{templateId}")
    public ResponseData<List<ApprovalFlowResponse>> listFlowsByTemplate(@PathVariable Long templateId) {
        var res = approvalFlowService.listFlowsByTemplate(templateId);
        return new ResponseData<>(200, "Lấy danh sách flow theo template thành công", res);
    }

    // Đặt flow mặc định cho template
    @PreAuthorize("hasAuthority('flow.set_default')")
    @PostMapping("/by-template/{templateId}/{flowId}/set-default")
    public ResponseData<Void> setDefaultFlow(@PathVariable Long templateId, @PathVariable Long flowId) {
        approvalFlowService.setDefaultFlow(templateId, flowId);
        return new ResponseData<>(200, "Đặt flow mặc định cho template thành công", null);
    }

    // (Tuỳ chọn) Xoá flow
    @PreAuthorize("hasAuthority('flow.delete')")
    @DeleteMapping("/{flowId}")
    public ResponseData<Void> deleteFlow(@PathVariable Long flowId) {
        approvalFlowService.deleteFlow(flowId);
        return new ResponseData<>(200, "Xoá flow thành công", null);
    }
}
