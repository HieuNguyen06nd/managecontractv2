package com.hieunguyen.ManageContract.controller;

import com.hieunguyen.ManageContract.common.constants.ContractStatus;
import com.hieunguyen.ManageContract.dto.ResponseData;
import com.hieunguyen.ManageContract.dto.contract.ContractResponse;
import com.hieunguyen.ManageContract.dto.contract.CreateContractRequest;
import com.hieunguyen.ManageContract.dto.file.FilePayload;
import com.hieunguyen.ManageContract.service.ContractFileService;
import com.hieunguyen.ManageContract.service.ContractService;
import com.hieunguyen.ManageContract.service.ContractViewService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.util.List;

@RestController
@RequestMapping("/api/contracts")
@RequiredArgsConstructor
public class ContractController {

    private final ContractService contractService;
    private final ContractViewService contractViewService; // <-- inject service view PDF
    private final ContractFileService contractFileService;

    @PostMapping("/create")
    public ResponseData<ContractResponse> createContract(@RequestBody CreateContractRequest request) {
        ContractResponse response = contractService.createContract(request);
        return new ResponseData<>(200, "Tạo hợp đồng thành công", response);
    }

    @GetMapping("/{id}/preview")
    public ResponseData<String> preview(@PathVariable Long id) {
        String html = contractService.previewContract(id);
        return new ResponseData<>(200, "Preview file contract", html);
    }

    @PostMapping("/preview")
    public ResponseData<String> previewTemplate(@RequestBody CreateContractRequest request) {
        String html = contractService.previewTemplate(request);
        return new ResponseData<>(200, "Preview template", html);
    }

    // ---------- NEW: View PDF inline ----------
    @GetMapping("/{id}/file")
    public ResponseEntity<Resource> viewContractPdf(@PathVariable Long id) {
        var pdf = contractFileService.getPdfOrConvert(id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .cacheControl(CacheControl.noCache().mustRevalidate())
                .header(HttpHeaders.PRAGMA, "no-cache")
                .header(HttpHeaders.EXPIRES, "0")
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"contract.pdf\"")
                .body(new FileSystemResource(pdf));
    }

    @GetMapping("/{id}/file/download")
    public ResponseEntity<Resource> downloadContractFile(@PathVariable Long id) {
        var pdf = contractFileService.getPdfOrConvert(id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .cacheControl(CacheControl.noCache().mustRevalidate())
                .header(HttpHeaders.PRAGMA, "no-cache")
                .header(HttpHeaders.EXPIRES, "0")
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"contract.pdf\"")
                .body(new FileSystemResource(pdf));
    }

    @GetMapping("/my")
    public ResponseData<List<ContractResponse>> getMyContracts(
            @RequestParam(value = "status", required = false) ContractStatus status) {
        List<ContractResponse> data = contractService.getMyContracts(status);
        return new ResponseData<>(200, "Danh sách hợp đồng của tôi", data);
    }
}
