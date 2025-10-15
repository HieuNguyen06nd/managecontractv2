package com.hieunguyen.ManageContract.controller;

import com.hieunguyen.ManageContract.common.constants.Status;
import com.hieunguyen.ManageContract.dto.ResponseData;
import com.hieunguyen.ManageContract.dto.approval.ApprovalFlowResponse;
import com.hieunguyen.ManageContract.dto.contractTemplate.ContractTemplateCreateRequest;
import com.hieunguyen.ManageContract.dto.contractTemplate.ContractTemplateResponse;
import com.hieunguyen.ManageContract.dto.contractTemplate.ContractTemplateUpdateRequest;
import com.hieunguyen.ManageContract.dto.contractTemplate.UpdateStatusRequest;
import com.hieunguyen.ManageContract.dto.templateVariable.TemplatePreviewResponse;
import com.hieunguyen.ManageContract.dto.templateVariable.VariableUpdateRequest;
import com.hieunguyen.ManageContract.security.jwt.SecurityUtil;
import com.hieunguyen.ManageContract.service.ContractTemplateService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/templates")
@RequiredArgsConstructor
public class ContractTemplateController {

    private final ContractTemplateService templateService;

    // ------------------ BƯỚC 1: Preview từ file ------------------
    @PostMapping("/preview-file")
    public ResponseData<TemplatePreviewResponse> previewTemplateFile(
            @RequestParam MultipartFile file
    ) throws Exception {
        TemplatePreviewResponse response = templateService.previewFromFile(file);
        return new ResponseData<>(200, "Preview template từ file thành công", response);
    }

    // ------------------ BƯỚC 1: Preview từ Google Docs ------------------
    @PostMapping("/preview-link")
    public ResponseData<TemplatePreviewResponse> previewTemplateLink(
            @RequestParam String docLink
    ) throws Exception {
        TemplatePreviewResponse response = templateService.previewFromGoogleDoc(docLink);
        return new ResponseData<>(200, "Preview template từ link Google Docs thành công", response);
    }

    // ------------------ BƯỚC 2: Finalize template ------------------
    @PostMapping("/finalize")
    @Operation(summary = "Finalize template (lưu template)")
    public ResponseData<ContractTemplateResponse> finalizeTemplate(
            @RequestBody ContractTemplateCreateRequest request
    ) {
        Long employeeId = SecurityUtil.getCurrentEmployeeId();
        if (employeeId == null) {
            throw new RuntimeException("Không tìm thấy thông tin người dùng trong token");
        }

        ContractTemplateResponse response = templateService.finalizeTemplate(request, employeeId);
        return new ResponseData<>(200, "Lưu template thành công", response);
    }

    // ------------------ Cập nhật kiểu dữ liệu biến (FE chọn) ------------------
    @PostMapping("/{templateId}/variables")
    public ResponseData<String> updateVariableTypes(
            @PathVariable Long templateId,
            @RequestBody List<VariableUpdateRequest> requests
    ) {
        templateService.updateVariableTypes(templateId, requests);
        return new ResponseData<>(200, "Cập nhật kiểu dữ liệu thành công", null);
    }

    // ------------------ Cập nhật template (name, description) ------------------
    @PutMapping("/{id}")
    public ResponseData<ContractTemplateResponse> updateTemplate(
            @PathVariable Long id,
            @RequestBody ContractTemplateUpdateRequest request
    ) {
        ContractTemplateResponse response = templateService.updateTemplate(id, request);
        return new ResponseData<>(200,"Cập nhật template thành công", response);
    }

    @GetMapping
    public ResponseData<?> getAllTemplates() {
        List<ContractTemplateResponse> templates = templateService.getAllTemplates();
        return new ResponseData<>(200,"Danh sách template", templates);
    }

    @GetMapping("/{templateId}/default-flow")
    public ResponseData<ApprovalFlowResponse> getDefaultFlow(@PathVariable Long templateId) {
        ApprovalFlowResponse res = templateService.getDefaultFlowByTemplate(templateId);
        return new ResponseData<>(200, "Default flow của template", res);
    }

    @PatchMapping("/{id}/status/toggle")
    public ResponseData<ContractTemplateResponse> toggle(@PathVariable Long id) {
        var res = templateService.toggleTemplateStatus(id);
        return new ResponseData<>(200, "Đã chuyển trạng thái template", res);
    }

    @PutMapping("/{id}/status")
    public ResponseData<ContractTemplateResponse> updateStatus(
            @PathVariable Long id,
            @RequestBody UpdateStatusRequest req
    ) {
        var res = templateService.updateTemplateStatus(id, req.getStatus());
        return new ResponseData<>(200, "Cập nhật trạng thái template thành công", res);
    }

    @GetMapping("/status/{status}")
    public ResponseData<List<ContractTemplateResponse>> getAllByStatus(@PathVariable String status) {
        try {
            Status st = Status.valueOf(status.trim().toUpperCase());
            var list = templateService.getAllTemplatesByStatus(st);
            return new ResponseData<>(200, "Danh sách template theo trạng thái " + st, list);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Trạng thái không hợp lệ. Hỗ trợ: ACTIVE, INACTIVE, LOCKED, PENDING"
            );
        }
    }

}
