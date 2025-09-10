package com.hieunguyen.ManageContract.service;

import com.hieunguyen.ManageContract.dto.contract.ContractResponse;
import com.hieunguyen.ManageContract.dto.contract.CreateContractRequest;
import com.hieunguyen.ManageContract.entity.AuthAccount;
import com.hieunguyen.ManageContract.entity.Contract;
import com.hieunguyen.ManageContract.entity.User;

import java.util.Map;

public interface ContractService {
    ContractResponse createContract(CreateContractRequest request, User createdBy);
}
