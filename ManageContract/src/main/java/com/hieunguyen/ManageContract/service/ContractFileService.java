package com.hieunguyen.ManageContract.service;

import com.hieunguyen.ManageContract.entity.Contract;
import com.hieunguyen.ManageContract.entity.ContractSignature;
import com.hieunguyen.ManageContract.entity.ContractVariableValue;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.List;

@Service
public interface ContractFileService {

    String generateContractFile(Contract contract);

    File getContractFile(Long contractId);

    // Phương thức mới - chỉ cần placeholder
    String embedSignature(String filePath, String imageUrl, String placeholder);

    // Phương thức thêm text phê duyệt
    void addApprovalText(String filePath, String approvalText);

    String generateContractFileWithVariables(Contract contract, List<ContractVariableValue> variableValues);
}