package com.hieunguyen.ManageContract.service;

import com.hieunguyen.ManageContract.dto.contract.ContractResponse;
import com.hieunguyen.ManageContract.dto.contract.CreateContractRequest;
import com.hieunguyen.ManageContract.entity.Employee;

public interface ContractService {
    ContractResponse createContract(CreateContractRequest request);
    String previewContract(Long contractId);
    String previewTemplate(CreateContractRequest request);
}
