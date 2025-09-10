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

    @PostMapping("/upload-file")
    public ResponseData<ContractTemplateResponse> uploadTemplateFile(
            @RequestParam MultipartFile file,
            @RequestParam Long accountId
    ) throws Exception {
        ContractTemplateResponse response = templateService.uploadTemplate(file, accountId);
        return new ResponseData<>(200, "Upload template từ file thành công", response);
    }


    @PostMapping("/upload-link")
    public ResponseData<ContractTemplateResponse> uploadTemplateLink(
            @RequestParam String docLink,
            @RequestParam Long accountId
    ) throws Exception {
        ContractTemplateResponse response = templateService.uploadTemplateFromGoogleDoc(docLink, accountId);
        return new ResponseData<>(200, "Upload template từ link Google Docs thành công", response);
    }
}
