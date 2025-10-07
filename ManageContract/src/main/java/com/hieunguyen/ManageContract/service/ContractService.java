package com.hieunguyen.ManageContract.service;

import com.hieunguyen.ManageContract.common.constants.ContractStatus;
import com.hieunguyen.ManageContract.dto.contract.ContractResponse;
import com.hieunguyen.ManageContract.dto.contract.CreateContractRequest;
import com.hieunguyen.ManageContract.entity.Employee;
import io.micrometer.common.lang.Nullable;

import java.util.List;

public interface ContractService {
    ContractResponse createContract(CreateContractRequest request);
    String previewContract(Long contractId);
    String previewTemplate(CreateContractRequest request);
    List<ContractResponse> getMyContracts(@Nullable ContractStatus status);

    void cancelContract(Long contractId);
    void changeApprover(Long contractId, Long stepId, Long newApproverId, boolean isUserApprover);
    ContractResponse updateContract(Long contractId, CreateContractRequest request);
    ContractResponse getById(Long id);
    void deleteContract(Long contractId);
}
