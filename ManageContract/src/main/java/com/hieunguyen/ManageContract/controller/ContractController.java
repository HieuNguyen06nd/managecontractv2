package com.hieunguyen.ManageContract.controller;

import com.hieunguyen.ManageContract.common.constants.ContractStatus;
import com.hieunguyen.ManageContract.dto.ResponseData;
import com.hieunguyen.ManageContract.dto.contract.ContractResponse;
import com.hieunguyen.ManageContract.dto.contract.CreateContractRequest;
import com.hieunguyen.ManageContract.dto.file.FilePayload;
import com.hieunguyen.ManageContract.service.ContractService;
import com.hieunguyen.ManageContract.service.ContractViewService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/contracts")
@RequiredArgsConstructor
public class ContractController {

    private final ContractService contractService;
    private final ContractViewService contractViewService;

    @PostMapping("/create")
    public ResponseData<ContractResponse> createContract(@RequestBody CreateContractRequest request) {
        ContractResponse response = contractService.createContract(request);
        return new ResponseData<>(200, "Tạo hợp đồng thành công", response);
    }

    // ---------- XEM PDF TRÌNH DUYỆT ----------
    @GetMapping("/{id}/view")
    public ResponseEntity<Resource> viewContractPdf(@PathVariable Long id) {
        FilePayload filePayload = contractViewService.viewPdf(id);

        return ResponseEntity.ok()
                .contentType(filePayload.getMediaType())
                .cacheControl(CacheControl.noCache().mustRevalidate())
                .header(HttpHeaders.PRAGMA, "no-cache")
                .header(HttpHeaders.EXPIRES, "0")
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" + filePayload.getFilename() + "\"")
                .body(filePayload.getResource());
    }

    // ---------- TẢI XUỐNG PDF ----------
    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> downloadContractFile(@PathVariable Long id) {
        FilePayload filePayload = contractViewService.downloadOriginal(id);

        return ResponseEntity.ok()
                .contentType(filePayload.getMediaType())
                .cacheControl(CacheControl.noCache().mustRevalidate())
                .header(HttpHeaders.PRAGMA, "no-cache")
                .header(HttpHeaders.EXPIRES, "0")
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + filePayload.getFilename() + "\"")
                .body(filePayload.getResource());
    }

    @GetMapping("/my")
    public ResponseData<List<ContractResponse>> getMyContracts(
            @RequestParam(value = "status", required = false) ContractStatus status) {
        List<ContractResponse> data = contractService.getMyContracts(status);
        return new ResponseData<>(200, "Danh sách hợp đồng của tôi", data);
    }

    @PutMapping("/{id}")
    public ResponseData<ContractResponse> updateContract(
            @PathVariable Long id, @RequestBody CreateContractRequest request) {
        ContractResponse response = contractService.updateContract(id, request);
        return new ResponseData<>(200, "Cập nhật hợp đồng thành công", response);
    }

    // ---------- THAY ĐỔI NGƯỜI PHÊ DUYỆT ----------
    @PutMapping("/{contractId}/approver/{stepId}")
    public ResponseData<String> changeApprover(
            @PathVariable Long contractId,
            @PathVariable Long stepId,
            @RequestParam Long newApproverId,
            @RequestParam boolean isUserApprover) {
        contractService.changeApprover(contractId, stepId, newApproverId, isUserApprover);
        return new ResponseData<>(200, "Thay đổi người ký thành công", null);
    }

    @GetMapping("/{id}")
    public ResponseData<ContractResponse> getById(@PathVariable Long id) {
        ContractResponse res = contractService.getById(id);
        return new ResponseData<>(200, "OK", res);
    }

    // Xóa hợp đồng theo id
    @DeleteMapping("/{contractId}")
    public ResponseData<Void> deleteContract(@PathVariable Long contractId) {
        contractService.deleteContract(contractId);
        return new ResponseData<>(200, "Xoá thành công");
    }

    // Hủy hợp đồng theo id
    @PutMapping("/{contractId}/cancel")
    public ResponseData<Void> cancelContract(@PathVariable Long contractId) {
        contractService.cancelContract(contractId);
        return new ResponseData<>(200, "Huỷ thành công");
    }

    @PostMapping("/preview-pdf")
    public ResponseEntity<byte[]> previewPdf(@RequestBody CreateContractRequest req) {
        byte[] pdf = contractService.previewContractPdf(req);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=preview.pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }
}