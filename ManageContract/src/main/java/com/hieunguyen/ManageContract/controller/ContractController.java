package com.hieunguyen.ManageContract.controller;

import com.hieunguyen.ManageContract.entity.AuthAccount;
import com.hieunguyen.ManageContract.entity.Contract;
import com.hieunguyen.ManageContract.repository.AuthAccountRepository;
import com.hieunguyen.ManageContract.service.ContractService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/contracts")
@RequiredArgsConstructor
public class ContractController {
    private final ContractService contractService;
    private final AuthAccountRepository accountRepository;

    @PostMapping("/create")
    public ResponseEntity<?> createContract(
            @RequestParam Long templateId,
            @RequestParam String title,
            @RequestParam Long accountId,
            @RequestBody Map<String, String> variableValues) {

        AuthAccount account = accountRepository.findById(accountId)
                .orElseThrow(() -> new RuntimeException("Account not found"));

        Contract contract = contractService.createContract(templateId, title, variableValues, account);

        return ResponseEntity.ok(contract);
    }
}
