package com.hieunguyen.ManageContract.controller;

import com.hieunguyen.ManageContract.dto.ResponseData;
import com.hieunguyen.ManageContract.dto.contract.ContractResponse;
import com.hieunguyen.ManageContract.dto.contract.CreateContractRequest;
import com.hieunguyen.ManageContract.entity.Employee;
import com.hieunguyen.ManageContract.service.ContractService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/contracts")
@RequiredArgsConstructor
public class ContractController {
    private final ContractService contractService;

    @PostMapping("/create")
    public ResponseData<ContractResponse> createContract(
            @RequestBody CreateContractRequest request) {
        ContractResponse response = contractService.createContract(request);
        return new ResponseData<>(200, "Tạo hợp đồng thành công", response);
    }


    @GetMapping("/{id}/preview")
    public ResponseData<String> preview(@PathVariable Long id) {
        String html = contractService.previewContract(id);
        return new ResponseData<>(200, "Preview file contract",html);
    }

    @PostMapping("/preview")
    public ResponseData<String> previewTemplate(@RequestBody CreateContractRequest request) {
        String html = contractService.previewTemplate(request);
        return new ResponseData<>(200, "Preview template", html);
    }
}

