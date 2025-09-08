package com.hieunguyen.ManageContract.controller;

import com.hieunguyen.ManageContract.dto.ResponseData;
import com.hieunguyen.ManageContract.dto.contract.ContractResponse;
import com.hieunguyen.ManageContract.dto.contract.CreateContractRequest;
import com.hieunguyen.ManageContract.entity.AuthAccount;
import com.hieunguyen.ManageContract.service.ContractService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/contracts")
@RequiredArgsConstructor
public class ContractController {
    private final ContractService contractService;

    @PostMapping("/create")
    public ResponseData<ContractResponse> createContract(
            @RequestBody CreateContractRequest request,
            @AuthenticationPrincipal AuthAccount user) {

        ContractResponse response = contractService.createContract(request, user);
        return new ResponseData<>(200,"Tạo hợp đồng thành công",response);
    }

    @PostMapping("/{id}/submit")
    public ResponseData<ContractResponse> submitForApproval(@PathVariable Long id) {
        return new ResponseData<>(200, "Tạo file trình ký thành công",contractService.submitForApproval(id));
    }
}

