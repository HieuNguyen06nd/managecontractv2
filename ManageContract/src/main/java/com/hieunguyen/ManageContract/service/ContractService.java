package com.hieunguyen.ManageContract.service;

import com.hieunguyen.ManageContract.dto.contract.ContractResponse;
import com.hieunguyen.ManageContract.dto.contract.CreateContractRequest;
import com.hieunguyen.ManageContract.entity.AuthAccount;
import com.hieunguyen.ManageContract.entity.Contract;

import java.util.Map;

public interface ContractService {
    ContractResponse createContract(CreateContractRequest request, AuthAccount createdBy);

    ContractResponse submitForApproval(Long contractId);
}
