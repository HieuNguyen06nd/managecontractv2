package com.hieunguyen.ManageContract.controller;

import com.hieunguyen.ManageContract.dto.ResponseData;
import com.hieunguyen.ManageContract.dto.contractTemplate.ContractTemplateResponse;
import com.hieunguyen.ManageContract.entity.AuthAccount;
import com.hieunguyen.ManageContract.repository.AuthAccountRepository;
import com.hieunguyen.ManageContract.service.ContractTemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/templates")
@RequiredArgsConstructor
public class ContractTemplateController {

    private final ContractTemplateService templateService;
    private final AuthAccountRepository accountRepository;

    /**
     * Upload template từ file local hoặc link Google Docs
     */
    @PostMapping("/upload")
    public ResponseEntity<ResponseData<ContractTemplateResponse>> uploadTemplate(
            @RequestParam(required = false) MultipartFile file,
            @RequestParam(required = false) String docLink,
            @RequestParam Long accountId
    ) throws Exception {

        // Lấy account
        AuthAccount account = accountRepository.findById(accountId)
                .orElseThrow(() -> new RuntimeException("Account không tồn tại"));

        ContractTemplateResponse response;

        if (file != null && !file.isEmpty()) {
            // Upload từ file local
            response = templateService.uploadTemplate(file, account);
        } else if (docLink != null && !docLink.isEmpty()) {
            // Upload từ link Google Docs
            response = templateService.uploadTemplateFromGoogleDoc(docLink, account);
        } else {
            return ResponseEntity.badRequest()
                    .body(new ResponseData<>(400, "Phải gửi file hoặc link Google Docs"));
        }

        return ResponseEntity.ok(
                new ResponseData<>(200, "Upload template thành công", response)
        );
    }
}
