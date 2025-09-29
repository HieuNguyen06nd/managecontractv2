package com.hieunguyen.ManageContract.controller;

import com.hieunguyen.ManageContract.dto.ResponseData;
import com.hieunguyen.ManageContract.dto.contractTemplate.ContractTemplateCreateRequest;
import com.hieunguyen.ManageContract.dto.contractTemplate.ContractTemplateResponse;
import com.hieunguyen.ManageContract.dto.contractTemplate.ContractTemplateUpdateRequest;
import com.hieunguyen.ManageContract.dto.templateVariable.TemplatePreviewResponse;
import com.hieunguyen.ManageContract.dto.templateVariable.VariableUpdateRequest;
import com.hieunguyen.ManageContract.security.jwt.SecurityUtil;
import com.hieunguyen.ManageContract.service.ContractTemplateService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

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
    public ResponseEntity<ResponseData<String>> updateVariableTypes(
            @PathVariable Long templateId,
            @RequestBody List<VariableUpdateRequest> requests
    ) {
        templateService.updateVariableTypes(templateId, requests);
        return ResponseEntity.ok(new ResponseData<>(200, "Cập nhật kiểu dữ liệu thành công", null));
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

}
